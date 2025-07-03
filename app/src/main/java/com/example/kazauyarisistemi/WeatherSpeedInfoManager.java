package com.example.kazauyarisistemi;

import android.content.Context;
import android.location.Location;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;
import java.util.Locale;

public class WeatherSpeedInfoManager {
    private static final String TAG = "WeatherSpeedInfoManager";
    private static final double PROXIMITY_THRESHOLD_KM = 0.1; // 100 metre yakınlık
    private static final String OPENWEATHERMAP_API_KEY = "c2754d862006a4017a037e2c8f03ef7d";
    private static final long MIN_UPDATE_INTERVAL = 10000; // 10 saniye minimum güncelleme aralığı (düşürüldü)
    private static final double MIN_DISTANCE_FOR_UPDATE = 0.5; // 500 metre minimum mesafe (düşürüldü)

    private Context context;
    private ImageView weatherIcon;
    private TextView weatherText;
    private ImageView speedIcon;
    private TextView speedText;
    private View infoPanel;
    private List<KazaData> kazaDataList;
    private Location currentLocation;
    private RequestQueue requestQueue;
    private String currentWeatherDescription = "Bilinmiyor";

    // Cache için değişkenler
    private double lastWeatherLat = Double.NaN;
    private double lastWeatherLon = Double.NaN;
    private long lastWeatherUpdateTime = 0;
    private boolean isWeatherUpdateInProgress = false;

    // Manuel konum takibi için yeni değişken
    private boolean isManualLocation = false;

    public WeatherSpeedInfoManager(Context context, View infoPanel,
                                   ImageView weatherIcon, TextView weatherText,
                                   ImageView speedIcon, TextView speedText) {
        this.context = context;
        this.infoPanel = infoPanel;
        this.weatherIcon = weatherIcon;
        this.weatherText = weatherText;
        this.speedIcon = speedIcon;
        this.speedText = speedText;
        this.requestQueue = Volley.newRequestQueue(context);

        // Başlangıç durumu
        updateWeatherInfo("Bilinmiyor");
        updateSpeedInfo(null);
    }

    // Harita üzerinde seçilen konum için özel method
    public void updateLocationFromMapSelection(double latitude, double longitude, List<KazaData> kazaDataList) {
        this.kazaDataList = kazaDataList;
        this.isManualLocation = true; // Manuel konum olduğunu işaretle

        // Yeni konum oluştur
        Location newLocation = new Location("map_selection");
        newLocation.setLatitude(latitude);
        newLocation.setLongitude(longitude);
        this.currentLocation = newLocation;

        Log.d(TAG, "=== MAP SELECTION UPDATE ===");
        Log.d(TAG, "Selected coordinates: " + latitude + ", " + longitude);
        Log.d(TAG, "Manual location flag set to: " + isManualLocation);

        // Harita seçiminde her zaman hava durumunu güncelle
        forceWeatherUpdate();
        updateSpeedInfoBasedOnLocation();
    }

    public void updateLocation(Location location, List<KazaData> kazaDataList) {
        this.currentLocation = location;
        this.kazaDataList = kazaDataList;
        this.isManualLocation = false; // GPS konumu olduğunu işaretle

        if (location != null) {
            Log.d(TAG, "=== GPS LOCATION UPDATE ===");
            Log.d(TAG, "New coordinates: " + location.getLatitude() + ", " + location.getLongitude());
            Log.d(TAG, "Manual location flag set to: " + isManualLocation);

            // GPS lokasyonu için normal cache kontrolü
            boolean shouldUpdateWeather = shouldUpdateWeather(location);
            Log.d(TAG, "Should update weather: " + shouldUpdateWeather);

            if (shouldUpdateWeather) {
                getWeatherData(location.getLatitude(), location.getLongitude());
            } else {
                Log.d(TAG, "Weather update skipped due to cache/timing");
            }

            updateSpeedInfoBasedOnLocation();
        }
    }

