package com.stockmonitor;

import org.knowm.xchart.OHLCChart;
import org.knowm.xchart.OHLCChartBuilder;
import org.knowm.xchart.OHLCSeries;
import org.knowm.xchart.style.AxesChartStyler;
import org.knowm.xchart.style.Styler;

import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.OptionalDouble;
import java.util.concurrent.CopyOnWriteArrayList;

public class XChartPanel extends JPanel {
    private String seriesName; 
    private String initialPanelTitle;

    private OHLCChart chart; // Artık her zaman OHLCChart
    private org.knowm.xchart.XChartPanel<OHLCChart> chartComponentPanel; 

    // Mum Grafik için veri listeleri
    private final List<Date> xDataCandle;
    private final List<Double> openDataCandle;
    private final List<Double> highDataCandle;
    private final List<Double> lowDataCandle;
    private final List<Double> closeDataCandle;

    // MAX_DATA_POINTS_CANDLE, son 1 saatlik 1dk'lık mumları (60 adet) + anlık akacak verileri (örn. 60 adet daha) alacak şekilde ayarlandı
    private static final int MAX_DATA_POINTS_CANDLE = 60; 

    // Y Ekseni Dinamik Aralık Ayarları için Yeni Sabitler
    private static final double MIN_Y_AXIS_SPAN_PERCENTAGE_OF_MIDPRICE = 0.001; // %2.5'ten %0.1'e düşürüldü (0.025 -> 0.001)
    private static final double ABSOLUTE_MIN_Y_AXIS_SPAN = 0.01; // 0.1'den 0.01'e düşürüldü
    private static final double Y_AXIS_PADDING_PERCENTAGE_OF_EFFECTIVE_RANGE = 0.10; // %15'ten %10'a düşürüldü (0.15 -> 0.10)

    private boolean seriesExists = false;

    public XChartPanel(String initialTitle) {
        this.initialPanelTitle = initialTitle;
        this.seriesName = initialTitle; 
        
        this.xDataCandle = new CopyOnWriteArrayList<>();
        this.openDataCandle = new CopyOnWriteArrayList<>();
        this.highDataCandle = new CopyOnWriteArrayList<>();
        this.lowDataCandle = new CopyOnWriteArrayList<>();
        this.closeDataCandle = new CopyOnWriteArrayList<>();

        setLayout(new BorderLayout());
        setupChartComponent();
    }

    private void initOHLCChart() {
        chart = new OHLCChartBuilder()
                // .width(350).height(250) // Sabit boyutlar kaldırıldı, layout manager'a güvenilecek
                .title(this.seriesName != null && !this.seriesName.equals(this.initialPanelTitle) ? this.seriesName : this.initialPanelTitle)
                .xAxisTitle("Zaman").yAxisTitle("Fiyat").build();

        AxesChartStyler styler = chart.getStyler();
        commonStylerSettings(styler);
        styler.setDatePattern("HH:mm:ss");
        styler.setYAxisDecimalPattern("#,##0.0000");
        // Mum renkleri için özel ayarlar (isteğe bağlı)
        styler.setToolTipsEnabled(true); // İpuçlarını etkinleştir
        styler.setLegendVisible(false); 
    }

    private void commonStylerSettings(AxesChartStyler styler) {
        styler.setPlotBackgroundColor(Color.BLACK);
        styler.setChartBackgroundColor(Color.BLACK);
        styler.setPlotBorderVisible(true);
        styler.setPlotGridLinesVisible(true); 
        styler.setPlotGridLinesColor(new Color(70, 70, 70));
        styler.setChartFontColor(Color.LIGHT_GRAY);
        styler.setLegendBackgroundColor(Color.DARK_GRAY);
        styler.setLegendBorderColor(Color.GRAY);
        styler.setLegendPosition(Styler.LegendPosition.InsideNW);
        styler.setXAxisTitleColor(Color.LIGHT_GRAY);
        styler.setYAxisTitleColor(Color.LIGHT_GRAY);
        styler.setXAxisTickLabelsColor(Color.LIGHT_GRAY);
        styler.setYAxisTickLabelsColor(Color.LIGHT_GRAY);
    }
    
