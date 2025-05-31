package com.stockmonitor;

// import com.stockmonitor.listeners.AlertListener; // No longer implements AlertListener directly
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

// Imports for playing custom sound (can remain)
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.IOException;
import java.net.URL;

public class AlertManager { // AlertListener implementation removed

    private JTextArea alertTextArea; // Area in UI to display alerts
    private final BlockingQueue<String> alertQueue; // Queue to process alerts
    private volatile boolean consumerRunning = true;
    private ExecutorService executorService;
    private Future<?> consumerTaskFuture;
    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final String ALARM_SOUND_FILE = "/alarm.wav";

    public AlertManager(JTextArea alertTextArea) {
        this.alertTextArea = alertTextArea;
        this.alertQueue = new LinkedBlockingQueue<>();
        System.out.println("[AlertManager] [Thread: " + Thread.currentThread().getName() + "] Instance created. alertTextArea is " + (alertTextArea == null ? "null" : "set") + ".");
    }

    // To be called by MainController after MainFrame is created
    public void setAlertTextArea(JTextArea alertTextArea) {
        this.alertTextArea = alertTextArea;
        System.out.println("[AlertManager] [Thread: " + Thread.currentThread().getName() + "] alertTextArea set.");
    }

    private void ensureExecutorIsReady() {
        if (executorService == null || executorService.isShutdown() || executorService.isTerminated()) {
            executorService = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "AlertConsumerThread");
                t.setDaemon(true); // Allows the thread to shut down when the main application closes
                return t;
            });
            System.out.println("[AlertManager] [Thread: " + Thread.currentThread().getName() + "] New ExecutorService (AlertConsumerThread) created for AlertManager.");
        }
    }

    public void startConsumer() {
        ensureExecutorIsReady();

        if (consumerTaskFuture != null && !consumerTaskFuture.isDone() && !consumerTaskFuture.isCancelled()) {
            System.out.println("[AlertManager] [Thread: " + Thread.currentThread().getName() + "] AlertManager consumer task is already running or pending completion.");
            return;
        }
        System.out.println("[AlertManager] [Thread: " + Thread.currentThread().getName() + "] Attempting to start consumer task.");
        consumerRunning = true; // Set to true before starting the task
        consumerTaskFuture = executorService.submit(() -> {
            System.out.println("[AlertManager] [Thread: " + Thread.currentThread().getName() + "] AlertManager consumer task (AlertConsumerThread) started.");
            try {
                while (consumerRunning || !alertQueue.isEmpty()) {
                    String alertMessage = null;
                    try {
                        alertMessage = alertQueue.poll(1, TimeUnit.SECONDS);
                        if (alertMessage != null) {
                            System.out.println("[AlertManager] [Thread: " + Thread.currentThread().getName() + "] Dequeued alert: " + alertMessage);
                            displayAlert(alertMessage);
                        }
                        // If consumerRunning becomes false and the queue is empty, the loop will terminate.
                    } catch (InterruptedException e) {
                        if (consumerRunning) {
                            // Log if interrupted for a reason other than stopping.
                            System.err.println("[AlertManager] [Thread: " + Thread.currentThread().getName() + "] AlertManager consumer thread (poll) unexpectedly interrupted.");
                            Thread.currentThread().interrupt(); // Preserve interrupt status
                        } else {
                            // Expected interruption during stop.
                            System.out.println("[AlertManager] [Thread: " + Thread.currentThread().getName() + "] AlertManager consumer thread (poll) interrupted during stop.");
                        }
                        break; // Exit loop on interruption
                    }
                }
            } finally {
                System.out.println("[AlertManager] [Thread: " + Thread.currentThread().getName() + "] AlertManager consumer task (AlertConsumerThread) finished.");
            }
        });
    }

    public void stopConsumer() {
        System.out.println("[AlertManager] [Thread: " + Thread.currentThread().getName() + "] stopConsumer called. Setting consumerRunning to false.");
        consumerRunning = false; // Signal for the task's loop to terminate

        if (consumerTaskFuture != null) {
            System.out.println("[AlertManager] [Thread: " + Thread.currentThread().getName() + "] Cancelling consumer task future.");
            consumerTaskFuture.cancel(true); // Attempt to cancel the task (sends interrupt if running)
        }

        if (executorService != null && !executorService.isShutdown()) {
            System.out.println("[AlertManager] [Thread: " + Thread.currentThread().getName() + "] Shutting down AlertManager executorService.");
            executorService.shutdown(); // Don't accept new tasks, try to finish existing ones
            try {
                if (!executorService.awaitTermination(2, TimeUnit.SECONDS)) {
                    System.err.println("[AlertManager] [Thread: " + Thread.currentThread().getName() + "] AlertManager executor did not terminate in time, trying shutdownNow.");
                    executorService.shutdownNow(); // Attempt to interrupt active tasks
                    if (!executorService.awaitTermination(1, TimeUnit.SECONDS)) {
                        System.err.println("[AlertManager] [Thread: " + Thread.currentThread().getName() + "] AlertManager executor did not terminate after shutdownNow.");
                    }
                }
            } catch (InterruptedException e) {
                System.err.println("[AlertManager] [Thread: " + Thread.currentThread().getName() + "] Interrupted while shutting down AlertManager executor.");
                executorService.shutdownNow(); // Try to interrupt again
                Thread.currentThread().interrupt(); // Preserve interrupt status of the current thread
            }
        }
        // alertQueue.clear(); // Optional: Clear remaining items in queue. Current loop already tries to empty it.
        System.out.println("[AlertManager] [Thread: " + Thread.currentThread().getName() + "] stopConsumer completed.");
    }

    // Simplified method to be called by StockWatcherThread
    public void queueAlert(String symbol, String message) {
        if (symbol == null || message == null) return;
        String fullMessage = String.format("[%s] ALERT (%s): %s",
                                         LocalDateTime.now().format(DTF), // Use DTF
                                         symbol,
                                         message);
        System.out.println("[AlertManager] [Thread: " + Thread.currentThread().getName() + "] Queuing alert: " + fullMessage);
        try {
            alertQueue.put(fullMessage);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("[AlertManager] [Thread: " + Thread.currentThread().getName() + "] Adding to alert queue interrupted: " + fullMessage);
        }
    }
    
    // To write system messages directly to UI (e.g., monitoring started/stopped)
    public void logSystemMessage(String message) {
        if (message == null) return;
        String fullMessage = String.format("[%s] SYSTEM: %s",
                                           LocalDateTime.now().format(DTF), // Use DTF
                                           message);
        // displayAlert(fullMessage); // Previously played sound.

        // Print system message directly to UI or console without playing sound:
        if (alertTextArea != null) {
            SwingUtilities.invokeLater(() -> {
                System.out.println("[AlertManager] [Thread: " + Thread.currentThread().getName() + "] Logging system message to UI: " + fullMessage);
                alertTextArea.append(fullMessage + "\n");
                alertTextArea.setCaretPosition(alertTextArea.getDocument().getLength());
            });
        } else {
            System.out.println("System Message (AlertTextArea null): " + fullMessage);
        }
    }

    // Displays alerts in the UI
    private void displayAlert(String message) {
        if (alertTextArea != null) {
            SwingUtilities.invokeLater(() -> {
                System.out.println("[AlertManager] [Thread: " + Thread.currentThread().getName() + "] Displaying alert to UI: " + message);
                alertTextArea.append(message + "\n");
                // Automatically scroll to the end (optional)
                alertTextArea.setCaretPosition(alertTextArea.getDocument().getLength());
                playSound(ALARM_SOUND_FILE); // Sound playing added
            });
        } else {
            System.out.println("Message (AlertTextArea null): " + message); // Fallback to console
            playSound(ALARM_SOUND_FILE); // Also play sound when printing to console (optional)
        }
    }

    private void playSound(String soundFilePath) {
        if (!consumerRunning) { // If consumer is not running (monitoring stopped), don't play sound
            System.out.println("[AlertManager] [Thread: " + Thread.currentThread().getName() + "] Consumer not running, skipping sound for: " + soundFilePath);
            return;
        }
        System.out.println("[AlertManager] [Thread: " + Thread.currentThread().getName() + "] Attempting to play sound: " + soundFilePath);
        try {
            URL soundURL = AlertManager.class.getResource(soundFilePath);
            if (soundURL == null) {
                System.err.println("[AlertManager] [Thread: " + Thread.currentThread().getName() + "] Warning: Sound file not found: " + soundFilePath);
                java.awt.Toolkit.getDefaultToolkit().beep(); // Fallback beep
                return;
            }
            AudioInputStream audioIn = AudioSystem.getAudioInputStream(soundURL);
            Clip clip = AudioSystem.getClip();
            clip.open(audioIn);
            clip.start();
            System.out.println("[AlertManager] [Thread: " + Thread.currentThread().getName() + "] Sound started: " + soundFilePath);
        } catch (UnsupportedAudioFileException | IOException | LineUnavailableException e) {
            System.err.println("[AlertManager] [Thread: " + Thread.currentThread().getName() + "] Error playing sound file " + soundFilePath + ": " + e.getMessage());
            java.awt.Toolkit.getDefaultToolkit().beep(); // Fallback beep
        }
    }
} 