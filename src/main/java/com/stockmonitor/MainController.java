package com.stockmonitor;

// import com.stockmonitor.listeners.AlertListener; // Removed, no longer used
import com.stockmonitor.listeners.GraphDataListener;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.swing.SwingUtilities;
// import javax.swing.JLabel; // Removed.
import java.util.Date;
// import com.stockmonitor.data.CandleStickData; // Removed
import javax.swing.*;

public class MainController {

    private MainFrame mainFrame; // Controller will have access to MainFrame
    private ConfigurationManager configManager;
    private PriceFetcher priceFetcher;
    private AlertManager alertManager;
    private GraphUpdater graphUpdater;
    // A separate ExecutorService can be used to fetch initial prices, or the existing one can be shared.
    private ExecutorService initialPriceExecutorService; //İlk fiyatı almak için kullanılır.

    private final Map<String, StockWatcherThread> activeWatchers = new ConcurrentHashMap<>(); //thread safe map birden fazla thread ile erişilebilir.
    private ExecutorService executorService; // ilk fiyatı aldıktan sonraki ana izleme için kullanılır.
    // Finnhub free API limit is ~60 requests per minute.
    // Monitoring 2 stocks/cryptos and fetching each every 3 seconds makes 2*20 = 40 requests per minute.
    private static final int MAX_DISPLAY_CHARTS = 4; // Increased from 2 to 4 limitation of the chart.
    private static final int MAX_API_REQUESTS_PER_MINUTE = 58; // Finnhub limit with a safety margin

    // AtomicInteger is used to create thread-safe counters for naming threads.
    private static final java.util.concurrent.atomic.AtomicInteger stockWatcherThreadCounter = new java.util.concurrent.atomic.AtomicInteger(0);
    private static final java.util.concurrent.atomic.AtomicInteger initialPriceFetchThreadCounter = new java.util.concurrent.atomic.AtomicInteger(0);

    public MainController() {
        // priceFetcher will be initialized later in initializeApplication
        System.out.println("[MainController] [Thread: " + Thread.currentThread().getName() + "] Constructor called (instance created).");
    }

