# Stock Monitoring System

This project is a desktop application developed using Java Swing and threads. It tracks live prices of selected stocks, provides real-time alerts, and visualizes price changes with a simple candlestick chart.

## Features

-   Live stock price tracking (using Finnhub API)
-   Visual and audible alerts when user-defined price thresholds are breached
-   Candlestick chart displaying real-time price changes for the selected stock (with XChart)
-   Ability to monitor multiple stocks simultaneously (each in a separate thread)
-   Improved UI with Nimbus Look and Feel

## Getting Started

These instructions will help you get a copy of the project up and running on your local machine for development and testing purposes.

### Prerequisites

What you need to install the software and how to install them:

-   Java Development Kit (JDK) 11 or later
-   JAR files required in the `lib` folder:
    -   XChart: `xchart-3.8.8.jar` (or a more recent version)
    -   org.json: `json-20250517.jar` (or a more recent version, check `calistir.bat` for the exact version used)
    -   You can download these JAR files from Maven Central or XChart's GitHub page.
-   Finnhub API Key: You will need to obtain a free API key from the [Finnhub.io](https://finnhub.io/) website.

### Setup

A step-by-step series of examples that tell you how to get a development environment running:

1.  Clone the project (if using a Git repository) or download the project files into a folder.
    ```bash
    # Example: git clone https://github.com/your_username/stock_monitoring.git
    ```
2.  Navigate to the project directory.
    ```bash
    # Example: cd stock_monitoring
    ```
3.  Create a folder named `lib` (if it doesn't exist) and copy the required JAR files (`xchart-*.jar`, `json-*.jar`) into this folder.
4.  Set up your API Key:
    -   Create a file named `.env` in the project's root directory.
    -   The content of the `.env` file should be exactly as follows (replace `YOUR_API_KEY_HERE` with your actual Finnhub API key):
        ```env
        FINNHUB_API_KEY=YOUR_API_KEY_HERE
        ```
    -   **IMPORTANT:** Never commit the `.env` file to version control systems like Git. Ensure that your `.gitignore` file contains the line `.env`.
    -   If your IDE or system does not automatically load `.env` files, you might need to ensure this environment variable is passed to the Java process when running the application. Most modern IDEs (IntelliJ IDEA, VS Code, etc.) recognize `.env` files automatically or support them via plugins.

## Usage

To compile and run the application:

**Method 1: Using `calistir.bat` Script (Recommended for Windows)**

You can easily compile and run the application by double-clicking the `calistir.bat` file located in the project's root directory.

This script automatically performs the following steps:
1.  Creates a `bin` folder (if it doesn't exist).
2.  Compiles the necessary Java source files. It assumes that `xchart-3.8.8.jar` and `json-20250517.jar` are present in the `lib` folder. If your JAR file names or versions differ, you may need to open `calistir.bat` with a text editor and adjust the relevant paths.
3.  Starts the application.

If there are any issues during compilation or execution, you will see error messages in the command prompt window.

**Method 2: Manual Commands**

1.  Create a `bin` folder in the project root directory (for compiled `.class` files).
    ```bash
    mkdir bin
    ```
2.  Compile the Java source files. Make sure to add the JAR files from the `lib` folder to your classpath. Use `;` for Windows and `:` for Linux/macOS.
    ```bash
    # Windows example (consistent with calistir.bat):
    javac -d bin -cp "src/main/java;lib/xchart-3.8.8.jar;lib/json-20250517.jar" src/main/java/com/stockmonitor/*.java src/main/java/com/stockmonitor/listeners/*.java
    # Linux/macOS example (check JAR names and paths):
    # javac -d bin -cp "src/main/java:lib/xchart-3.8.8.jar:lib/json-20250517.jar" src/main/java/com/stockmonitor/*.java src/main/java/com/stockmonitor/listeners/*.java
    ```
    *Note: Update the exact names of the JAR files in the commands above to match the versions you downloaded and those specified in `calistir.bat`.*
3.  Run the application:
    ```bash
    # Windows example (consistent with calistir.bat):
    java -cp ".;bin;lib/xchart-3.8.8.jar;lib/json-20250517.jar" com.stockmonitor.StockMonitorApp
    # Linux/macOS example (check JAR names and paths):
    # java -cp "bin:lib/xchart-3.8.8.jar:lib/json-20250517.jar" com.stockmonitor.StockMonitorApp
    ```

When the application opens, select the stock you want to monitor, enter the upper and/or lower price thresholds for alerts, and click the "Start Monitoring" button. Alerts and current price information will be displayed in the interface.

## Running Tests

Currently, there is no automated testing system in the project. Tests are performed manually.

### End-to-End Tests

-   Verify if the application launches correctly.
-   Check if stock selection and threshold settings work as expected.
-   Confirm that prices are fetched correctly from the Finnhub API and displayed.
-   Ensure alerts are triggered when thresholds are breached.
-   Verify that charts are updated with live prices.
-   Check if the "Stop Monitoring" functionality works as intended.

## Technologies Used

*   [Java](https://www.oracle.com/java/) - Core programming language
*   [Java Swing](https://docs.oracle.com/javase/tutorial/uiswing/) - GUI creation library
*   [Finnhub API](https://finnhub.io/) - API used for stock price data
*   [XChart](https://knowm.org/open-source/xchart/) - Library used for chart plotting
*   [org.json](https://github.com/stleary/JSON-java) - Library used for parsing JSON data

## Authors

*   **Biquu** (GitHub: biquu) - *Initial development and ongoing support*

## License

This project is licensed under the MIT License - see the [LICENSE.md](LICENSE.md) file for details (to be created). 