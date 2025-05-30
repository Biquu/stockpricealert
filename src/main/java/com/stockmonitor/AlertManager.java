package com.stockmonitor;

// import com.stockmonitor.listeners.AlertListener; // Artık doğrudan AlertListener implement etmeyecek
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

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
    private ExecutorService executorService;
    private Future<?> consumerTaskFuture;
    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final String ALARM_SOUND_FILE = "/alarm.wav";

    public AlertManager(JTextArea alertTextArea) {
        this.alertTextArea = alertTextArea;
        this.alertQueue = new LinkedBlockingQueue<>();
    }

    // MainController tarafından MainFrame oluşturulduktan sonra çağrılacak
    public void setAlertTextArea(JTextArea alertTextArea) {
        this.alertTextArea = alertTextArea;
    }

    private void ensureExecutorIsReady() {
        if (executorService == null || executorService.isShutdown() || executorService.isTerminated()) {
            executorService = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "AlertConsumerThread");
                t.setDaemon(true); // Ana uygulama kapandığında thread'in de kapanmasını sağlar
                return t;
            });
            System.out.println("AlertManager için yeni ExecutorService oluşturuldu.");
        }
    }

    public void startConsumer() {
        ensureExecutorIsReady();

        if (consumerTaskFuture != null && !consumerTaskFuture.isDone() && !consumerTaskFuture.isCancelled()) {
            System.out.println("AlertManager tüketici görevi zaten çalışıyor veya tamamlanmayı bekliyor.");
            return;
        }

        consumerRunning = true; // Görevi başlatmadan önce true olarak ayarla
        consumerTaskFuture = executorService.submit(() -> {
            System.out.println("AlertManager tüketici görevi başlatıldı.");
            try {
                while (consumerRunning || !alertQueue.isEmpty()) {
                    String alertMessage = null;
                    try {
                        alertMessage = alertQueue.poll(1, TimeUnit.SECONDS);
                        if (alertMessage != null) {
                            displayAlert(alertMessage);
                        }
                        // Eğer consumerRunning false olduysa ve kuyruk boşsa, döngü sonlanacak.
                    } catch (InterruptedException e) {
                        if (consumerRunning) {
                            // Durdurma dışındaki bir nedenle kesinti olduysa logla.
                            System.err.println("AlertManager tüketici thread'i (poll) beklenmedik şekilde kesintiye uğradı.");
                            Thread.currentThread().interrupt(); // Kesinti durumunu koru
                        } else {
                            // Durdurma sırasında beklenen kesinti.
                            System.out.println("AlertManager tüketici thread'i (poll) durdurma sırasında kesintiye uğradı.");
                        }
                        break; // Kesinti durumunda döngüden çık
                    }
                }
            } finally {
                System.out.println("AlertManager tüketici görevi sonlandı.");
            }
        });
    }

    public void stopConsumer() {
        consumerRunning = false; // Görevin döngüsünün sonlanması için işaret

        if (consumerTaskFuture != null) {
            consumerTaskFuture.cancel(true); // Görevi iptal etmeyi dene (çalışıyorsa interrupt gönderir)
        }

        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown(); // Yeni görev kabul etme, mevcutları bitirmeye çalış
            try {
                if (!executorService.awaitTermination(2, TimeUnit.SECONDS)) {
                    System.err.println("AlertManager executor'ı zamanında durmadı, shutdownNow deneniyor.");
                    executorService.shutdownNow(); // Aktif görevleri kesmeyi dene
                    if (!executorService.awaitTermination(1, TimeUnit.SECONDS)) {
                        System.err.println("AlertManager executor'ı shutdownNow sonrası da durmadı.");
                    }
                }
            } catch (InterruptedException e) {
                System.err.println("AlertManager executor'ını durdururken kesinti.");
                executorService.shutdownNow(); // Tekrar kesmeyi dene
                Thread.currentThread().interrupt(); // Ana thread'in kesinti durumunu koru
            }
        }
        // alertQueue.clear(); // Opsiyonel: Kuyrukta kalanları temizle. Mevcut döngü zaten boşaltmaya çalışır.
        System.out.println("AlertManager stopConsumer tamamlandı.");
    }

    // StockWatcherThread tarafından çağrılacak basitleştirilmiş metot
    public void queueAlert(String symbol, String message) {
        if (symbol == null || message == null) return;
        String fullMessage = String.format("[%s] ALARM (%s): %s",
                                         LocalDateTime.now().format(DTF), // DTF kullanımı
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
                                           LocalDateTime.now().format(DTF), // DTF kullanımı
                                           message);
        // displayAlert(fullMessage); // Eski durumda ses çalıyordu.

        // Sistem mesajını ses çalmadan doğrudan UI veya konsola yazdır:
        if (alertTextArea != null) {
            SwingUtilities.invokeLater(() -> {
                alertTextArea.append(fullMessage + "\n");
                alertTextArea.setCaretPosition(alertTextArea.getDocument().getLength());
            });
        } else {
            System.out.println("Sistem Mesajı (AlertTextArea null): " + fullMessage);
        }
    }

    // Alarmları UI'da gösterir
    private void displayAlert(String message) {
        if (alertTextArea != null) {
            SwingUtilities.invokeLater(() -> {
                alertTextArea.append(message + "\n");
                // Otomatik olarak en sona kaydır (opsiyonel)
                alertTextArea.setCaretPosition(alertTextArea.getDocument().getLength());
                playSound(ALARM_SOUND_FILE); // Ses çalma eklendi
            });
        } else {
            System.out.println("Mesaj (AlertTextArea null): " + message); // Fallback to console
            playSound(ALARM_SOUND_FILE); // Konsola yazdırılırken de ses çal (opsiyonel)
        }
    }

    private void playSound(String soundFilePath) {
        if (!consumerRunning) { // Eğer tüketici çalışmıyorsa (izleme durduysa) ses çalma
            return;
        }
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