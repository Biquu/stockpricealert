package com.stockmonitor;

import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

// Enum defining graph types
enum GraphType {
    LINE("Line"),
    CANDLE("Candle (Live Derived)");

    private final String displayName;

    GraphType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static GraphType fromDisplayName(String displayName) {
        for (GraphType type : GraphType.values()) {
            if (type.displayName.equals(displayName)) {
                return type;
            }
        }
        return LINE; // Default
    }
    
    @Override
    public String toString() {
        return displayName;
    }
}

// Class to hold simple stock and threshold information
class StockConfig {
    private String symbol;
    private String threshold; // Format: "Condition@Value"
    // private GraphType graphType; // Removed

    // Constructor updated, graphType parameter removed
    public StockConfig(String symbol, String threshold) {
        this.symbol = symbol;
        this.threshold = threshold;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getThreshold() {
        return threshold;
    }

    // public GraphType getGraphType() { // Removed
    //     return graphType;
    // }
}

public class ConfigurationManager {
    private static final int MAX_STOCKS = 4; // Synchronized with MainController
    private List<StockConfig> selectedStocks;
    // private boolean apiMode = true; // Deleted as CSV Mode was removed

    // Node paths for Preferences API
    private static final String PREFS_NODE_PATH = "com/stockmonitor";
    private static final String KEY_STOCK_SYMBOL_PREFIX = "stock_symbol_";
    private static final String KEY_STOCK_THRESHOLD_PREFIX = "stock_threshold_";
    // private static final String KEY_STOCK_GRAPHTYPE_PREFIX = "stock_graphtype_"; // Removed
    // private static final String KEY_API_MODE = "api_mode"; // Deleted as CSV Mode was removed

    public ConfigurationManager() {
        selectedStocks = new ArrayList<>();
        System.out.println("[ConfigurationManager] [Thread: " + Thread.currentThread().getName() + "] Instance created.");
        // loadPreferences(); // Load settings at startup
    }

    // addStock and updateStock updated, graphType parameter removed
    public void addStock(String symbol, String threshold) {
        if (selectedStocks.size() < MAX_STOCKS) {
            boolean exists = selectedStocks.stream().anyMatch(s -> s.getSymbol().equalsIgnoreCase(symbol));
            if (!exists) {
                 selectedStocks.add(new StockConfig(symbol, threshold));
                 System.out.println("[ConfigurationManager] [Thread: " + Thread.currentThread().getName() + "] Stock added: " + symbol);
            } else {
                System.out.println("[ConfigurationManager] [Thread: " + Thread.currentThread().getName() + "] Warning: " + symbol + " is already in the monitoring list.");
            }
        }
    }
    
    public void updateStock(int index, String symbol, String threshold) {
        if (index >= 0 && index < selectedStocks.size()) {
            selectedStocks.set(index, new StockConfig(symbol, threshold));
            System.out.println("[ConfigurationManager] [Thread: " + Thread.currentThread().getName() + "] Stock updated at index " + index + ": " + symbol);
        } else if (index < MAX_STOCKS) { 
            if (selectedStocks.size() <= index) { 
                 for(int i = selectedStocks.size(); i < index; i++) {
                    selectedStocks.add(new StockConfig("", "")); 
                 }
                 selectedStocks.add(new StockConfig(symbol, threshold)); 
                 System.out.println("[ConfigurationManager] [Thread: " + Thread.currentThread().getName() + "] Stock added (with padding) at index " + index + ": " + symbol);
            } else {
                 selectedStocks.set(index, new StockConfig(symbol, threshold)); 
                 System.out.println("[ConfigurationManager] [Thread: " + Thread.currentThread().getName() + "] Stock updated (should be caught by first if) at index " + index + ": " + symbol);
            }
        }
    }

    public List<StockConfig> getSelectedStocks() {
        return new ArrayList<>(selectedStocks); // Return a copy for encapsulation
    }

    public void clearSelectedStocks() {
        System.out.println("[ConfigurationManager] [Thread: " + Thread.currentThread().getName() + "] Clearing selected stocks. Count before: " + selectedStocks.size());
        selectedStocks.clear();
    }

