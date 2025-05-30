package com.stockmonitor;

import com.stockmonitor.listeners.AlertListener;
import com.stockmonitor.listeners.GraphDataListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
// import javax.swing.JLabel; // Artık doğrudan kullanılmıyor olabilir, kontrol edilecek.

public class MainController {

    private MainFrame mainFrame; // Controller'ın MainFrame'e erişimi olacak
    private ConfigurationManager configManager;
    private PriceFetcher priceFetcher;
    private AlertManager alertManager;
    private GraphUpdater graphUpdater;
    // Başlangıç fiyatlarını çekmek için ayrı bir ExecutorService kullanılabilir veya mevcut olan paylaşılabilir.
    private ExecutorService initialPriceExecutorService;

    private List<StockWatcherThread> activeWatchers;
    private ExecutorService executorService; // Thread'leri yönetmek için
    // Finnhub ücretsiz API limiti dakikada ~60 istektir.
    // 2 hisse/kripto izlerken her birini 3 saniyede bir çekmek dakikada 2*20 = 40 istek yapar.
    private static final long FETCH_INTERVAL_SECONDS = 3;
    private static final int MAX_DISPLAY_CHARTS = 2; // MainFrame ile senkronize

    public MainController() {
        this.configManager = new ConfigurationManager();
        this.priceFetcher = new PriceFetcher();
        this.activeWatchers = new ArrayList<>();
        // ExecutorService, en fazla 3 thread'i aynı anda çalıştıracak şekilde ayarlanabilir,
        // ancak StockWatcherThread'ler zaten kendi içlerinde periyodik bekleme yapacakları için
        // dinamik bir thread havuzu kullanmak daha esnek olabilir.
        // Sabit 3 thread'lik bir havuz da kullanılabilir: Executors.newFixedThreadPool(configManager.getMaxStocks());
        this.executorService = Executors.newCachedThreadPool(); 
        this.initialPriceExecutorService = Executors.newSingleThreadExecutor(); // Başlangıç fiyatları için tek thread yeterli
    }

    // MainFrame oluşturulduktan sonra çağrılacak
    public void setMainFrame(MainFrame mainFrame) {
        this.mainFrame = mainFrame;
        this.alertManager = new AlertManager(mainFrame.getAlertArea()); 
        this.graphUpdater = new GraphUpdater();
        // mainFrame.setController(this); // Bu satır zaten StockMonitorApp'de yapılıyor olmalı
    }

    public void startMonitoring() {
        if (mainFrame == null) {
            System.err.println("MainFrame ayarlanmadan izleme başlatılamaz.");
            return;
        }

        stopMonitoring(); 
        activeWatchers.clear();
        configManager.clearSelectedStocks();
        graphUpdater.unregisterAllChartPanels(); 
        // graphUpdater.clearAllGraphs(); // XChartPanel.clearChart() her panel için çağrılacak

        List<StockConfig> configsFromUI = mainFrame.getSelectedStockConfigurations();
        // Önce tüm panellerin başlıklarını ve seri adlarını sıfırla/temizle
        for (int i = 0; i < MAX_DISPLAY_CHARTS; i++) { // Maksimum panel sayısına göre döngü
            XChartPanel existingPanel = mainFrame.getXChartPanel(i);
            if (existingPanel != null) {
                existingPanel.setSeriesNameAndTitle("Hisse/Kripto " + (i + 1)); // Başlangıç başlığı
                existingPanel.clearChart(); // Grafiği temizle
            }
        }

        for (StockConfig uiConfig : configsFromUI) {
            if (uiConfig.getSymbol() != null && !uiConfig.getSymbol().trim().isEmpty()) {
                configManager.addStock(uiConfig.getSymbol(), uiConfig.getThreshold());
            }
        }
        configManager.setApiMode(mainFrame.isApiModeSelected());

        if (configManager.getSelectedStocks().isEmpty()) {
            alertManager.logSystemMessage("İzlenecek hisse senedi seçilmedi.");
            mainFrame.updateButtonStates(false);
            return;
        }

        alertManager.logSystemMessage("İzleme başlatılıyor...");

        int panelIndex = 0;
        for (StockConfig stock : configManager.getSelectedStocks()) {
            if (stock.getSymbol() != null && !stock.getSymbol().isEmpty()) {
                if (panelIndex < MAX_DISPLAY_CHARTS) { // En fazla MAX_DISPLAY_CHARTS panelimiz var
                    XChartPanel chartPanel = mainFrame.getXChartPanel(panelIndex);
                    if (chartPanel != null) {
                        chartPanel.setSeriesNameAndTitle(stock.getSymbol()); // Seri adını ve başlığı ayarla
                        // chartPanel.clearChart(); // Yukarıda genel temizlik yapıldı, burada tekrar gerekebilir veya gerekmeyebilir.
                                                 // Eğer yukarıdaki genel temizlik yeterliyse bu satır kaldırılabilir.
                                                 // Şimdilik kalsın, zararı olmaz.
                        graphUpdater.registerChartPanel(stock.getSymbol(), chartPanel);
                    } else {
                        System.err.println("Uyarı: " + stock.getSymbol() + " için XChartPanel bulunamadı (index: " + panelIndex + ")");
                    }
                }
                panelIndex++;

                StockWatcherThread watcher = new StockWatcherThread(
                        stock.getSymbol(),
                        stock.getThreshold(),
                        priceFetcher,
                        alertManager,      
                        graphUpdater,      
                        FETCH_INTERVAL_SECONDS
                );
                activeWatchers.add(watcher);
                executorService.submit(watcher);
            }
        }
        mainFrame.updateButtonStates(true);
    }

