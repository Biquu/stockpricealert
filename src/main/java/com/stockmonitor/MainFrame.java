package com.stockmonitor;

import javax.swing.*;
import java.awt.*;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

public class MainFrame extends JFrame {

    private JPanel stockSelectionPanel;
    private JPanel graphPanelContainer; // Birden fazla grafik paneli içerecek
    private JTextArea alertArea;
    private JButton startButton, stopButton;
    private JComboBox<String>[] stockSymbolComboBoxes;
    private JTextField[] thresholdTextFields;
    private JLabel[] currentPriceLabels; // Her hisse için anlık fiyatı gösterecek JLabel
    private JRadioButton apiModeRadioButton, simulationModeRadioButton;
    private ButtonGroup dataSourceGroup;
    private MainController controller; // MainController referansı
    private XChartPanel[] xChartPanels; // XChart panellerini tutacak dizi
    private static final int NUM_STOCKS_TO_DISPLAY = 2; // Yeni sabit

    public MainFrame() {
        setTitle("Stock Monitor & Alert System");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10, 10)); // Ana yerleşim

        // Üst Panel: Kontroller ve Hisse Seçimi
        setupControlPanel();
        add(stockSelectionPanel, BorderLayout.NORTH);

        // Merkezi Alan: Canlı Hisse Grafikleri
        setupGraphPanelContainer();
        add(graphPanelContainer, BorderLayout.CENTER);

        // Alt Panel: Alarmlar ve Kayıtlar
        setupAlertPanel();
        add(new JScrollPane(alertArea), BorderLayout.SOUTH); // Kaydırılabilir olması için JScrollPane

        // Pencere boyutunu ayarla
        setPreferredSize(new Dimension(1200, 700)); // Tercih edilen boyutu artır
        pack(); // Bileşenlere göre boyutu ayarlar
        setMinimumSize(new Dimension(1000, 600)); // Minimum pencere boyutunu da biraz artıralım
        setLocationRelativeTo(null); // Pencereyi ortalar
    }

    private void setupControlPanel() {
        stockSelectionPanel = new JPanel(new GridBagLayout());
        stockSelectionPanel.setBorder(BorderFactory.createTitledBorder("Ayarlar ve Hisse Seçimi"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5); // Bileşenler arası boşluk
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Hisse senedi sembolleri için örnekler (daha sonra dinamik hale getirilebilir)
        String[] sampleSymbols = {
            "AAPL", "TSLA", "MSFT", "GOOGL", "AMZN", "NVDA", "META", // Hisseler
            "BINANCE:BTCUSDT", "BINANCE:ETHUSDT", "BINANCE:SOLUSDT", "BINANCE:XRPUSDT" // Kriptolar
        }; 

        stockSymbolComboBoxes = new JComboBox[NUM_STOCKS_TO_DISPLAY];
        thresholdTextFields = new JTextField[NUM_STOCKS_TO_DISPLAY];
        currentPriceLabels = new JLabel[NUM_STOCKS_TO_DISPLAY]; // Fiyat etiketleri için dizi oluşturuldu

        for (int i = 0; i < NUM_STOCKS_TO_DISPLAY; i++) {
            gbc.gridx = 0;
            gbc.gridy = i;
            gbc.weightx = 0;
            gbc.fill = GridBagConstraints.NONE;
            stockSelectionPanel.add(new JLabel("Hisse " + (i + 1) + ":"), gbc);

            stockSymbolComboBoxes[i] = new JComboBox<>(sampleSymbols);
            stockSymbolComboBoxes[i].setSelectedIndex(-1); // Başlangıçta hiçbir şey seçili olmasın
             // Kullanıcı bir sembol seçtiğinde başlangıç fiyatını getirmek için ActionListener eklenebilir (opsiyonel)
            final int currentIndex = i; // Lambda içinde kullanmak için
            stockSymbolComboBoxes[i].addActionListener(e -> {
                String selectedSymbol = (String) stockSymbolComboBoxes[currentIndex].getSelectedItem();
                if (selectedSymbol != null && !selectedSymbol.trim().isEmpty() && controller != null) {
                    controller.fetchAndDisplayInitialPrice(currentIndex, selectedSymbol);
                }
            });
            gbc.gridx = 1;
            gbc.weightx = 0.4; // ComboBox için ağırlık
            gbc.fill = GridBagConstraints.HORIZONTAL;
            stockSelectionPanel.add(stockSymbolComboBoxes[i], gbc);
            
            currentPriceLabels[i] = new JLabel("Fiyat: -");
            currentPriceLabels[i].setPreferredSize(new Dimension(100, currentPriceLabels[i].getPreferredSize().height));
            gbc.gridx = 2;
            gbc.weightx = 0.2; // Fiyat etiketi için ağırlık
            gbc.fill = GridBagConstraints.HORIZONTAL;
            stockSelectionPanel.add(currentPriceLabels[i], gbc);

            gbc.gridx = 3;
            gbc.weightx = 0.05; // "Eşik:" etiketi için dar ağırlık
            gbc.fill = GridBagConstraints.NONE;
            stockSelectionPanel.add(new JLabel("Eşik:"), gbc);

            thresholdTextFields[i] = new JTextField(8);
            gbc.gridx = 4; // Eşik alanı için yeni gridx
            gbc.weightx = 0.35; // Eşik alanı için ağırlık
            gbc.fill = GridBagConstraints.HORIZONTAL;
            stockSelectionPanel.add(thresholdTextFields[i], gbc);
        }

        // Veri Kaynağı Seçimi
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 1; // Genişliği sıfırla
        gbc.weightx = 0;
        stockSelectionPanel.add(new JLabel("Veri Kaynağı:"), gbc);

        apiModeRadioButton = new JRadioButton("API (Canlı)");
        apiModeRadioButton.setSelected(true); // Varsayılan olarak API seçili
        simulationModeRadioButton = new JRadioButton("Simülasyon (CSV)");

        dataSourceGroup = new ButtonGroup();
        dataSourceGroup.add(apiModeRadioButton);
        dataSourceGroup.add(simulationModeRadioButton);

        JPanel radioPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        radioPanel.add(apiModeRadioButton);
        radioPanel.add(simulationModeRadioButton);
        gbc.gridx = 1;
        gbc.gridy = 3;
        gbc.gridwidth = 4; // 4 sütunu kaplasın (Hisse, Fiyat, Eşik Etiketi, Eşik Alanı)
        stockSelectionPanel.add(radioPanel, gbc);

        // Kontrol Butonları
        startButton = new JButton("İzlemeyi Başlat");
        stopButton = new JButton("İzlemeyi Durdur");
        stopButton.setEnabled(false); // Başlangıçta pasif

        startButton.addActionListener(e -> {
            if (controller != null) {
                controller.startMonitoring();
            }
        });

        stopButton.addActionListener(e -> {
            if (controller != null) {
                controller.stopMonitoring();
            }
        });

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        buttonPanel.add(startButton);
        buttonPanel.add(stopButton);

        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.gridwidth = 4; // Tüm genişliği kaplasın
        gbc.fill = GridBagConstraints.NONE; // Butonlar genişlemesin
        gbc.anchor = GridBagConstraints.CENTER;
        stockSelectionPanel.add(buttonPanel, gbc);

        // TODO: Butonlar için ActionListener eklenecek
    }

    private void setupGraphPanelContainer() {
        graphPanelContainer = new JPanel();
        graphPanelContainer.setBorder(BorderFactory.createTitledBorder("Canlı Fiyat Grafikleri"));
        graphPanelContainer.setLayout(new GridLayout(1, NUM_STOCKS_TO_DISPLAY, 5, 5)); // NUM_STOCKS_TO_DISPLAY grafik için örnek yerleşim
        
        xChartPanels = new XChartPanel[NUM_STOCKS_TO_DISPLAY]; // Yeni XChartPanel dizisi

        for (int i = 0; i < NUM_STOCKS_TO_DISPLAY; i++) {
            // Başlangıçta boş bir sembolle veya genel bir başlıkla XChartPanel oluştur
            xChartPanels[i] = new XChartPanel("Hisse/Kripto " + (i + 1)); // Sembol daha sonra güncellenecek
            graphPanelContainer.add(xChartPanels[i]);
        }
    }

    private void setupAlertPanel() {
        alertArea = new JTextArea(10, 50);
        alertArea.setEditable(false);
        alertArea.setBorder(BorderFactory.createTitledBorder("Alarmlar ve Sistem Kayıtları"));
        // TODO: AlertManager'dan gelen mesajlar buraya eklenecek
        alertArea.setText("Alarmlar burada gösterilecek..."); // Geçici içerik
    }

    // Diğer GUI elemanlarını ve olay dinleyicilerini eklemek için metodlar buraya gelecek

    public void setController(MainController controller) {
        this.controller = controller;
    }

    public JTextArea getAlertArea() {
        return alertArea;
    }

    public XChartPanel getXChartPanel(int index) {
        if (index >= 0 && index < xChartPanels.length) {
            return xChartPanels[index];
        }
        return null;
    }

    public java.util.List<StockConfig> getSelectedStockConfigurations() {
        java.util.List<StockConfig> configs = new java.util.ArrayList<>();
        for (int i = 0; i < stockSymbolComboBoxes.length; i++) {
            String symbol = (String) stockSymbolComboBoxes[i].getSelectedItem();
            String threshold = thresholdTextFields[i].getText();
            if (symbol != null && !symbol.trim().isEmpty()) {
                 // TODO: "Select Symbol" gibi bir placeholder varsa onu atla
                if (!"Select Symbol".equals(symbol)) { // Örnek bir placeholder kontrolü
                    configs.add(new StockConfig(symbol.trim(), threshold.trim()));
                }
            }
        }
        return configs;
    }

    public boolean isApiModeSelected() {
        return apiModeRadioButton.isSelected();
    }

    public void updateButtonStates(boolean monitoringActive) {
        startButton.setEnabled(!monitoringActive);
        stopButton.setEnabled(monitoringActive);
    }

    // Uygulama kapatılırken kaynakları serbest bırakmak için
    private void addWindowClosingListener(MainController controller) {
        this.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                if (controller != null) {
                    controller.onApplicationExit();
                }
                System.exit(0); // Emin olmak için, normalde setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE) yeterli
            }
        });
    }

    // Bu metod StockMonitorApp'tan çağrılabilir.
    public void initializeController() {
        this.controller = new MainController();
        this.controller.setMainFrame(this);
        addWindowClosingListener(this.controller); // Kapatma dinleyicisini ekle
        // Başlangıçta seçili olabilecek sembollerin fiyatlarını yükle (eğer varsa)
        // Ya da ComboBox'lar boş olduğu için bu adımı atlayabiliriz, kullanıcı seçince yüklenir.
    }

    public void updateInitialPriceDisplay(int stockIndex, double price, String symbol) {
        if (stockIndex >= 0 && stockIndex < currentPriceLabels.length) {
            SwingUtilities.invokeLater(() -> {
                if (price > 0) {
                    currentPriceLabels[stockIndex].setText(String.format("Fiyat: %.4f", price));
                } else {
                    currentPriceLabels[stockIndex].setText("Fiyat: N/A");
                }
            });
        }
    }

    public void clearInitialPriceDisplay(int stockIndex) {
        if (stockIndex >= 0 && stockIndex < currentPriceLabels.length) {
            SwingUtilities.invokeLater(() -> currentPriceLabels[stockIndex].setText("Fiyat: -"));
        }
    }
} 