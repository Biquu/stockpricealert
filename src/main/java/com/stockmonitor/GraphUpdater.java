package com.stockmonitor;

import com.stockmonitor.listeners.GraphDataListener;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import javax.swing.SwingUtilities;

/**
 * Bu sınıf, StockWatcherThread'lerden gelen fiyat güncellemelerini dinler
 * ve ilgili XChartPanel'leri günceller.
 */
public class GraphUpdater implements GraphDataListener {

    // Sembolleri XChartPanel örnekleriyle eşleştiren bir harita.
    private final Map<String, XChartPanel> chartPanelsMap;
    // private final MainFrame mainFrame; // MainFrame referansına doğrudan erişim yerine panelleri alacağız.

    public GraphUpdater() {
        this.chartPanelsMap = new HashMap<>();
        // this.mainFrame = mainFrame; // Eğer MainFrame'den panel almak gerekiyorsa
    }

    /**
     * Belirli bir hisse senedi sembolü için bir XChartPanel kaydeder.
     * MainController, izleme başladığında bu metodu çağırır.
     *
     * @param symbol        İzlenecek hisse senedi sembolü.
     * @param chartPanel    Bu sembol için kullanılacak XChartPanel örneği.
     */
    public void registerChartPanel(String symbol, XChartPanel chartPanel) {
        if (symbol != null && !symbol.trim().isEmpty() && chartPanel != null) {
            chartPanelsMap.put(symbol.toUpperCase(), chartPanel);
            // Yeni bir sembol kaydedildiğinde grafiği temizleyebiliriz (isteğe bağlı)
            // SwingUtilities.invokeLater(() -> chartPanel.clearChart());
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
            chartPanelsMap.remove(symbol.toUpperCase());
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
        if (symbol == null) return;
        XChartPanel chartPanel = chartPanelsMap.get(symbol.toUpperCase());
        if (chartPanel != null) {
            // Grafik güncellemesini Swing EDT (Event Dispatch Thread) üzerinde yapalım.
            // XChartPanel içindeki addDataPoint zaten SwingUtilities.invokeLater kullanıyor,
            // bu yüzden burada tekrar sarmalamak gerekmeyebilir, ancak emin olmak adına yapılabilir.
            // SwingUtilities.invokeLater(() -> chartPanel.addDataPoint(timestamp, price));
            chartPanel.addDataPoint(timestamp, price); // XChartPanel zaten thread-safe olmalı
        } else {
            // System.err.println("GraphUpdater: " + symbol + " için kayıtlı XChartPanel bulunamadı.");
        }
    }

    @Override
    public void clearGraph(String symbol) {
        if (symbol == null) return;
        XChartPanel chartPanel = chartPanelsMap.get(symbol.toUpperCase());
        if (chartPanel != null) {
            // SwingUtilities.invokeLater(() -> chartPanel.clearChart());
            chartPanel.clearChart(); // XChartPanel zaten thread-safe olmalı
        } else {
            // System.err.println("GraphUpdater: Temizlenecek grafik için " + symbol + " bulunamadı.");
        }
    }

    @Override
    public void clearAllGraphs() {
        // SwingUtilities.invokeLater(() -> {
            for (XChartPanel panel : chartPanelsMap.values()) {
                if (panel != null) {
                    panel.clearChart();
                }
            }
        // });
    }

    // Belirli bir sembol için grafiğin başlığını güncellemek için bir metot eklenebilir.
    // Bu, MainController tarafından çağrılabilir.
    public void updateChartTitle(String symbol, String newTitle) {
        XChartPanel chartPanel = chartPanelsMap.get(symbol.toUpperCase());
        if (chartPanel != null && chartPanel.getXyChart() != null) {
            SwingUtilities.invokeLater(() -> {
                chartPanel.getXyChart().setTitle(newTitle);
                chartPanel.repaint(); // Başlık değiştiğinde paneli yeniden çizmek gerekebilir.
            });
        }
    }
} 