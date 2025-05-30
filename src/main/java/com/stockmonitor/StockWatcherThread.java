package com.stockmonitor;

// import com.stockmonitor.listeners.AlertListener; // Removed
import com.stockmonitor.listeners.GraphDataListener;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.concurrent.TimeUnit;

public class StockWatcherThread implements Runnable {

    private final StockConfig stockConfig; // Using StockConfig instead of String symbol and rawThresholdInput
    private final PriceFetcher priceFetcher;
    private final AlertManager alertManager;
    private final GraphDataListener graphDataListener;
    private final long fetchIntervalSeconds; // No longer static, now a final instance variable
    private volatile boolean running = true;
    private double previousClosePrice = -1; // To store the previous closing price
    private boolean firstDataPoint = true;

    // Constructor updated, fetchIntervalSeconds parameter added
    public StockWatcherThread(StockConfig stockConfig, 
                              PriceFetcher priceFetcher,
                              AlertManager alertManager,
                              GraphDataListener graphDataListener,
                              long fetchIntervalSeconds) { // New parameter
        this.stockConfig = stockConfig;
        this.priceFetcher = priceFetcher;
        this.alertManager = alertManager;
        this.graphDataListener = graphDataListener;
        this.fetchIntervalSeconds = fetchIntervalSeconds; // Assignment
        System.out.println("[StockWatcherThread] [Thread: " + Thread.currentThread().getName() + "] Instance created for symbol: " + stockConfig.getSymbol() + " with interval: " + fetchIntervalSeconds + "s");
    }

    @Override
    public void run() {
        String symbol = stockConfig.getSymbol();
        System.out.println("[StockWatcherThread] [Thread: " + Thread.currentThread().getName() + "] run() started for symbol: " + symbol + ". Interval: " + this.fetchIntervalSeconds + "s.");
        if (symbol == null || symbol.trim().isEmpty()) {
            System.err.println("[StockWatcherThread] [Thread: " + Thread.currentThread().getName() + "] Symbol not specified for monitoring: " + symbol);
            return;
        }
        // Log message moved from MainController to here for more accurate thread info, or keep MainController's log
        // alertManager.logSystemMessage("Monitoring started for " + symbol + " with " + fetchIntervalSeconds + " seconds interval."); 

        while (running) {
            try {
                // System.out.println("[StockWatcherThread] [Thread: " + Thread.currentThread().getName() + "] " + symbol + ": Fetching price..."); // Can be too verbose
                double currentPrice = priceFetcher.fetchPrice(symbol);
                Date timestamp = new Date();
                // System.out.println("[StockWatcherThread] [Thread: " + Thread.currentThread().getName() + "] " + symbol + ": Price fetched: " + currentPrice);

                if (currentPrice != -1 && !Double.isNaN(currentPrice)) {
                    // Derive OHLC data from the current price
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
                    
                    // System.out.println("[StockWatcherThread] [Thread: " + Thread.currentThread().getName() + "] " + symbol + ": Sending OHLC data to listener.");
                    graphDataListener.onOHLCDataUpdate(symbol, timestamp, open, high, low, close);
                    previousClosePrice = currentPrice; 

                    checkAlerts(symbol, currentPrice);
                } else {
                    // System.err.println("[StockWatcherThread] [Thread: " + Thread.currentThread().getName() + "] " + symbol + ": Could not fetch price or invalid price from API.");
                    alertManager.queueAlert(symbol, "Could not fetch price or invalid price from API for " + symbol + ".");
                }
                // System.out.println("[StockWatcherThread] [Thread: " + Thread.currentThread().getName() + "] " + symbol + ": Sleeping for " + this.fetchIntervalSeconds + " seconds...");
                TimeUnit.SECONDS.sleep(this.fetchIntervalSeconds); // Using this.fetchIntervalSeconds
            } catch (InterruptedException e) {
                running = false; // Ensure loop termination
                Thread.currentThread().interrupt(); // Preserve interrupt status
                System.out.println("[StockWatcherThread] [Thread: " + Thread.currentThread().getName() + "] Monitoring interrupted for " + symbol + ". Thread stopping.");
                // alertManager.logSystemMessage("Monitoring interrupted by InterruptedException for " + symbol + "."); // AlertManager will log this
            } catch (Exception e) {
                System.err.println("[StockWatcherThread] [Thread: " + Thread.currentThread().getName() + "] Error while fetching price for " + symbol + ": " + e.getMessage());
                alertManager.queueAlert(symbol, "Error fetching price for " + symbol + ": " + e.getMessage());
                try {
                    TimeUnit.SECONDS.sleep(this.fetchIntervalSeconds * 2); // Wait longer in case of error, based on dynamic interval
                } catch (InterruptedException ie) {
                    running = false;
                    Thread.currentThread().interrupt();
                    System.out.println("[StockWatcherThread] [Thread: " + Thread.currentThread().getName() + "] Sleep after error interrupted for " + symbol + ". Thread stopping.");
                }
            }
        }
        System.out.println("[StockWatcherThread] [Thread: " + Thread.currentThread().getName() + "] run() finished for symbol: " + symbol + ". Cleaning up graph.");
        // alertManager.logSystemMessage("Monitoring stopped for " + symbol + "."); // AlertManager can handle this or MainController
        graphDataListener.clearGraph(symbol); // Clear graph when monitoring stops
    }

