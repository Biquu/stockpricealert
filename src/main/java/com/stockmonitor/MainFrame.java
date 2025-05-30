package com.stockmonitor;

import javax.swing.*;
// import javax.swing.border.TitledBorder; // Not used
import java.awt.*;
// import java.awt.event.ActionEvent; // Not used
// import java.awt.event.ActionListener; // Not used
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

    // Lists for dynamic stock input
    private List<JComboBox<String>> stockSelectionCombos;
    private List<JComboBox<String>> conditionCombos; // Formerly operatorCombos, name changed
    private List<JTextField> targetValueFields;   // Formerly thresholdValueFields, name changed
    private List<JLabel> priceLabels; // To display live prices
    private List<XChartPanel> chartPanels;

    private static final int NUM_STOCK_SLOTS = 4; // Maximum number of stocks to monitor

    private final String[] availableSymbols = {"", "AAPL", "MSFT", "GOOGL", "AMZN", "TSLA", "NVDA", "META", 
                                               "BINANCE:BTCUSDT", "BINANCE:ETHUSDT", "BINANCE:SOLUSDT", 
                                               "BINANCE:ADAUSDT", "BINANCE:XRPUSDT", "BINANCE:DOGEUSDT",
                                               "COINBASE:BTC-USD", "COINBASE:ETH-USD",
                                               "OANDA:EUR_USD", "OANDA:GBP_USD", "OANDA:USD_JPY" };
    // New alert conditions
    private final String[] alertConditions = {"Price > Value", "Price < Value", "Price Crosses (Up)", "Price Crosses (Down)"};
    private DecimalFormat priceDecimalFormat;

    public MainFrame(MainController controller) {
        this.controller = controller;
        setTitle("Stock Monitoring System v1.5 (Advanced Layout)"); 
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        // Full-screen settings
        setExtendedState(JFrame.MAXIMIZED_BOTH); 
        // setUndecorated(true); // If you want to hide the title bar (optional)

        setLayout(new BorderLayout(10, 10));
        ((JPanel) getContentPane()).setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        priceDecimalFormat = new DecimalFormat("0.####", symbols);

        stockSelectionCombos = new ArrayList<>();
        conditionCombos = new ArrayList<>();
        targetValueFields = new ArrayList<>();
        priceLabels = new ArrayList<>();
        chartPanels = new ArrayList<>();

        // Create main components
        JPanel controlPanel = setupControlPanel();
        JPanel chartsPanel = setupChartsPanel(); // Panel containing only charts
        JScrollPane alertsPanel = setupAlertsPanel(); // Scroll pane containing only alarms

        // JSplitPane for control panel and alarms
        JSplitPane southSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, controlPanel, alertsPanel);
        southSplitPane.setResizeWeight(0.4); // Control panel 40%, alarms 60% initial width
        southSplitPane.setOneTouchExpandable(true); // Arrows for quick collapse/expand

        // Add components to the main window
        add(chartsPanel, BorderLayout.CENTER); // Charts in the center, takes the largest area
        add(southSplitPane, BorderLayout.SOUTH); // Control and alarms side by side at the bottom

        // pack(); // pack() is generally not called for full screen, or called at the end
        // setMinimumSize(new Dimension(800, 700)); // Meaningless for full screen
        setLocationRelativeTo(null); // May not be very effective if called after full screen
        updateButtonStates(false);
        loadConfigurationsToUI(); // Load saved settings to UI
    }

    private JPanel setupControlPanel() {
        JPanel controlPanel = new JPanel(new GridBagLayout());
        controlPanel.setBorder(BorderFactory.createTitledBorder("Control Panel"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weighty = 0; // Don't leave vertical space

        // Titles updated (Graph Type removed)
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.30; controlPanel.add(new JLabel("Stock/Crypto Symbol:"), gbc);
        gbc.gridx = 1; gbc.gridy = 0; gbc.weightx = 0.30; controlPanel.add(new JLabel("Alert Condition:"), gbc);
        gbc.gridx = 2; gbc.gridy = 0; gbc.weightx = 0.20; controlPanel.add(new JLabel("Target Value:"), gbc);
        gbc.gridx = 3; gbc.gridy = 0; gbc.weightx = 0.20; gbc.anchor = GridBagConstraints.CENTER; controlPanel.add(new JLabel("Current Price:"), gbc);
        gbc.anchor = GridBagConstraints.WEST; // Default for subsequent components

        // Stock input fields (Dynamically created up to NUM_STOCK_SLOTS)
        for (int i = 0; i < NUM_STOCK_SLOTS; i++) {
            gbc.gridy = i + 1; // Increment y position for each row

            // Stock Selection ComboBox
            gbc.gridx = 0; gbc.weightx = 0.30;
            JComboBox<String> stockCombo = new JComboBox<>(availableSymbols);
            stockCombo.setEditable(true); 
            stockCombo.setSelectedIndex(0); // Empty option selected initially
            final int stockIndex = i; // Final or effectively final for lambda
            stockCombo.addActionListener(_e -> { // Lambda parameter changed to _e (not used)
                String selectedSymbol = (String) stockCombo.getSelectedItem();
                if (controller != null) {
                    if (selectedSymbol != null && !selectedSymbol.trim().isEmpty()) {
                        controller.fetchAndDisplayInitialPrice(stockIndex, selectedSymbol);
                    } else {
                        // Clear UI when symbol is selected as empty
                        clearInitialPriceDisplay(stockIndex);
                        if (chartPanels.get(stockIndex) != null) {
                            chartPanels.get(stockIndex).clearChart();
                            // Reset chart title to its initial state
                            chartPanels.get(stockIndex).setPanelTitle("Stock/Crypto " + (stockIndex + 1));
                        }
                    }
                }
            });
            stockSelectionCombos.add(stockCombo);
            controlPanel.add(stockCombo, gbc);

            // Alert Condition Selection ComboBox
            gbc.gridx = 1; gbc.weightx = 0.30;
            JComboBox<String> condCombo = new JComboBox<>(alertConditions);
            conditionCombos.add(condCombo);
            controlPanel.add(condCombo, gbc);

            // Target Value Input Field
            gbc.gridx = 2; gbc.weightx = 0.20;
            JTextField valField = new JTextField(7); // Size slightly reduced
            targetValueFields.add(valField);
            controlPanel.add(valField, gbc);

            // Current Price Label
            gbc.gridx = 3; gbc.weightx = 0.20;
            JLabel priceLabel = new JLabel("N/A");
            priceLabel.setPreferredSize(new Dimension(70, priceLabel.getPreferredSize().height)); // Size adjusted
            priceLabel.setHorizontalAlignment(SwingConstants.RIGHT);
            priceLabels.add(priceLabel);
            controlPanel.add(priceLabel, gbc);
        }

        // Buttons Panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        startButton = new JButton("Start Monitoring"); // Icons removed for now, L&F issues possible
        startButton.addActionListener(_e -> controller.startMonitoring()); // Lambda parameter _e
        buttonPanel.add(startButton);

        stopButton = new JButton("Stop Monitoring");
        stopButton.addActionListener(_e -> controller.stopMonitoring()); // Lambda parameter _e
        buttonPanel.add(stopButton);

        gbc.gridx = 0; gbc.gridy = NUM_STOCK_SLOTS + 1; // Y position adjusted according to data source row
        gbc.gridwidth = 4;       // Span 4 columns
        gbc.anchor = GridBagConstraints.EAST;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 1.0; // Ensure panel aligns to the right
        controlPanel.add(buttonPanel, gbc);

        return controlPanel;
    }

    // Creates the panel containing only charts
    private JPanel setupChartsPanel() {
        JPanel chartsOuterPanel = new JPanel(new GridLayout(2, 2, 10, 10)); 
        chartsOuterPanel.setBorder(BorderFactory.createTitledBorder("Price Charts (Candle)")); 

        for (int i = 0; i < NUM_STOCK_SLOTS; i++) {
            XChartPanel chartPanel = new XChartPanel("Stock/Crypto " + (i + 1));
            chartPanels.add(chartPanel);
            chartsOuterPanel.add(chartPanel);
        }
        return chartsOuterPanel;
    }

    // Creates the panel containing only alarms
    private JScrollPane setupAlertsPanel() {
        alertArea = new JTextArea(10, 30); // Narrower initial width in JSplitPane (column count reduced)
        alertArea.setEditable(false);
        alertArea.setLineWrap(true);
        alertArea.setWrapStyleWord(true);
        JScrollPane alertScrollPane = new JScrollPane(alertArea);
        alertScrollPane.setBorder(BorderFactory.createTitledBorder("Alerts and System Messages"));
        // alertScrollPane.setMinimumSize(new Dimension(200, 100)); // Minimum size for JSplitPane (optional)
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
                    System.err.println("Warning: Invalid target value for " + symbol + ": " + valueText + ". Alert condition will be ignored.");
                }
            }
            configs.add(new StockConfig(symbol.trim().toUpperCase(), combinedThreshold));
        }
        return configs;
    }

    public void updateButtonStates(boolean monitoringActive) {
        startButton.setEnabled(!monitoringActive);
        stopButton.setEnabled(monitoringActive);
        // Input fields in the UI can also be enabled/disabled based on monitoring state.
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
            if (price == -1 || Double.isNaN(price)) { // Error or N/A state
                priceLabels.get(stockIndex).setText("Error");
                priceLabels.get(stockIndex).setForeground(Color.RED);
                targetValueFields.get(stockIndex).setText(""); // Clear target value in case of error
            } else {
                String formattedPrice = priceDecimalFormat.format(price);
                priceLabels.get(stockIndex).setText(formattedPrice);
                priceLabels.get(stockIndex).setForeground(Color.BLUE);
                // Also write the current price to the corresponding Target Value field (user can change it)
                targetValueFields.get(stockIndex).setText(formattedPrice.replace(',', '.')); // Ensure format uses a dot
            }
        }
    }
    
    public void clearInitialPriceDisplay(int stockIndex) {
        if (stockIndex >= 0 && stockIndex < NUM_STOCK_SLOTS) {
            priceLabels.get(stockIndex).setText("N/A");
            priceLabels.get(stockIndex).setForeground(Color.BLACK); // Default color
            // targetValueFields.get(stockIndex).setText(""); // Optionally clear target value as well
        }
    }

    // Load configurations from ConfigurationManager and update UI
    public void loadConfigurationsToUI() {
        List<StockConfig> savedConfigs = controller.getSavedConfigurations();
        if (savedConfigs != null) {
            for (int i = 0; i < NUM_STOCK_SLOTS && i < savedConfigs.size(); i++) {
                StockConfig config = savedConfigs.get(i);
                if (config != null && config.getSymbol() != null && !config.getSymbol().isEmpty()) {
                    stockSelectionCombos.get(i).setSelectedItem(config.getSymbol());
                    // Trigger action listener to fetch initial price
                    // Ensure this doesn't cause issues if controller or other parts are not fully initialized
                    // This might be better handled by having controller fetch prices after UI is fully up
                    // For now, let's directly call it if symbol is valid
                    controller.fetchAndDisplayInitialPrice(i, config.getSymbol());

                    String threshold = config.getThreshold();
                    if (threshold != null && threshold.contains("@")) {
                        String[] parts = threshold.split("@", 2);
                        conditionCombos.get(i).setSelectedItem(parts[0]);
                        targetValueFields.get(i).setText(parts[1]);
                    } else {
                        conditionCombos.get(i).setSelectedIndex(0); // Default condition
                        targetValueFields.get(i).setText("");
                    }
                } else {
                    // Clear this slot if no valid config or empty symbol
                    stockSelectionCombos.get(i).setSelectedIndex(0);
                    conditionCombos.get(i).setSelectedIndex(0);
                    targetValueFields.get(i).setText("");
                    clearInitialPriceDisplay(i);
                }
            }
        }
    }
} 