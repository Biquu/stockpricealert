package com.stockmonitor.listeners;

public interface AlertListener {
    void onAlertTriggered(String symbol, double currentPrice, String thresholdCondition, String message);
} 