package com.example.kazauyarisistemi;

import android.content.Context;
import android.location.Location;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.util.List;
import java.util.Locale;

import android.os.Vibrator;
import android.os.VibrationEffect;
import android.os.Build;
import android.media.MediaPlayer;
import android.media.AudioManager;
import android.media.RingtoneManager;
import android.view.animation.Animation;
import android.view.animation.AlphaAnimation;
import android.view.animation.ScaleAnimation;
import android.view.animation.AnimationSet;
import android.graphics.Color;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.os.Handler;
import android.content.Context;
import android.net.Uri;

public class MapManager implements WeatherSpeedInfoManager.WeatherWarningListener {
    private static final String TAG = "MapManager";
    private static final float PROXIMITY_THRESHOLD_METERS = 100;
    private GoogleMap mMap;
    private LatLng currentLocation;
    private Marker userMarker;
    private LinearLayout warningLayout;
    private List<KazaData> kazaDataList;
    private boolean manualLocationChange = false;
    private Context context;
    private MapsActivity mapsActivity;
    private WeatherSpeedInfoManager weatherSpeedInfoManager;

    private TextToSpeech textToSpeech;

    private LinearLayout weatherWarningLayout;
    private View currentWeatherWarningView;
    private String currentWeatherWarningType = null;
    private long lastWeatherWarningTime = 0;
    private static final long WEATHER_WARNING_COOLDOWN = 30000; // 30 saniye cooldown

    public MapManager(Context context, GoogleMap googleMap, LinearLayout warningLayout) {
        this.context = context;
        this.mMap = googleMap;
        this.warningLayout = warningLayout;
        this.mapsActivity = (MapsActivity) context;

        textToSpeech = new TextToSpeech(context.getApplicationContext(), status -> {
            if (status != TextToSpeech.ERROR) {
                textToSpeech.setLanguage(new Locale("tr", "TR"));
            }
        });

        mMap.setOnMarkerClickListener(marker -> {
            String title = marker.getTitle();
            if (title != null && title.contains("KAZA")) {
                showKazaDetails((KazaData) marker.getTag(), marker.getPosition());
                return true;
            }
            return false;
        });
    }

    public void setWeatherSpeedInfoManager(WeatherSpeedInfoManager weatherSpeedInfoManager) {
        this.weatherSpeedInfoManager = weatherSpeedInfoManager;
        // WeatherSpeedInfoManager'a listener olarak kendimizi kaydet
        if (weatherSpeedInfoManager != null) {
            weatherSpeedInfoManager.setWeatherWarningListener(this);
        }
    }

    public void setWeatherWarningLayout(LinearLayout weatherWarningLayout) {
        this.weatherWarningLayout = weatherWarningLayout;
    }

    // WeatherWarningListener interface implementasyonu
    @Override
    public void onSevereWeatherDetected(String weatherDescription) {
        Log.d(TAG, "Severe weather detected from WeatherSpeedInfoManager: " + weatherDescription);

        // Hava durumuna göre uygun uyarı göster
        String weatherType = determineWeatherType(weatherDescription);
        String warningMessage = generateWeatherWarningMessage(weatherDescription, weatherType);

        showWeatherWarning(warningMessage, weatherType);
    }

    // Hava durumu tipini belirle
    public static String determineWeatherType(String weatherDescription) {
        if (weatherDescription == null) return "unknown";

        String lower = weatherDescription.toLowerCase();

        if (lower.contains("yağmur") || lower.contains("sağanak") || lower.contains("çisenti") ||
                lower.contains("rain") || lower.contains("shower")) {
            return "rain";
        } else if (lower.contains("kar") || lower.contains("snow")) {
            return "snow";
        } else if (lower.contains("fırtına") || lower.contains("gök gürültülü") ||
                lower.contains("storm") || lower.contains("thunderstorm")) {
            return "storm";
        } else if (lower.contains("sis") || lower.contains("pus") || lower.contains("fog") ||
                lower.contains("mist")) {
            return "fog";
        } else if (lower.contains("şiddetli") || lower.contains("yoğun") || lower.contains("heavy")) {
            return "severe";
        } else if (lower.contains("rüzgar") || lower.contains("wind")) {
            return "wind";
        }

        return "unknown";
    }