    private void setupChartComponent() {
        if (chartComponentPanel != null) {
            remove(chartComponentPanel); 
        }
        seriesExists = false; 
        initOHLCChart(); 
        chartComponentPanel = new org.knowm.xchart.XChartPanel<>(chart); 
        add(chartComponentPanel, BorderLayout.CENTER);
        revalidate();
        repaint();
    }

    public void setSeriesNameAndTitle(String newSeriesName) {
        this.seriesName = (newSeriesName != null && !newSeriesName.trim().isEmpty()) ? newSeriesName : this.initialPanelTitle;
        if (chart != null) {
            chart.setTitle(this.seriesName);
            if (chartComponentPanel != null) {
                chartComponentPanel.repaint();
            }
        }
    }
    
    private void clearLocalData(){
        xDataCandle.clear();
        openDataCandle.clear();
        highDataCandle.clear();
        lowDataCandle.clear();
        closeDataCandle.clear();
        seriesExists = false;
    }

    public void addOHLCDataPoint(Date timestamp, double open, double high, double low, double close) {
        if (this.seriesName == null || this.seriesName.equals(this.initialPanelTitle)) {
             // System.err.println("XChartPanel: Seri adı atanmamış, OHLC verisi eklenemiyor: " + this.initialPanelTitle);
            return; // Seri adı atanana kadar veri ekleme
        }
        synchronized (xDataCandle) { // Veri listelerine erişimi senkronize et
            xDataCandle.add(timestamp);
            openDataCandle.add(open);
            highDataCandle.add(high);
            lowDataCandle.add(low);
            closeDataCandle.add(close);

            while (xDataCandle.size() > MAX_DATA_POINTS_CANDLE) {
                xDataCandle.remove(0);
                openDataCandle.remove(0);
                highDataCandle.remove(0);
                lowDataCandle.remove(0);
                closeDataCandle.remove(0);
            }
            updateOHLCChartSeries();
        }
    }

    private void updateOHLCChartSeries() {
        final List<Date> xCopy = new ArrayList<>(xDataCandle);
        final List<Double> openCopy = new ArrayList<>(openDataCandle);
        final List<Double> highCopy = new ArrayList<>(highDataCandle);
        final List<Double> lowCopy = new ArrayList<>(lowDataCandle);
        final List<Double> closeCopy = new ArrayList<>(closeDataCandle);

        javax.swing.SwingUtilities.invokeLater(() -> {
            try {
                if (xCopy.isEmpty()) {
                    // Eğer veri yoksa ve seri varsa seriyi temizle/kaldır
                    if(seriesExists && chart.getSeriesMap().containsKey(this.seriesName)){
                        chart.removeSeries(this.seriesName);
                        seriesExists = false;
                    }
                    applyYAxisPadding(new ArrayList<>(), new ArrayList<>(), (AxesChartStyler) chart.getStyler());
                    if (chartComponentPanel != null) chartComponentPanel.repaint();
                    return;
                }

                if (!seriesExists || !chart.getSeriesMap().containsKey(this.seriesName)) {
                    if (chart.getSeriesMap().containsKey(this.seriesName)) chart.removeSeries(this.seriesName);
                    OHLCSeries series = chart.addSeries(this.seriesName, xCopy, openCopy, highCopy, lowCopy, closeCopy);
                    // Mum renklerini burada ayarlayabilirsiniz (isteğe bağlı)
                    // series.setUpColor(XChartSeriesColors.GREEN); 
                    // series.setDownColor(XChartSeriesColors.RED);
                    seriesExists = true;
                } else {
                    chart.updateOHLCSeries(this.seriesName, xCopy, openCopy, highCopy, lowCopy, closeCopy, null);
                }
                applyYAxisPadding(highCopy, lowCopy, (AxesChartStyler) chart.getStyler());
                if (chartComponentPanel != null) chartComponentPanel.repaint();
            } catch (Exception e) { 
                seriesExists = false; // Hata durumunda seriyi tekrar oluşturmaya çalışır
                System.err.println("Hata (updateOHLCChartSeries - " + this.seriesName + "): " + e.getMessage());
                // e.printStackTrace();
            }
        });
    }
    
