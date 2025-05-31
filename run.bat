@echo off

echo Checking Java version...
java -version

echo.
echo Checking project root directory...
cd /D "%~dp0"

echo.
echo Reading API Key from .env file...
IF NOT EXIST .env (
    echo ERROR: .env file not found. Please create an .env file containing FINNHUB_API_KEY.
    goto end
)

FOR /F "tokens=1,* delims==" %%A IN (.env) DO (
    IF /I "%%A"=="FINNHUB_API_KEY" SET FINNHUB_API_KEY=%%B
)

IF NOT DEFINED FINNHUB_API_KEY (
    echo ERROR: FINNHUB_API_KEY not found in .env file or its value is empty.
    goto end
)

echo API Key found and set (for this session).
REM echo DEBUG: FINNHUB_API_KEY = %FINNHUB_API_KEY%

echo.
echo Creating "bin" folder (if it does not exist)...
mkdir bin 2>nul

echo.
echo Compiling project...
echo Ensure that src and lib folders exist and JAR file names are correct.
REM Check the names and versions of JAR files in the lib folder and update if necessary.
REM For example, it is assumed that xchart-3.8.8.jar and json-20250517.jar are used.
javac -d bin -cp "src/main/java;lib/xchart-3.8.8.jar;lib/json-20250517.jar" src/main/java/com/stockmonitor/*.java src/main/java/com/stockmonitor/listeners/*.java

IF ERRORLEVEL 1 (
    echo.
    echo !!! COMPILATION FAILED !!!
    echo Please check the errors above.
    echo Ensure your Java PATH is set correctly and JAR file names in the lib folder match those in the script.
    goto end
)

echo.
echo Compilation successful.
echo.
echo Starting application...
echo Keep this window open for console outputs.
echo.
REM Check the names and versions of JAR files in the lib folder and update if necessary.
java -cp ".;bin;lib/xchart-3.8.8.jar;lib/json-20250517.jar" com.stockmonitor.StockMonitorApp

:end
echo.
echo Script finished.
REM Optionally, you can uncomment the line below to prevent the console from closing immediately.
REM pause 