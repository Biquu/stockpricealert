package com.stockmonitor;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class StockMonitorApp {

    public static void main(String[] args) {
        try {
            // Daha modern bir görünüm için Nimbus Look and Feel ayarla
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (Exception e) {
            // Nimbus kullanılamazsa, varsayılan L&F kullanılır.
            System.err.println("Nimbus Look and Feel ayarlanamadı: " + e.getMessage());
        }

        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                MainFrame mainFrame = new MainFrame();
                mainFrame.initializeController();
                mainFrame.setVisible(true);
            }
        });
    }
} 