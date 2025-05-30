package com.stockmonitor;

// import org.knowm.xchart.XChartPanel; // Kendi sınıfımızla çakıştığı için tam adını kullanacağız
import org.knowm.xchart.XYChart;
import org.knowm.xchart.XYChartBuilder;
import org.knowm.xchart.XYSeries;
import org.knowm.xchart.style.Styler;
import org.knowm.xchart.style.AxesChartStyler;
import org.knowm.xchart.style.colors.XChartSeriesColors;
import org.knowm.xchart.style.lines.SeriesLines;
import org.knowm.xchart.style.markers.SeriesMarkers;

import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.BasicStroke; // BasicStroke için import eklendi
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class XChartPanel extends JPanel {
    private String seriesName; // Dinamik seri adı
    private XYChart xyChart;
    private org.knowm.xchart.XChartPanel<XYChart> chartComponentPanel;
    private final List<Date> xData;
    private final List<Double> yData;
    private static final int MAX_DATA_POINTS = 100;
    private boolean seriesExists = false;

    public XChartPanel(String initialTitle) { // Başlangıç başlığı alır
        this.seriesName = initialTitle; // Başlangıçta grafik başlığı için kullanılır, sonra güncellenir
        this.xData = new CopyOnWriteArrayList<>();
        this.yData = new CopyOnWriteArrayList<>();
        setLayout(new BorderLayout());
        initChart(initialTitle);
    }

    private void initChart(String initialTitle) {
        xyChart = new XYChartBuilder()
                .width(300)
                .height(200)
                .title(initialTitle) // Başlangıç başlığı
                .xAxisTitle("Zaman")
                .yAxisTitle("Fiyat")
                .build();

        AxesChartStyler styler = xyChart.getStyler();
        styler.setPlotBackgroundColor(Color.BLACK);
        styler.setChartBackgroundColor(Color.BLACK);
        styler.setPlotBorderVisible(true);
        styler.setPlotGridLinesVisible(true);
        styler.setPlotGridLinesColor(new Color(70, 70, 70));
        styler.setChartFontColor(Color.LIGHT_GRAY);
        styler.setLegendBackgroundColor(Color.DARK_GRAY);
        styler.setLegendBorderColor(Color.GRAY);
        styler.setLegendPosition(Styler.LegendPosition.InsideNW);
        styler.setXAxisTickLabelsColor(Color.LIGHT_GRAY);
        styler.setXAxisTitleColor(Color.LIGHT_GRAY);
        styler.setDatePattern("HH:mm:ss");
        styler.setYAxisTickLabelsColor(Color.LIGHT_GRAY);
        styler.setYAxisTitleColor(Color.LIGHT_GRAY);
        styler.setYAxisDecimalPattern("#,##0.0000");
        styler.setSeriesColors(new Color[]{XChartSeriesColors.GREEN}); // Varsayılan seri rengi
        styler.setSeriesLines(new BasicStroke[]{SeriesLines.SOLID});
        styler.setSeriesMarkers(new org.knowm.xchart.style.markers.Marker[]{SeriesMarkers.NONE});

        // Seri, ilk veri geldiğinde addDataPoint içinde eklenecek.
        seriesExists = false;

        chartComponentPanel = new org.knowm.xchart.XChartPanel<>(xyChart);
        add(chartComponentPanel, BorderLayout.CENTER);
    }

    /**
     * MainController tarafından çağrılır, grafik başlığını ve veri eklenecek seri adını ayarlar.
     * @param newSeriesName Ayarlanacak yeni seri adı (genellikle hisse senedi sembolü).
     */
    public void setSeriesNameAndTitle(String newSeriesName) {
        this.seriesName = newSeriesName;
        if (xyChart != null) {
            xyChart.setTitle(newSeriesName + " Fiyat Grafiği");
            if (chartComponentPanel != null) {
                chartComponentPanel.repaint();
            }
        }
    }

    public void addDataPoint(Date timestamp, double price) {
        // Seri adı "Hisse X" gibi bir başlangıç değeri ise veya null ise veri ekleme.
        if (this.seriesName == null || this.seriesName.startsWith("Hisse ")) {
            return;
        }

        synchronized (xData) {
            xData.add(timestamp);
            yData.add(price);

            while (xData.size() > MAX_DATA_POINTS) {
                xData.remove(0);
                yData.remove(0);
            }

            final List<Date> xDataCopy = new ArrayList<>(xData);
            final List<Double> yDataCopy = new ArrayList<>(yData);

            javax.swing.SwingUtilities.invokeLater(() -> {
                try {
                    if (!seriesExists) {
                        // Seri ilk defa oluşturuluyor.
                        // xDataCopy veya yDataCopy burada boşsa XChart hata verebilir.
                        // Bu yüzden ilk nokta geldiğinde boş olmamalarını sağlamalıyız.
                        if (xDataCopy.isEmpty() || yDataCopy.isEmpty()) {
                            // Henüz grafiğe eklenecek geçerli bir veri noktası yoksa çık.
                            // Bu genellikle ilk çağrıda olmaz çünkü addDataPoint bir veri ile çağrılır.
                            return;
                        }
                        XYSeries series = xyChart.addSeries(this.seriesName, xDataCopy, yDataCopy);
                        series.setLineColor(XChartSeriesColors.GREEN);
                        series.setMarker(SeriesMarkers.NONE);
                        seriesExists = true;
                    } else {
                        // Seri zaten var, güncelle.
                        xyChart.updateXYSeries(this.seriesName, xDataCopy, yDataCopy, null);
                    }

                    if (chartComponentPanel != null) {
                        chartComponentPanel.revalidate();
                        chartComponentPanel.repaint();
                    }
                } catch (IllegalArgumentException iae) {
                    // Genellikle ilk veri noktası eklenirken "Y-Axis data cannot be empty" gibi bir hata alınırsa
                    // seriesExists false kalır ve bir sonraki veri noktasında seri tekrar eklenmeye çalışılır.
                    // System.err.println("XChartPanel: Veri eklenirken/seri oluşturulurken hata (muhtemelen ilk nokta): " + iae.getMessage() + " Seri: " + this.seriesName);
                    seriesExists = false; // Serinin tekrar oluşturulmasını sağlamak için.
                } catch (Exception e) {
                    // System.err.println("XChartPanel: Grafik güncellenirken genel hata: " + e.getMessage());
                }
            });
        }
    }

    public void clearChart() {
        synchronized (xData) {
            xData.clear();
            yData.clear();

            javax.swing.SwingUtilities.invokeLater(() -> {
                if (seriesExists && this.seriesName != null && xyChart.getSeriesMap().containsKey(this.seriesName)) {
                    try {
                        xyChart.removeSeries(this.seriesName);
                    } catch (Exception e) {
                        // System.err.println("XChartPanel: Seri kaldırılırken hata: " + e.getMessage());
                    }
                }
                seriesExists = false; // Serinin bir sonraki addDataPoint'te yeniden oluşturulmasını sağlar.
                
                // Başlığı başlangıç durumuna getirebiliriz, örneğin "Hisse X" gibi
                // Bu, MainController tarafından setSeriesNameAndTitle ile tekrar ayarlanacaktır.
                // if (xyChart != null && this.seriesName != null && this.seriesName.startsWith("Hisse ")) {
                //     xyChart.setTitle(this.seriesName);
                // }

                if (chartComponentPanel != null) {
                    chartComponentPanel.revalidate();
                    chartComponentPanel.repaint();
                }
            });
        }
    }

    public XYChart getXyChart() {
        return xyChart;
    }

    // Panelin başlığını güncellemek için (MainController'daki setTitle ile karışmasın)
    public void setPanelTitle(String title) {
        if (xyChart != null) {
            xyChart.setTitle(title);
            if (chartComponentPanel != null) {
                chartComponentPanel.repaint();
            }
        }
    }
} 