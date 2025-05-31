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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.swing.JOptionPane;

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
    private final Map<String, Long> lastPlayedSoundTimes = new ConcurrentHashMap<>();
    private static final long SOUND_COOLDOWN_MS = 30000; // 30 seconds cooldown

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

    // Extracts a key from the alert message to identify the alert type for cooldown
    private String getAlertKey(String message) {
        if (message == null) {
            return "";
        }
        // Example message: "[04:02:43] ALERT (COINBASE:BTC-USD): COINBASE:BTC-USD price (103948.9600) < target (103960.8900)"
        // We want a key like "COINBASE:BTC-USD: price < target"
        try {
            String noTimestamp = message.substring(message.indexOf("] ") + 2); // Remove timestamp
            if (noTimestamp.startsWith("ALERT (")) {
                String symbolPart = noTimestamp.substring(noTimestamp.indexOf("(") + 1, noTimestamp.indexOf(")"));
                String detailsPart = noTimestamp.substring(noTimestamp.indexOf("): ") + 3);
                // Further simplify detailsPart to get the core condition
                // e.g., "COINBASE:BTC-USD price (103948.9600) < target (103960.8900)" -> "price < target"
                // This is a bit naive, might need more robust parsing based on actual message formats
                if (detailsPart.contains("price (") && detailsPart.contains(") < target (")) {
                    return symbolPart + ": price < target";
                } else if (detailsPart.contains("price (") && detailsPart.contains(") > target (")) {
                    return symbolPart + ": price > target";
                } else if (detailsPart.contains("price (") && detailsPart.contains(") crossed target (") && detailsPart.contains(" upwards")) {
                    return symbolPart + ": price crossed target upwards";
                } else if (detailsPart.contains("price (") && detailsPart.contains(") crossed target (") && detailsPart.contains(" downwards")) {
                    return symbolPart + ": price crossed target downwards";
                }
                // Fallback to a more generic key if parsing fails
                return symbolPart + ":" + detailsPart.split(" ")[1] + detailsPart.split(" ")[2];
            }
        } catch (Exception e) {
            // If parsing fails, use a less specific part of the message or the whole message (minus timestamp)
            System.err.println("[AlertManager] Error parsing alert key from message: '" + message + "'. Using broader key. Error: " + e.getMessage());
            if (message.contains("ALERT (")) {
                 return message.substring(message.indexOf("ALERT ("));
            }
        }
        return message; // Fallback to full message if specific parsing fails
    }

    private boolean canPlaySound(String alertMessage) {
        String alertKey = getAlertKey(alertMessage);
        long currentTime = System.currentTimeMillis();
        Long lastPlayedTime = lastPlayedSoundTimes.get(alertKey);

        if (lastPlayedTime == null || (currentTime - lastPlayedTime) > SOUND_COOLDOWN_MS) {
            System.out.println("[AlertManager] [Thread: " + Thread.currentThread().getName() + "] Cooldown check PASSED for alert key: '" + alertKey + "'. Sound can be played.");
            lastPlayedSoundTimes.put(alertKey, currentTime);
            return true;
        }
        System.out.println("[AlertManager] [Thread: " + Thread.currentThread().getName() + "] Cooldown check FAILED for alert key: '" + alertKey + "'. Sound was played recently. Skipping.");
        return false;
    }

    // Displays alerts in the UI
    private void displayAlert(String message) {
        if (alertTextArea != null) {
            SwingUtilities.invokeLater(() -> {
                System.out.println("[AlertManager] [Thread: " + Thread.currentThread().getName() + "] Displaying alert to UI: " + message);
                alertTextArea.append(message + "\n");
                alertTextArea.setCaretPosition(alertTextArea.getDocument().getLength());

                if (canPlaySound(message)) {
                    // Show JOptionPane dialog only when sound is also played
                    java.awt.Component parentComponent = (alertTextArea != null && alertTextArea.isVisible()) ? SwingUtilities.getWindowAncestor(alertTextArea) : null;
                    JOptionPane.showMessageDialog(parentComponent, 
                                                  message, 
                                                  "Stock Monitor Alert!", 
                                                  JOptionPane.WARNING_MESSAGE);
                    playSoundInternal(ALARM_SOUND_FILE); 
                }
            });
        } else { // Fallback for when UI is not available (e.g. testing or headless mode)
            System.out.println("Message (AlertTextArea null): " + message); 
            if (canPlaySound(message)) {
                 // Optionally, decide if JOptionPane makes sense in headless/no-UI mode
                 // For now, skipping JOptionPane if alertTextArea is null, but sound still plays.
                 playSoundInternal(ALARM_SOUND_FILE); 
            }
        }
    }

    private void playSoundInternal(String soundFilePath) { // Renamed from playSound
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
            // Add a listener to close the clip after it finishes playing
            // This is important to release system resources, especially if sounds are played frequently.
            clip.addLineListener(event -> {
                if (event.getType() == javax.sound.sampled.LineEvent.Type.STOP) {
                    Clip c = (Clip) event.getSource();
                    c.close();
                    // System.out.println("[AlertManager] [Thread: " + Thread.currentThread().getName() + "] Sound clip closed: " + soundFilePath);
                }
            });
            clip.start();
            System.out.println("[AlertManager] [Thread: " + Thread.currentThread().getName() + "] Sound started: " + soundFilePath);
        } catch (UnsupportedAudioFileException | IOException | LineUnavailableException e) {
            System.err.println("[AlertManager] [Thread: " + Thread.currentThread().getName() + "] Error playing sound file " + soundFilePath + ": " + e.getMessage());
            java.awt.Toolkit.getDefaultToolkit().beep(); // Fallback beep
        }
    }
} 