    private boolean shouldUpdateWeather(Location location) {
        // Eğer weather update devam ediyorsa bekleme
        if (isWeatherUpdateInProgress) {
            Log.d(TAG, "Weather update already in progress, skipping");
            return false;
        }

        long currentTime = System.currentTimeMillis();
        double lat = location.getLatitude();
        double lon = location.getLongitude();

        // İlk çağrı ise güncelle
        if (Double.isNaN(lastWeatherLat) || Double.isNaN(lastWeatherLon)) {
            Log.d(TAG, "First weather update");
            return true;
        }

        // Minimum güncelleme aralığı kontrolü
        if (currentTime - lastWeatherUpdateTime < MIN_UPDATE_INTERVAL) {
            Log.d(TAG, "Too soon for weather update. Last update: " +
                    (currentTime - lastWeatherUpdateTime) + "ms ago");
            return false;
        }

        // Konum farkı kontrolü (minimum mesafe değişikliği)
        double distance = calculateDistance(lastWeatherLat, lastWeatherLon, lat, lon);
        Log.d(TAG, "Distance from last weather location: " + distance + "km");

        if (distance > MIN_DISTANCE_FOR_UPDATE) {
            return true;
        }

        // Eğer hava durumu hala "Bilinmiyor" ise tekrar dene
        if (currentWeatherDescription.equals("Bilinmiyor") ||
                currentWeatherDescription.contains("Hata") ||
                currentWeatherDescription.contains("API")) {
            Log.d(TAG, "Weather status is unknown/error, forcing update");
            return true;
        }

        return false;
    }

    private void updateSpeedInfoBasedOnLocation() {
        if (currentLocation == null) return;

        KazaData nearestKaza = findNearestAccident();

        if (nearestKaza != null) {
            updateSpeedInfo(nearestKaza.yasalHizLimiti);
        } else {
            updateSpeedInfo(simulateSpeedLimitForLocation());
        }

        infoPanel.setVisibility(View.VISIBLE);
    }

    private KazaData findNearestAccident() {
        if (currentLocation == null || kazaDataList == null) return null;

        KazaData nearest = null;
        double minDistance = Double.MAX_VALUE;

        for (KazaData kaza : kazaDataList) {
            float[] results = new float[1];
            Location.distanceBetween(
                    currentLocation.getLatitude(), currentLocation.getLongitude(),
                    kaza.y, kaza.x, results
            );

            double distanceKm = results[0] / 1000.0;

            if (distanceKm <= PROXIMITY_THRESHOLD_KM && distanceKm < minDistance) {
                minDistance = distanceKm;
                nearest = kaza;
            }
        }

        return nearest;
    }

