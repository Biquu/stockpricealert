package com.stockmonitor;

import java.time.LocalDateTime;

public interface GraphDataListener {
    void onPriceUpdate(String symbol, double price, LocalDateTime timestamp);
} 