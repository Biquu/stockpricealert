package com.stockmonitor;

import org.json.JSONObject;
import org.json.JSONException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class PriceFetcher {

    private static String FINNHUB_API_KEY = System.getenv("FINNHUB_API_KEY");
    private static final String API_URL_TEMPLATE_QUOTE = "https://finnhub.io/api/v1/quote?symbol=%s&token=%s";

    public PriceFetcher() {
        if (FINNHUB_API_KEY == null || FINNHUB_API_KEY.trim().isEmpty()) {
            System.err.println("HATA: FINNHUB_API_KEY ortam değişkeni ayarlanmamış.");
        }
    }

    public double fetchPrice(String symbol) throws IOException {
        if (symbol == null || symbol.trim().isEmpty()) {
            throw new IllegalArgumentException("Hisse senedi sembolü boş olamaz.");
        }
        if (FINNHUB_API_KEY == null || FINNHUB_API_KEY.trim().isEmpty()){
            throw new IOException("Finnhub API anahtarı ayarlanmamış veya boş.");
        }

        String apiUrlString = String.format(API_URL_TEMPLATE_QUOTE, symbol.toUpperCase(), FINNHUB_API_KEY);
        URL url = new URL(apiUrlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(10000); 
        connection.setReadTimeout(10000);    

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
                errorResponse = connection.getResponseMessage(); 
            }
            throw new IOException("Finnhub API (/quote) isteği başarısız oldu. HTTP Kodu: " + responseCode + ". Sembol: " + symbol + ". Detay: " + errorResponse);
        }
    }

    private double parsePriceFromFinnhubQuoteResponse(String jsonResponse, String symbol) throws IOException {
        if (jsonResponse == null || jsonResponse.trim().isEmpty() || jsonResponse.trim().equals("{}") || jsonResponse.trim().equalsIgnoreCase("Symbol not supported")) {
            throw new IOException("Finnhub API'den (/quote) boş, geçersiz veya desteklenmeyen sembol yanıtı alındı: " + symbol + ". Yanıt: " + jsonResponse);
        }
        
        try {
            JSONObject jsonObject = new JSONObject(jsonResponse);
            double currentPrice = jsonObject.optDouble("c", 0.0);
            double previousClose = jsonObject.optDouble("pc", 0.0);

            if (currentPrice != 0.0) {
                return currentPrice;
            } else if (previousClose != 0.0) {
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
        System.out.println("FINNHUB_API_KEY ortam değişkeni: " + System.getenv("FINNHUB_API_KEY"));
        
        PriceFetcher fetcher = new PriceFetcher();
        if (FINNHUB_API_KEY == null || FINNHUB_API_KEY.trim().isEmpty()) {
            System.err.println("TEST BAŞARISIZ: FINNHUB_API_KEY ayarlanmamış. Lütfen .env dosyasını veya ortam değişkenlerini kontrol edin.");
            System.err.println("Bu testi IDE üzerinden veya 'calistir.bat' benzeri bir betikle FINNHUB_API_KEY ayarlanmış şekilde çalıştırın.");
            return;
        }
        
        System.out.println("Finnhub API Anlık Fiyat Test Başlatılıyor...");
        
        String aaplSymbol = "AAPL";
        String btcSymbol = "BINANCE:BTCUSDT"; 
        String olmayanSymbol = "XYZ123NONEXISTENT";

        System.out.println("\n--- Test 1: Anlık Fiyat (" + aaplSymbol + ") ---");
        try {
            double price = fetcher.fetchPrice(aaplSymbol);
            System.out.println(aaplSymbol + " Anlık Fiyat: " + price);
        } catch (Exception e) {
            System.err.println(aaplSymbol + " Anlık Fiyat için HATA: " + e.getMessage());
        }

        System.out.println("\n--- Test 2: Anlık Fiyat (" + btcSymbol + ") ---");
        try {
            double price = fetcher.fetchPrice(btcSymbol);
            System.out.println(btcSymbol + " Anlık Fiyat: " + price);
        } catch (Exception e) {
            System.err.println(btcSymbol + " Anlık Fiyat için HATA: " + e.getMessage());
        }
        
        System.out.println("\n--- Test 3: Anlık Fiyat (Olmayan Sembol: " + olmayanSymbol + ") ---");
        try {
            double price = fetcher.fetchPrice(olmayanSymbol);
            System.out.println(olmayanSymbol + " Anlık Fiyat: " + price);
        } catch (Exception e) {
            System.err.println(olmayanSymbol + " Anlık Fiyat için HATA: " + e.getMessage());
        }

        System.out.println("\nFinnhub API Anlık Fiyat Testi Tamamlandı.");
    }
} 