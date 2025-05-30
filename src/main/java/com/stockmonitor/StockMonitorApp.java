package com.stockmonitor;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class StockMonitorApp {

    public static void main(String[] args) {
        System.out.println("[StockMonitorApp] [Thread: " + Thread.currentThread().getName() + "] Application main method started.");
        try {
            // Set Nimbus L&F for a modern look and feel
            System.out.println("[StockMonitorApp] [Thread: " + Thread.currentThread().getName() + "] Attempting to set Nimbus Look and Feel.");
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    System.out.println("[StockMonitorApp] [Thread: " + Thread.currentThread().getName() + "] Nimbus Look and Feel set successfully.");
                    break;
                }
            }
        } catch (Exception e) {
            // If Nimbus is not available, continue with the default L&F
            System.err.println("[StockMonitorApp] [Thread: " + Thread.currentThread().getName() + "] Could not set Nimbus Look and Feel: " + e.getMessage());
        }

        // Create the main controller
        System.out.println("[StockMonitorApp] [Thread: " + Thread.currentThread().getName() + "] Creating MainController instance.");
        final MainController controller = new MainController();

        // Add ShutdownHook to release resources when the application is closed
        System.out.println("[StockMonitorApp] [Thread: " + Thread.currentThread().getName() + "] Registering Shutdown Hook.");
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            // This will run in a separate thread, typically named "Shutdown Thread" or similar
            System.out.println("[StockMonitorApp] [Thread: " + Thread.currentThread().getName() + "] Shutdown Hook triggered. Executing onApplicationExit on controller.");
            if (controller != null) {
                controller.onApplicationExit();
            }
            System.out.println("[StockMonitorApp] [Thread: " + Thread.currentThread().getName() + "] Shutdown Hook finished.");
        }, "AppShutdownThread")); // Giving a name to the shutdown hook thread

        // Start Swing operations on the Event Dispatch Thread (EDT)
        System.out.println("[StockMonitorApp] [Thread: " + Thread.currentThread().getName() + "] Scheduling UI initialization (controller.initializeApplication) on EDT using SwingUtilities.invokeLater.");
        SwingUtilities.invokeLater(() -> {
            System.out.println("[StockMonitorApp] [Thread: " + Thread.currentThread().getName() + "] Now running on EDT. Calling controller.initializeApplication().");
            controller.initializeApplication(); // This method creates the MainFrame and links it to the controller
            System.out.println("[StockMonitorApp] [Thread: " + Thread.currentThread().getName() + "] controller.initializeApplication() finished on EDT.");
        });
        System.out.println("[StockMonitorApp] [Thread: " + Thread.currentThread().getName() + "] Application main method finished. EDT tasks may still be pending/running.");
    }
} 