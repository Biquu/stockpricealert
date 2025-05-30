package com.stockmonitor;

// import com.stockmonitor.listeners.AlertListener; // Kaldırıldı
import com.stockmonitor.listeners.GraphDataListener;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.concurrent.TimeUnit;

public class StockWatcherThread implements Runnable {

    private final StockConfig stockConfig; // String symbol ve rawThresholdInput yerine StockConfig
    private final PriceFetcher priceFetcher;
    private final AlertManager alertManager;
    private final GraphDataListener graphDataListener;
    private volatile boolean running = true;
    private double previousClosePrice = -1; // Önceki kapanış fiyatını saklamak için
    private boolean firstDataPoint = true;

    private static final long FETCH_INTERVAL_SECONDS = 10; // Fiyat çekme aralığı (saniye)

    // Constructor güncellendi, AlertManager eklendi, GraphType kaldırıldı
    public StockWatcherThread(StockConfig stockConfig, 
                              PriceFetcher priceFetcher,
                              AlertManager alertManager,
                              GraphDataListener graphDataListener) {
        this.stockConfig = stockConfig;
        this.priceFetcher = priceFetcher;
        this.alertManager = alertManager;
        this.graphDataListener = graphDataListener;
    }

    @Override
    public void run() {
        String symbol = stockConfig.getSymbol();
        if (symbol == null || symbol.trim().isEmpty()) {
            System.err.println("StockWatcherThread: İzlenecek sembol belirtilmemiş.");
            return;
        }
        alertManager.logSystemMessage(symbol + " için izleme başladı.");

        while (running) {
            try {
                double currentPrice = priceFetcher.fetchPrice(symbol);
                Date timestamp = new Date();

                if (currentPrice != -1 && !Double.isNaN(currentPrice)) {
                    // Anlık fiyattan OHLC verisi türet
                    double open, high, low, close;
                    close = currentPrice;

                    if (firstDataPoint) {
                        open = currentPrice;
                        high = currentPrice;
                        low = currentPrice;
                        firstDataPoint = false;
                    } else {
                        open = previousClosePrice;
                        high = Math.max(open, currentPrice);
                        low = Math.min(open, currentPrice);
                    }
                    
                    graphDataListener.onOHLCDataUpdate(symbol, timestamp, open, high, low, close);
                    previousClosePrice = currentPrice; 

                    checkAlerts(symbol, currentPrice);
                } else {
                    // alertManager.logAlert(symbol, "API'den fiyat alınamadı veya geçersiz fiyat.");
                    alertManager.queueAlert(symbol, "API'den fiyat alınamadı veya geçersiz fiyat.");
                }

                TimeUnit.SECONDS.sleep(FETCH_INTERVAL_SECONDS);
            } catch (InterruptedException e) {
                running = false;
                Thread.currentThread().interrupt(); 
                alertManager.logSystemMessage(symbol + " için izleme kesildi (InterruptedException).");
            } catch (Exception e) {
                // alertManager.logAlert(symbol, "Fiyat alınırken bir hata oluştu: " + e.getMessage());
                alertManager.queueAlert(symbol, "Fiyat alınırken bir hata oluştu: " + e.getMessage());
                try {
                    TimeUnit.SECONDS.sleep(FETCH_INTERVAL_SECONDS * 2); 
                } catch (InterruptedException ie) {
                    running = false;
                    Thread.currentThread().interrupt();
                }
            }
        }
        alertManager.logSystemMessage(symbol + " için izleme durdu.");
        graphDataListener.clearGraph(symbol); // İzleme durduğunda grafiği temizle
    }

    private void checkAlerts(String symbol, double currentPrice) {
        String thresholdConfig = stockConfig.getThreshold();
        if (thresholdConfig == null || thresholdConfig.trim().isEmpty() || !thresholdConfig.contains("@")) {
            return; // Eşik yapılandırılmamış veya format hatalı
        }

        String[] parts = thresholdConfig.split("@", 2);
        String condition = parts[0];
        double targetValue;
        try {
            targetValue = Double.parseDouble(parts[1]);
        } catch (NumberFormatException e) {
            System.err.println("Hata: " + symbol + " için geçersiz hedef değer formatı: " + parts[1]);
            return;
        }

        boolean alertTriggered = false;
        String alertMessage = "";

        // Önceki fiyatı da kullanarak kesişme kontrolü için (şimdilik basitçe tutuluyor)
        // Daha karmaşık kesişme senaryoları için önceki fiyatın da takip edilmesi gerekebilir.

        switch (condition) {
            case "Fiyat > Değer":
                if (currentPrice > targetValue) {
                    alertTriggered = true;
                    alertMessage = String.format("%s fiyatı (%.4f) > hedef (%.4f)", symbol, currentPrice, targetValue);
                }
                break;
            case "Fiyat < Değer":
                if (currentPrice < targetValue) {
                    alertTriggered = true;
                    alertMessage = String.format("%s fiyatı (%.4f) < hedef (%.4f)", symbol, currentPrice, targetValue);
                }
                break;
            case "Fiyat Kesişir (Yukarı)": // Basitçe, önceki < hedef && mevcut > hedef
                // Bu, daha sağlam bir kesişme tespiti için 'previousPrice' gerektirir.
                // Şimdilik, fiyat hedefin üzerine çıktığında tetiklenecek şekilde basitleştirilmiştir.
                // Eğer fiyat zaten hedefin üzerindeyse ve bu koşul seçiliyse, her seferinde tetiklenmemesi için ek mantık gerekir.
                // Şimdilik: Eğer bir önceki değer hedefin altındaysa ve şimdiki değer hedefin üstündeyse (veya eşitse)
                if (previousClosePrice != -1 && previousClosePrice < targetValue && currentPrice >= targetValue) {
                    alertTriggered = true;
                    alertMessage = String.format("%s fiyatı (%.4f) hedefi (%.4f) yukarı yönlü kesti", symbol, currentPrice, targetValue);
                }
                break;
            case "Fiyat Kesişir (Aşağı)": // Basitçe, önceki > hedef && mevcut < hedef
                if (previousClosePrice != -1 && previousClosePrice > targetValue && currentPrice <= targetValue) {
                    alertTriggered = true;
                    alertMessage = String.format("%s fiyatı (%.4f) hedefi (%.4f) aşağı yönlü kesti", symbol, currentPrice, targetValue);
                }
                break;
        }

        if (alertTriggered) {
            alertManager.queueAlert(symbol, alertMessage);
        }
    }

    public void stopWatching() {
        this.running = false;
    }

    public String getSymbol() {
        return stockConfig.getSymbol();
    }
} 