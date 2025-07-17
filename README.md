# 🚨 SafeDrive - Kaza Uyarı Sistemi

https://github.com/user-attachments/assets/ef11efb5-1a80-4cd5-be7f-e84cfe3d0819

Android tabanlı bu uygulama, geçmiş kazaların verilerini, hava durumu ve hız limitleriyle birleştirerek sürücülere bulunduğu konuma göre uyarılar gönderir. Uygulama, risk seviyesini hesaplayarak AI destekli uyarı mesajı üretir ve bu mesajı sesli olarak iletir.

## 📱 Özellikler

- 📍 **Gerçek zamanlı konum takibi:** Kullanıcının anlık konumu harita üzerinde gösterilir.
- 💥 **Kaza bölgesi uyarısı:** Geçmişte kaza yaşanmış noktalara yaklaşıldığında uyarı ekranı gösterilir.
- 🌦️ **Hava durumu entegrasyonu:** OpenWeatherMap API üzerinden hava durumu alınır.
- 🧠 **Yapay zeka destekli mesajlar:** Gemini API kullanılarak AI tabanlı kişiselleştirilmiş uyarı mesajları üretilir.
- 🔊 **TTS (Text-to-Speech):** Uyarı mesajları sesli olarak okunur.
- 🚦 **Risk seviyesi hesaplama:** Araç hızı, yasal hız limiti, kaza türü ve hava durumuna göre risk yüzdesi hesaplanır.

## 🛠️ Kullanılan Teknolojiler

- **Java** — Android uygulama geliştirme
- **Google Maps SDK** — Harita ve konum işlemleri
- **Firebase** — Gerçek zamanlı veri ve kullanıcı yönetimi
- **OpenWeatherMap API** — Hava durumu verisi
- **Gemini API** — AI ile uyarı mesajı üretimi
- **TextToSpeech API** — Mesajların sesli okunması

## 🔐 Gereken API Anahtarları

Aşağıdaki API anahtarlarını projenize eklemeniz gerekir:

- OpenWeatherMap API Key
- Google Maps API Key
- Gemini API Key

## 📌 Not
  Bu uygulama, **üniversite stajı kapsamında** geliştirilmiştir. Amaç; konum, hava durumu ve geçmiş kaza verilerini bir araya getirerek sürücülere **yapay zeka destekli akıllı uyarılar sunan** bir mobil sistem geliştirmektir.

## 👨‍💻 Geliştirici
Riad Memmedli
