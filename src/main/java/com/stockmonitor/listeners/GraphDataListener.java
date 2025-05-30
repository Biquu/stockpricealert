package com.stockmonitor.listeners;

import java.util.Date;
// import java.util.List; // For onCandleDataUpdate, if re-enabled
// import com.stockmonitor.CandleData; // For onCandleDataUpdate, if re-enabled

/**
 * Interface defining methods to be called when graph data is updated.
 * This is typically used for adding live price data to a graph.
 */
public interface GraphDataListener {

    /**
     * Called when a new price point arrives for a specific stock.
     *
     * @param symbol The stock symbol (e.g., "AAPL").
     * @param price The latest price received.
     * @param timestamp The timestamp when the price was received.
     */
    void onPriceUpdate(String symbol, double price, Date timestamp);

    /**
     * Called when OHLC (Open, High, Low, Close) data is updated (for candlestick charts).
     * @param symbol The symbol.
     * @param timestamp The timestamp (usually the start of the day or period).
     * @param open The opening price.
     * @param high The highest price.
     * @param low The lowest price.
     * @param close The closing price.
     */
    void onOHLCDataUpdate(String symbol, Date timestamp, double open, double high, double low, double close);

    /**
     * Called to clear the graph for a specific stock.
     * This is typically used when monitoring is stopped or the symbol is changed.
     * @param symbol The stock symbol of the graph to be cleared.
     */
    void clearGraph(String symbol);

    /**
     * Called to clear all monitored graphs.
     */
    void clearAllGraphs();

    // A method for candle chart data could be added, but for now, we continue with live price.
    // void onCandleDataUpdate(String symbol, List<CandleData> candles);
}