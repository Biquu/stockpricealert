package com.stockmonitor;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class StockMonitorApp {

    public static void main(String[] args) {
        try {
            // Modern bir görünüm ve his için Nimbus L&F ayarla
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (Exception e) {
            // Nimbus kullanılamıyorsa, varsayılan L&F ile devam et
            System.err.println("Nimbus Look and Feel ayarlanamadı: " + e.getMessage());
        }

        // Ana denetleyiciyi (controller) oluştur
        final MainController controller = new MainController();

        // Uygulama kapatılırken kaynakları serbest bırakmak için ShutdownHook ekle
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutdown Hook tetiklendi.");
            if (controller != null) {
                controller.onApplicationExit();
            }
        }));

        // Swing işlemlerini Event Dispatch Thread (EDT) üzerinde başlat
        SwingUtilities.invokeLater(() -> {
            controller.initializeApplication(); // Bu metot MainFrame'i oluşturur ve controller'a bağlar
        });
    }
} 