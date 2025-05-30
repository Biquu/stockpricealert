package com.stockmonitor;

// import com.stockmonitor.data.CandleStickData; // Kaldırıldı
import com.stockmonitor.listeners.GraphDataListener;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.swing.SwingUtilities;

/**
 * Bu sınıf, StockWatcherThread'lerden gelen fiyat güncellemelerini dinler
 * ve ilgili XChartPanel'leri günceller.
 */
public class GraphUpdater implements GraphDataListener {

    // Sembolleri XChartPanel örnekleriyle eşleştiren bir harita.
    private final Map<String, XChartPanel> chartPanelsMap;

    public GraphUpdater() {
        this.chartPanelsMap = new ConcurrentHashMap<>();
    }

    /**
     * Belirli bir hisse senedi sembolü için bir XChartPanel kaydeder.
     */
    public void registerChartPanel(String symbol, XChartPanel chartPanel) {
        if (symbol != null && !symbol.trim().isEmpty() && chartPanel != null) {
            String upperSymbol = symbol.toUpperCase();
            chartPanelsMap.put(upperSymbol, chartPanel);
            SwingUtilities.invokeLater(() -> {
                chartPanel.clearChart();
            }); 
        } else {
            System.err.println("GraphUpdater: Geçersiz sembol veya chartPanel kaydedilemedi.");
        }
    }

    /**
     * Belirli bir hisse senedi sembolü için kaydedilmiş XChartPanel'i kaldırır.
     * @param symbol Kaldırılacak panelin sembolü.
     */
    public void unregisterChartPanel(String symbol) {
        if (symbol != null) {
            String upperSymbol = symbol.toUpperCase();
            chartPanelsMap.remove(upperSymbol);
        }
    }

    /**
     * Kayıtlı tüm chart panellerini temizler.
     */
    public void unregisterAllChartPanels() {
        chartPanelsMap.clear();
    }

    @Override
    public void onPriceUpdate(String symbol, double price, Date timestamp) {
        // Bu metot artık StockWatcherThread tarafından doğrudan çağrılmıyor.
        // OHLC verisi üzerinden güncelleme yapılıyor.
    }
    
    @Override
    public void onOHLCDataUpdate(String symbol, Date timestamp, double open, double high, double low, double close) {
        if (symbol == null) return;
        String upperSymbol = symbol.toUpperCase();
        XChartPanel chartPanel = chartPanelsMap.get(upperSymbol);

        if (chartPanel != null) {
            // SwingUtilities.invokeLater zaten XChartPanel içinde kullanılıyor addOHLCDataPoint için.
            chartPanel.addOHLCDataPoint(timestamp, open, high, low, close);
        } else {
            // Uygulama ilk başladığında veya sembol değiştiğinde bu log normal olabilir.
            // System.err.println("GraphUpdater: " + upperSymbol + " için kayıtlı XChartPanel bulunamadı (onOHLCDataUpdate).");
        }
    }

    @Override
    public void clearGraph(String symbol) {
        if (symbol == null) return;
        XChartPanel chartPanel = chartPanelsMap.get(symbol.toUpperCase());
        if (chartPanel != null) {
            // SwingUtilities.invokeLater zaten XChartPanel içinde kullanılıyor clearChart için.
            chartPanel.clearChart();
        }
    }

    @Override
    public void clearAllGraphs() {
        for (XChartPanel panel : chartPanelsMap.values()) {
            if (panel != null) {
                // SwingUtilities.invokeLater zaten XChartPanel içinde kullanılıyor clearChart için.
                panel.clearChart();
            }
        }
    }

    // Belirli bir sembol için grafiğin başlığını güncellemek için bir metot eklenebilir.
    // Bu, MainController tarafından çağrılabilir.
    public void updateChartTitle(String symbol, String newTitle) {
        XChartPanel chartPanel = chartPanelsMap.get(symbol.toUpperCase());
        if (chartPanel != null) { 
            SwingUtilities.invokeLater(() -> {
                chartPanel.setPanelTitle(newTitle);
            });
        }
    }

    // displayHistoricalCandles metodu kaldırıldı.
    // updateGraphData metodu da aslında onOHLCDataUpdate ile aynı işi yapıyor,
    // ve GraphDataListener arayüzünden gelen onOHLCDataUpdate kullanıldığı için kaldırılabilir.
    // Ancak StockWatcherThread doğrudan updateGraphData'yı çağırıyorsa kalmalı.
    // Kontrol: StockWatcherThread artık doğrudan GraphUpdater objesine sahip ve onOHLCDataUpdate'i çağırıyor olmalı.
    // Bu durumda updateGraphData gereksiz.

    /* 
    // Anlık (veya kısa periyotlarla türetilmiş) OHLC verilerini güncellemek için metot
    // Bu metot onOHLCDataUpdate ile aynı işi yapıyor ve GraphDataListener üzerinden geldiği için gereksiz.
    public void updateGraphData(String symbol, Date timestamp, double open, double high, double low, double close) {
        XChartPanel chartPanel = chartPanelsMap.get(symbol.toUpperCase());
        if (chartPanel != null) {
            SwingUtilities.invokeLater(() -> {
                chartPanel.addOHLCDataPoint(timestamp, open, high, low, close);
            });
        } else {
            System.err.println("GraphUpdater: " + symbol + " için kayıtlı XChartPanel bulunamadı (updateGraphData).");
        }
    }
    */
} 