    // Hava durumu uyarı mesajını oluştur
    private String generateWeatherWarningMessage(String weatherDescription, String weatherType) {
        switch (weatherType) {
            case "rain":
                return "🌧️ YAĞMUR UYARISI: Hava durumu yağmurlu! " +
                        "Sürüş yaparken dikkatli olun. Fren mesafesi artabilir, " +
                        "yol kaygan olabilir. Hızınızı düşürün.";
            case "snow":
                return "❄️ KAR UYARISI: Hava durumu karlı! " +
                        "Sürüş yaparken son derece dikkatli olun. Yol buzlu ve kaygan olabilir. " +
                        "Hızınızı düşürün, ani fren yapmayın.";
            case "storm":
                return "⛈️ FIRTINA UYARISI: Hava durumu fırtınalı! " +
                        "Sürüş yaparken çok dikkatli olun. Görüş mesafesi azalabilir, " +
                        "rüzgar etkisiyle araç kontrolü zorlaşabilir.";
            case "fog":
                return "🌫️ SIS UYARISI: Hava durumu sisli! " +
                        "Görüş mesafesi azalmış. Farları yakın, hızınızı düşürün, " +
                        "araç takip mesafenizi artırın.";
            case "severe":
                return "⚠️ ŞİDDETLİ HAVA UYARISI: Hava durumu çok kötü! " +
                        "Sürüş yaparken son derece dikkatli olun. Mümkünse seyahatinizi erteleyiniz.";
            case "wind":
                return "💨 RÜZGAR UYARISI: Hava durumu rüzgarlı! " +
                        "Araç kontrolünde dikkatli olun. Yan rüzgar etkisiyle sürüş zorlaşabilir.";
            default:
                return "⚠️ HAVA DURUMU UYARISI: Hava durumu sürüş için uygun değil! " +
                        "Sürüş yaparken dikkatli olun. (" + weatherDescription + ")";
        }
    }

    public void showWeatherWarning(String message, String weatherType) {
        if (weatherWarningLayout == null || mapsActivity == null) {
            Log.e(TAG, "weatherWarningLayout or mapsActivity is null!");
            return;
        }

        // Cooldown kontrolü - aynı tip uyarının çok sık gösterilmesini engelle
        long currentTime = System.currentTimeMillis();
        if (weatherType.equals(currentWeatherWarningType) &&
                (currentTime - lastWeatherWarningTime) < WEATHER_WARNING_COOLDOWN) {
            Log.d(TAG, "Weather warning cooldown active, skipping: " + weatherType);
            return;
        }

        mapsActivity.runOnUiThread(() -> {
            try {
                // Önceki hava durumu uyarısını kaldır
                hideWeatherWarning();

                LayoutInflater inflater = LayoutInflater.from(context);
                View weatherWarningView = inflater.inflate(R.layout.weather_warning_layout, weatherWarningLayout, false);

                TextView warningTextView = weatherWarningView.findViewById(R.id.weatherWarningTextView);
                ImageView closeWarning = weatherWarningView.findViewById(R.id.closeWeatherWarning);
                ImageView weatherIcon = weatherWarningView.findViewById(R.id.weatherWarningIcon);

                // Hava durumu tipine göre renk ve ikon ayarla
                int warningColor = getWeatherWarningColor(weatherType);
                int iconResource = getWeatherWarningIcon(weatherType);

                weatherWarningView.setBackgroundColor(ContextCompat.getColor(context, warningColor));
                weatherIcon.setImageResource(iconResource);
                warningTextView.setText(message);

                closeWarning.setOnClickListener(v -> hideWeatherWarning());

                weatherWarningLayout.addView(weatherWarningView);
                weatherWarningLayout.setVisibility(View.VISIBLE);

                currentWeatherWarningView = weatherWarningView;
                currentWeatherWarningType = weatherType;
                lastWeatherWarningTime = currentTime;

                Log.d(TAG, "WEATHER WARNING DISPLAYED: " + message);

                // Toast mesajı
                String toastMessage = "Hava Durumu Uyarısı: " + getWeatherTypeText(weatherType);
                Toast.makeText(context, toastMessage, Toast.LENGTH_LONG).show();

                // Sesli uyarı
                speakWeatherWarning(message, weatherType);

            } catch (Exception e) {
                Log.e(TAG, "Error showing weather warning: " + e.getMessage(), e);
            }
        });
    }

