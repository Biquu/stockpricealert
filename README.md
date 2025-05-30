# Hisse Senedi İzleme Sistemi

Bu proje, Java Swing ve thread'ler kullanılarak geliştirilmiş, seçilen hisse senetlerinin canlı fiyatlarını takip eden, gerçek zamanlı uyarılar veren ve basit bir çizgi grafik ile fiyat değişimlerini görselleştiren bir masaüstü uygulamasıdır.

## Özellikler

-   Canlı hisse senedi fiyat takibi (Finnhub API kullanarak)
-   Kullanıcı tanımlı fiyat eşikleri aşıldığında görsel ve sesli uyarılar
-   Seçilen hisse senedi için anlık fiyat değişimlerini gösteren çizgi grafik (XChart ile)
-   Aynı anda birden fazla hisse senedini izleyebilme (her biri ayrı bir thread'de)
-   API veya simüle edilmiş CSV verisi (henüz tam olarak entegre edilmedi) arasında seçim yapabilme (temel altyapı mevcut)
-   Nimbus Look and Feel ile geliştirilmiş kullanıcı arayüzü

## Başlarken

Bu talimatlar, projenin bir kopyasını geliştirme ve test amacıyla yerel makinenizde çalıştırmanıza yardımcı olacaktır.

### Önkoşullar

Yazılımı yüklemek için nelere ihtiyacınız olduğu ve bunların nasıl kurulacağı:

-   Java Development Kit (JDK) 11 veya üzeri
-   `lib` klasöründe bulunması gereken JAR dosyaları:
    -   XChart: `xchart-3.8.8.jar` (veya daha güncel bir sürüm)
    -   org.json: `json-20231013.jar` (veya daha güncel bir sürüm)
    -   Bu JAR dosyalarını Maven Central veya XChart'ın GitHub sayfasından indirebilirsiniz.
-   Finnhub API Anahtarı: Finnhub.io web sitesinden ücretsiz bir API anahtarı almanız gerekmektedir.

### Kurulum

Geliştirme ortamını çalıştırmak için adım adım bir dizi örnek:

1.  Projeyi klonlayın (eğer bir Git deposu kullanılıyorsa) veya proje dosyalarını bir klasöre indirin.
    ```bash
    # Örnek: git clone https://github.com/kullanici_adiniz/hisse_senedi_izleme.git
    ```
2.  Proje dizinine gidin.
    ```bash
    # Örnek: cd hisse_senedi_izleme
    ```
3.  `lib` adında bir klasör oluşturun (eğer yoksa) ve gerekli JAR dosyalarını (`xchart-*.jar`, `json-*.jar`) bu klasöre kopyalayın.
4.  API Anahtarınızı ayarlayın:
    -   Proje ana dizininde `.env` adında bir dosya oluşturun.
    -   `.env` dosyasının içeriği tam olarak aşağıdaki gibi olmalıdır (yalnızca `SENIN_API_ANAHTARIN_BURAYA` kısmını kendi Finnhub API anahtarınızla değiştirin):
        ```env
        FINNHUB_API_KEY=SENIN_API_ANAHTARIN_BURAYA
        ```
    -   **ÖNEMLİ:** `.env` dosyasını asla Git gibi sürüm kontrol sistemlerine göndermeyin. `.gitignore` dosyanızda `.env` satırının olduğundan emin olun.
    -   Eğer IDE'niz veya sisteminiz `.env` dosyalarını otomatik olarak yüklemiyorsa, uygulamayı çalıştırırken bu ortam değişkeninin Java sürecine aktarıldığından emin olmanız gerekebilir. Çoğu modern IDE (IntelliJ IDEA, VS Code vb.) `.env` dosyalarını otomatik olarak tanır veya eklentiler aracılığıyla destekler.

## Kullanım

Uygulamayı derlemek ve çalıştırmak için:

**Yöntem 1: `calistir.bat` Betiği ile (Windows için Önerilen)**

Proje ana dizininde bulunan `calistir.bat` dosyasına çift tıklayarak uygulamayı kolayca derleyebilir ve çalıştırabilirsiniz.

Bu betik aşağıdaki adımları otomatik olarak gerçekleştirir:
1.  `bin` klasörünü oluşturur (eğer yoksa).
2.  Gerekli Java kaynak dosyalarını derler. `lib` klasöründeki `xchart-3.8.8.jar` ve `json-20250517.jar` dosyalarının var olduğunu varsayar. Eğer JAR dosyalarınızın adları veya sürümleri farklıysa, `calistir.bat` dosyasını bir metin editörü ile açıp ilgili yerleri düzenlemeniz gerekebilir.
3.  Uygulamayı başlatır.

Derleme veya çalıştırma sırasında bir sorun olursa, komut istemi penceresinde hata mesajlarını görebilirsiniz.

**Yöntem 2: Manuel Komutlarla**

1.  Proje ana dizininde bir `bin` klasörü oluşturun (derlenmiş `.class` dosyaları için).
    ```bash
    mkdir bin
    ```
2.  Java kaynak dosyalarını derleyin. `lib` klasöründeki JAR dosyalarını classpath'e eklediğinizden emin olun. Windows için `;`, Linux/macOS için `:` kullanılır.
    ```bash
    # Windows örneği (calistir.bat içeriği ile tutarlı):
    javac -d bin -cp "src/main/java;lib/xchart-3.8.8.jar;lib/json-20250517.jar" src/main/java/com/stockmonitor/*.java src/main/java/com/stockmonitor/listeners/*.java
    # Linux/macOS örneği (JAR adlarını ve yollarını kontrol edin):
    # javac -d bin -cp "src/main/java:lib/xchart-3.8.8.jar:lib/json-20250517.jar" src/main/java/com/stockmonitor/*.java src/main/java/com/stockmonitor/listeners/*.java
    ```
    *Not: Yukarıdaki komutlarda JAR dosyalarının tam adlarını kendi indirdiğiniz sürümlerle ve `calistir.bat` içindekiyle eşleşecek şekilde güncelleyin.*
3.  Uygulamayı çalıştırın:
    ```bash
    # Windows örneği (calistir.bat içeriği ile tutarlı):
    java -cp "bin;lib/xchart-3.8.8.jar;lib/json-20250517.jar" com.stockmonitor.StockMonitorApp
    # Linux/macOS örneği (JAR adlarını ve yollarını kontrol edin):
    # java -cp "bin:lib/xchart-3.8.8.jar:lib/json-20250517.jar" com.stockmonitor.StockMonitorApp
    ```

Uygulama açıldığında, izlemek istediğiniz hisse senedini seçin, alarm için üst ve/veya alt fiyat eşiklerini girin ve "İzlemeyi Başlat" butonuna tıklayın. Alarmlar ve güncel fiyat bilgileri arayüzde gösterilecektir.

## Testleri Çalıştırma

Şu anda projede otomatize edilmiş bir test sistemi bulunmamaktadır. Testler manuel olarak yapılmaktadır.

### Uçtan Uca Testler

- Uygulamanın doğru şekilde başlatılıp başlatılmadığı.
- Hisse senedi seçimi ve eşik ayarlarının doğru çalışıp çalışmadığı.
- Fiyatların Finnhub API'sinden doğru şekilde çekilip gösterilip gösterilmediği.
- Eşikler aşıldığında alarmların tetiklenip tetiklenmediği.
- Grafiklerin anlık fiyatlarla güncellenip güncellenmediği.
- "İzlemeyi Durdur" fonksiyonunun beklendiği gibi çalışıp çalışmadığı.

## Geliştirildiği Teknolojiler

*   [Java](https://www.oracle.com/java/) - Ana programlama dili
*   [Java Swing](https://docs.oracle.com/javase/tutorial/uiswing/) - GUI oluşturma kütüphanesi
*   [Finnhub API](https://finnhub.io/) - Hisse senedi fiyat verileri için kullanılan API
*   [XChart](https://knowm.org/open-source/xchart/) - Grafik çizimi için kullanılan kütüphane
*   [org.json](https://github.com/stleary/JSON-java) - JSON verilerini parse etmek için kullanılan kütüphane

## Katkıda Bulunma

Katkıda bulunma rehberi için [CONTRIBUTING.md](CONTRIBUTING.md) dosyasına bakın (henüz oluşturulmadı).

## Sürümleme

Sürümleme için [SemVer](http://semver.org/) kullanılmaktadır (henüz resmi bir sürüm yayınlanmadı).

## Yazarlar

*   **AI Assistant (Gemini Pro 2.5)** - *İlk geliştirme ve devam eden destek*

Projenin geliştirilmesine katkıda bulunan diğer kişilerin listesi için [contributors](https://github.com/your_username/your_project_name/contributors) sayfasına bakabilirsiniz (varsa).

## Lisans

Bu proje MIT Lisansı altında lisanslanmıştır - detaylar için [LICENSE.md](LICENSE.md) dosyasına bakın (henüz oluşturulmadı).

## Teşekkürler

*   Finnhub.io ücretsiz API sağladığı için.
*   XChart ve org.json kütüphanelerinin geliştiricilerine.
*   Kullanıcının sabrı ve geri bildirimleri için. 