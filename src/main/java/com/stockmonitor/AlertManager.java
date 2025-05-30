package com.stockmonitor;

// import com.stockmonitor.listeners.AlertListener; // Artık doğrudan AlertListener implement etmeyecek
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

// Özel ses çalmak için gerekli importlar (bunlar kalabilir)
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.IOException;
import java.net.URL;

public class AlertManager { // AlertListener implementasyonu kaldırıldı

    private JTextArea alertTextArea; // UI'da alarmların gösterileceği alan
    private final BlockingQueue<String> alertQueue; // Alarmları işlemek için bir kuyruk
    private volatile boolean consumerRunning = true;
    private Thread alertConsumerThread;
    private final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final String ALARM_SOUND_FILE = "/alarm.wav";

    public AlertManager(JTextArea alertTextArea) {
        this.alertTextArea = alertTextArea;
        this.alertQueue = new LinkedBlockingQueue<>();
    }

    // MainController tarafından MainFrame oluşturulduktan sonra çağrılacak
    public void setAlertTextArea(JTextArea alertTextArea) {
        this.alertTextArea = alertTextArea;
    }

    public void startConsumer() {
        if (alertConsumerThread != null && alertConsumerThread.isAlive()) {
            return; // Zaten çalışıyor
        }
        consumerRunning = true;
        alertConsumerThread = new Thread(() -> {
            while (consumerRunning || !alertQueue.isEmpty()) {
                try {
                    String alertMessage = alertQueue.poll(1, java.util.concurrent.TimeUnit.SECONDS);
                    if (alertMessage != null) {
                        displayAlert(alertMessage);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    // consumerRunning false ise ve kuyruk boşsa zaten çıkacak
                    if (consumerRunning) {
                         System.err.println("AlertManager tüketici thread'i kesintiye uğradı.");
                    }
                    break; // Kesintiye uğrarsa döngüden çık
                }
            }
            System.out.println("AlertManager tüketici thread'i durdu.");
        });
        alertConsumerThread.setName("AlertConsumerThread");
        alertConsumerThread.setDaemon(true); // Ana uygulama kapandığında bu thread'in de kapanmasını sağla
        alertConsumerThread.start();
    }

    public void stopConsumer() {
        consumerRunning = false;
        if (alertConsumerThread != null) {
            alertConsumerThread.interrupt(); // Kuyrukta bekliyorsa kesintiye uğrat
            try {
                alertConsumerThread.join(2000); // Thread'in bitmesini 2 saniye bekle
                if (alertConsumerThread.isAlive()) {
                    System.err.println("AlertManager tüketici thread'i zamanında durmadı.");
                }
            } catch (InterruptedException e) {
                System.err.println("AlertManager tüketici thread'ini durdururken kesinti.");
                Thread.currentThread().interrupt();
            }
        }
        alertQueue.clear(); // Kuyrukta kalanları temizle (opsiyonel)
    }

    // StockWatcherThread tarafından çağrılacak basitleştirilmiş metot
    public void queueAlert(String symbol, String message) {
        if (symbol == null || message == null) return;
        String fullMessage = String.format("[%s] ALARM (%s): %s", 
                                         LocalDateTime.now().format(dtf), 
                                         symbol, 
                                         message);
        try {
            alertQueue.put(fullMessage);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Alarm kuyruğuna ekleme kesintiye uğradı: " + fullMessage);
        }
    }
    
    // Sistem mesajlarını doğrudan UI'a yazdırmak için (örn: izleme başladı/durdu)
    public void logSystemMessage(String message) {
        if (message == null) return;
        String fullMessage = String.format("[%s] SİSTEM: %s", 
                                           LocalDateTime.now().format(dtf), 
                                           message);
        displayAlert(fullMessage);
    }

    // Alarmları UI'da gösterir
    private void displayAlert(String message) {
        if (alertTextArea != null) {
            SwingUtilities.invokeLater(() -> {
                alertTextArea.append(message + "\n");
                // Otomatik olarak en sona kaydır (opsiyonel)
                alertTextArea.setCaretPosition(alertTextArea.getDocument().getLength());
            });
        } else {
            System.out.println("Mesaj (AlertTextArea null): " + message); // Fallback to console
        }
    }

    private void playSound(String soundFilePath) {
        try {
            URL soundURL = AlertManager.class.getResource(soundFilePath);
            if (soundURL == null) {
                System.err.println("Uyarı: Ses dosyası bulunamadı: " + soundFilePath);
                java.awt.Toolkit.getDefaultToolkit().beep();
                return;
            }
            AudioInputStream audioIn = AudioSystem.getAudioInputStream(soundURL);
            Clip clip = AudioSystem.getClip();
            clip.open(audioIn);
            clip.start();
        } catch (UnsupportedAudioFileException | IOException | LineUnavailableException e) {
            System.err.println("Ses dosyası çalınırken hata: " + e.getMessage());
            java.awt.Toolkit.getDefaultToolkit().beep();
        }
    }
} 