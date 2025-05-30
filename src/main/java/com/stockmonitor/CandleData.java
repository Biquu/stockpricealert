package com.stockmonitor;

import java.util.Date;

public class CandleData {
    private final Date date;    // Zaman damgası (XChart genellikle Date nesneleriyle çalışır)
    private final double open;
    private final double high;
    private final double low;
    private final double close;
    private final double volume; // Hacim bilgisini de alalım

    public CandleData(long timestampSeconds, double open, double high, double low, double close, double volume) {
        this.date = new Date(timestampSeconds * 1000); // Unix timestamp (saniye) -> Date (milisaniye)
        this.open = open;
        this.high = high;
        this.low = low;
        this.close = close;
        this.volume = volume;
    }

    public Date getDate() {
        return date;
    }

    public double getOpen() {
        return open;
    }

    public double getHigh() {
        return high;
    }

    public double getLow() {
        return low;
    }

    public double getClose() {
        return close;
    }

    public double getVolume() {
        return volume;
    }

    @Override
    public String toString() {
        return "CandleData{" +
               "date=" + date +
               ", open=" + open +
               ", high=" + high +
               ", low=" + low +
               ", close=" + close +
               ", volume=" + volume +
               '}';
    }
} 