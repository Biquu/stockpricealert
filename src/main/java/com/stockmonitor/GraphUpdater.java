package com.stockmonitor;

// import com.stockmonitor.data.CandleStickData; // Removed
import com.stockmonitor.listeners.GraphDataListener;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.swing.SwingUtilities;

/**
 * This class listens for price updates from StockWatcherThreads
 * and updates the corresponding XChartPanels.
 */
public class GraphUpdater implements GraphDataListener {

    // A map that associates symbols with XChartPanel instances.
    private final Map<String, XChartPanel> chartPanelsMap;

    public GraphUpdater() {
        this.chartPanelsMap = new ConcurrentHashMap<>();
        System.out.println("[GraphUpdater] [Thread: " + Thread.currentThread().getName() + "] Instance created.");
    }

    /**
     * Registers an XChartPanel for a specific stock symbol.
     */
    public void registerChartPanel(String symbol, XChartPanel chartPanel) {
        if (symbol != null && !symbol.trim().isEmpty() && chartPanel != null) {
            String upperSymbol = symbol.toUpperCase();
            chartPanelsMap.put(upperSymbol, chartPanel);
            System.out.println("[GraphUpdater] [Thread: " + Thread.currentThread().getName() + "] Registered chart panel for symbol: " + upperSymbol);
            SwingUtilities.invokeLater(() -> {
                // System.out.println("[GraphUpdater] [Thread: " + Thread.currentThread().getName() + "] Clearing chart on EDT for: " + upperSymbol);
                chartPanel.clearChart(); // Clear chart when registered
            }); 
        } else {
            System.err.println("[GraphUpdater] [Thread: " + Thread.currentThread().getName() + "] Invalid symbol or chartPanel, could not register.");
        }
    }

    /**
     * Unregisters the XChartPanel for a specific stock symbol.
     * @param symbol The symbol of the panel to unregister.
     */
    public void unregisterChartPanel(String symbol) {
        if (symbol != null) {
            String upperSymbol = symbol.toUpperCase();
            XChartPanel removedPanel = chartPanelsMap.remove(upperSymbol);
            if (removedPanel != null) {
                System.out.println("[GraphUpdater] [Thread: " + Thread.currentThread().getName() + "] Unregistered chart panel for symbol: " + upperSymbol);
            } else {
                // System.out.println("[GraphUpdater] [Thread: " + Thread.currentThread().getName() + "] No chart panel to unregister for symbol: " + upperSymbol);
            }
        }
    }

    /**
     * Clears all registered chart panels.
     */
    public void unregisterAllChartPanels() {
        System.out.println("[GraphUpdater] [Thread: " + Thread.currentThread().getName() + "] Unregistering all chart panels. Current count: " + chartPanelsMap.size());
        chartPanelsMap.clear();
    }

    @Override
    public void onPriceUpdate(String symbol, double price, Date timestamp) {
        // This method is no longer directly called by StockWatcherThread.
        // Updates are done via OHLC data.
    }
    
    @Override
    public void onOHLCDataUpdate(String symbol, Date timestamp, double open, double high, double low, double close) {
        if (symbol == null) return;
        String upperSymbol = symbol.toUpperCase();
        XChartPanel chartPanel = chartPanelsMap.get(upperSymbol);

        if (chartPanel != null) {
            // SwingUtilities.invokeLater is already used inside XChartPanel for addOHLCDataPoint.
            // System.out.println("[GraphUpdater] [Thread: " + Thread.currentThread().getName() + "] Received OHLC data for " + upperSymbol + ". Forwarding to chart panel.");
            chartPanel.addOHLCDataPoint(timestamp, open, high, low, close);
        } else {
            // This log can be normal when the application first starts or when the symbol changes.
            // System.err.println("[GraphUpdater] [Thread: " + Thread.currentThread().getName() + "] No registered XChartPanel found for " + upperSymbol + " (onOHLCDataUpdate).");
        }
    }

    @Override
    public void clearGraph(String symbol) {
        if (symbol == null) return;
        String upperSymbol = symbol.toUpperCase();
        XChartPanel chartPanel = chartPanelsMap.get(upperSymbol);
        if (chartPanel != null) {
            // SwingUtilities.invokeLater is already used inside XChartPanel for clearChart.
            // System.out.println("[GraphUpdater] [Thread: " + Thread.currentThread().getName() + "] Clearing graph for symbol: " + upperSymbol);
            chartPanel.clearChart();
        }
    }

    @Override
    public void clearAllGraphs() {
        System.out.println("[GraphUpdater] [Thread: " + Thread.currentThread().getName() + "] Clearing all graphs. Panel count: " + chartPanelsMap.size());
        for (XChartPanel panel : chartPanelsMap.values()) {
            if (panel != null) {
                // SwingUtilities.invokeLater is already used inside XChartPanel for clearChart.
                panel.clearChart();
            }
        }
    }

    // A method can be added to update the title of the graph for a specific symbol.
    // This can be called by MainController.
    public void updateChartTitle(String symbol, String newTitle) {
        if (symbol == null) return;
        String upperSymbol = symbol.toUpperCase();
        XChartPanel chartPanel = chartPanelsMap.get(upperSymbol);
        if (chartPanel != null) { 
            // System.out.println("[GraphUpdater] [Thread: " + Thread.currentThread().getName() + "] Updating chart title for " + upperSymbol + " to: " + newTitle);
            SwingUtilities.invokeLater(() -> {
                // System.out.println("[GraphUpdater] [Thread: " + Thread.currentThread().getName() + "] Setting panel title on EDT for " + upperSymbol);
                chartPanel.setPanelTitle(newTitle);
            });
        }
    }
}