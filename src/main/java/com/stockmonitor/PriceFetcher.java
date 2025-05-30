package com.stockmonitor;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
// Basit JSON işleme için bir regex veya string manipülasyonu kullanılacak.
// Daha gelişmiş projeler için Gson veya Jackson gibi bir kütüphane önerilir.

public class PriceFetcher {

    // Finnhub API Anahtarı
    // private static final String API_KEY = "d0rk7apr01qumepeo3f0d0rk7apr01qumepeo3fg"; // Kaldırılacak veya yorum satırı yapılacak
    private static String FINNHUB_API_KEY = System.getenv("FINNHUB_API_KEY");
    private static final String API_URL_TEMPLATE_QUOTE = "https://finnhub.io/api/v1/quote?symbol=%s&token=%s";
    private static final String API_URL_TEMPLATE_CANDLE = "https://finnhub.io/api/v1/stock/candle?symbol=%s&resolution=%s&from=%d&to=%d&token=%s";
    private static final String API_URL_TEMPLATE_CRYPTO_CANDLE = "https://finnhub.io/api/v1/crypto/candle?symbol=%s&resolution=%s&from=%d&to=%d&token=%s";
    private static final String SYMBOL_PARAM = "&symbol=";

    public PriceFetcher() {
        // API anahtarının yüklenip yüklenmediğini kontrol edelim
        if (FINNHUB_API_KEY == null || FINNHUB_API_KEY.trim().isEmpty()) {
            System.err.println("HATA: FINNHUB_API_KEY ortam değişkeni ayarlanmamış.");
            // Burada kullanıcıya bir uyarı mesajı gösterilebilir veya uygulama sonlandırılabilir.
            // Şimdilik sadece konsola yazdırıyoruz ve null key ile devam etmeye çalışabilir (API hatası verecektir)
        }
    }

