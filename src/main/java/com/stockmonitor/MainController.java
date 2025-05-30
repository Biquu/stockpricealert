package com.stockmonitor;

// import com.stockmonitor.listeners.AlertListener; // Kaldırıldı, artık kullanılmıyor
import com.stockmonitor.listeners.GraphDataListener;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.swing.SwingUtilities;
// import javax.swing.JLabel; // Kaldırıldı.
import java.util.Date;
// import com.stockmonitor.data.CandleStickData; // Kaldırıldı
import javax.swing.*;

public class MainController {

    private MainFrame mainFrame; // Controller'ın MainFrame'e erişimi olacak
    private ConfigurationManager configManager;
    private PriceFetcher priceFetcher;
    private AlertManager alertManager;
    private GraphUpdater graphUpdater;
    // Başlangıç fiyatlarını çekmek için ayrı bir ExecutorService kullanılabilir veya mevcut olan paylaşılabilir.
    private ExecutorService initialPriceExecutorService;

    private final Map<String, StockWatcherThread> activeWatchers = new ConcurrentHashMap<>();
    private ExecutorService executorService; // Thread'leri yönetmek için
    // Finnhub ücretsiz API limiti dakikada ~60 istektir.
    // 2 hisse/kripto izlerken her birini 3 saniyede bir çekmek dakikada 2*20 = 40 istek yapar.
    private static final int MAX_DISPLAY_CHARTS = 4; // 2'den 4'e çıkarıldı

    public MainController() {
        // priceFetcher initialize edilecek.
    }

