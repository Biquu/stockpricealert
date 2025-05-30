package com.stockmonitor;

import com.stockmonitor.listeners.AlertListener;
import com.stockmonitor.listeners.GraphDataListener;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

public class StockWatcherThread implements Runnable {

    private final String symbol;
    private final String thresholdString; // Örn: ">150" veya "<700"
    private final PriceFetcher priceFetcher;
    private final AlertListener alertListener;
    private final GraphDataListener graphDataListener;
    private volatile boolean running = true;
    private final long fetchIntervalMillis;

    private double thresholdValue = -1; // Eşik değeri
    private String comparisonOperator = ""; // ">", "<", ">=", "<="

    public StockWatcherThread(String symbol, String thresholdString, PriceFetcher priceFetcher,
                              AlertListener alertListener, GraphDataListener graphDataListener,
                              long fetchIntervalSeconds) {
        this.symbol = symbol;
        this.thresholdString = thresholdString != null ? thresholdString.trim() : "";
        this.priceFetcher = priceFetcher;
        this.alertListener = alertListener;
        this.graphDataListener = graphDataListener;
        this.fetchIntervalMillis = fetchIntervalSeconds * 1000;
        parseThreshold();
    }

    private void parseThreshold() {
        if (thresholdString.isEmpty()) {
            return; // Eşik belirtilmemiş
        }
        try {
            if (thresholdString.startsWith(">")) {
                comparisonOperator = ">";
                thresholdValue = Double.parseDouble(thresholdString.substring(1).trim().replace(',', '.'));
            } else if (thresholdString.startsWith("<")) {
                comparisonOperator = "<";
                thresholdValue = Double.parseDouble(thresholdString.substring(1).trim().replace(',', '.'));
            } else if (thresholdString.startsWith(">=")) {
                comparisonOperator = ">=";
                thresholdValue = Double.parseDouble(thresholdString.substring(2).trim().replace(',', '.'));
            } else if (thresholdString.startsWith("<=")) {
                comparisonOperator = "<=";
                thresholdValue = Double.parseDouble(thresholdString.substring(2).trim().replace(',', '.'));
            } else {
                // Operatör yoksa, düz sayı olarak kabul et ve "<=" olarak varsay (kullanıcının beklentisine göre)
                try {
                    thresholdValue = Double.parseDouble(thresholdString.trim().replace(',', '.'));
                    comparisonOperator = "<="; // Varsayılan operatör "altına düşerse veya eşitse"
                    System.out.println("Bilgi: " + symbol + " için eşik '" + thresholdString + "' operatörsüz girildi, '<=' olarak varsayıldı.");
                } catch (NumberFormatException nfe) {
                    System.err.println("Geçersiz eşik formatı (operatörsüz de sayı değil): " + thresholdString + " Sembol: " + symbol);
                    thresholdValue = -1; // Geçersiz kıl
                    comparisonOperator = "";
                }
            }
        } catch (NumberFormatException e) {
            System.err.println("Eşik değeri parse edilemedi: " + thresholdString + " Sembol: " + symbol + " Hata: " + e.getMessage());
            thresholdValue = -1; // Geçersiz kıl
            comparisonOperator = "";
        }
    }

    @Override
    public void run() {
        System.out.println(symbol + " için izleme iş parçacığı başlatıldı. Eşik: '" + thresholdString + "', Aralık: " + (fetchIntervalMillis/1000) + " sn.");
        while (running) {
            try {
                double currentPrice = priceFetcher.fetchPrice(symbol);
                LocalDateTime localDateTime = LocalDateTime.now();

                System.out.println(String.format("[%s] %s Fiyatı: %.4f", localDateTime.toLocalTime(), symbol, currentPrice));

                if (graphDataListener != null) {
                    Date dateForGraph = Date.from(localDateTime.atZone(ZoneId.systemDefault()).toInstant());
                    graphDataListener.onPriceUpdate(symbol, currentPrice, dateForGraph);
                }

                checkThreshold(currentPrice);

                Thread.sleep(fetchIntervalMillis);
            } catch (IOException e) {
                System.err.println(symbol + " için fiyat alınırken hata: " + e.getMessage());
                if (alertListener != null) {
                    // API hatası için de bir uyarı verilebilir (opsiyonel)
                    // alertListener.onAlertTriggered(symbol, -1, "API HATASI", "Fiyat alınamadı: " + e.getMessage());
                }
                // Hata durumunda biraz daha uzun bekle (API limitlerini aşmamak için)
                try {
                    Thread.sleep(Math.min(fetchIntervalMillis * 2, 60000)); // En fazla 60sn
                } catch (InterruptedException ie) {
                    running = false; // Kesintiye uğrarsa durdur
                }
            } catch (InterruptedException e) {
                running = false; // Thread kesintiye uğrarsa döngüyü sonlandır
                System.out.println(symbol + " için izleme iş parçacığı kesildi.");
            }
        }
        System.out.println(symbol + " için izleme iş parçacığı durduruldu.");
    }

    private void checkThreshold(double currentPrice) {
        if (thresholdValue == -1 || comparisonOperator.isEmpty() || alertListener == null) {
            return; // Eşik tanımlı değil veya dinleyici yok
        }

        boolean alert = false;
        switch (comparisonOperator) {
            case ">":
                if (currentPrice > thresholdValue) alert = true;
                break;
            case "<":
                if (currentPrice < thresholdValue) alert = true;
                break;
            case ">=":
                if (currentPrice >= thresholdValue) alert = true;
                break;
            case "<=":
                if (currentPrice <= thresholdValue) alert = true;
                break;
        }

        if (alert) {
            String alertMessage = String.format("ALARM: %s %s %.4f (Mevcut: %.4f)", 
                                              symbol, thresholdString, thresholdValue, currentPrice);
            alertListener.onAlertTriggered(symbol, currentPrice, thresholdString, alertMessage);
            // TODO: Aynı alarmın sürekli tetiklenmemesi için bir mekanizma eklenebilir (örn: bir süre bekleme)
        }
    }

    public void stopWatching() {
        this.running = false;
    }

    public String getSymbol() {
        return symbol;
    }
} 