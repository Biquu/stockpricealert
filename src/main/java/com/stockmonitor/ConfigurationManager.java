package com.stockmonitor;

import java.util.ArrayList;
import java.util.List;

// Basit bir hisse senedi ve eşik bilgisi tutan sınıf
class StockConfig {
    String symbol;
    String threshold;
    // İleride daha fazla yapılandırma eklenebilir (örn: renk, ses)

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
}

public class ConfigurationManager {
    private List<StockConfig> selectedStocks;
    private boolean apiMode = true; // true: API, false: Simülasyon
    private static final int MAX_STOCKS = 2;

    public ConfigurationManager() {
        selectedStocks = new ArrayList<>();
    }

    public void addStock(String symbol, String threshold) {
        if (selectedStocks.size() < MAX_STOCKS) {
            // Aynı sembolün tekrar eklenmemesini kontrol et (opsiyonel)
            boolean exists = selectedStocks.stream().anyMatch(s -> s.getSymbol().equalsIgnoreCase(symbol));
            if (!exists) {
                 selectedStocks.add(new StockConfig(symbol, threshold));
            } else {
                System.out.println("Uyarı: " + symbol + " zaten izleme listesinde.");
                // Varsa güncelleme de yapılabilir.
            }
        }
    }
    
    public void updateStock(int index, String symbol, String threshold) {
        if (index >= 0 && index < selectedStocks.size()) {
            selectedStocks.set(index, new StockConfig(symbol, threshold));
        } else if (index < MAX_STOCKS) { // Eğer o index boşsa ve max kapasiteye ulaşılmamışsa ekle
            // Mevcut olmayan bir sembolü doğrudan güncellemek yerine, boş slotları dolduracak şekilde ekleme mantığı eklenebilir.
            // Şimdilik bu durumu basitleştirelim, UI tarafı zaten 3 slot üzerinden gidecek.
            if (selectedStocks.size() <= index) { // Index'e kadar boşluk varsa doldur
                 for(int i = selectedStocks.size(); i < index; i++) {
                    selectedStocks.add(new StockConfig("", "")); // Boş konfigürasyon
                 }
                 selectedStocks.add(new StockConfig(symbol, threshold)); 
            } else {
                 selectedStocks.set(index, new StockConfig(symbol, threshold)); // Varolanı değiştir
            }
        }
    }

    public List<StockConfig> getSelectedStocks() {
        return new ArrayList<>(selectedStocks); // Değiştirilemez bir kopya döndür
    }

    public void clearSelectedStocks() {
        selectedStocks.clear();
    }

    public boolean isApiMode() {
        return apiMode;
    }

    public void setApiMode(boolean apiMode) {
        this.apiMode = apiMode;
    }
    
    public int getMaxStocks() {
        return MAX_STOCKS;
    }
} 