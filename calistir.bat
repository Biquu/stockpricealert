@echo off

echo Java surumu kontrol ediliyor...
java -version

echo.
echo Proje ana dizini kontrol ediliyor...
cd /D "%~dp0"

echo.
echo API Anahtari .env dosyasindan okunuyor...
IF NOT EXIST .env (
    echo HATA: .env dosyasi bulunamadi. Lutfen FINNHUB_API_KEY iceren bir .env dosyasi olusturun.
    goto end
)

FOR /F "tokens=1,* delims==" %%A IN (.env) DO (
    IF /I "%%A"=="FINNHUB_API_KEY" SET FINNHUB_API_KEY=%%B
)

IF NOT DEFINED FINNHUB_API_KEY (
    echo HATA: FINNHUB_API_KEY, .env dosyasinda bulunamadi veya degeri bos.
    goto end
)

echo API Anahtari bulundu ve ayarlandi (bu oturum icin).
REM echo DEBUG: FINNHUB_API_KEY = %FINNHUB_API_KEY%

echo.
echo "bin" klasoru olusturuluyor (eger yoksa)...
mkdir bin 2>nul

echo.
echo Proje derleniyor...
echo src ve lib klasorlerinin mevcut oldugundan ve JAR dosyalarinin adlarinin dogru oldugundan emin olun.
REM lib klasorundeki JAR dosyalarinin adlarini ve surumlerini kontrol edin ve gerekiyorsa guncelleyin.
REM Ornegin, xchart-3.8.8.jar ve json-20250517.jar kullanildigi varsayilmistir.
javac -d bin -cp "src/main/java;lib/xchart-3.8.8.jar;lib/json-20250517.jar" src/main/java/com/stockmonitor/*.java src/main/java/com/stockmonitor/listeners/*.java

IF ERRORLEVEL 1 (
    echo.
    echo !!! DERLEME BASARISIZ OLDU !!!
    echo Lutfen yukaridaki hatalari kontrol edin.
    echo Java PATH'inizin dogru ayarlandigindan ve lib klasorundeki JAR dosyalarinin adlarinin betiktekiyle eslestiginden emin olun.
    goto end
)

echo.
echo Derleme basarili.
echo.
echo Uygulama baslatiliyor...
echo Konsol cikislari icin bu pencereyi acik tutun.
echo.
REM lib klasorundeki JAR dosyalarinin adlarini ve surumlerini kontrol edin ve gerekiyorsa guncelleyin.
java -cp "bin;lib/xchart-3.8.8.jar;lib/json-20250517.jar" com.stockmonitor.StockMonitorApp

:end
echo.
echo Betik tamamlandi.
REM Konsolun hemen kapanmamasi icin istege bagli olarak asagidaki satiri aktiflestirebilirsiniz.
REM pause 