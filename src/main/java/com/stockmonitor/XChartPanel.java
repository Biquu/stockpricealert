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

    private OHLCChart chart; // Always OHLCChart now
    private org.knowm.xchart.XChartPanel<OHLCChart> chartComponentPanel; 

    // Data lists for Candle Chart
    private final List<Date> xDataCandle;
    private final List<Double> openDataCandle;
    private final List<Double> highDataCandle;
    private final List<Double> lowDataCandle;
    private final List<Double> closeDataCandle;

    // MAX_DATA_POINTS_CANDLE is set to accommodate 1-minute candles for the last hour (60) + incoming live data (e.g., 60 more)
    private static final int MAX_DATA_POINTS_CANDLE = 300; 

    // New Constants for Y-Axis Dynamic Range Settings
    private static final double MIN_Y_AXIS_SPAN_PERCENTAGE_OF_MIDPRICE = 0.001; // Reduced from 2.5% to 0.1% (0.025 -> 0.001)
    private static final double ABSOLUTE_MIN_Y_AXIS_SPAN = 0.01; // Reduced from 0.1 to 0.01
    private static final double Y_AXIS_PADDING_PERCENTAGE_OF_EFFECTIVE_RANGE = 0.10; // Reduced from 15% to 10% (0.15 -> 0.10)

    private boolean seriesExists = false;

    public XChartPanel(String initialTitle) {
        this.initialPanelTitle = initialTitle;
        this.seriesName = initialTitle; 
        System.out.println("[XChartPanel] [Thread: " + Thread.currentThread().getName() + "] Instance created with initial title: " + initialTitle);
        
        this.xDataCandle = new CopyOnWriteArrayList<>();
        this.openDataCandle = new CopyOnWriteArrayList<>();
        this.highDataCandle = new CopyOnWriteArrayList<>();
        this.lowDataCandle = new CopyOnWriteArrayList<>();
        this.closeDataCandle = new CopyOnWriteArrayList<>();

        setLayout(new BorderLayout());
        setupChartComponent();
    }

    private void initOHLCChart() {
        System.out.println("[XChartPanel] [Thread: " + Thread.currentThread().getName() + "] initOHLCChart called for: " + (this.seriesName != null ? this.seriesName : this.initialPanelTitle));
        chart = new OHLCChartBuilder()
                // .width(350).height(250) // Fixed sizes removed, will rely on layout manager
                .title(this.seriesName != null && !this.seriesName.equals(this.initialPanelTitle) ? this.seriesName : this.initialPanelTitle)
                .xAxisTitle("Time").yAxisTitle("Price").build();

        AxesChartStyler styler = chart.getStyler();
        commonStylerSettings(styler);
        styler.setDatePattern("HH:mm:ss");
        styler.setYAxisDecimalPattern("#,##0.0000");
        // Custom settings for candle colors (optional)
        styler.setToolTipsEnabled(true); // Enable tooltips
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
        System.out.println("[XChartPanel] [Thread: " + Thread.currentThread().getName() + "] setupChartComponent called for: " + this.initialPanelTitle);
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
        System.out.println("[XChartPanel] [Thread: " + Thread.currentThread().getName() + "] setSeriesNameAndTitle called. New series name: " + this.seriesName + " (Panel: " + this.initialPanelTitle + ")");
        if (chart != null) {
            chart.setTitle(this.seriesName);
            if (chartComponentPanel != null) {
                chartComponentPanel.repaint();
            }
        }
    }
    
    private void clearLocalData(){
        System.out.println("[XChartPanel] [Thread: " + Thread.currentThread().getName() + "] clearLocalData called for: " + this.initialPanelTitle);
        xDataCandle.clear();
        openDataCandle.clear();
        highDataCandle.clear();
        lowDataCandle.clear();
        closeDataCandle.clear();
        seriesExists = false;
    }

    public void addOHLCDataPoint(Date timestamp, double open, double high, double low, double close) {
        // System.out.println("[XChartPanel] [Thread: " + Thread.currentThread().getName() + "] addOHLCDataPoint called for: " + this.seriesName);
        if (this.seriesName == null || this.seriesName.equals(this.initialPanelTitle)) {
            // System.err.println("[XChartPanel] Series name not assigned, cannot add OHLC data for: " + this.initialPanelTitle);
            return; // Do not add data until series name is assigned
        }
        synchronized (xDataCandle) { // Synchronize access to data lists
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
            // System.out.println("[XChartPanel] [Thread: " + Thread.currentThread().getName() + "] Data point added for " + this.seriesName + ". Total points: " + xDataCandle.size());
            updateOHLCChartSeries();
        }
    }

    private void updateOHLCChartSeries() {
        final List<Date> xCopy = new ArrayList<>(xDataCandle);
        final List<Double> openCopy = new ArrayList<>(openDataCandle);
        final List<Double> highCopy = new ArrayList<>(highDataCandle);
        final List<Double> lowCopy = new ArrayList<>(lowDataCandle);
        final List<Double> closeCopy = new ArrayList<>(closeDataCandle);
        // System.out.println("[XChartPanel] [Thread: " + Thread.currentThread().getName() + "] Scheduling updateOHLCChartSeries for " + this.seriesName + " on EDT. Data points: " + xCopy.size());

        javax.swing.SwingUtilities.invokeLater(() -> {
            // System.out.println("[XChartPanel] [Thread: " + Thread.currentThread().getName() + "] updateOHLCChartSeries (EDT) running for: " + this.seriesName);
            try {
                if (xCopy.isEmpty()) {
                    // If no data and series exists, clear/remove series
                    if(seriesExists && chart.getSeriesMap().containsKey(this.seriesName)){
                        // System.out.println("[XChartPanel] [Thread: " + Thread.currentThread().getName() + "] Removing existing series " + this.seriesName + " due to empty data.");
                        chart.removeSeries(this.seriesName);
                        seriesExists = false;
                    }
                    applyYAxisPadding(new ArrayList<>(), new ArrayList<>(), (AxesChartStyler) chart.getStyler());
                    if (chartComponentPanel != null) chartComponentPanel.repaint();
                    return;
                }

                if (!seriesExists || !chart.getSeriesMap().containsKey(this.seriesName)) {
                    if (chart.getSeriesMap().containsKey(this.seriesName)) chart.removeSeries(this.seriesName); // Should not happen if !seriesExists
                    // System.out.println("[XChartPanel] [Thread: " + Thread.currentThread().getName() + "] Adding new OHLC series: " + this.seriesName);
                    OHLCSeries series = chart.addSeries(this.seriesName, xCopy, openCopy, highCopy, lowCopy, closeCopy);
                    // You can set candle colors here (optional)
                    // series.setUpColor(XChartSeriesColors.GREEN); 
                    // series.setDownColor(XChartSeriesColors.RED);
                    seriesExists = true;
                } else {
                    // System.out.println("[XChartPanel] [Thread: " + Thread.currentThread().getName() + "] Updating existing OHLC series: " + this.seriesName);
                    chart.updateOHLCSeries(this.seriesName, xCopy, openCopy, highCopy, lowCopy, closeCopy, null);
                }
                applyYAxisPadding(highCopy, lowCopy, (AxesChartStyler) chart.getStyler());
                if (chartComponentPanel != null) chartComponentPanel.repaint();
            } catch (Exception e) { 
                seriesExists = false; // Try to recreate series in case of error
                System.err.println("[XChartPanel] [Thread: " + Thread.currentThread().getName() + "] Error in updateOHLCChartSeries for " + this.seriesName + ": " + e.getMessage());
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
            if (Double.isNaN(midPrice) || Double.isInfinite(midPrice)) midPrice = 0; // Assign zero if NaN or Infinite

            double minDisplaySpan;
            if (Math.abs(midPrice) < 0.00001) { // If midPrice is very small or zero
                minDisplaySpan = ABSOLUTE_MIN_Y_AXIS_SPAN;
            } else {
                minDisplaySpan = Math.max(Math.abs(midPrice) * MIN_Y_AXIS_SPAN_PERCENTAGE_OF_MIDPRICE, ABSOLUTE_MIN_Y_AXIS_SPAN);
            }
            
            double effectiveRange = Math.max(dataRange, minDisplaySpan);
            if (effectiveRange < 0.00001) effectiveRange = minDisplaySpan; // Use minDisplaySpan if dataRange is too small

            double displayMin = midPrice - (effectiveRange / 2.0);
            double displayMax = midPrice + (effectiveRange / 2.0);
            
            double padding = effectiveRange * Y_AXIS_PADDING_PERCENTAGE_OF_EFFECTIVE_RANGE;

            styler.setYAxisMin(displayMin - padding);
            styler.setYAxisMax(displayMax + padding);

        } else { // This should not happen if lists are not empty, kept as a fallback
            styler.setYAxisMin(-1.0);
            styler.setYAxisMax(1.0);
        }
    }

    public void clearChart() {
        System.out.println("[XChartPanel] [Thread: " + Thread.currentThread().getName() + "] clearChart called for: " + this.initialPanelTitle + ", current series: " + this.seriesName);
        clearLocalData();
        if (chart != null) {
            if (seriesExists && seriesName != null && !seriesName.equals(initialPanelTitle) && chart.getSeriesMap().containsKey(seriesName)) {
                 try { 
                     // System.out.println("[XChartPanel] [Thread: " + Thread.currentThread().getName() + "] Removing series: " + seriesName + " from chart.");
                     chart.removeSeries(seriesName); 
                 } catch (Exception e) { /* Ignore error */ }
            }
        }
        seriesExists = false;
        // Call setSeriesNameAndTitle from MainController to reset panel title to initial.
        // setPanelTitle(this.initialPanelTitle); // Let MainController manage this instead of direct call
        
        if (chart != null && chart.getStyler() instanceof AxesChartStyler) { // Check styler type
            applyYAxisPadding(new ArrayList<>(), new ArrayList<>(), (AxesChartStyler) chart.getStyler());
        }

        if (chartComponentPanel != null) {
            chartComponentPanel.revalidate();
            chartComponentPanel.repaint();
        }
    }

    // To update panel title (called by MainController)
    public void setPanelTitle(String title) {
        // This method could be merged with or called by setSeriesNameAndTitle.
        // For now, we use chart.setTitle directly, but updating seriesName is also important.
        String oldSeriesName = this.seriesName;
        this.seriesName = (title != null && !title.trim().isEmpty()) ? title : this.initialPanelTitle;
        System.out.println("[XChartPanel] [Thread: " + Thread.currentThread().getName() + "] setPanelTitle called for: " + this.initialPanelTitle + ". New title/series name: " + this.seriesName);
        if (chart != null) {
            chart.setTitle(this.seriesName);
            // If series name changes and series exists with old name, it might need to be renamed or re-added.
            // This is complex because chart.updateSeriesName might not exist.
            // For now, clearing and re-adding is handled by updateOHLCChartSeries logic when seriesName changes.
            if (seriesExists && oldSeriesName != null && !oldSeriesName.equals(this.seriesName) && chart.getSeriesMap().containsKey(oldSeriesName)){
                try {
                    // System.out.println("[XChartPanel] [Thread: " + Thread.currentThread().getName() + "] Title changed, removing old series: " + oldSeriesName);
                    chart.removeSeries(oldSeriesName);
                    seriesExists = false; // Force re-creation with new name on next data point
                } catch (Exception e) {
                     System.err.println("[XChartPanel] [Thread: " + Thread.currentThread().getName() + "] Error removing old series " + oldSeriesName + " after title change: " + e.getMessage());
                }
            }
            if (chartComponentPanel != null) {
                chartComponentPanel.repaint();
            }
        }
    }
} 