    private void applyYAxisPadding(List<Double> highValues, List<Double> lowValues, AxesChartStyler styler) {
        if (highValues.isEmpty() || lowValues.isEmpty()) {
            styler.setYAxisMin(-1.0);
            styler.setYAxisMax(1.0);
            return;
        }

        OptionalDouble minOptional = lowValues.stream().mapToDouble(Double::doubleValue).min();
        OptionalDouble maxOptional = highValues.stream().mapToDouble(Double::doubleValue).max();

        if (minOptional.isPresent() && maxOptional.isPresent()) {
            double actualMinVal = minOptional.getAsDouble();
            double actualMaxVal = maxOptional.getAsDouble();
            double dataRange = actualMaxVal - actualMinVal;
            double midPrice = (actualMinVal + actualMaxVal) / 2.0;
            if (Double.isNaN(midPrice) || Double.isInfinite(midPrice)) midPrice = 0; // NaN veya Sonsuz ise sıfır ata

            double minDisplaySpan;
            if (Math.abs(midPrice) < 0.00001) { // Midprice çok küçük veya sıfırsa
                minDisplaySpan = ABSOLUTE_MIN_Y_AXIS_SPAN;
            } else {
                minDisplaySpan = Math.max(Math.abs(midPrice) * MIN_Y_AXIS_SPAN_PERCENTAGE_OF_MIDPRICE, ABSOLUTE_MIN_Y_AXIS_SPAN);
            }
            
            double effectiveRange = Math.max(dataRange, minDisplaySpan);
            if (effectiveRange < 0.00001) effectiveRange = minDisplaySpan; // dataRange çok küçükse minDisplaySpan kullan

            double displayMin = midPrice - (effectiveRange / 2.0);
            double displayMax = midPrice + (effectiveRange / 2.0);
            
            double padding = effectiveRange * Y_AXIS_PADDING_PERCENTAGE_OF_EFFECTIVE_RANGE;

            styler.setYAxisMin(displayMin - padding);
            styler.setYAxisMax(displayMax + padding);

        } else { // Bu durum listeler boş değilse oluşmamalı, yedek olarak kalıyor
            styler.setYAxisMin(-1.0);
            styler.setYAxisMax(1.0);
        }
    }

    public void clearChart() {
        clearLocalData();
        if (chart != null) {
            if (seriesExists && seriesName != null && !seriesName.equals(initialPanelTitle) && chart.getSeriesMap().containsKey(seriesName)) {
                 try { chart.removeSeries(seriesName); } catch (Exception e) { /* Hata yoksayılabilir */ }
            }
        }
        seriesExists = false;
        // Panelin başlığını ilk haline döndürmek için setSeriesNameAndTitle'ı MainController'dan çağırın.
        // setPanelTitle(this.initialPanelTitle); // Bu doğrudan çağrı yerine MainController yönetsin
        
        if (chart != null && chart.getStyler() instanceof AxesChartStyler) { // Styler tipini kontrol et
            applyYAxisPadding(new ArrayList<>(), new ArrayList<>(), (AxesChartStyler) chart.getStyler());
        }

        if (chartComponentPanel != null) {
            chartComponentPanel.revalidate();
            chartComponentPanel.repaint();
        }
    }

    // Panelin başlığını güncellemek için (MainController tarafından çağrılır)
    public void setPanelTitle(String title) {
        // Bu metot setSeriesNameAndTitle ile birleştirilebilir veya onun tarafından çağrılabilir.
        // Şimdilik doğrudan chart.setTitle kullanıyoruz, ama seri adını da güncellemek önemli.
        this.seriesName = (title != null && !title.trim().isEmpty()) ? title : this.initialPanelTitle;
        if (chart != null) {
            chart.setTitle(this.seriesName);
            // Eğer seri adı değişiyorsa ve seri varsa, serinin adını da güncellemek gerekebilir.
            // XChart'ta var olan bir serinin adını değiştirmek doğrudan desteklenmiyor olabilir.
            // Genellikle seriyi kaldırıp yeni adla eklemek gerekir. updateOHLCChartSeries bunu yönetiyor.
            if (chartComponentPanel != null) {
                chartComponentPanel.repaint();
            }
        }
    }
} 