    public double fetchPrice(String symbol) throws IOException {
        if (symbol == null || symbol.trim().isEmpty()) {
            throw new IllegalArgumentException("Hisse senedi sembolü boş olamaz.");
        }

        String apiUrlString = String.format(API_URL_TEMPLATE_QUOTE, symbol.toUpperCase(), FINNHUB_API_KEY);
        URL url = new URL(apiUrlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(10000); // 10 saniye bağlantı zaman aşımı
        connection.setReadTimeout(10000);    // 10 saniye okuma zaman aşımı

        int responseCode = connection.getResponseCode();

        if (responseCode == HttpURLConnection.HTTP_OK) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();
            return parsePriceFromFinnhubQuoteResponse(response.toString(), symbol);
        } else {
            String errorResponse = "";
            if (connection.getErrorStream() != null) {
                try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(connection.getErrorStream()))) {
                    StringBuilder errorBuilder = new StringBuilder();
                    String errorLine;
                    while ((errorLine = errorReader.readLine()) != null) {
                        errorBuilder.append(errorLine);
                    }
                    errorResponse = errorBuilder.toString();
                }
            } else {
                errorResponse = connection.getResponseMessage(); // Hata akışı yoksa mesajı al
            }
            throw new IOException("Finnhub API (/quote) isteği başarısız oldu. HTTP Kodu: " + responseCode + ". Sembol: " + symbol + ". Detay: " + errorResponse);
        }
    }

    private double parsePriceFromFinnhubQuoteResponse(String jsonResponse, String symbol) throws IOException {
        if (jsonResponse == null || jsonResponse.trim().isEmpty() || jsonResponse.trim().equals("{}") || jsonResponse.trim().equalsIgnoreCase("Symbol not supported")) {
            // System.err.println("Finnhub API'den (/quote) boş, geçersiz veya desteklenmeyen sembol yanıtı (Sembol: " + symbol + "): " + jsonResponse);
            throw new IOException("Finnhub API'den (/quote) boş, geçersiz veya desteklenmeyen sembol yanıtı alındı: " + symbol + ". Yanıt: " + jsonResponse);
        }
        
        try {
            JSONObject jsonObject = new JSONObject(jsonResponse);
            // Finnhub bazen güncel fiyatı (c) 0 olarak dönebilir, bu durumda pc (previous close) kullanılır.
            // Kripto için de benzer bir durum olabilir.
            double currentPrice = jsonObject.optDouble("c", 0.0);
            double previousClose = jsonObject.optDouble("pc", 0.0);

            if (currentPrice != 0.0) {
                return currentPrice;
            } else if (previousClose != 0.0) {
                // System.out.println(symbol + " için güncel fiyat (c) 0, önceki kapanış (pc) kullanılıyor: " + previousClose);
                return previousClose;
            } else {
                 System.err.println("Finnhub fiyati parse edilemedi ('c' ve 'pc' 0 veya yok), Sembol: " + symbol + ", Yanıt: " + jsonResponse.substring(0, Math.min(jsonResponse.length(), 300)));
                 throw new IOException("'c' veya 'pc' anahtarı Finnhub JSON yanıtından uygun bir değerle parse edilemedi. Sembol: " + symbol);
            }
        } catch (JSONException e) {
            System.err.println("Finnhub (/quote) JSON parse hatası, Sembol: " + symbol + ", Yanıt: " + jsonResponse.substring(0, Math.min(jsonResponse.length(), 300)) + ", Hata: " + e.getMessage());
            throw new IOException("Finnhub (/quote) JSON yanıtı parse edilemedi. Sembol: " + symbol, e);
        }
    }

    
    public static void main(String[] args) {
        PriceFetcher fetcher = new PriceFetcher();
        System.out.println("Finnhub API Test Başlatılıyor...");
        long toTimestamp = System.currentTimeMillis() / 1000;
        long fromTimestamp24h = toTimestamp - (24 * 60 * 60); // Son 24 saat
        String resolution15min = "15";

        // Test Sembolleri
        String aaplSymbol = "AAPL";
        String btcSymbol = "BINANCE:BTCUSDT"; // Kripto için örnek sembol
        String olmayanSymbol = "XYZ123NONEXISTENT";

        // Test 1: Anlık Fiyat (AAPL)
        System.out.println("\n--- Test 1: Anlık Fiyat (" + aaplSymbol + ") ---");
        try {
            double price = fetcher.fetchPrice(aaplSymbol);
            System.out.println(aaplSymbol + " Anlık Fiyat: " + price);
        } catch (Exception e) {
            System.err.println(aaplSymbol + " Anlık Fiyat için HATA: " + e.getMessage());
        }

        // Test 2: Mum Verisi (AAPL) - Son 24 saat, 15 dakikalık çözünürlük
        System.out.println("\n--- Test 2: Mum Verisi (" + aaplSymbol + ") Son 24 Saat --- (isCrypto: false)");
        try {
            String candleJson = fetcher.fetchCandleDataJSON(aaplSymbol, resolution15min, fromTimestamp24h, toTimestamp, false);
            if (candleJson != null) {
                System.out.println(aaplSymbol + " Ham Mum Verisi (JSON ilk 300 krktr): " + candleJson.substring(0, Math.min(candleJson.length(), 300)) + "...");
                List<CandleData> candles = fetcher.parseCandleData(candleJson);
                System.out.println(aaplSymbol + " Parse Edilen Mum Sayısı: " + candles.size());
                if (!candles.isEmpty()) {
                    System.out.println(aaplSymbol + " İlk Mum: " + candles.get(0));
                    System.out.println(aaplSymbol + " Son Mum: " + candles.get(candles.size()-1));
                }
            } else {
                System.out.println(aaplSymbol + " için mum verisi alınamadı (null döndü).");
            }
        } catch (Exception e) {
            System.err.println(aaplSymbol + " Mum Verisi için HATA: " + e.getMessage());
        }

        // Test 3: Anlık Fiyat (BINANCE:BTCUSDT)
        System.out.println("\n--- Test 3: Anlık Fiyat (" + btcSymbol + ") ---");
        try {
            double price = fetcher.fetchPrice(btcSymbol);
            System.out.println(btcSymbol + " Anlık Fiyat: " + price);
        } catch (Exception e) {
            System.err.println(btcSymbol + " Anlık Fiyat için HATA: " + e.getMessage());
        }

        // Test 4: Mum Verisi (BINANCE:BTCUSDT) - Son 24 saat, 15 dakikalık çözünürlük
        // Kripto paralar için /crypto/candle endpoint'i kullanılacak.
        System.out.println("\n--- Test 4: Mum Verisi (" + btcSymbol + ") Son 24 Saat --- (isCrypto: true)");
        try {
            String candleJson = fetcher.fetchCandleDataJSON(btcSymbol, resolution15min, fromTimestamp24h, toTimestamp, true);
            if (candleJson != null) {
                System.out.println(btcSymbol + " Ham Mum Verisi (JSON ilk 300 krktr): " + candleJson.substring(0, Math.min(candleJson.length(), 300)) + "...");
                List<CandleData> candles = fetcher.parseCandleData(candleJson);
                System.out.println(btcSymbol + " Parse Edilen Mum Sayısı: " + candles.size());
                if (!candles.isEmpty()) {
                    System.out.println(btcSymbol + " İlk Mum: " + candles.get(0));
                    System.out.println(btcSymbol + " Son Mum: " + candles.get(candles.size()-1));
                }
            } else {
                System.out.println(btcSymbol + " için mum verisi alınamadı (null döndü).");
            }
        } catch (Exception e) {
            System.err.println(btcSymbol + " Mum Verisi için HATA: " + e.getMessage());
        }
        
        // Test 5: Anlık Fiyat (Olmayan Sembol)
        System.out.println("\n--- Test 5: Anlık Fiyat (" + olmayanSymbol + ") ---");
        try {
            double price = fetcher.fetchPrice(olmayanSymbol);
            System.out.println(olmayanSymbol + " Anlık Fiyat: " + price);
        } catch (Exception e) {
            System.err.println(olmayanSymbol + " Anlık Fiyat için HATA: " + e.getMessage());
        }

        // Test 6: Mum Verisi (Olmayan Sembol)
        System.out.println("\n--- Test 6: Mum Verisi (" + olmayanSymbol + ") Son 24 Saat --- (isCrypto: false)");
        try {
            String candleJson = fetcher.fetchCandleDataJSON(olmayanSymbol, resolution15min, fromTimestamp24h, toTimestamp, false);
             if (candleJson != null) {
                System.out.println(olmayanSymbol + " Ham Mum Verisi (JSON ilk 300 krktr): " + candleJson.substring(0, Math.min(candleJson.length(), 300)) + "...");
                List<CandleData> candles = fetcher.parseCandleData(candleJson);
                System.out.println(olmayanSymbol + " Parse Edilen Mum Sayısı: " + candles.size());
            } else {
                System.out.println(olmayanSymbol + " için mum verisi alınamadı (null döndü).");
            }
        } catch (Exception e) {
            System.err.println(olmayanSymbol + " Mum Verisi için HATA: " + e.getMessage());
        }

        System.out.println("\nFinnhub API Testi Tamamlandı.");
    }
    
    // Mum grafiği verilerini (OHLC) çekmek için yeni metot
    public String fetchCandleDataJSON(String symbol, String resolution, long fromTimestamp, long toTimestamp, boolean isCrypto) throws IOException {
        if (symbol == null || symbol.trim().isEmpty()) {
            throw new IllegalArgumentException("Sembol boş olamaz.");
        }
        if (resolution == null || resolution.trim().isEmpty()) {
            throw new IllegalArgumentException("Çözünürlük (resolution) boş olamaz.");
        }

        String templateUrl = isCrypto ? API_URL_TEMPLATE_CRYPTO_CANDLE : API_URL_TEMPLATE_CANDLE;
        String endpointName = isCrypto ? "/crypto/candle" : "/stock/candle";

        String apiUrlString = String.format(templateUrl, 
                                            symbol.toUpperCase(), 
                                            resolution, 
                                            fromTimestamp, 
                                            toTimestamp, 
                                            FINNHUB_API_KEY);

        URL url = new URL(apiUrlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(15000); 
        connection.setReadTimeout(15000);

        int responseCode = connection.getResponseCode();

        if (responseCode == HttpURLConnection.HTTP_OK) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();
            String jsonResponse = response.toString();
            // Finnhub "no_data" durumunu kontrol et
            if (jsonResponse.contains("\"s\":\"no_data\"") || jsonResponse.equals("{\"s\":\"no_data\"}")) {
                System.err.println("Finnhub API'den (" + endpointName + ") veri yok (no_data), Sembol: " + symbol + ", Çözünürlük: " + resolution);
                return null; 
            }
            return jsonResponse;
        } else {
            String errorResponse = "";
            if (connection.getErrorStream() != null) {
                try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(connection.getErrorStream()))) {
                    StringBuilder errorBuilder = new StringBuilder();
                    String errorLine;
                    while ((errorLine = errorReader.readLine()) != null) {
                        errorBuilder.append(errorLine);
                    }
                    errorResponse = errorBuilder.toString();
                }
            } else {
                 errorResponse = connection.getResponseMessage();
            }
            throw new IOException("Finnhub Mum Verisi API (" + endpointName + ") isteği başarısız oldu. HTTP Kodu: " + responseCode + ". Sembol: " + symbol + ". Detay: " + errorResponse);
        }
    }

    public List<CandleData> parseCandleData(String jsonString) throws IOException, JSONException {
        List<CandleData> candleDataList = new ArrayList<>();
        if (jsonString == null || jsonString.trim().isEmpty()) {
            return candleDataList; 
        }

        JSONObject jsonObject = new JSONObject(jsonString);
        
        if (!jsonObject.optString("s", "").equals("ok")) {
            System.err.println("Finnhub mum verisi durumu 'ok' değil veya 's' alanı eksik. Yanıt: " + jsonString.substring(0, Math.min(jsonString.length(), 300)));
            return candleDataList; 
        }

        JSONArray openPrices = jsonObject.optJSONArray("o");
        JSONArray highPrices = jsonObject.optJSONArray("h");
        JSONArray lowPrices = jsonObject.optJSONArray("l");
        JSONArray closePrices = jsonObject.optJSONArray("c");
        JSONArray volumes = jsonObject.optJSONArray("v");
        JSONArray timestamps = jsonObject.optJSONArray("t");

        if (timestamps == null || openPrices == null || highPrices == null || lowPrices == null || closePrices == null || volumes == null) {
             throw new IOException("Finnhub mum verisi JSON dizilerinden biri veya birkaçı null geldi! Yanıt: " + jsonString.substring(0, Math.min(jsonString.length(), 300)));
        }

        int length = timestamps.length();
        if (length == 0) { // Hiç mum verisi gelmemişse (diziler boşsa)
             System.out.println("Parse edilecek mum verisi bulunamadı (diziler boş). Sembol muhtemelen bu zaman aralığında veri sunmuyor.");
             return candleDataList;
        }
        
        if (openPrices.length() != length || highPrices.length() != length || 
            lowPrices.length() != length || closePrices.length() != length || volumes.length() != length) {
            throw new IOException("Finnhub mum verisi dizilerinin uzunlukları eşleşmiyor! Yanıt: " + jsonString.substring(0, Math.min(jsonString.length(), 300)));
        }

        for (int i = 0; i < length; i++) {
            long timestamp = timestamps.optLong(i, -1);
            double open = openPrices.optDouble(i, Double.NaN);
            double high = highPrices.optDouble(i, Double.NaN);
            double low = lowPrices.optDouble(i, Double.NaN);
            double close = closePrices.optDouble(i, Double.NaN);
            double volume = volumes.optDouble(i, Double.NaN);
            
            // Geçerli bir mum olup olmadığını kontrol et (NaN veya -1 olmamalı)
            if (timestamp != -1 && !Double.isNaN(open) && !Double.isNaN(high) && !Double.isNaN(low) && !Double.isNaN(close) && !Double.isNaN(volume)) {
                 candleDataList.add(new CandleData(timestamp, open, high, low, close, volume));
            } else {
                 System.out.println("Geçersiz mum verisi atlandı (index " + i + "): t=" + timestamp + ", o=" + open + ", h=" + high + ", l=" + low + ", c=" + close + ", v=" + volume);
            }
        }
        return candleDataList;
    }

    // Test için fetchCandleDataJSON ve parseCandleData'yı kullanan örnek bir main metodu eklenebilir.
    /*
    public static void main(String[] args) {
        PriceFetcher fetcher = new PriceFetcher();
        try {
            long toTimestamp = System.currentTimeMillis() / 1000; // Şu anki zaman
            long fromTimestamp = toTimestamp - (24 * 60 * 60); // Son 24 saat (örnek)
            String jsonData = fetcher.fetchCandleDataJSON("AAPL", "15", fromTimestamp, toTimestamp);
            if (jsonData != null) {
                List<CandleData> candles = fetcher.parseCandleData(jsonData);
                System.out.println("AAPL için mum sayısı: " + candles.size());
                for (CandleData candle : candles) {
                    System.out.println(candle);
                }
            }
        } catch (Exception e) {
            System.err.println("Mum verisi işlenirken hata: " + e.getMessage());
            e.printStackTrace();
        }
    }
    */
} 