    private void checkAlerts(String symbol, double currentPrice) {
        String thresholdConfig = stockConfig.getThreshold();
        if (thresholdConfig == null || thresholdConfig.trim().isEmpty() || !thresholdConfig.contains("@")) {
            return; // Threshold not configured or format is incorrect
        }

        String[] parts = thresholdConfig.split("@", 2);
        String condition = parts[0];
        double targetValue;
        try {
            targetValue = Double.parseDouble(parts[1]);
        } catch (NumberFormatException e) {
            System.err.println("[StockWatcherThread] [Thread: " + Thread.currentThread().getName() + "] Error: Invalid target value format for " + symbol + ": " + parts[1]);
            return;
        }

        boolean alertTriggered = false;
        String alertMessage = "";

        // For intersection control using the previous price (kept simple for now)
        // More complex intersection scenarios might require tracking the previous price.

        switch (condition) {
            case "Fiyat > Değer": // Price > Value
                if (currentPrice > targetValue) {
                    alertTriggered = true;
                    alertMessage = String.format("%s price (%.4f) > target (%.4f)", symbol, currentPrice, targetValue);
                }
                break;
            case "Fiyat < Değer": // Price < Value
                if (currentPrice < targetValue) {
                    alertTriggered = true;
                    alertMessage = String.format("%s price (%.4f) < target (%.4f)", symbol, currentPrice, targetValue);
                }
                break;
            case "Fiyat Kesişir (Yukarı)": // Price Crosses (Up)
                if (previousClosePrice != -1 && previousClosePrice < targetValue && currentPrice >= targetValue) {
                    alertTriggered = true;
                    alertMessage = String.format("%s price (%.4f) crossed target (%.4f) upwards", symbol, currentPrice, targetValue);
                }
                break;
            case "Fiyat Kesişir (Aşağı)": // Price Crosses (Down)
                if (previousClosePrice != -1 && previousClosePrice > targetValue && currentPrice <= targetValue) {
                    alertTriggered = true;
                    alertMessage = String.format("%s price (%.4f) crossed target (%.4f) downwards", symbol, currentPrice, targetValue);
                }
                break;
        }

        if (alertTriggered) {
            // System.out.println("[StockWatcherThread] [Thread: " + Thread.currentThread().getName() + "] " + symbol + ": Alert triggered: " + alertMessage);
            alertManager.queueAlert(symbol, alertMessage);
        }
    }

    public void stopWatching() {
        System.out.println("[StockWatcherThread] [Thread: " + Thread.currentThread().getName() + "] stopWatching() called for symbol: " + getSymbol() + ". Setting running to false.");
        this.running = false;
        // Thread.currentThread().interrupt(); // Consider if interruption is needed here if sleep is long or blocked on I/O
    }

    public String getSymbol() {
        return stockConfig.getSymbol();
    }
} 