    public void stopMonitoring() {
        if (activeWatchers.isEmpty() && (executorService == null || executorService.isShutdown())) {
            mainFrame.updateButtonStates(false);
            return;
        }
        
        alertManager.logSystemMessage("İzleme durduruluyor...");
        for (StockWatcherThread watcher : activeWatchers) {
            watcher.stopWatching();
        }
        activeWatchers.clear();

        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdownNow(); 
            try {
                if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    System.err.println("Bazı izleme threadleri zamanında durdurulamadı.");
                }
            } catch (InterruptedException e) {
                System.err.println("Threadlerin durdurulması beklenirken kesinti oluştu.");
                executorService.shutdownNow(); 
                Thread.currentThread().interrupt();
            }
        }
        this.executorService = Executors.newCachedThreadPool(); 
        // İzleme durduğunda grafikler kalsın mı, temizlensin mi?
        // Şimdilik temizleyelim, bir sonraki başlatmada temiz başlar.
        // graphUpdater.unregisterAllChartPanels(); // Panelleri ayırmak için
        // graphUpdater.clearAllGraphs(); // Veriyi temizlemek için
        // Kullanıcı Durdur dediğinde grafiklerin son halini görmesi daha iyi olabilir.
        // Bu yüzden clearAllGraphs() veya unregisterAllChartPanels() burada çağrılmayabilir.
        // Yeniden Başlatıldığında startMonitoring içinde zaten temizleniyor.
        mainFrame.updateButtonStates(false);
        alertManager.logSystemMessage("İzleme durduruldu.");
    }
    
    // Uygulama kapanırken çağrılacak temizlik metodu
    public void onApplicationExit() {
        System.out.println("Uygulama kapatılıyor, kaynaklar serbest bırakılıyor...");
        stopMonitoring(); // Bu zaten executorService'i ele alıyor (veya almalı)

        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        if (initialPriceExecutorService != null && !initialPriceExecutorService.isShutdown()) {
            initialPriceExecutorService.shutdown();
            try {
                if (!initialPriceExecutorService.awaitTermination(2, TimeUnit.SECONDS)) {
                    initialPriceExecutorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                initialPriceExecutorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        System.out.println("Uygulama kapatıldı.");
    }

    // MainFrame'deki ComboBox'tan bir hisse seçildiğinde çağrılır
    public void fetchAndDisplayInitialPrice(final int stockIndex, final String symbol) {
        if (symbol == null || symbol.trim().isEmpty()) {
            mainFrame.clearInitialPriceDisplay(stockIndex);
            return;
        }

        initialPriceExecutorService.submit(() -> {
            try {
                // Simülasyon modu seçiliyse API çağrısı yapma
                if (mainFrame != null && !mainFrame.isApiModeSelected()) {
                    // Simülasyon modunda başlangıç fiyatı için özel bir mantık eklenebilir
                    // veya sadece N/A gösterilebilir.
                    mainFrame.updateInitialPriceDisplay(stockIndex, -1, symbol); // -1 N/A anlamına gelsin
                    System.out.println("Simülasyon modu aktif, " + symbol + " için başlangıç fiyatı API'den çekilmiyor.");
                    return;
                }
                
                System.out.println(symbol + " için başlangıç fiyatı çekiliyor...");
                double price = priceFetcher.fetchPrice(symbol);
                if (mainFrame != null) {
                    mainFrame.updateInitialPriceDisplay(stockIndex, price, symbol);
                }
                System.out.println(symbol + " başlangıç fiyatı: " + price);
            } catch (IOException e) {
                System.err.println(symbol + " için başlangıç fiyatı alınırken hata: " + e.getMessage());
                if (mainFrame != null) {
                    mainFrame.updateInitialPriceDisplay(stockIndex, -1, symbol); // Hata durumunda N/A
                }
            }
        });
    }
    
    // ExecutorService'leri düzgün kapatmak için onApplicationExit'i güncelle
    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        if (initialPriceExecutorService != null && !initialPriceExecutorService.isShutdown()) {
            initialPriceExecutorService.shutdownNow();
        }
    }
} 