    private Integer simulateSpeedLimitForLocation() {
        Integer[] speedLimits = {30, 50, 70, 90, 110, 120};

        if (currentLocation != null) {
            double distanceFromCenter = calculateDistance(
                    currentLocation.getLatitude(), currentLocation.getLongitude(),
                    38.7312, 35.4787 // Kayseri merkezi koordinatları
            );

            if (distanceFromCenter < 5) {
                return speedLimits[new java.util.Random().nextInt(3)];
            } else if (distanceFromCenter < 15) {
                return speedLimits[3 + new java.util.Random().nextInt(2)];
            } else {
                return speedLimits[5];
            }
        }

        return 50;
    }

    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371;
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    private void getWeatherData(double latitude, double longitude) {
        // Eğer zaten bir weather update devam ediyorsa iptal et
        if (isWeatherUpdateInProgress) {
            Log.d(TAG, "Weather update already in progress, ignoring new request");
            return;
        }

        isWeatherUpdateInProgress = true;

        // Loading durumunu göster
        updateWeatherInfo("Yükleniyor...");

        // Timestamp ekle
        long timestamp = System.currentTimeMillis();

        // API URL'sini koordinatlar ile oluştur
        String url = String.format(Locale.US,
                "https://api.openweathermap.org/data/2.5/weather?lat=%.6f&lon=%.6f&appid=%s&units=metric&lang=tr&_=%d",
                latitude, longitude, OPENWEATHERMAP_API_KEY, timestamp);

        Log.d(TAG, "=== WEATHER API CALL ===");
        Log.d(TAG, "Request URL: " + url);
        Log.d(TAG, "Requested coordinates: " + latitude + ", " + longitude);

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, url, null,
                response -> {
                    isWeatherUpdateInProgress = false;

                    Log.d(TAG, "=== WEATHER API RESPONSE ===");
                    Log.d(TAG, "Full response: " + response.toString());

                    try {
                        // Koordinat bilgilerini logla
                        if (response.has("coord")) {
                            JSONObject coord = response.getJSONObject("coord");
                            double responseLat = coord.getDouble("lat");
                            double responseLon = coord.getDouble("lon");
                            Log.d(TAG, "API Response Coordinates: " + responseLat + ", " + responseLon);

                            // Cache değerlerini güncelle
                            lastWeatherLat = responseLat;
                            lastWeatherLon = responseLon;
                            lastWeatherUpdateTime = System.currentTimeMillis();
                        }

                        // Hava durumu bilgisini al
                        JSONArray weatherArray = response.getJSONArray("weather");
                        JSONObject weatherObject = weatherArray.getJSONObject(0);
                        String weatherDescription = weatherObject.getString("description");
                        String weatherMain = weatherObject.getString("main");
                        String weatherId = weatherObject.getString("id");

                        // Şehir bilgisini al
                        String cityName = response.optString("name", "Bilinmiyor");

                        // Sıcaklık bilgisini al
                        double temperature = response.getJSONObject("main").getDouble("temp");

                        Log.d(TAG, "Weather data received:");
                        Log.d(TAG, "  City: " + cityName);
                        Log.d(TAG, "  Main: " + weatherMain);
                        Log.d(TAG, "  Description: " + weatherDescription);
                        Log.d(TAG, "  Temperature: " + temperature);

                        // Türkçe çeviri ile güncelle
                        String translatedWeather = translateWeatherToTurkish(weatherMain, weatherDescription);
                        String weatherWithTemp = translatedWeather + " (" + Math.round(temperature) + "°C)";
                        currentWeatherDescription = weatherWithTemp;

                        Log.d(TAG, "Translated weather: " + weatherWithTemp);
                        updateWeatherInfo(weatherWithTemp);

                        // Manuel konum mu GPS konum mu olduğuna göre farklı mesaj
                        String locationSource = isManualLocation ? "Manuel konum" : "GPS konum";
                        Toast.makeText(context, locationSource + " - Hava durumu güncellendi: " + weatherWithTemp +
                                " - " + cityName, Toast.LENGTH_SHORT).show();

                    } catch (JSONException e) {
                        Log.e(TAG, "JSON parsing error: " + e.getMessage());
                        Log.e(TAG, "Response was: " + response.toString());
                        currentWeatherDescription = "Ayrıştırma Hatası";
                        updateWeatherInfo("Ayrıştırma Hatası");
                    }
                },
                error -> {
                    isWeatherUpdateInProgress = false;

                    String errorMessage = "API Hatası";

                    Log.e(TAG, "=== WEATHER API ERROR ===");
                    Log.e(TAG, "Error message: " + error.getMessage());

                    if (error.networkResponse != null) {
                        errorMessage += " (Kod: " + error.networkResponse.statusCode + ")";
                        Log.e(TAG, "Status code: " + error.networkResponse.statusCode);

                        if (error.networkResponse.data != null) {
                            String responseBody = new String(error.networkResponse.data);
                            Log.e(TAG, "Error response body: " + responseBody);
                        }
                    }

                    if (error.getCause() != null) {
                        Log.e(TAG, "Error cause: " + error.getCause().getMessage());
                    }

                    Log.e(TAG, "Failed coordinates: " + latitude + ", " + longitude);

                    currentWeatherDescription = errorMessage;
                    updateWeatherInfo(errorMessage);

                    Toast.makeText(context, "Hava durumu alınamadı: " + errorMessage,
                            Toast.LENGTH_LONG).show();
                });

        // Request timeout ayarları
        request.setRetryPolicy(new com.android.volley.DefaultRetryPolicy(
                10000, // 10 saniye timeout
                1, // 1 retry
                1.0f // backoff multiplier
        ));

