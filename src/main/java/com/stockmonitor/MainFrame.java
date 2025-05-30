package com.stockmonitor;

import javax.swing.*;
// import javax.swing.border.TitledBorder; // Kullanılmıyor
import java.awt.*;
// import java.awt.event.ActionEvent; // Kullanılmıyor
// import java.awt.event.ActionListener; // Kullanılmıyor
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public class MainFrame extends JFrame {

    private MainController controller;
    private JTextArea alertArea;
    private JButton startButton, stopButton;

    // Dinamik hisse senedi girişi için listeler
    private List<JComboBox<String>> stockSelectionCombos;
    private List<JComboBox<String>> conditionCombos; // Eskiden operatorCombos idi, adı değişti
    private List<JTextField> targetValueFields;   // Eskiden thresholdValueFields idi, adı değişti
    private List<JLabel> priceLabels; // Anlık fiyatları göstermek için
    private List<XChartPanel> chartPanels;

    private static final int NUM_STOCK_SLOTS = 4; // İzlenecek maksimum hisse senedi sayısı

    private final String[] availableSymbols = {"AAPL", "MSFT", "GOOGL", "AMZN", "TSLA", "NVDA", "META", 
                                               "BINANCE:BTCUSDT", "BINANCE:ETHUSDT", "BINANCE:SOLUSDT", 
                                               "BINANCE:ADAUSDT", "BINANCE:XRPUSDT", "BINANCE:DOGEUSDT",
                                               "COINBASE:BTC-USD", "COINBASE:ETH-USD",
                                               "OANDA:EUR_USD", "OANDA:GBP_USD", "OANDA:USD_JPY" };
    // Yeni uyarı koşulları
    private final String[] alertConditions = {"Fiyat > Değer", "Fiyat < Değer", "Fiyat Kesişir (Yukarı)", "Fiyat Kesişir (Aşağı)"};
    private DecimalFormat priceDecimalFormat;

    public MainFrame(MainController controller) {
        this.controller = controller;
        setTitle("Hisse Senedi İzleme Sistemi v1.5 (Gelişmiş Yerleşim)"); 
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        // Tam ekran ayarları
        setExtendedState(JFrame.MAXIMIZED_BOTH); 
        // setUndecorated(true); // Başlık çubuğunu gizlemek istenirse (opsiyonel)

        setLayout(new BorderLayout(10, 10));
        ((JPanel) getContentPane()).setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        priceDecimalFormat = new DecimalFormat("0.####", symbols);

        stockSelectionCombos = new ArrayList<>();
        conditionCombos = new ArrayList<>();
        targetValueFields = new ArrayList<>();
        priceLabels = new ArrayList<>();
        chartPanels = new ArrayList<>();

        // Ana bileşenleri oluştur
        JPanel controlPanel = setupControlPanel();
        JPanel chartsPanel = setupChartsPanel(); // Sadece grafikleri içeren panel
        JScrollPane alertsPanel = setupAlertsPanel(); // Sadece alarmları içeren scroll pane

        // Kontrol paneli ve alarmlar için JSplitPane
        JSplitPane southSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, controlPanel, alertsPanel);
        southSplitPane.setResizeWeight(0.4); // Kontrol paneli %40, alarmlar %60 başlangıç genişliği
        southSplitPane.setOneTouchExpandable(true); // Hızlı daraltma/genişletme okları

        // Ana pencereye bileşenleri ekle
        add(chartsPanel, BorderLayout.CENTER); // Grafikler merkezde, en büyük alanı alır
        add(southSplitPane, BorderLayout.SOUTH); // Kontrol ve alarmlar yan yana altta

        // pack(); // Tam ekran için pack() genellikle çağrılmaz veya sonda çağrılır
        // setMinimumSize(new Dimension(800, 700)); // Tam ekran için bu anlamsız
        setLocationRelativeTo(null); // Tam ekrandan sonra çağrılması pek etkili olmayabilir
        updateButtonStates(false);
        loadConfigurationsToUI(); // Kayıtlı ayarları UI'a yükle
    }

    private JPanel setupControlPanel() {
        JPanel controlPanel = new JPanel(new GridBagLayout());
        controlPanel.setBorder(BorderFactory.createTitledBorder("Kontrol Paneli"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weighty = 0; // Dikeyde boşluk bırakmasın

        // Başlıklar güncellendi (Grafik Tipi kaldırıldı)
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.30; controlPanel.add(new JLabel("Hisse/Kripto Sembolü:"), gbc);
        gbc.gridx = 1; gbc.gridy = 0; gbc.weightx = 0.30; controlPanel.add(new JLabel("Uyarı Koşulu:"), gbc);
        gbc.gridx = 2; gbc.gridy = 0; gbc.weightx = 0.20; controlPanel.add(new JLabel("Hedef Değer:"), gbc);
        gbc.gridx = 3; gbc.gridy = 0; gbc.weightx = 0.20; gbc.anchor = GridBagConstraints.CENTER; controlPanel.add(new JLabel("Anlık Fiyat:"), gbc);
        gbc.anchor = GridBagConstraints.WEST; // Sonraki bileşenler için varsayılan

        // Hisse senedi giriş alanları (Dinamik olarak NUM_STOCK_SLOTS kadar oluşturulacak)
        for (int i = 0; i < NUM_STOCK_SLOTS; i++) {
            gbc.gridy = i + 1; // Her satır için y konumunu artır

            // Hisse Senedi Seçimi ComboBox
            gbc.gridx = 0; gbc.weightx = 0.30;
            JComboBox<String> stockCombo = new JComboBox<>(availableSymbols);
            stockCombo.setEditable(true); 
            stockCombo.setSelectedIndex(-1); // Başlangıçta hiçbir şey seçili olmasın
            final int stockIndex = i; // Lambda için final veya effectively final
            stockCombo.addActionListener(_e -> { // Lambda parametresi _e olarak değiştirildi (kullanılmıyor)
                String selectedSymbol = (String) stockCombo.getSelectedItem();
                if (controller != null && selectedSymbol != null && !selectedSymbol.trim().isEmpty()) {
                    controller.fetchAndDisplayInitialPrice(stockIndex, selectedSymbol);
                }
            });
            stockSelectionCombos.add(stockCombo);
            controlPanel.add(stockCombo, gbc);

            // Uyarı Koşulu Seçimi ComboBox
            gbc.gridx = 1; gbc.weightx = 0.30;
            JComboBox<String> condCombo = new JComboBox<>(alertConditions);
            conditionCombos.add(condCombo);
            controlPanel.add(condCombo, gbc);

            // Hedef Değer Giriş Alanı
            gbc.gridx = 2; gbc.weightx = 0.20;
            JTextField valField = new JTextField(7); // Boyut biraz küçültüldü
            targetValueFields.add(valField);
            controlPanel.add(valField, gbc);

            // Anlık Fiyat Etiketi
            gbc.gridx = 3; gbc.weightx = 0.20;
            JLabel priceLabel = new JLabel("N/A");
            priceLabel.setPreferredSize(new Dimension(70, priceLabel.getPreferredSize().height)); // Boyut ayarlandı
            priceLabel.setHorizontalAlignment(SwingConstants.RIGHT);
            priceLabels.add(priceLabel);
            controlPanel.add(priceLabel, gbc);
        }

        // Butonlar Paneli
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        startButton = new JButton("İzlemeyi Başlat"); // İkonlar şimdilik kaldırıldı, L&F sorunları olabilir
        startButton.addActionListener(_e -> controller.startMonitoring()); // Lambda parametresi _e
        buttonPanel.add(startButton);

        stopButton = new JButton("İzlemeyi Durdur");
        stopButton.addActionListener(_e -> controller.stopMonitoring()); // Lambda parametresi _e
        buttonPanel.add(stopButton);

        gbc.gridx = 0; gbc.gridy = NUM_STOCK_SLOTS + 1; // Y pozisyonu veri kaynağı satırına göre ayarlandı
        gbc.gridwidth = 4;       // 4 sütunu kaplasın
        gbc.anchor = GridBagConstraints.EAST;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 1.0; // Panelin sağa yaslanmasını sağla
        controlPanel.add(buttonPanel, gbc);

        return controlPanel;
    }

    // Sadece grafikleri içeren paneli oluşturur
    private JPanel setupChartsPanel() {
        JPanel chartsOuterPanel = new JPanel(new GridLayout(2, 2, 10, 10)); 
        chartsOuterPanel.setBorder(BorderFactory.createTitledBorder("Fiyat Grafikleri (Mum)")); 

        for (int i = 0; i < NUM_STOCK_SLOTS; i++) {
            XChartPanel chartPanel = new XChartPanel("Hisse/Kripto " + (i + 1));
            chartPanels.add(chartPanel);
            chartsOuterPanel.add(chartPanel);
        }
        return chartsOuterPanel;
    }

    // Sadece alarmları içeren paneli oluşturur
    private JScrollPane setupAlertsPanel() {
        alertArea = new JTextArea(10, 30); // JSplitPane içinde daha dar bir başlangıç (sütun sayısı azaltıldı)
        alertArea.setEditable(false);
        alertArea.setLineWrap(true);
        alertArea.setWrapStyleWord(true);
        JScrollPane alertScrollPane = new JScrollPane(alertArea);
        alertScrollPane.setBorder(BorderFactory.createTitledBorder("Alarmlar ve Sistem Mesajları"));
        // alertScrollPane.setMinimumSize(new Dimension(200, 100)); // JSplitPane için minimum boyut (opsiyonel)
        return alertScrollPane;
    }

    public JTextArea getAlertArea() {
        return alertArea;
    }

    public List<StockConfig> getSelectedStockConfigurations() {
        List<StockConfig> configs = new ArrayList<>();
        for (int i = 0; i < NUM_STOCK_SLOTS; i++) {
            String symbol = (String) stockSelectionCombos.get(i).getSelectedItem();
            if (symbol == null || symbol.trim().isEmpty()) {
                continue; 
            }

            String condition = (String) conditionCombos.get(i).getSelectedItem();
            String valueText = targetValueFields.get(i).getText();

            String combinedThreshold = "";
            if (condition != null && valueText != null && !valueText.trim().isEmpty()) {
                try {
                    Double.parseDouble(valueText.trim().replace(',', '.'));
                    combinedThreshold = condition + "@" + valueText.trim().replace(',', '.');
                } catch (NumberFormatException e) {
                    System.err.println("Uyarı: " + symbol + " için geçersiz hedef değer: " + valueText + ". Uyarı koşulu dikkate alınmayacak.");
                }
            }
            configs.add(new StockConfig(symbol.trim().toUpperCase(), combinedThreshold));
        }
        return configs;
    }

    public void updateButtonStates(boolean monitoringActive) {
        startButton.setEnabled(!monitoringActive);
        stopButton.setEnabled(monitoringActive);
        // Kullanıcı arayüzündeki giriş alanları da izleme durumuna göre enable/disable edilebilir.
        for (int i = 0; i < NUM_STOCK_SLOTS; i++) {
            stockSelectionCombos.get(i).setEnabled(!monitoringActive);
            conditionCombos.get(i).setEnabled(!monitoringActive);
            targetValueFields.get(i).setEnabled(!monitoringActive);
        }
    }

    public XChartPanel getXChartPanel(int index) {
        if (index >= 0 && index < chartPanels.size()) {
            return chartPanels.get(index);
        }
        return null;
    }

    public void updateInitialPriceDisplay(int stockIndex, double price, String symbol) {
        if (stockIndex >= 0 && stockIndex < NUM_STOCK_SLOTS) {
            if (price == -1 || Double.isNaN(price)) { // Hata veya N/A durumu
                priceLabels.get(stockIndex).setText("Hata");
                priceLabels.get(stockIndex).setForeground(Color.RED);
                targetValueFields.get(stockIndex).setText(""); // Hata durumunda hedef değeri temizle
            } else {
                String formattedPrice = priceDecimalFormat.format(price);
                priceLabels.get(stockIndex).setText(formattedPrice);
                priceLabels.get(stockIndex).setForeground(Color.BLUE);
                // Anlık fiyatı ilgili Hedef Değer alanına da yaz (kullanıcı değiştirebilir)
                targetValueFields.get(stockIndex).setText(formattedPrice.replace(',', '.')); // Formatın nokta kullandığından emin ol
            }
        } else {
            System.err.println("Geçersiz stockIndex: " + stockIndex + " for symbol: " + symbol);
        }
    }

    public void clearInitialPriceDisplay(int stockIndex) {
        if (stockIndex >= 0 && stockIndex < NUM_STOCK_SLOTS) {
            priceLabels.get(stockIndex).setText("N/A");
            priceLabels.get(stockIndex).setForeground(UIManager.getColor("Label.foreground"));
            targetValueFields.get(stockIndex).setText(""); // Temizlerken hedef değeri de temizle
        }
    }

    // Kayıtlı konfigürasyonları UI elemanlarına yükler
    public void loadConfigurationsToUI() {
        if (controller == null) return;
        List<StockConfig> configs = controller.getSavedConfigurations(); // Kayıtlı konfigürasyonları al

        for (int i = 0; i < NUM_STOCK_SLOTS; i++) {
            if (i < configs.size()) {
                StockConfig config = configs.get(i);
                stockSelectionCombos.get(i).setSelectedItem(config.getSymbol());
                
                // Eşik stringini parse et: "Koşul@Değer"
                String thresholdStr = config.getThreshold();
                if (thresholdStr != null && !thresholdStr.isEmpty() && thresholdStr.contains("@")) {
                    String[] parts = thresholdStr.split("@", 2);
                    conditionCombos.get(i).setSelectedItem(parts[0]);
                    targetValueFields.get(i).setText(parts[1]);
                } else {
                    conditionCombos.get(i).setSelectedIndex(0); // Varsayılan koşul
                    targetValueFields.get(i).setText("");
                }
                if (config.getSymbol() != null && !config.getSymbol().trim().isEmpty()) {
                     controller.fetchAndDisplayInitialPrice(i, config.getSymbol());
                }

            } else {
                // Bu slot için kayıtlı konfigürasyon yok, UI'ı temizle
                stockSelectionCombos.get(i).setSelectedIndex(-1);
                conditionCombos.get(i).setSelectedIndex(0);
                targetValueFields.get(i).setText("");
                clearInitialPriceDisplay(i);
            }
        }
    }
} 