    public void hideWeatherWarning() {
        if (weatherWarningLayout != null && currentWeatherWarningView != null) {
            weatherWarningLayout.removeView(currentWeatherWarningView);
            if (weatherWarningLayout.getChildCount() == 0) {
                weatherWarningLayout.setVisibility(View.GONE);
            }
            currentWeatherWarningView = null;
            currentWeatherWarningType = null;
        }
    }

    private int getWeatherWarningColor(String weatherType) {
        switch (weatherType.toLowerCase()) {
            case "rain":
                return android.R.color.holo_blue_light;
            case "snow":
                return android.R.color.holo_blue_bright;
            case "storm":
                return android.R.color.holo_purple;
            case "fog":
                return android.R.color.darker_gray;
            case "severe":
                return android.R.color.holo_red_dark;
            case "wind":
                return android.R.color.holo_orange_dark;
            default:
                return android.R.color.holo_orange_light;
        }
    }

    private int getWeatherWarningIcon(String weatherType) {
        switch (weatherType.toLowerCase()) {
            case "rain":
                return R.drawable.ic_rainy;
            case "snow":
                return R.drawable.ic_snowy;
            case "storm":
                return R.drawable.ic_windy;
            case "fog":
                return R.drawable.ic_foggy;
            case "severe":
                return R.drawable.ic_weather_severe;
            case "wind":
                return R.drawable.ic_windy;
            default:
                return R.drawable.ic_weather_unknown;
        }
    }

    private String getWeatherTypeText(String weatherType) {
        switch (weatherType.toLowerCase()) {
            case "rain":
                return "Yağmur";
            case "snow":
                return "Kar";
            case "storm":
                return "Fırtına";
            case "fog":
                return "Sis";
            case "severe":
                return "Şiddetli Hava";
            case "wind":
                return "Rüzgar";
            default:
                return "Kötü Hava";
        }
    }

    private void speakWeatherWarning(String message, String weatherType) {
        if (textToSpeech == null) return;

        String speechText = generateSpeechText(weatherType);

        textToSpeech.stop(); // Önceki konuşmayı durdur
        textToSpeech.speak(speechText, TextToSpeech.QUEUE_FLUSH, null, "WEATHER_WARNING_ID");
    }

    private String generateSpeechText(String weatherType) {
        switch (weatherType.toLowerCase()) {
            case "rain":
                return "Dikkat! Hava durumu yağmurlu. Sürüş yaparken dikkatli olun. Hızınızı düşürün.";
            case "snow":
                return "Dikkat! Hava durumu karlı. Yol buzlu ve kaygan olabilir. Çok dikkatli sürün.";
            case "storm":
                return "Dikkat! Hava durumu fırtınalı. Görüş mesafesi azalabilir. Dikkatli sürün.";
            case "fog":
                return "Dikkat! Hava durumu sisli. Görüş mesafesi azalmış. Farları yakın, hızınızı düşürün.";
            case "severe":
                return "Dikkat! Hava durumu çok kötü. Sürüş yaparken son derece dikkatli olun.";
            case "wind":
                return "Dikkat! Hava durumu rüzgarlı. Araç kontrolünde dikkatli olun.";
            default:
                return "Dikkat! Hava durumu kötü. Sürüş yaparken dikkatli olun.";
        }
    }

    // Belirli hava durumu tiplerini kontrol et
    public boolean isCurrentWeatherSevere() {
        return currentWeatherWarningType != null &&
                (currentWeatherWarningType.equals("severe") ||
                        currentWeatherWarningType.equals("storm") ||
                        currentWeatherWarningType.equals("snow"));
    }

    // Mevcut hava durumu uyarı tipini al
    public String getCurrentWeatherWarningType() {
        return currentWeatherWarningType;
    }

    // Hava durumu uyarısını manuel olarak temizle
    public void clearWeatherWarning() {
        hideWeatherWarning();
        Log.d(TAG, "Weather warning manually cleared");
    }

    // Hava durumu uyarısı cooldown süresini sıfırla
    public void resetWeatherWarningCooldown() {
        lastWeatherWarningTime = 0;
        Log.d(TAG, "Weather warning cooldown reset");
    }