    public int getMaxStocks() {
        return MAX_STOCKS;
    }

    // public boolean isApiMode() { // Deleted as CSV Mode was removed
    //     return apiMode;
    // }

    // public void setApiMode(boolean apiMode) { // Deleted as CSV Mode was removed
    //     this.apiMode = apiMode;
    //     // savePreferences(); // Save when mode changes
    // }

    // Methods for saving and loading settings (kept simple for now)
    // More comprehensive error handling and data validation could be added in a real application.
    public void savePreferences(List<StockConfig> configsToSave) {
        System.out.println("[ConfigurationManager] [Thread: " + Thread.currentThread().getName() + "] Saving preferences. Number of configs to save: " + configsToSave.size());
        Preferences prefs = Preferences.userRoot().node(PREFS_NODE_PATH);
        // prefs.putBoolean(KEY_API_MODE, apiMode); // Deleted as CSV Mode was removed

        // Clear previous records
        for (int i = 0; i < MAX_STOCKS; i++) {
            prefs.remove(KEY_STOCK_SYMBOL_PREFIX + i);
            prefs.remove(KEY_STOCK_THRESHOLD_PREFIX + i);
            // prefs.remove(KEY_STOCK_GRAPHTYPE_PREFIX + i); // Removed
        }
        
        this.selectedStocks.clear(); // Clear current list

        for (int i = 0; i < configsToSave.size(); i++) {
            if (i < MAX_STOCKS) {
                StockConfig config = configsToSave.get(i);
                if (config.getSymbol() != null && !config.getSymbol().isEmpty()) {
                    prefs.put(KEY_STOCK_SYMBOL_PREFIX + i, config.getSymbol());
                    prefs.put(KEY_STOCK_THRESHOLD_PREFIX + i, config.getThreshold() != null ? config.getThreshold() : "");
                    // prefs.put(KEY_STOCK_GRAPHTYPE_PREFIX + i, config.getGraphType().name()); // Removed
                    this.selectedStocks.add(config); // Also add saved ones to current list
                    System.out.println("[ConfigurationManager] [Thread: " + Thread.currentThread().getName() + "] Saved config for slot " + i + ": " + config.getSymbol());
                }
            }
        }
        // try { prefs.flush(); } catch (Exception e) { e.printStackTrace(); }
    }

    public List<StockConfig> loadPreferences() {
        System.out.println("[ConfigurationManager] [Thread: " + Thread.currentThread().getName() + "] Loading preferences...");
        Preferences prefs = Preferences.userRoot().node(PREFS_NODE_PATH);
        List<StockConfig> loadedConfigs = new ArrayList<>();
        // apiMode = prefs.getBoolean(KEY_API_MODE, true); // Deleted as CSV Mode was removed

        for (int i = 0; i < MAX_STOCKS; i++) {
            String symbol = prefs.get(KEY_STOCK_SYMBOL_PREFIX + i, null);
            if (symbol != null && !symbol.isEmpty()) {
                String threshold = prefs.get(KEY_STOCK_THRESHOLD_PREFIX + i, "");
                // String graphTypeName = prefs.get(KEY_STOCK_GRAPHTYPE_PREFIX + i, GraphType.LINE.name()); // Removed
                // GraphType graphType = GraphType.LINE; // Removed
                // try { graphType = GraphType.valueOf(graphTypeName); } ... // Removed
                loadedConfigs.add(new StockConfig(symbol, threshold)); // without graphType
                System.out.println("[ConfigurationManager] [Thread: " + Thread.currentThread().getName() + "] Loaded config for slot " + i + ": " + symbol);
            }
        }
        this.selectedStocks = new ArrayList<>(loadedConfigs); // Assign loaded ones to current list
        System.out.println("[ConfigurationManager] [Thread: " + Thread.currentThread().getName() + "] Preferences loaded. Number of configs: " + loadedConfigs.size());
        return loadedConfigs;
    }
} 