    public void initializeApplication() {
        this.configManager = new ConfigurationManager();
        this.priceFetcher = new PriceFetcher();
        this.graphUpdater = new GraphUpdater();
        this.alertManager = new AlertManager(null); // MainFrame set edildikten sonra set edilecek
        this.initialPriceExecutorService = Executors.newFixedThreadPool(MAX_DISPLAY_CHARTS > 0 ? MAX_DISPLAY_CHARTS : 1);
        
        SwingUtilities.invokeLater(() -> {
            try {
                for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                    if ("Nimbus".equals(info.getName())) {
                        UIManager.setLookAndFeel(info.getClassName());
                        break;
                    }
                }
            } catch (Exception e) {
                System.err.println("Nimbus L&F ayarlanamadı: " + e.getMessage());
            }
            this.mainFrame = new MainFrame(this);
            this.alertManager.setAlertTextArea(mainFrame.getAlertArea()); // AlertManager'a TextArea'yı tanıt
            mainFrame.setVisible(true);
            alertManager.startConsumer(); // AlertManager'ın tüketici thread'ini başlat
        });
        int coreCount = Runtime.getRuntime().availableProcessors();
        // executorService için thread sayısını, mevcut çekirdek sayısına göre ayarla (minimum 2 olacak şekilde)
        // Çekirdek sayısının yarısı genellikle I/O ağırlıklı işler için iyi bir başlangıçtır.
        this.executorService = Executors.newFixedThreadPool(Math.max(2, coreCount / 2));

    }

    public void startMonitoring() {
        System.out.println("[MainController] İzleme başlatılıyor...");
        if (mainFrame == null) {
            System.err.println("[MainController] MainFrame null, izleme başlatılamıyor.");
            return;
        }
        List<StockConfig> configs = mainFrame.getSelectedStockConfigurations();
        if (configs.isEmpty()) {
            System.out.println("[MainController] İzlenecek sembol seçilmedi.");
            mainFrame.getAlertArea().append("Uyarı: İzlemek için en az bir sembol seçilmelidir.\n");
            return;
        }
        configManager.savePreferences(configs);

        // İzleme başlamadan önce, önceki durumdan kalan panelleri ve watcherları temizle.
        // Ancak grafikleri temizleme (clearChart) çağrısı yapılmayacak.
        activeWatchers.values().forEach(StockWatcherThread::stopWatching); 
        activeWatchers.clear();
        graphUpdater.unregisterAllChartPanels(); // Sadece sembol-panel eşleşmelerini kaldır

        int chartIndex = 0;
        for (StockConfig config : configs) {
            final String currentSymbol = config.getSymbol();
            System.out.println("[MainController] Sembol " + currentSymbol + " için izleme ve grafik ayarlanıyor.");
            if (currentSymbol != null && !currentSymbol.isEmpty()) {
                if (chartIndex < MAX_DISPLAY_CHARTS) {
                    XChartPanel panel = mainFrame.getXChartPanel(chartIndex);
                    if (panel != null) {
                        // Yeni izleme için paneli TEMİZLEMEK GEREKEBİLİR, eğer farklı bir sembol izlenecekse.
                        // Veya aynı sembolse bile, eski verilerin üzerine yazılmaması için.
                        panel.clearChart(); // Yeni izleme başlamadan önce grafiği temizle
                        graphUpdater.registerChartPanel(currentSymbol, panel);
                        graphUpdater.updateChartTitle(currentSymbol, currentSymbol + " Fiyatları (Mum)");
                        System.out.println("[MainController] Sembol " + currentSymbol + " için XChartPanel " + chartIndex + " kaydedildi, temizlendi ve başlığı ayarlandı.");
                    } else {
                        System.err.println("[MainController] Hata: " + chartIndex + " indexli XChartPanel bulunamadı ("+ currentSymbol + ").");
                    }
                    chartIndex++;
                }

                System.out.println("[MainController] Sembol " + currentSymbol + " için StockWatcherThread başlatılıyor...");
                StockWatcherThread watcher = new StockWatcherThread(
                    config, 
                    priceFetcher,
                    alertManager,  
                    graphUpdater 
                );
                activeWatchers.put(currentSymbol, watcher);
                executorService.submit(watcher); 
                System.out.println("[MainController] Sembol " + currentSymbol + " için StockWatcherThread başlatıldı.");
            }
        }
        mainFrame.updateButtonStates(true); 
        alertManager.logSystemMessage("İzleme tüm seçili semboller için başlatıldı.");
    }

    public void stopMonitoring() {
        System.out.println("[MainController] İzleme durduruluyor...");
        if (mainFrame == null) return;
        activeWatchers.values().forEach(StockWatcherThread::stopWatching);
        activeWatchers.clear(); // Aktif watcher listesini temizle
        mainFrame.updateButtonStates(false);
        alertManager.logSystemMessage("İzleme durduruldu.");
        
        // Grafikleri temizleme ve panel başlıklarını sıfırlama işlemleri kaldırıldı.
        // graphUpdater.unregisterAllChartPanels(); // Bu da kaldırıldı, böylece panel-sembol eşleşmesi kalır.
        // Ancak bu, bir sonraki startMonitoring'de ilgisiz bir panelin güncellenmesine yol açabilir.
        // Daha iyi bir yaklaşım: unregister et, ama clearChart yapma.
        // Şimdilik, izleme durduğunda grafiklerin olduğu gibi kalması için en basit yol bu.

        System.out.println("[MainController] Aktif izleyiciler durduruldu. Grafik verileri korunuyor.");
    }

    // Kayıtlı konfigürasyonları ConfigurationManager'dan al
    public List<StockConfig> getSavedConfigurations() {
        return configManager.loadPreferences(); 
    }

    // Belirli bir hisse için başlangıç fiyatını çek ve UI'da göster
    public void fetchAndDisplayInitialPrice(int stockIndex, String symbol) {
        if (symbol == null || symbol.trim().isEmpty() || stockIndex < 0 || stockIndex >= MAX_DISPLAY_CHARTS) {
            // Geçersiz sembol veya index durumunda UI'ı temizle
            SwingUtilities.invokeLater(() -> {
                 if (mainFrame != null) mainFrame.clearInitialPriceDisplay(stockIndex);
            });
            return;
        }
        initialPriceExecutorService.submit(() -> {
            try {
                double price = priceFetcher.fetchPrice(symbol.toUpperCase()); // Fiyatı çek
                SwingUtilities.invokeLater(() -> {
                    if (mainFrame != null) {
                        mainFrame.updateInitialPriceDisplay(stockIndex, price, symbol);
                    }
                });
            } catch (Exception e) {
                System.err.println("[MainController] Başlangıç fiyatı alınırken hata oluştu (" + symbol + "): " + e.getMessage());
                SwingUtilities.invokeLater(() -> {
                    if (mainFrame != null) {
                         mainFrame.updateInitialPriceDisplay(stockIndex, -1, symbol); // Hata durumunu UI'da göster
                    }
                });
            }
        });
    }

    // Uygulama kapanırken çağrılacak temizlik metodu
    public void onApplicationExit() {
        System.out.println("[MainController] Uygulama kapatılıyor, kaynaklar serbest bırakılıyor...");
        // Ayarları kaydet (opsiyonel, eğer UI'dan anlık alınıp start'ta kaydediliyorsa burada tekrar gerekmeyebilir)
        // Ama en son UI durumunu kaydetmek iyi olabilir.
        if (mainFrame != null) {
            List<StockConfig> lastConfigs = mainFrame.getSelectedStockConfigurations();
            if (!lastConfigs.isEmpty() || !configManager.getSelectedStocks().isEmpty()) { // Sadece doluysa veya önceden doluysa kaydet
                 configManager.savePreferences(lastConfigs);
                 System.out.println("[MainController] Son konfigürasyonlar kaydedildi.");
            }
        }

        stopMonitoring(); // stopMonitoring artık grafikleri temizlemiyor.

        // Uygulama kapanırken grafikleri ve panelleri son bir kez temizleyelim.
        if (graphUpdater != null) {
            graphUpdater.clearAllGraphs();
            graphUpdater.unregisterAllChartPanels();
            System.out.println("[MainController] Uygulama çıkışında tüm grafikler ve panel kayıtları temizlendi.");
        }

        if (executorService != null && !executorService.isShutdown()) {
            System.out.println("[MainController] Ana watcher thread havuzu kapatılıyor...");
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                    System.out.println("[MainController] Ana watcher thread havuzu zorla kapatıldı.");
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        if (initialPriceExecutorService != null && !initialPriceExecutorService.isShutdown()) {
            System.out.println("[MainController] Başlangıç fiyatı thread havuzu kapatılıyor...");
            initialPriceExecutorService.shutdown();
            try {
                if (!initialPriceExecutorService.awaitTermination(2, TimeUnit.SECONDS)) {
                    initialPriceExecutorService.shutdownNow();
                     System.out.println("[MainController] Başlangıç fiyatı thread havuzu zorla kapatıldı.");
                }
            } catch (InterruptedException e) {
                initialPriceExecutorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        if (alertManager != null) { // AlertManager tüketici thread'ini durdur
            alertManager.stopConsumer();
        }

        System.out.println("[MainController] Uygulama kapatıldı.");
    }
} 