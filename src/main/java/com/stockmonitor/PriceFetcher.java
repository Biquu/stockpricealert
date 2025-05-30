package com.stockmonitor;

import org.json.JSONObject;
import org.json.JSONException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class PriceFetcher {

    private static String FINNHUB_API_KEY = System.getenv("FINNHUB_API_KEY");
    private static final String API_URL_TEMPLATE_QUOTE = "https://finnhub.io/api/v1/quote?symbol=%s&token=%s";

    public PriceFetcher() {
        System.out.println("[PriceFetcher] [Thread: " + Thread.currentThread().getName() + "] Instance created.");
        if (FINNHUB_API_KEY == null || FINNHUB_API_KEY.trim().isEmpty()) {
            System.err.println("[PriceFetcher] [Thread: " + Thread.currentThread().getName() + "] ERROR: FINNHUB_API_KEY environment variable is not set.");
        }
    }

    public double fetchPrice(String symbol) throws IOException {
        // System.out.println("[PriceFetcher] [Thread: " + Thread.currentThread().getName() + "] fetchPrice called for symbol: " + symbol);
        if (symbol == null || symbol.trim().isEmpty()) {
            throw new IllegalArgumentException("Stock symbol cannot be empty.");
        }
        if (FINNHUB_API_KEY == null || FINNHUB_API_KEY.trim().isEmpty()){
            throw new IOException("Finnhub API key is not set or is empty.");
        }

        String apiUrlString = String.format(API_URL_TEMPLATE_QUOTE, symbol.toUpperCase(), FINNHUB_API_KEY);
        URL url = new URL(apiUrlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(10000); // 10 seconds
        connection.setReadTimeout(10000);    // 10 seconds

        int responseCode = connection.getResponseCode();
        // System.out.println("[PriceFetcher] [Thread: " + Thread.currentThread().getName() + "] API request for " + symbol + " completed. Response code: " + responseCode);

        if (responseCode == HttpURLConnection.HTTP_OK) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();
            return parsePriceFromFinnhubQuoteResponse(response.toString(), symbol);
        } else {
            String errorResponse = "";
            if (connection.getErrorStream() != null) {
                try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(connection.getErrorStream()))) {
                    StringBuilder errorBuilder = new StringBuilder();
                    String errorLine;
                    while ((errorLine = errorReader.readLine()) != null) {
                        errorBuilder.append(errorLine);
                    }
                    errorResponse = errorBuilder.toString();
                }
            } else {
                errorResponse = connection.getResponseMessage(); 
            }
            System.err.println("[PriceFetcher] [Thread: " + Thread.currentThread().getName() + "] Finnhub API (/quote) request failed. HTTP Code: " + responseCode + ". Symbol: " + symbol + ". Detail: " + errorResponse);
            throw new IOException("Finnhub API (/quote) request failed. HTTP Code: " + responseCode + ". Symbol: " + symbol + ". Detail: " + errorResponse);
        }
    }

    private double parsePriceFromFinnhubQuoteResponse(String jsonResponse, String symbol) throws IOException {
        // System.out.println("[PriceFetcher] [Thread: " + Thread.currentThread().getName() + "] Parsing Finnhub quote response for " + symbol + ": " + jsonResponse.substring(0, Math.min(jsonResponse.length(), 100)) + "...");
        if (jsonResponse == null || jsonResponse.trim().isEmpty() || jsonResponse.trim().equals("{}") || jsonResponse.trim().equalsIgnoreCase("Symbol not supported")) {
            System.err.println("[PriceFetcher] [Thread: " + Thread.currentThread().getName() + "] Empty, invalid, or unsupported symbol response from Finnhub API (/quote) for: " + symbol + ". Response: " + jsonResponse);
            throw new IOException("Empty, invalid, or unsupported symbol response from Finnhub API (/quote): " + symbol + ". Response: " + jsonResponse);
        }
        
        try {
            JSONObject jsonObject = new JSONObject(jsonResponse);
            double currentPrice = jsonObject.optDouble("c", 0.0); // Current price
            double previousClose = jsonObject.optDouble("pc", 0.0); // Previous close price

            if (currentPrice != 0.0) {
                // System.out.println("[PriceFetcher] [Thread: " + Thread.currentThread().getName() + "] Parsed current price (c) for " + symbol + ": " + currentPrice);
                return currentPrice;
            } else if (previousClose != 0.0) {
                // System.out.println("[PriceFetcher] [Thread: " + Thread.currentThread().getName() + "] Current price (c) is 0, using previous close (pc) for " + symbol + ": " + previousClose);
                return previousClose;
            } else {
                 System.err.println("[PriceFetcher] [Thread: " + Thread.currentThread().getName() + "] Could not parse Finnhub price ('c' and 'pc' are 0 or missing). Symbol: " + symbol + ", Response: " + jsonResponse.substring(0, Math.min(jsonResponse.length(), 300)));
                 throw new IOException("Could not parse 'c' or 'pc' key with a valid value from Finnhub JSON response. Symbol: " + symbol);
            }
        } catch (JSONException e) {
            System.err.println("[PriceFetcher] [Thread: " + Thread.currentThread().getName() + "] Finnhub (/quote) JSON parse error. Symbol: " + symbol + ", Response: " + jsonResponse.substring(0, Math.min(jsonResponse.length(), 300)) + ", Error: " + e.getMessage());
            throw new IOException("Could not parse Finnhub (/quote) JSON response. Symbol: " + symbol, e);
        }
    }

    // Main method for testing
    public static void main(String[] args) {
        System.out.println("FINNHUB_API_KEY environment variable: " + System.getenv("FINNHUB_API_KEY"));
        
        PriceFetcher fetcher = new PriceFetcher();
        if (FINNHUB_API_KEY == null || FINNHUB_API_KEY.trim().isEmpty()) {
            System.err.println("TEST FAILED: FINNHUB_API_KEY is not set. Please check your .env file or environment variables.");
            System.err.println("Run this test from an IDE or a script like 'calistir.bat' where FINNHUB_API_KEY is set.");
            return;
        }
        
        System.out.println("Starting Finnhub API Live Price Test...");
        
        String aaplSymbol = "AAPL";
        String btcSymbol = "BINANCE:BTCUSDT"; 
        String nonExistentSymbol = "XYZ123NONEXISTENT";

        System.out.println("\n--- Test 1: Live Price (" + aaplSymbol + ") ---");
        try {
            double price = fetcher.fetchPrice(aaplSymbol);
            System.out.println(aaplSymbol + " Live Price: " + price);
        } catch (Exception e) {
            System.err.println(aaplSymbol + " ERROR fetching live price: " + e.getMessage());
        }

        System.out.println("\n--- Test 2: Live Price (" + btcSymbol + ") ---");
        try {
            double price = fetcher.fetchPrice(btcSymbol);
            System.out.println(btcSymbol + " Live Price: " + price);
        } catch (Exception e) {
            System.err.println(btcSymbol + " ERROR fetching live price: " + e.getMessage());
        }
        
        System.out.println("\n--- Test 3: Live Price (Non-existent Symbol: " + nonExistentSymbol + ") ---");
        try {
            double price = fetcher.fetchPrice(nonExistentSymbol);
            System.out.println(nonExistentSymbol + " Live Price: " + price);
        } catch (Exception e) {
            System.err.println(nonExistentSymbol + " ERROR fetching live price: " + e.getMessage());
        }

        System.out.println("\nFinnhub API Live Price Test Completed.");
    }
} 