        requestQueue.add(request);
        Log.d(TAG, "Weather request added to queue");
    }

    private String translateWeatherToTurkish(String main, String description) {
        // Ana hava durumu kategorilerine göre Türkçe çeviri
        switch (main.toLowerCase()) {
            case "clear":
                return "Açık";
            case "clouds":
                if (description.contains("few")) return "Az Bulutlu";
                if (description.contains("scattered")) return "Parçalı Bulutlu";
                if (description.contains("broken") || description.contains("overcast")) return "Çok Bulutlu";
                return "Bulutlu";
            case "rain":
                if (description.contains("light")) return "Hafif Yağmur";
                if (description.contains("heavy")) return "Şiddetli Yağmur";
                if (description.contains("shower")) return "Sağanak";
                return "Yağmurlu";
            case "drizzle":
                return "Çisenti";
            case "thunderstorm":
                return "Gök Gürültülü Fırtına";
            case "snow":
                if (description.contains("light")) return "Hafif Kar";
                if (description.contains("heavy")) return "Yoğun Kar";
                return "Karlı";
            case "mist":
            case "fog":
                return "Sisli";
            case "haze":
                return "Puslu";
            case "dust":
                return "Tozlu";
            case "sand":
                return "Kum Fırtınası";
            case "smoke":
                return "Dumanlı";
            default:
                // API'den gelen Türkçe açıklama varsa onu kullan
                return description.substring(0, 1).toUpperCase() + description.substring(1);
        }
    }

    private void updateWeatherInfo(String weather) {
        int weatherIconRes = getWeatherIcon(weather);
        weatherIcon.setImageResource(weatherIconRes);

        weatherText.setText(weather);

        int weatherColor = getWeatherColor(weather);
        weatherIcon.setColorFilter(ContextCompat.getColor(context, weatherColor));

        Log.d(TAG, "UI updated with weather: " + weather);
    }

    private void updateSpeedInfo(Integer speedLimit) {
        if (speedLimit == null || speedLimit <= 0) {
            speedLimit = simulateSpeedLimitForLocation();
        }

        speedIcon.setImageResource(R.drawable.ic_speed_limit);
        speedText.setText(speedLimit + " km/h");

        int speedColor = getSpeedLimitColor(speedLimit);
        speedIcon.setColorFilter(ContextCompat.getColor(context, speedColor));
        speedText.setTextColor(ContextCompat.getColor(context, speedColor));

        Log.d(TAG, "Hız limiti güncellendi: " + speedLimit + " km/h");
    }

    private int getWeatherIcon(String weather) {
        String lowerWeather = weather.toLowerCase();
        if (lowerWeather.contains("açık") || lowerWeather.contains("güneş")) {
            return R.drawable.ic_sunny;
        } else if (lowerWeather.contains("bulut")) {
            return R.drawable.ic_cloudy;
        } else if (lowerWeather.contains("yağmur") || lowerWeather.contains("sağanak") || lowerWeather.contains("çisenti")) {
            return R.drawable.ic_rainy;
        } else if (lowerWeather.contains("sis") || lowerWeather.contains("pus")) {
            return R.drawable.ic_foggy;
        } else if (lowerWeather.contains("kar")) {
            return R.drawable.ic_snowy;
        } else if (lowerWeather.contains("fırtına") || lowerWeather.contains("rüzgar")) {
            return R.drawable.ic_windy;
        } else {
            return R.drawable.ic_weather_unknown;
        }
    }

    private int getWeatherColor(String weather) {
        String lowerWeather = weather.toLowerCase();
        if (lowerWeather.contains("açık") || lowerWeather.contains("güneş")) {
            return android.R.color.holo_orange_light;
        } else if (lowerWeather.contains("bulut")) {
            return android.R.color.holo_blue_light;
        } else if (lowerWeather.contains("yağmur") || lowerWeather.contains("sağanak")) {
            return android.R.color.holo_blue_dark;
        } else if (lowerWeather.contains("sis") || lowerWeather.contains("pus")) {
            return android.R.color.darker_gray;
        } else if (lowerWeather.contains("kar")) {
            return android.R.color.white;
        } else if (lowerWeather.contains("fırtına")) {
            return android.R.color.holo_purple;
        } else {
            return android.R.color.holo_green_light;
        }
    }

    private int getSpeedLimitColor(Integer speedLimit) {
        if (speedLimit <= 30) {
            return android.R.color.holo_green_dark;
        } else if (speedLimit <= 50) {
            return android.R.color.holo_orange_light;
        } else if (speedLimit <= 90) {
            return android.R.color.holo_red_light;
        } else {
            return android.R.color.holo_red_dark;
        }
    }

    public void toggleInfoPanel() {
        if (infoPanel.getVisibility() == View.VISIBLE) {
            infoPanel.setVisibility(View.GONE);
        } else {
            infoPanel.setVisibility(View.VISIBLE);
        }
    }

    public void showDetailedInfo() {
        if (currentLocation == null) {
            Toast.makeText(context, "Konum bilgisi alınamadı", Toast.LENGTH_SHORT).show();
            return;
        }

        KazaData nearestKaza = findNearestAccident();
        StringBuilder infoText = new StringBuilder();

        infoText.append("📍 Mevcut Konum Bilgileri:\n\n");
        infoText.append("🌤️ Anlık Hava Durumu: ").append(currentWeatherDescription).append("\n");
        infoText.append("📍 Koordinat: ").append(String.format("%.6f, %.6f",
                currentLocation.getLatitude(), currentLocation.getLongitude())).append("\n");
        infoText.append("🎯 Konum Tipi: ").append(isManualLocation ? "Manuel Seçim" : "GPS Konumu").append("\n");

        if (nearestKaza != null) {
            infoText.append("\n📍 Yakın Kaza Bilgileri:\n\n");
            infoText.append("🏢 İlçe: ").append(nearestKaza.ilce).append("\n");
            infoText.append("🏘️ Mahalle: ").append(nearestKaza.mahalle).append("\n");
            infoText.append("🌩️ Kaza Anı Hava Durumu: ").append(nearestKaza.havaDurumu).append("\n");
            infoText.append("🚗 Hız Limiti: ").append(nearestKaza.yasalHizLimiti != null
                    ? nearestKaza.yasalHizLimiti + " km/h" : "Belirtilmemiş").append("\n");
            infoText.append("⚠️ Kaza Türü: ").append(nearestKaza.kazaTuru.equals("olumlu")
                    ? "Ölümlü" : "Yaralı").append("\n");
        } else {
            infoText.append("\nℹ️ Yakında kaza verisi bulunmuyor\n");
            infoText.append("🚗 Tahmini Hız Limiti: ").append(simulateSpeedLimitForLocation()).append(" km/h\n");
        }

        new androidx.appcompat.app.AlertDialog.Builder(context)
                .setTitle("🌍 Konum Bilgileri")
                .setMessage(infoText.toString())
                .setPositiveButton("Tamam", null)
                .show();
    }

    // Manuel hava durumu güncellemesi için method
    public void forceWeatherUpdate() {
        if (currentLocation != null) {
            Log.d(TAG, "FORCED WEATHER UPDATE");
            lastWeatherUpdateTime = 0; // Cache'i sıfırla
            isWeatherUpdateInProgress = false; // Progress flag'i sıfırla
            getWeatherData(currentLocation.getLatitude(), currentLocation.getLongitude());
        }
    }

    // Cache'i temizle
    public void clearWeatherCache() {
        lastWeatherLat = Double.NaN;
        lastWeatherLon = Double.NaN;
        lastWeatherUpdateTime = 0;
        isWeatherUpdateInProgress = false;
        currentWeatherDescription = "Bilinmiyor";
        updateWeatherInfo("Bilinmiyor");
    }

    // Manuel konum durumunu manuel olarak set etmek için method
    public void setManualLocation(boolean isManual) {
        this.isManualLocation = isManual;
        Log.d(TAG, "Manual location flag manually set to: " + isManual);
    }

    // Manuel konum durumunu kontrol etmek için getter
    public boolean isManualLocation() {
        return isManualLocation;
    }
}