    public void addKazaMarker(KazaData kazaData) {
        try {
            LatLng position = new LatLng(kazaData.y, kazaData.x);

            String title = kazaData.kazaTuru.equals("olumlu") ? "🔴 ÖLÜMLÜ KAZA" : "🟡 YARALI KAZA";
            String snippet = String.format("%s - %s\n%s\nSaat: %s:%s",
                    kazaData.ilce != null ? kazaData.ilce : "Bilinmiyor",
                    kazaData.mahalle != null ? kazaData.mahalle : "Bilinmiyor",
                    kazaData.yol != null && !kazaData.yol.isEmpty() ? kazaData.yol : "Yol bilgisi yok",
                    kazaData.saat != null ? kazaData.saat : "?",
                    kazaData.dakika != null ? kazaData.dakika : "?");

            float markerColor = kazaData.kazaTuru.equals("olumlu") ?
                    BitmapDescriptorFactory.HUE_RED :
                    BitmapDescriptorFactory.HUE_YELLOW;

            MarkerOptions markerOptions = new MarkerOptions()
                    .position(position)
                    .title(title)
                    .snippet(snippet)
                    .icon(BitmapDescriptorFactory.defaultMarker(markerColor));

            Marker marker = mMap.addMarker(markerOptions);
            if (marker != null) {
                marker.setTag(kazaData);
            }

            Log.d(TAG, String.format("%s marker eklendi: %s - %s,%s",
                    kazaData.kazaTuru.toUpperCase(), title, kazaData.x, kazaData.y));

        } catch (Exception e) {
            Log.e(TAG, kazaData.kazaTuru + " marker ekleme hatası: " + e.getMessage(), e);
        }
    }

    public void updateLocationOnMap(Location location, List<KazaData> kazaDataList) {
        this.kazaDataList = kazaDataList;
        if (location == null) return;

        currentLocation = new LatLng(location.getLatitude(), location.getLongitude());
        Log.d(TAG, "Location updated: " + currentLocation.latitude + ", " + currentLocation.longitude);

        if (userMarker != null) {
            userMarker.remove();
        }

        float markerColor = manualLocationChange ?
                BitmapDescriptorFactory.HUE_GREEN :
                BitmapDescriptorFactory.HUE_BLUE;

        String markerTitle = manualLocationChange ? "Manuel Konum" : "Benim Konumum";

        userMarker = mMap.addMarker(new MarkerOptions()
                .position(currentLocation)
                .title(markerTitle)
                .icon(BitmapDescriptorFactory.defaultMarker(markerColor)));

        if (weatherSpeedInfoManager != null) {
            weatherSpeedInfoManager.updateLocation(location, kazaDataList);
            Log.d(TAG, "WeatherSpeedInfoManager updated with new location");
        }

        checkProximityToAccidents();
    }

    public void onMapClick(LatLng latLng) {
        Log.d(TAG, "Map clicked: " + latLng.latitude + ", " + latLng.longitude);
        manualLocationChange = true;

        if (mapsActivity != null) {
            mapsActivity.setManualLocationChange(true);
            mapsActivity.stopSimulatedMovement();
        }

        Location newLocation = new Location("manual");
        newLocation.setLatitude(latLng.latitude);
        newLocation.setLongitude(latLng.longitude);

        hideWarning();
        updateLocationOnMap(newLocation, kazaDataList);

        if (weatherSpeedInfoManager != null) {
            weatherSpeedInfoManager.forceWeatherUpdate();
        }

        Toast.makeText(context, "Konum manuel olarak güncellendi: " +
                        String.format("%.6f, %.6f", latLng.latitude, latLng.longitude),
                Toast.LENGTH_SHORT).show();
    }

