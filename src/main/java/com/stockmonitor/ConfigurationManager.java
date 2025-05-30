package com.stockmonitor;

import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

// Grafik tiplerini tanımlayan enum
enum GraphType {
    LINE("Çizgi"),
    CANDLE("Mum (Anlık Türetilmiş)");

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
        return LINE; // Varsayılan
    }
    
    @Override
    public String toString() {
        return displayName;
    }
}

// Basit bir hisse senedi ve eşik bilgisi tutan sınıf
class StockConfig {
    private String symbol;
    private String threshold; // "Koşul@Değer" formatında
    // private GraphType graphType; // Kaldırıldı

    // Constructor güncellendi, graphType parametresi kaldırıldı
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

    // public GraphType getGraphType() { // Kaldırıldı
    //     return graphType;
    // }
}

public class ConfigurationManager {
    private static final int MAX_STOCKS = 4; // MainController ile senkronize
    private List<StockConfig> selectedStocks;
    // private boolean apiMode = true; // CSV Modu kaldırıldığı için silindi

    // Preferences API için düğüm yolları
    private static final String PREFS_NODE_PATH = "com/stockmonitor";
    private static final String KEY_STOCK_SYMBOL_PREFIX = "stock_symbol_";
    private static final String KEY_STOCK_THRESHOLD_PREFIX = "stock_threshold_";
    // private static final String KEY_STOCK_GRAPHTYPE_PREFIX = "stock_graphtype_"; // Kaldırıldı
    // private static final String KEY_API_MODE = "api_mode"; // CSV Modu kaldırıldığı için silindi

    public ConfigurationManager() {
        selectedStocks = new ArrayList<>();
        // loadPreferences(); // Başlangıçta ayarları yükle
    }

    // addStock ve updateStock güncellendi, graphType parametresi kaldırıldı
    public void addStock(String symbol, String threshold) {
        if (selectedStocks.size() < MAX_STOCKS) {
            boolean exists = selectedStocks.stream().anyMatch(s -> s.getSymbol().equalsIgnoreCase(symbol));
            if (!exists) {
                 selectedStocks.add(new StockConfig(symbol, threshold));
            } else {
                System.out.println("Uyarı: " + symbol + " zaten izleme listesinde.");
            }
        }
    }
    
    public void updateStock(int index, String symbol, String threshold) {
        if (index >= 0 && index < selectedStocks.size()) {
            selectedStocks.set(index, new StockConfig(symbol, threshold));
        } else if (index < MAX_STOCKS) { 
            if (selectedStocks.size() <= index) { 
                 for(int i = selectedStocks.size(); i < index; i++) {
                    selectedStocks.add(new StockConfig("", "")); 
                 }
                 selectedStocks.add(new StockConfig(symbol, threshold)); 
            } else {
                 selectedStocks.set(index, new StockConfig(symbol, threshold)); 
            }
        }
    }

    public List<StockConfig> getSelectedStocks() {
        return new ArrayList<>(selectedStocks); // Kapsülleme için kopya döndür
    }

    public void clearSelectedStocks() {
        selectedStocks.clear();
    }

    public int getMaxStocks() {
        return MAX_STOCKS;
    }

    // public boolean isApiMode() { // CSV Modu kaldırıldığı için silindi
    //     return apiMode;
    // }

    // public void setApiMode(boolean apiMode) { // CSV Modu kaldırıldığı için silindi
    //     this.apiMode = apiMode;
    //     // savePreferences(); // Mod değiştiğinde kaydet
    // }

    // Ayarları kaydetmek ve yüklemek için metotlar (şu an için basit tutuluyor)
    // Gerçek bir uygulamada daha kapsamlı hata yönetimi ve veri doğrulama eklenebilir.
    public void savePreferences(List<StockConfig> configsToSave) {
        Preferences prefs = Preferences.userRoot().node(PREFS_NODE_PATH);
        // prefs.putBoolean(KEY_API_MODE, apiMode); // CSV Modu kaldırıldığı için silindi

        // Önceki kayıtları temizle
        for (int i = 0; i < MAX_STOCKS; i++) {
            prefs.remove(KEY_STOCK_SYMBOL_PREFIX + i);
            prefs.remove(KEY_STOCK_THRESHOLD_PREFIX + i);
            // prefs.remove(KEY_STOCK_GRAPHTYPE_PREFIX + i); // Kaldırıldı
        }
        
        this.selectedStocks.clear(); // Mevcut listeyi temizle

        for (int i = 0; i < configsToSave.size(); i++) {
            if (i < MAX_STOCKS) {
                StockConfig config = configsToSave.get(i);
                if (config.getSymbol() != null && !config.getSymbol().isEmpty()) {
                    prefs.put(KEY_STOCK_SYMBOL_PREFIX + i, config.getSymbol());
                    prefs.put(KEY_STOCK_THRESHOLD_PREFIX + i, config.getThreshold() != null ? config.getThreshold() : "");
                    // prefs.put(KEY_STOCK_GRAPHTYPE_PREFIX + i, config.getGraphType().name()); // Kaldırıldı
                    this.selectedStocks.add(config); // Kaydedilenleri mevcut listeye de ekle
                }
            }
        }
        // try { prefs.flush(); } catch (Exception e) { e.printStackTrace(); }
    }

    public List<StockConfig> loadPreferences() {
        Preferences prefs = Preferences.userRoot().node(PREFS_NODE_PATH);
        List<StockConfig> loadedConfigs = new ArrayList<>();
        // apiMode = prefs.getBoolean(KEY_API_MODE, true); // CSV Modu kaldırıldığı için silindi

        for (int i = 0; i < MAX_STOCKS; i++) {
            String symbol = prefs.get(KEY_STOCK_SYMBOL_PREFIX + i, null);
            if (symbol != null && !symbol.isEmpty()) {
                String threshold = prefs.get(KEY_STOCK_THRESHOLD_PREFIX + i, "");
                // String graphTypeName = prefs.get(KEY_STOCK_GRAPHTYPE_PREFIX + i, GraphType.LINE.name()); // Kaldırıldı
                // GraphType graphType = GraphType.LINE; // Kaldırıldı
                // try { graphType = GraphType.valueOf(graphTypeName); } ... // Kaldırıldı
                loadedConfigs.add(new StockConfig(symbol, threshold)); // graphType olmadan
            }
        }
        this.selectedStocks = new ArrayList<>(loadedConfigs); // Yüklenenleri mevcut listeye ata
        return loadedConfigs;
    }
} 