    public void initializeApplication() {
        System.out.println("[MainController] [Thread: " + Thread.currentThread().getName() + "] initializeApplication called.");
        this.configManager = new ConfigurationManager();
        System.out.println("[MainController] [Thread: " + Thread.currentThread().getName() + "] ConfigurationManager instance created.");
        this.priceFetcher = new PriceFetcher();
        System.out.println("[MainController] [Thread: " + Thread.currentThread().getName() + "] PriceFetcher instance created.");
        this.graphUpdater = new GraphUpdater();
        System.out.println("[MainController] [Thread: " + Thread.currentThread().getName() + "] GraphUpdater instance created.");
        this.alertManager = new AlertManager(null); // Will be set after MainFrame is set
        System.out.println("[MainController] [Thread: " + Thread.currentThread().getName() + "] AlertManager instance created (TextArea will be set later).");
        

        // Initial price fetch thread pool is created. 
        // It is used to fetch initial prices for the symbols.
        // It is a fixed thread pool with a size of MAX_DISPLAY_CHARTS.
        // The threads are named InitialPriceFetchThread-X.
        int initialPoolSize = MAX_DISPLAY_CHARTS > 0 ? MAX_DISPLAY_CHARTS : 1;
        this.initialPriceExecutorService = Executors.newFixedThreadPool(initialPoolSize, r -> new Thread(r, "InitialPriceFetchThread-" + initialPriceFetchThreadCounter.getAndIncrement()));
        System.out.println("[MainController] [Thread: " + Thread.currentThread().getName() + "] initialPriceExecutorService (for fetching initial prices) created with a fixed pool of " + initialPoolSize + " threads (named InitialPriceFetchThread-X).");
        
        System.out.println("[MainController] [Thread: " + Thread.currentThread().getName() + "] Scheduling MainFrame creation and AlertManager setup on EDT using SwingUtilities.invokeLater.");
        // SwingUtilities.invokeLater is used to run the code on the EDT (Event Dispatch Thread).
        // This is a thread-safe way to initialize the UI components.
        SwingUtilities.invokeLater(() -> {
            System.out.println("[MainController] [Thread: " + Thread.currentThread().getName() + "] Now running on EDT. Initializing UI components (MainFrame, AlertManager TextArea).");
            try {
                for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                    if ("Nimbus".equals(info.getName())) {
                        UIManager.setLookAndFeel(info.getClassName());
                        break;
                    }
                }
            } catch (Exception e) {
                System.err.println("Could not set Nimbus L&F: " + e.getMessage());
            }
            this.mainFrame = new MainFrame(this);
            System.out.println("[MainController] [Thread: " + Thread.currentThread().getName() + "] MainFrame instance created on EDT.");
            this.alertManager.setAlertTextArea(mainFrame.getAlertArea()); // Introduce TextArea to AlertManager
            System.out.println("[MainController] [Thread: " + Thread.currentThread().getName() + "] AlertTextArea set in AlertManager on EDT.");
            mainFrame.setVisible(true);
            System.out.println("[MainController] [Thread: " + Thread.currentThread().getName() + "] MainFrame set visible on EDT.");
            alertManager.startConsumer(); // Start AlertManager's consumer thread
            System.out.println("[MainController] [Thread: " + Thread.currentThread().getName() + "] alertManager.startConsumer() called on EDT (consumer runs on its own thread).");
        });
        int coreCount = Runtime.getRuntime().availableProcessors();
        int mainPoolSize = Math.max(2, coreCount / 2);
        this.executorService = Executors.newFixedThreadPool(mainPoolSize, r -> new Thread(r, "StockWatcherTaskThread-" + stockWatcherThreadCounter.getAndIncrement()));
        System.out.println("[MainController] [Thread: " + Thread.currentThread().getName() + "] Main executorService (for StockWatcherThreads) created with a fixed pool of " + mainPoolSize + " threads (named StockWatcherTaskThread-X). Core count: " + coreCount + ".");
        System.out.println("[MainController] [Thread: " + Thread.currentThread().getName() + "] initializeApplication finished.");
    }

    public void startMonitoring() {
        System.out.println("[MainController] [Thread: " + Thread.currentThread().getName() + "] startMonitoring called.");
        if (mainFrame == null) {
            System.err.println("[MainController] [Thread: " + Thread.currentThread().getName() + "] MainFrame is null, cannot start monitoring.");
            return;
        }
        List<StockConfig> configs = mainFrame.getSelectedStockConfigurations();
        if (configs.isEmpty()) {
            System.out.println("[MainController] [Thread: " + Thread.currentThread().getName() + "] No symbols selected for monitoring.");
            mainFrame.getAlertArea().append("Warning: At least one symbol must be selected for monitoring.\n");
            return;
        }
        configManager.savePreferences(configs);

        System.out.println("[MainController] [Thread: " + Thread.currentThread().getName() + "] Stopping and clearing previous active watchers (count: " + activeWatchers.size() + ").");
        activeWatchers.values().forEach(watcher -> {
            System.out.println("[MainController] [Thread: " + Thread.currentThread().getName() + "] Calling stopWatching() for previous watcher: " + watcher.getSymbol());
            watcher.stopWatching();
        }); 
        activeWatchers.clear();
        System.out.println("[MainController] [Thread: " + Thread.currentThread().getName() + "] Unregistering all chart panels from GraphUpdater.");
        graphUpdater.unregisterAllChartPanels(); 
        System.out.println("[MainController] [Thread: " + Thread.currentThread().getName() + "] Previous watchers stopped and panels unregistered.");

        int numberOfActiveSymbols = configs.size();
        long fetchIntervalSeconds;

        if (numberOfActiveSymbols == 1) {
            fetchIntervalSeconds = 2; // 2 seconds for a single symbol
        } else if (numberOfActiveSymbols == 2) {
            fetchIntervalSeconds = 3; // 3 seconds for 2 symbols (60 / (2*3) = 10 req/sym/min -> total 20*2=40)
        } else if (numberOfActiveSymbols == 3) {
            fetchIntervalSeconds = 4; // 4 seconds for 3 symbols (60 / (3*4) = 5 req/sym/min -> total 15*3=45)
        } else if (numberOfActiveSymbols >= 4) {
            fetchIntervalSeconds = 5; // 5 seconds for 4 or more symbols (60 / (4*5) = 3 req/sym/min -> total 12*4=48)
        } else { // This case should be caught by configs.isEmpty() above, but as a fallback
            fetchIntervalSeconds = 10; // Default
        }
        
        // We can also check the minimum interval to avoid exceeding the API limit.
        // E.g., (60 seconds / (MAX_API_REQUESTS_PER_MINUTE / numberOfActiveSymbols) )
        // If numberOfActiveSymbols > 0: Max requests per minute per symbol = MAX_API_REQUESTS_PER_MINUTE / numberOfActiveSymbols
        // In this case, min interval in seconds = 60 / (MAX_API_REQUESTS_PER_MINUTE / numberOfActiveSymbols)
        // A check like fetchIntervalSeconds = Math.max(fetchIntervalSeconds, calculatedMinInterval); could be added.
        // For now, the manual settings above should suffice.

        System.out.println("[MainController] [Thread: " + Thread.currentThread().getName() + "] Number of active symbols: " + numberOfActiveSymbols + ", Data fetch interval set to: " + fetchIntervalSeconds + " seconds.");

        int chartIndex = 0;
        for (StockConfig config : configs) {
            final String currentSymbol = config.getSymbol();
            System.out.println("[MainController] [Thread: " + Thread.currentThread().getName() + "] Setting up monitoring and graph for symbol " + currentSymbol + ".");
            if (currentSymbol != null && !currentSymbol.isEmpty()) {
                if (chartIndex < MAX_DISPLAY_CHARTS) {
                    XChartPanel panel = mainFrame.getXChartPanel(chartIndex);
                    if (panel != null) {
                        System.out.println("[MainController] [Thread: " + Thread.currentThread().getName() + "] Preparing XChartPanel at index " + chartIndex + " for symbol " + currentSymbol + ". Clearing chart, registering with GraphUpdater, and setting title.");
                        panel.clearChart(); 
                        graphUpdater.registerChartPanel(currentSymbol, panel);
                        graphUpdater.updateChartTitle(currentSymbol, currentSymbol + " Prices (Candle)");
                        System.out.println("[MainController] [Thread: " + Thread.currentThread().getName() + "] XChartPanel " + chartIndex + " registered, cleared, and title set for symbol " + currentSymbol + ".");
                    } else {
                        System.err.println("[MainController] [Thread: " + Thread.currentThread().getName() + "] Error: XChartPanel at index " + chartIndex + " not found for symbol ("+ currentSymbol + ").");
                    }
                    chartIndex++;
                }

                System.out.println("[MainController] [Thread: " + Thread.currentThread().getName() + "] Creating StockWatcherThread for symbol " + currentSymbol + " with interval " + fetchIntervalSeconds + "s.");
                StockWatcherThread watcher = new StockWatcherThread(
                    config, 
                    priceFetcher,
                    alertManager,  
                    graphUpdater,
                    fetchIntervalSeconds 
                );
                activeWatchers.put(currentSymbol, watcher);
                System.out.println("[MainController] [Thread: " + Thread.currentThread().getName() + "] Submitting StockWatcherThread for symbol " + currentSymbol + " to main executorService.");
                executorService.submit(watcher); 
                System.out.println("[MainController] [Thread: " + Thread.currentThread().getName() + "] StockWatcherThread for symbol " + currentSymbol + " submitted successfully to executorService. It will run on a StockWatcherTaskThread-X.");
            }
        }
        System.out.println("[MainController] [Thread: " + Thread.currentThread().getName() + "] Finished setting up watchers. Updating button states and logging system message via AlertManager.");
        mainFrame.updateButtonStates(true); 
        alertManager.logSystemMessage("Monitoring started for all selected symbols (with " + fetchIntervalSeconds + "s interval).");
    }

    public void stopMonitoring() {
        System.out.println("[MainController] [Thread: " + Thread.currentThread().getName() + "] stopMonitoring called. Number of active watchers: " + activeWatchers.size() + ".");
        if (mainFrame == null) {
            System.err.println("[MainController] [Thread: " + Thread.currentThread().getName() + "] MainFrame is null in stopMonitoring. Cannot proceed.");
            return;
        }
        activeWatchers.values().forEach(watcher -> {
            System.out.println("[MainController] [Thread: " + Thread.currentThread().getName() + "] Calling stopWatching() on StockWatcherThread for symbol: " + watcher.getSymbol());
            watcher.stopWatching();
        });
        activeWatchers.clear();
        System.out.println("[MainController] [Thread: " + Thread.currentThread().getName() + "] All active watchers instructed to stop and cleared from map. Updating button states and logging system message.");
        mainFrame.updateButtonStates(false);
        
        // Clearing graphs and resetting panel titles has been removed.
        // graphUpdater.unregisterAllChartPanels(); // Also removed, so panel-symbol mapping remains.
        // However, this might lead to an irrelevant panel being updated in the next startMonitoring.
        // A better approach: unregister, but don't clearChart.
        // For now, this is the simplest way to keep graphs as they are when monitoring stops.

        System.out.println("[MainController] [Thread: " + Thread.currentThread().getName() + "] Active watchers stopped. Graph data preserved.");
    }

    // Get saved configurations from ConfigurationManager
    public List<StockConfig> getSavedConfigurations() {
        return configManager.loadPreferences(); 
    }

    // Fetch initial price for a specific stock and display it in the UI
    public void fetchAndDisplayInitialPrice(int stockIndex, String symbol) {
        System.out.println("[MainController] [Thread: " + Thread.currentThread().getName() + "] fetchAndDisplayInitialPrice called for symbol: " + symbol + ", index: " + stockIndex + ".");
        if (symbol == null || symbol.trim().isEmpty() || stockIndex < 0 || stockIndex >= MAX_DISPLAY_CHARTS) {
            System.out.println("[MainController] [Thread: " + Thread.currentThread().getName() + "] Invalid symbol or index for fetchAndDisplayInitialPrice. Symbol: " + symbol + ", Index: " + stockIndex + ". Clearing UI display on EDT.");
            SwingUtilities.invokeLater(() -> {
                 if (mainFrame != null) mainFrame.clearInitialPriceDisplay(stockIndex);
            });
            return;
        }
        System.out.println("[MainController] [Thread: " + Thread.currentThread().getName() + "] Submitting task to initialPriceExecutorService to fetch price for " + symbol + ". Will run on an InitialPriceFetchThread-X.");
        initialPriceExecutorService.submit(() -> {
            System.out.println("[MainController] [Thread: " + Thread.currentThread().getName() + "] initialPriceExecutorService: Now running task to fetch initial price for " + symbol + ".");
            try {
                double price = priceFetcher.fetchPrice(symbol.toUpperCase());
                System.out.println("[MainController] [Thread: " + Thread.currentThread().getName() + "] initialPriceExecutorService: Price fetched for " + symbol + ": " + price + ". Scheduling UI update on EDT.");
                SwingUtilities.invokeLater(() -> {
                    System.out.println("[MainController] [Thread: " + Thread.currentThread().getName() + "] Now on EDT. Updating initial price display for " + symbol + " with price " + price + ".");
                    if (mainFrame != null) {
                        mainFrame.updateInitialPriceDisplay(stockIndex, price, symbol);
                    }
                });
            } catch (Exception e) {
                System.err.println("[MainController] [Thread: " + Thread.currentThread().getName() + "] Error fetching initial price for (" + symbol + ") in initialPriceExecutorService: " + e.getMessage());
                System.out.println("[MainController] [Thread: " + Thread.currentThread().getName() + "] Scheduling error display update on EDT for " + symbol + ".");
                SwingUtilities.invokeLater(() -> {
                    System.out.println("[MainController] [Thread: " + Thread.currentThread().getName() + "] Now on EDT. Updating initial price display for " + symbol + " to show error.");
                    if (mainFrame != null) {
                         mainFrame.updateInitialPriceDisplay(stockIndex, -1, symbol); 
                    }
                });
            }
            System.out.println("[MainController] [Thread: " + Thread.currentThread().getName() + "] initialPriceExecutorService: Task for " + symbol + " finished.");
        });
    }

    // Cleanup method to be called when the application is closing
    public void onApplicationExit() {
        System.out.println("[MainController] [Thread: " + Thread.currentThread().getName() + "] onApplicationExit called. Releasing resources...");
        if (mainFrame != null) {
            List<StockConfig> lastConfigs = mainFrame.getSelectedStockConfigurations();
            if (!lastConfigs.isEmpty() || !configManager.getSelectedStocks().isEmpty()) { // Save only if there are new or previously saved configs
                 configManager.savePreferences(lastConfigs);
                 System.out.println("[MainController] [Thread: " + Thread.currentThread().getName() + "] Last configurations saved.");
            }
        }

        stopMonitoring(); 

        if (graphUpdater != null) {
            graphUpdater.clearAllGraphs();
            graphUpdater.unregisterAllChartPanels();
            System.out.println("[MainController] [Thread: " + Thread.currentThread().getName() + "] All graphs and panel registrations cleared on application exit.");
        }

        System.out.println("[MainController] [Thread: " + Thread.currentThread().getName() + "] Shutting down main watcher thread pool (executorService). Waiting up to 5 seconds for termination.");
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                System.err.println("[MainController] [Thread: " + Thread.currentThread().getName() + "] Main watcher thread pool (executorService) did not terminate in 5s. Forcing shutdownNow...");
                executorService.shutdownNow();
                if (!executorService.awaitTermination(2, TimeUnit.SECONDS)) {
                     System.err.println("[MainController] [Thread: " + Thread.currentThread().getName() + "] Main watcher thread pool (executorService) did not terminate after shutdownNow.");
                } else {
                    System.out.println("[MainController] [Thread: " + Thread.currentThread().getName() + "] Main watcher thread pool (executorService) forcibly shut down successfully after shutdownNow.");
                }
            } else {
                System.out.println("[MainController] [Thread: " + Thread.currentThread().getName() + "] Main watcher thread pool (executorService) shut down successfully within 5s.");
            }
        } catch (InterruptedException e) {
            System.err.println("[MainController] [Thread: " + Thread.currentThread().getName() + "] Interrupted while waiting for main watcher thread pool (executorService) to shut down. Forcing shutdownNow...");
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        if (initialPriceExecutorService != null && !initialPriceExecutorService.isShutdown()) {
            System.out.println("[MainController] [Thread: " + Thread.currentThread().getName() + "] Shutting down initial price thread pool (initialPriceExecutorService). Waiting up to 2 seconds.");
            initialPriceExecutorService.shutdown();
            try {
                if (!initialPriceExecutorService.awaitTermination(2, TimeUnit.SECONDS)) {
                    System.err.println("[MainController] [Thread: " + Thread.currentThread().getName() + "] Initial price thread pool (initialPriceExecutorService) did not terminate in 2s. Forcing shutdownNow...");
                    initialPriceExecutorService.shutdownNow();
                     if (!initialPriceExecutorService.awaitTermination(1, TimeUnit.SECONDS)) {
                        System.err.println("[MainController] [Thread: " + Thread.currentThread().getName() + "] Initial price thread pool (initialPriceExecutorService) did not terminate after shutdownNow.");
                     } else {
                        System.out.println("[MainController] [Thread: " + Thread.currentThread().getName() + "] Initial price thread pool (initialPriceExecutorService) forcibly shut down successfully after shutdownNow.");
                     }
                } else {
                    System.out.println("[MainController] [Thread: " + Thread.currentThread().getName() + "] Initial price thread pool (initialPriceExecutorService) shut down successfully within 2s.");
                }
            } catch (InterruptedException e) {
                System.err.println("[MainController] [Thread: " + Thread.currentThread().getName() + "] Interrupted while waiting for initial price thread pool (initialPriceExecutorService) to shut down. Forcing shutdownNow...");
                initialPriceExecutorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        if (alertManager != null) { 
            System.out.println("[MainController] [Thread: " + Thread.currentThread().getName() + "] Stopping AlertManager consumer thread...");
            alertManager.stopConsumer();
            System.out.println("[MainController] [Thread: " + Thread.currentThread().getName() + "] AlertManager consumer stopped.");
        }

        System.out.println("[MainController] [Thread: " + Thread.currentThread().getName() + "] onApplicationExit completed. All resources should be released.");
    }
} 