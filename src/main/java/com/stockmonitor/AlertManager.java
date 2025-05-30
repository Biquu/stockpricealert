package com.stockmonitor;

import com.stockmonitor.listeners.AlertListener;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

// Özel ses çalmak için gerekli importlar
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.IOException;
import java.net.URL;

public class AlertManager implements AlertListener {

    private JTextArea alertArea;
    private static final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final String ALARM_SOUND_FILE = "/alarm.wav"; // Kaynaklar klasöründeki ses dosyasının yolu

    public AlertManager(JTextArea alertArea) {
        this.alertArea = alertArea;
    }

    @Override
    public void onAlertTriggered(String symbol, double currentPrice, String thresholdCondition, String message) {
        String fullMessage = String.format("⚠️ [%s] %s", 
                                           LocalDateTime.now().format(dtf), 
                                           message);
        // GUI güncellemeleri her zaman Event Dispatch Thread (EDT) üzerinde yapılmalıdır.
        SwingUtilities.invokeLater(() -> {
            if (alertArea.getText().length() > 0) {
                alertArea.append("\n");
            }
            alertArea.append(fullMessage);
            playSound(ALARM_SOUND_FILE); // beep() yerine özel ses çal
        });
        System.out.println("ALERT MANAGER: " + fullMessage); // Konsola da loglayalım
    }
    
    private void playSound(String soundFilePath) {
        try {
            URL soundURL = AlertManager.class.getResource(soundFilePath);
            if (soundURL == null) {
                System.err.println("Uyarı: Ses dosyası bulunamadı: " + soundFilePath);
                // Fallback to default beep if custom sound not found
                java.awt.Toolkit.getDefaultToolkit().beep();
                return;
            }
            AudioInputStream audioIn = AudioSystem.getAudioInputStream(soundURL);
            Clip clip = AudioSystem.getClip();
            clip.open(audioIn);
            clip.start();
            // İsteğe bağlı: clip.addLineListener(event -> { ... }); // Sesin bittiğini vs. dinlemek için
        } catch (UnsupportedAudioFileException | IOException | LineUnavailableException e) {
            System.err.println("Ses dosyası çalınırken hata: " + e.getMessage());
            // Hata durumunda varsayılan bip sesini çal
            java.awt.Toolkit.getDefaultToolkit().beep();
        }
    }

    // Sistem mesajlarını loglamak için ek bir metot
    public void logSystemMessage(String message) {
        String fullMessage = String.format("ℹ️ [%s] %s", 
                                           LocalDateTime.now().format(dtf), 
                                           message);
        SwingUtilities.invokeLater(() -> {
            if (alertArea.getText().length() > 0) {
                alertArea.append("\n");
            }
            alertArea.append(fullMessage);
        });
        System.out.println("SYSTEM LOG: " + fullMessage); 
    }
} 