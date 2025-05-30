package com.stockmonitor.listeners;

import java.util.Date;

/**
 * Arayüz, grafik verileri güncellendiğinde çağrılacak metotları tanımlar.
 * Bu, genellikle anlık fiyat verilerinin grafiğe eklenmesi için kullanılır.
 */
public interface GraphDataListener {

    /**
     * Belirli bir hisse senedi için yeni bir fiyat noktası geldiğinde çağrılır.
     *
     * @param symbol Hisse senedi sembolü (örn: "AAPL").
     * @param price Alınan son fiyat.
     * @param timestamp Fiyatın alındığı zaman damgası.
     */
    void onPriceUpdate(String symbol, double price, Date timestamp);

    /**
     * Belirli bir hisse senedine ait grafiği temizlemek için çağrılır.
     * Bu, genellikle izleme durdurulduğunda veya sembol değiştirildiğinde kullanılır.
     * @param symbol Temizlenecek grafiğin hisse senedi sembolü.
     */
    void clearGraph(String symbol);

    /**
     * İzlenen tüm grafiklerin temizlenmesi için çağrılır.
     */
    void clearAllGraphs();

    // Mum grafiği verileri için bir metot eklenebilir, ancak şimdilik anlık fiyatla devam ediyoruz.
    // void onCandleDataUpdate(String symbol, List<CandleData> candles);
} 