    private void showKazaDetails(KazaData kazaData, LatLng position) {
        if (kazaData == null) return;

        StringBuilder details = new StringBuilder();
        details.append("🏢 İlçe: ").append(kazaData.ilce).append("\n");
        details.append("🏘️ Mahalle: ").append(kazaData.mahalle).append("\n");
        details.append("🛣️ Yol: ").append(kazaData.yol).append("\n");
        details.append("⏰ Saat: ").append(kazaData.saat).append(":").append(kazaData.dakika).append("\n");

        if (kazaData.kazaTarihi != null && !kazaData.kazaTarihi.equals("Bilinmiyor")) {
            details.append("📅 Tarih: ").append(kazaData.kazaTarihi).append("\n");
        }
        if (kazaData.havaDurumu != null && !kazaData.havaDurumu.equals("Bilinmiyor")) {
            details.append("🌤️ Hava: ").append(kazaData.havaDurumu).append("\n");
        }
        if (kazaData.yasalHizLimiti != null) {
            details.append("🚗 Hız Limiti: ").append(kazaData.yasalHizLimiti).append(" km/h\n");
        }

        details.append("📍 Koordinat: ").append(String.format("%.6f, %.6f", kazaData.x, kazaData.y)).append("\n");
        details.append("⚠️ Tür: ").append(kazaData.kazaTuru.equals("olumlu") ? "Ölümlü" : "Yaralı");

        new androidx.appcompat.app.AlertDialog.Builder(context)
                .setTitle("🚨 Kaza Detayları")
                .setMessage(details.toString())
                .setPositiveButton("Tamam", null)
                .setNeutralButton("Haritada Göster", (dialog, which) -> {
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(position, 16));
                })
                .show();
    }

    private void showWarning(KazaData kaza, float distance) {
        if (warningLayout == null || mapsActivity == null) {
            Log.e(TAG, "warningLayout or mapsActivity is null!");
            return;
        }

        mapsActivity.runOnUiThread(() -> {
            warningLayout.removeAllViews();
            LayoutInflater inflater = LayoutInflater.from(context);
            View warningView = inflater.inflate(R.layout.warning_layout, warningLayout, false);

            TextView warningTextView = warningView.findViewById(R.id.warningTextView);
            ImageView closeWarning = warningView.findViewById(R.id.closeWarning);

            String currentWeather = weatherSpeedInfoManager.getCurrentWeatherDescription();
            float currentSpeed = mapsActivity.getSimulatedSpeed();
            Integer speedLimit = kaza.yasalHizLimiti;

            // 📊 Risk oranını hesapla
            double risk = WeatherSpeedInfoManager.calculateAccidentRepeatProbability(
                    currentSpeed,
                    speedLimit,
                    currentWeather,
                    kaza.havaDurumu
            );
            int riskPct = (int)(risk * 100);

            // 🎯 Risk seviyesini belirle
            RiskLevel riskLevel = getRiskLevel(riskPct);

            // 🎨 Risk seviyesine göre görsel ayarlar
            applyRiskBasedStyling(warningView, warningTextView, riskLevel, kaza);

            // 📝 Risk seviyesine göre uyarı mesajı
            String warningText = buildWarningMessage(kaza, distance, riskPct, riskLevel);
            warningTextView.setText(warningText);

            closeWarning.setOnClickListener(v -> hideWarning());
            warningLayout.addView(warningView);
            warningLayout.setVisibility(View.VISIBLE);

            Log.d(TAG, "WARNING DISPLAYED: " + warningText);

            // 🔊 Risk seviyesine göre ses ve titreşim efektleri
            applyRiskBasedEffects(riskLevel);

            // 🗣️ Sesli uyarı
            speakWarning(kaza, distance, riskPct, riskLevel);
        });
    }

    // Risk seviyesi enum'u
    private enum RiskLevel {
        LOW(0, 30, "DÜŞÜK", "💚"),
        MEDIUM(31, 60, "ORTA", "🟡"),
        HIGH(61, 80, "YÜKSEK", "🟠"),
        CRITICAL(81, 100, "KRİTİK", "🔴");

        final int minPercent;
        final int maxPercent;
        final String description;
        final String emoji;

        RiskLevel(int minPercent, int maxPercent, String description, String emoji) {
            this.minPercent = minPercent;
            this.maxPercent = maxPercent;
            this.description = description;
            this.emoji = emoji;
        }
    }

    private RiskLevel getRiskLevel(int riskPct) {
        for (RiskLevel level : RiskLevel.values()) {
            if (riskPct >= level.minPercent && riskPct <= level.maxPercent) {
                return level;
            }
        }
        return RiskLevel.LOW;
    }

    private void applyRiskBasedStyling(View warningView, TextView warningTextView, RiskLevel riskLevel, KazaData kaza) {
        int warningColor;
        int textColor = Color.WHITE;

        switch (riskLevel) {
            case LOW:
                warningColor = kaza.kazaTuru.equals("olumlu") ?
                        ContextCompat.getColor(context, android.R.color.holo_red_light) :
                        ContextCompat.getColor(context, android.R.color.holo_orange_light);
                break;
            case MEDIUM:
                warningColor = Color.parseColor("#FF8C00"); // Koyu turuncu
                break;
            case HIGH:
                warningColor = Color.parseColor("#FF4500"); // Kırmızı-turuncu
                // Yanıp sönme animasyonu
                startBlinkingAnimation(warningView);
                break;
            case CRITICAL:
                warningColor = Color.parseColor("#DC143C"); // Crimson kırmızı
                textColor = Color.YELLOW;
                // Hızlı yanıp sönme + büyüme animasyonu
                startCriticalAnimation(warningView);
                break;
            default:
                warningColor = ContextCompat.getColor(context, android.R.color.holo_orange_light);
        }

        warningView.setBackgroundColor(warningColor);
        warningTextView.setTextColor(textColor);

        // Kritik seviyede kalın yazı
        if (riskLevel == RiskLevel.CRITICAL || riskLevel == RiskLevel.HIGH) {
            warningTextView.setTypeface(null, Typeface.BOLD);
            warningTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        }
    }

    private String buildWarningMessage(KazaData kaza, float distance, int riskPct, RiskLevel riskLevel) {
        String urgencyPrefix = getUrgencyPrefix(riskLevel);

        String warningText = urgencyPrefix + " " +
                (kaza.kazaTuru.equals("olumlu") ? "ÖLÜMLÜ" : "YARALI") +
                " kaza!\nMesafe: " + Math.round(distance) + "m\n" +
                "Konum: " + kaza.ilce + " - " + kaza.mahalle;

        if (kaza.havaDurumu != null && !kaza.havaDurumu.equals("Bilinmiyor")) {
            warningText += "\n🌤️ Hava: " + kaza.havaDurumu;
        }
        if (kaza.yasalHizLimiti != null) {
            warningText += "\n🚗 Hız Limiti: " + kaza.yasalHizLimiti + " km/h";
        }

        // Risk seviyesine göre özel mesaj
        warningText += "\n📊 Tekrar Riski: " + riskLevel.emoji + " %" + riskPct + " (" + riskLevel.description + ")";

        if (riskLevel == RiskLevel.CRITICAL) {
            warningText += "\n⚠️ ACIL DURUM: Aşırı yüksek risk!";
        } else if (riskLevel == RiskLevel.HIGH) {
            warningText += "\n⚠️ Çok dikkatli olun!";
        }

        return warningText;
    }

    private String getUrgencyPrefix(RiskLevel riskLevel) {
        switch (riskLevel) {
            case LOW:
                return "⚠️ DİKKAT:";
            case MEDIUM:
                return "⚠️ UYARI:";
            case HIGH:
                return "🚨 YÜKSEK RİSK:";
            case CRITICAL:
                return "🚨 KRİTİK UYARI:";
            default:
                return "⚠️ UYARI:";
        }
    }

    private void applyRiskBasedEffects(RiskLevel riskLevel) {
        // Vibrator servisini al
        Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);

        if (vibrator != null && vibrator.hasVibrator()) {
            switch (riskLevel) {
                case LOW:
                    // Hafif tek titreşim
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE));
                    } else {
                        vibrator.vibrate(300);
                    }
                    break;

                case MEDIUM:
                    // Çift titreşim
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        long[] pattern = {0, 200, 100, 200};
                        vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1));
                    } else {
                        long[] pattern = {0, 200, 100, 200};
                        vibrator.vibrate(pattern, -1);
                    }
                    break;

                case HIGH:
                    // Güçlü tekrarlayan titreşim
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        long[] pattern = {0, 150, 50, 150, 50, 150};
                        vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1));
                    } else {
                        long[] pattern = {0, 150, 50, 150, 50, 150};
                        vibrator.vibrate(pattern, -1);
                    }
                    break;

                case CRITICAL:
                    // Çok güçlü alarm benzeri titreşim
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        long[] pattern = {0, 100, 50, 100, 50, 100, 50, 100, 50, 100};
                        vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1));
                    } else {
                        long[] pattern = {0, 100, 50, 100, 50, 100, 50, 100, 50, 100};
                        vibrator.vibrate(pattern, -1);
                    }
                    break;
            }
        }

        // Ses efektleri
        playWarningSound(riskLevel);
    }

    private void playWarningSound(RiskLevel riskLevel) {
        try {
            MediaPlayer mediaPlayer = new MediaPlayer();
            android.net.Uri soundUri;

            switch (riskLevel) {
                case LOW:
                    // Hafif bildirim sesi
                    soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
                    break;

                case MEDIUM:
                    // Alarm sesi
                    soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
                    break;

                case HIGH:
                case CRITICAL:
                    // En yüksek seviye alarm
                    soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
                    break;
                default:
                    soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            }

            mediaPlayer.setDataSource(context, soundUri);
            mediaPlayer.setAudioStreamType(AudioManager.STREAM_ALARM);
            mediaPlayer.prepare();
            mediaPlayer.start();

            // Ses süresini risk seviyesine göre ayarla
            int duration = riskLevel == RiskLevel.CRITICAL ? 3000 :
                    riskLevel == RiskLevel.HIGH ? 2000 : 1000;

            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (mediaPlayer.isPlaying()) {
                        mediaPlayer.stop();
                    }
                    mediaPlayer.release();
                }
            }, duration);

        } catch (Exception e) {
            Log.e(TAG, "Ses çalınırken hata: " + e.getMessage());
        }
    }

    private void startBlinkingAnimation(View view) {
        Animation blinkAnimation = new AlphaAnimation(0.3f, 1.0f);
        blinkAnimation.setDuration(500);
        blinkAnimation.setRepeatMode(Animation.REVERSE);
        blinkAnimation.setRepeatCount(5);
        view.startAnimation(blinkAnimation);
    }

    private void startCriticalAnimation(View view) {
        // Yanıp sönme + büyüme animasyonu
        AnimationSet animationSet = new AnimationSet(true);

        // Yanıp sönme
        AlphaAnimation alphaAnimation = new AlphaAnimation(0.2f, 1.0f);
        alphaAnimation.setDuration(300);
        alphaAnimation.setRepeatMode(Animation.REVERSE);
        alphaAnimation.setRepeatCount(8);

        // Büyüme-küçülme
        ScaleAnimation scaleAnimation = new ScaleAnimation(
                1.0f, 1.05f, 1.0f, 1.05f,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.5f
        );
        scaleAnimation.setDuration(300);
        scaleAnimation.setRepeatMode(Animation.REVERSE);
        scaleAnimation.setRepeatCount(8);

        animationSet.addAnimation(alphaAnimation);
        animationSet.addAnimation(scaleAnimation);
        view.startAnimation(animationSet);
    }

    private void speakWarning(KazaData kaza, float distance, int riskPct, RiskLevel riskLevel) {
        if (textToSpeech == null) return;

        String ilce = getPhoneticText(kaza.ilce);
        String mahalle = getPhoneticText(kaza.mahalle);

        // Risk seviyesine göre konuşma hızı ve ses tonu
        float speechRate = getSpeechRate(riskLevel);
        float pitch = getPitch(riskLevel);

        String urgencyWord = getUrgencyWord(riskLevel);

        String speechText = urgencyWord + "! Yakınlarda " +
                (kaza.kazaTuru.equals("olumlu") ? "ölümlü" : "yaralanmalı") +
                " bir kaza var. Mesafe yaklaşık " + Math.round(distance) + " metre. " +
                ilce + " ilçesi, " + mahalle + " mahallesi. " +
                "Kaza tekrar riski yüzde " + riskPct + ". " + riskLevel.description + " seviye.";

        if (riskLevel == RiskLevel.CRITICAL) {
            speechText += " Acil durum! Aşırı dikkatli olun!";
        } else if (riskLevel == RiskLevel.HIGH) {
            speechText += " Çok dikkatli sürün!";
        }

        textToSpeech.setSpeechRate(speechRate);
        textToSpeech.setPitch(pitch);
        textToSpeech.stop();
        textToSpeech.speak(speechText, TextToSpeech.QUEUE_FLUSH, null, "UYARI_ID");
    }

    private float getSpeechRate(RiskLevel riskLevel) {
        switch (riskLevel) {
            case LOW: return 0.8f;      // Yavaş
            case MEDIUM: return 1.0f;   // Normal
            case HIGH: return 1.2f;     // Hızlı
            case CRITICAL: return 1.4f; // Çok hızlı
            default: return 1.0f;
        }
    }

    private float getPitch(RiskLevel riskLevel) {
        switch (riskLevel) {
            case LOW: return 0.9f;      // Düşük ton
            case MEDIUM: return 1.0f;   // Normal ton
            case HIGH: return 1.1f;     // Yüksek ton
            case CRITICAL: return 1.3f; // Çok yüksek ton
            default: return 1.0f;
        }
    }

    private String getUrgencyWord(RiskLevel riskLevel) {
        switch (riskLevel) {
            case LOW: return "Dikkat";
            case MEDIUM: return "Uyarı";
            case HIGH: return "Tehlike";
            case CRITICAL: return "Acil durum";
            default: return "Dikkat";
        }
    }

    private String getPhoneticText(String text) {
        if (text == null) return "";

        // İlçe adları (büyük/küçük harf duyarsız)
        text = text.replaceAll("(?i)kocasinan", "Ko-ca-si-nan");
        text = text.replaceAll("(?i)melikgazi", "Me-lik-ga-zi");
        text = text.replaceAll("(?i)talas", "Ta-las");

        // Mahalle adları
        text = text.replaceAll("(?i)yeniköy", "Ye-ni-köy");
        text = text.replaceAll("(?i)esentepe", "E-sen-te-pe");
        text = text.replaceAll("(?i)fevzi çakmak", "Fev-zi Çak-mak");
        text = text.replaceAll("(?i)ismet paşa", "İs-met Pa-şa");
        text = text.replaceAll("(?i)yıldırım beyazıt", "Yıl-dı-rım Be-ya-zıt");
        text = text.replaceAll("(?i)erciyes", "Er-ci-yes");
        text = text.replaceAll("(?i)zümrüt", "Züm-rüt");
        text = text.replaceAll("(?i)bahçelievler", "Bah-çe-li-ev-ler");
        text = text.replaceAll("(?i)anbar", "An-bar");
        text = text.replaceAll("(?i)eki̇nli̇k", "E-kin-lik");
        text = text.replaceAll("(?i)gültepe", "Gül-te-pe");
        text = text.replaceAll("(?i)sanayi", "Sa-na-yi");
        text = text.replaceAll("(?i)kayabasi", "Ka-ya-ba-şı");

        // Tire yerine duraklama için virgül
        text = text.replaceAll(" - ", ", ");

        return text;
    }

    public void hideWarning() {
        if (warningLayout != null) {
            warningLayout.setVisibility(View.GONE);
        }
    }

    public void shutdownTextToSpeech() {
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
            textToSpeech = null;
        }
        // Hava durumu uyarısını da gizle
        hideWeatherWarning();
    }

    public void checkProximityToAccidents() {
        if (currentLocation == null || kazaDataList == null || kazaDataList.isEmpty()) {
            Log.d(TAG, "checkProximityToAccidents: currentLocation or kazaDataList is null or empty.");
            return;
        }

        for (KazaData kaza : kazaDataList) {
            float[] results = new float[1];
            Location.distanceBetween(currentLocation.latitude, currentLocation.longitude,
                    kaza.y, kaza.x, results);

            float distance = results[0];
            if (distance <= PROXIMITY_THRESHOLD_METERS) {
                showWarning(kaza, distance);
                return;
            }
        }
    }

    public void testProximitySystem() {
        if (currentLocation != null && kazaDataList != null && !kazaDataList.isEmpty()) {
            for (int i = 0; i < Math.min(5, kazaDataList.size()); i++) {
                KazaData kaza = kazaDataList.get(i);
                float[] results = new float[1];
                Location.distanceBetween(currentLocation.latitude, currentLocation.longitude,
                        kaza.y, kaza.x, results);
                Log.d(TAG, "Accident " + i + " at " + kaza.ilce + ": " + results[0] + "m");
            }
        }
    }
}