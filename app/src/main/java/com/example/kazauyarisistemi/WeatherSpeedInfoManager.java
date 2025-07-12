package com.example.kazauyarisistemi;

import static com.example.kazauyarisistemi.MapManager.determineWeatherType;

import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.os.Build;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;
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
    private static final double PROXIMITY_THRESHOLD_KM = 0.1;
    private static final String OPENWEATHERMAP_API_KEY = "c2754d862006a4017a037e2c8f03ef7d";
    private static final long MIN_UPDATE_INTERVAL = 10000;
    private static final double MIN_DISTANCE_FOR_UPDATE = 0.5;

    // Bildirim için sabitler
    private static final String CHANNEL_ID = "WEATHER_ALERT_CHANNEL";
    private static final String CHANNEL_NAME = "Hava Durumu Uyarıları";
    private static final String CHANNEL_DESCRIPTION = "Ekstrem hava durumu uyarıları";
    private static final int NOTIFICATION_ID = 1001;

    private Context context;
    private ImageView weatherIcon;
    private TextView weatherText;
    private ImageView speedIcon;
    private TextView speedText;
    private View infoPanel;
    private List<KazaData> kazaDataList;
    private Location currentLocation;
    private RequestQueue requestQueue;
    private static String currentWeatherDescription = "Bilinmiyor";
    private NotificationManager notificationManager;

    // Cache için değişkenler
    private double lastWeatherLat = Double.NaN;
    private double lastWeatherLon = Double.NaN;
    private long lastWeatherUpdateTime = 0;
    private boolean isWeatherUpdateInProgress = false;
    private boolean isManualLocation = false;

    // Son bildirim zamanını takip etmek için
    private long lastNotificationTime = 0;
    private static final long NOTIFICATION_COOLDOWN = 300000; // 5 dakika

    private MapManager mapManager;
    private WeatherWarningListener weatherWarningListener;

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
        this.notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        // Bildirim kanalını oluştur
        createNotificationChannel();

        // Başlangıç durumu
        updateWeatherInfo("Bilinmiyor");
        updateSpeedInfo(null);
    }

    // Bildirim kanalını oluştur
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription(CHANNEL_DESCRIPTION);
            channel.enableLights(true);
            channel.enableVibration(true);
            channel.setLightColor(android.graphics.Color.RED);
            notificationManager.createNotificationChannel(channel);
        }
    }

    // Ekstrem hava durumu kontrolü ve bildirim gönderme
    private void checkAndNotifyExtremeWeather(String weatherDescription) {
        if (weatherDescription == null || weatherDescription.equals("Bilinmiyor")) {
            return;
        }

        // Bildirim cooldown kontrolü
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastNotificationTime < NOTIFICATION_COOLDOWN) {
            Log.d(TAG, "Notification cooldown active, skipping notification");
            return;
        }

        String extremeWeatherType = getExtremeWeatherType(weatherDescription);
        if (extremeWeatherType != null) {
            sendWeatherNotification(extremeWeatherType, weatherDescription);
            lastNotificationTime = currentTime;
        }
    }

    // Ekstrem hava durumu tipini belirle
    private String getExtremeWeatherType(String description) {
        if (description == null) return null;

        String lower = description.toLowerCase();

        // Şiddetli fırtına
        if (lower.contains("şiddetli fırtına") || lower.contains("gök gürültülü fırtına") ||
                lower.contains("thunderstorm")) {
            return "FIRTINA";
        }
        // Yoğun kar
        else if (lower.contains("yoğun kar") || lower.contains("şiddetli kar") ||
                lower.contains("heavy snow")) {
            return "YOGUN_KAR";
        }
        // Şiddetli yağmur
        else if (lower.contains("şiddetli yağmur") || lower.contains("heavy rain") ||
                lower.contains("sağanak")) {
            return "SIDDETLI_YAGMUR";
        }
        // Yoğun sis
        else if (lower.contains("yoğun sis") || lower.contains("dense fog")) {
            return "YOGUN_SIS";
        }
        // Kum fırtınası
        else if (lower.contains("kum fırtınası") || lower.contains("dust storm") ||
                lower.contains("sand storm")) {
            return "KUM_FIRTINASI";
        }
        // Hafif ekstrem durumlar
        else if (lower.contains("yağmur") || lower.contains("kar") || lower.contains("sis") ||
                lower.contains("fırtına")) {
            return "HAFIF_EKSTREM";
        }

        return null;
    }

    // Hava durumu bildirimi gönder
    private void sendWeatherNotification(String weatherType, String weatherDescription) {
        String title = "";
        String message = "";
        int iconResource = R.drawable.ic_weather_unknown;
        int priority = NotificationCompat.PRIORITY_DEFAULT;

        switch (weatherType) {
            case "FIRTINA":
                title = "⚠️ ŞİDDETLİ FIRTINA UYARISI";
                message = "Hava durumu: " + weatherDescription +
                        "\nDışarı çıkmayın! Sürüş yapmayın! Güvenli bir yerde kalın.";
                iconResource = R.drawable.ic_windy;
                priority = NotificationCompat.PRIORITY_MAX;
                break;

            case "YOGUN_KAR":
                title = "❄️ YOĞUN KAR UYARISI";
                message = "Hava durumu: " + weatherDescription +
                        "\nYollar buzlu ve tehlikeli! Sürüş yapmaktan kaçının.";
                iconResource = R.drawable.ic_snowy;
                priority = NotificationCompat.PRIORITY_HIGH;
                break;

            case "SIDDETLI_YAGMUR":
                title = "🌧️ ŞİDDETLİ YAĞMUR UYARISI";
                message = "Hava durumu: " + weatherDescription +
                        "\nSel riski var! Hızınızı düşürün, dikkatli sürün.";
                iconResource = R.drawable.ic_rainy;
                priority = NotificationCompat.PRIORITY_HIGH;
                break;

            case "YOGUN_SIS":
                title = "🌫️ YOĞUN SIS UYARISI";
                message = "Hava durumu: " + weatherDescription +
                        "\nGörüş mesafesi çok düşük! Farları yakın, yavaş sürün.";
                iconResource = R.drawable.ic_foggy;
                priority = NotificationCompat.PRIORITY_HIGH;
                break;

            case "KUM_FIRTINASI":
                title = "🌪️ KUM FIRTINASI UYARISI";
                message = "Hava durumu: " + weatherDescription +
                        "\nSoluk almakta zorluk çekebilirsiniz! İç mekanda kalın.";
                iconResource = R.drawable.ic_windy;
                priority = NotificationCompat.PRIORITY_MAX;
                break;

            case "HAFIF_EKSTREM":
                title = "⚠️ HAVA DURUMU UYARISI";
                message = "Hava durumu: " + weatherDescription +
                        "\nSürüş yaparken dikkatli olun.";
                iconResource = getWeatherIcon(weatherDescription);
                priority = NotificationCompat.PRIORITY_DEFAULT;
                break;
        }

        // Bildirim oluştur
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(iconResource)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(priority)
                .setAutoCancel(true)
                .setDefaults(NotificationCompat.DEFAULT_ALL);

        // Ana aktiviteyi açmak için intent
        Intent intent = new Intent(context, context.getClass());
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 0, intent,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ?
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE :
                        PendingIntent.FLAG_UPDATE_CURRENT
        );

        builder.setContentIntent(pendingIntent);

        // Bildirimi gönder
        notificationManager.notify(NOTIFICATION_ID, builder.build());

        Log.d(TAG, "Extreme weather notification sent: " + title);
        // Ayrıca yazılı AlertDialog göster
        showExtremeWeatherAlert(weatherDescription);

    }

    // Harita üzerinde seçilen konum için özel method
    public void updateLocationFromMapSelection(double latitude, double longitude, List<KazaData> kazaDataList) {
        this.kazaDataList = kazaDataList;
        this.isManualLocation = true;

        Location newLocation = new Location("map_selection");
        newLocation.setLatitude(latitude);
        newLocation.setLongitude(longitude);
        this.currentLocation = newLocation;

        Log.d(TAG, "=== MAP SELECTION UPDATE ===");
        Log.d(TAG, "Selected coordinates: " + latitude + ", " + longitude);
        Log.d(TAG, "Manual location flag set to: " + isManualLocation);

        forceWeatherUpdate();
        updateSpeedInfoBasedOnLocation();
    }

    public void setMapManager(MapManager mapManager) {
        this.mapManager = mapManager;
    }

    public void updateLocation(Location location, List<KazaData> kazaDataList) {
        this.currentLocation = location;
        this.kazaDataList = kazaDataList;
        this.isManualLocation = false;

        if (location != null) {
            Log.d(TAG, "=== GPS LOCATION UPDATE ===");
            Log.d(TAG, "New coordinates: " + location.getLatitude() + ", " + location.getLongitude());
            Log.d(TAG, "Manual location flag set to: " + isManualLocation);

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

    public void setWeatherWarningListener(WeatherWarningListener listener) {
        this.weatherWarningListener = listener;
    }

    public interface WeatherWarningListener {
        void onSevereWeatherDetected(String weatherDescription);
    }

    private boolean isSevereWeather(String description) {
        if (description == null) return false;
        String lower = description.toLowerCase();
        return lower.contains("yağmur") || lower.contains("kar") || lower.contains("sis") ||
                lower.contains("fırtına") || lower.contains("sağanak") || lower.contains("çisenti") ||
                lower.contains("rain") || lower.contains("snow") || lower.contains("fog") ||
                lower.contains("storm") || lower.contains("thunderstorm");
    }

    private boolean shouldUpdateWeather(Location location) {
        if (isWeatherUpdateInProgress) {
            Log.d(TAG, "Weather update already in progress, skipping");
            return false;
        }

        long currentTime = System.currentTimeMillis();
        double lat = location.getLatitude();
        double lon = location.getLongitude();

        if (Double.isNaN(lastWeatherLat) || Double.isNaN(lastWeatherLon)) {
            Log.d(TAG, "First weather update");
            return true;
        }

        if (currentTime - lastWeatherUpdateTime < MIN_UPDATE_INTERVAL) {
            Log.d(TAG, "Too soon for weather update. Last update: " +
                    (currentTime - lastWeatherUpdateTime) + "ms ago");
            return false;
        }

        double distance = calculateDistance(lastWeatherLat, lastWeatherLon, lat, lon);
        Log.d(TAG, "Distance from last weather location: " + distance + "km");

        if (distance > MIN_DISTANCE_FOR_UPDATE) {
            return true;
        }

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
        return null;
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
        if (isWeatherUpdateInProgress) {
            Log.d(TAG, "Weather update already in progress, ignoring new request");
            return;
        }

        isWeatherUpdateInProgress = true;
        updateWeatherInfo("Yükleniyor...");

        long timestamp = System.currentTimeMillis();
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
                        if (response.has("coord")) {
                            JSONObject coord = response.getJSONObject("coord");
                            double responseLat = coord.getDouble("lat");
                            double responseLon = coord.getDouble("lon");
                            Log.d(TAG, "API Response Coordinates: " + responseLat + ", " + responseLon);

                            lastWeatherLat = responseLat;
                            lastWeatherLon = responseLon;
                            lastWeatherUpdateTime = System.currentTimeMillis();
                        }

                        JSONArray weatherArray = response.getJSONArray("weather");
                        JSONObject weatherObject = weatherArray.getJSONObject(0);
                        String weatherDescription = weatherObject.getString("description");
                        String weatherMain = weatherObject.getString("main");

                        String cityName = response.optString("name", "Bilinmiyor");
                        double temperature = response.getJSONObject("main").getDouble("temp");

                        Log.d(TAG, "Weather data received:");
                        Log.d(TAG, "  City: " + cityName);
                        Log.d(TAG, "  Main: " + weatherMain);
                        Log.d(TAG, "  Description: " + weatherDescription);
                        Log.d(TAG, "  Temperature: " + temperature);

                        String translatedWeather = translateWeatherToTurkish(weatherMain, weatherDescription);
                        String weatherWithTemp = translatedWeather + " (" + Math.round(temperature) + "°C)";
                        currentWeatherDescription = weatherWithTemp;

                        Log.d(TAG, "Translated weather: " + weatherWithTemp);
                        updateWeatherInfo(weatherWithTemp);

                        // EKSTREM HAVA DURUMU BİLDİRİMİ KONTROLÜ
                        checkAndNotifyExtremeWeather(weatherWithTemp);

                        if (isSevereWeather(translatedWeather)) {
                            Log.d(TAG, "Severe weather detected: " + translatedWeather);
                            checkWeatherWarning(translatedWeather);

                            if (weatherWarningListener != null) {
                                weatherWarningListener.onSevereWeatherDetected(translatedWeather);
                            }
                        } else {
                            clearWeatherWarning();
                        }

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

        request.setRetryPolicy(new com.android.volley.DefaultRetryPolicy(
                10000, 1, 1.0f
        ));

        requestQueue.add(request);
        Log.d(TAG, "Weather request added to queue");
    }

    private String translateWeatherToTurkish(String main, String description) {
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
                return description.substring(0, 1).toUpperCase() + description.substring(1);
        }
    }

    private void updateWeatherInfo(String weatherInfo) {
        if (weatherText != null) {
            weatherText.setText(weatherInfo);
        }

        if (weatherIcon != null) {
            int iconResource = getWeatherIcon(weatherInfo);
            weatherIcon.setImageResource(iconResource);

            int weatherColor = getWeatherColor(weatherInfo);
            weatherIcon.setColorFilter(ContextCompat.getColor(context, weatherColor));
        }

        Log.d(TAG, "Weather info updated: " + weatherInfo);

        // 🌩️ Ekstrem hava durumu tespiti ve AlertDialog gösterimi
        if (isExtremeWeather(weatherInfo)) {
            showExtremeWeatherAlert(weatherInfo);
        }
    }

    private boolean isExtremeWeather(String description) {
        if (description == null) return false;
        String lower = description.toLowerCase();
        return lower.contains("fırtına") || lower.contains("yoğun kar") ||
                lower.contains("şiddetli yağmur") || lower.contains("yoğun sis") ||
                lower.contains("kum fırtınası") || lower.contains("storm") ||
                lower.contains("heavy snow") || lower.contains("heavy rain") ||
                lower.contains("dense fog") || lower.contains("dust storm");
    }

    private void showExtremeWeatherAlert(String weatherDescription) {
        new AlertDialog.Builder(context)
                .setTitle("⚠️ Ekstrem Hava Koşulları")
                .setMessage("Tehlikeli hava durumu tespit edildi: " + weatherDescription +
                        "\n\nLütfen dikkatli olun ve güvenliğinizi sağlayın.")
                .setIcon(R.drawable.ic_warning) // Eğer uyarı ikonu varsa
                .setCancelable(true)
                .setPositiveButton("Tamam", null)
                .show();

        Log.d(TAG, "AlertDialog gösterildi (bildirimle birlikte): " + weatherDescription);
    }



    private void checkWeatherWarning(String weather) {
        if (mapManager == null) return;

        String lowerWeather = weather.toLowerCase();

        if (lowerWeather.contains("yağmur") || lowerWeather.contains("sağanak") ||
                lowerWeather.contains("çisenti") || lowerWeather.contains("rain")) {

            String warningMessage = "🌧️ YAĞMUR UYARISI: Hava durumu yağmurlu! " +
                    "Sürüş yaparken dikkatli olun. Fren mesafesi artabilir, " +
                    "yol kaygan olabilir. Hızınızı düşürün.";

            mapManager.showWeatherWarning(warningMessage, "rain");
            Log.d(TAG, "Rain warning triggered: " + weather);
        }
        else if (lowerWeather.contains("kar") || lowerWeather.contains("snow")) {

            String warningMessage = "❄️ KAR UYARISI: Hava durumu karlı! " +
                    "Sürüş yaparken son derece dikkatli olun. Yol buzlu ve kaygan olabilir. " +
                    "Hızınızı düşürün, ani fren yapmayın.";

            mapManager.showWeatherWarning(warningMessage, "snow");
            Log.d(TAG, "Snow warning triggered: " + weather);
        }
        else if (lowerWeather.contains("fırtına") || lowerWeather.contains("thunderstorm") ||
                lowerWeather.contains("gök gürültülü")) {

            String warningMessage = "⛈️ FIRTINA UYARISI: Hava durumu fırtınalı! " +
                    "Sürüş yaperken çok dikkatli olun. Görüş mesafesi azalabilir, " +
                    "rüzgar etkisiyle araç kontrolü zorlaşabilir.";

            mapManager.showWeatherWarning(warningMessage, "storm");
            Log.d(TAG, "Storm warning triggered: " + weather);
        }
        else if (lowerWeather.contains("sis") || lowerWeather.contains("pus") ||
                lowerWeather.contains("fog") || lowerWeather.contains("mist")) {

            String warningMessage = "🌫️ SIS UYARISI: Hava durumu sisli! " +
                    "Görüş mesafesi azalmış. Farları yakın, hızınızı düşürün, " +
                    "araç takip mesafenizi artırın.";

            mapManager.showWeatherWarning(warningMessage, "fog");
            Log.d(TAG, "Fog warning triggered: " + weather);
        }
        else if (lowerWeather.contains("şiddetli") || lowerWeather.contains("yoğun")) {
            String warningMessage = "⚠️ ŞİDDETLİ HAVA UYARISI: Hava durumu çok kötü! " +
                    "Sürüş yaparken son derece dikkatli olun. Mümkünse seyahatinizi erteleyiniz.";
            mapManager.showWeatherWarning(warningMessage, "severe");
            Log.d(TAG, "Severe weather warning triggered: " + weather);
        }
    }

    public void clearWeatherWarning() {
        if (mapManager != null) {
            mapManager.hideWeatherWarning();
        }
    }

    private void updateSpeedInfo(Integer speedLimit) {
        speedIcon.setImageResource(R.drawable.ic_speed_limit);

        if (speedLimit == null || speedLimit <= 0) {
            speedText.setText("Bilinmiyor");
            speedIcon.setColorFilter(ContextCompat.getColor(context, android.R.color.darker_gray));
            speedText.setTextColor(ContextCompat.getColor(context, android.R.color.darker_gray));
            Log.d(TAG, "Hız limiti güncellendi: Bilinmiyor");
        } else {
            speedText.setText(speedLimit + " km/h");
            int speedColor = getSpeedLimitColor(speedLimit);
            speedIcon.setColorFilter(ContextCompat.getColor(context, speedColor));
            speedText.setTextColor(ContextCompat.getColor(context, speedColor));
            Log.d(TAG, "Hız limiti güncellendi: " + speedLimit + " km/h");
        }
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
        Integer roadSpeedLimit = getSpeedLimitForCurrentRoad();
        StringBuilder infoText = new StringBuilder();

        infoText.append("📍 Mevcut Konum Bilgileri:\n\n");
        infoText.append("🌤️ Anlık Hava Durumu: ").append(currentWeatherDescription).append("\n");
        infoText.append("🚗 Yol Boyunca Ortalama Hız Limiti: ")
                .append(roadSpeedLimit != null ? roadSpeedLimit + " km/h" : "Belirtilmemiş").append("\n");
        infoText.append("📍 Koordinat: ").append(String.format("%.6f, %.6f",
                currentLocation.getLatitude(), currentLocation.getLongitude())).append("\n");
        infoText.append("🎯 Konum Tipi: ").append(isManualLocation ? "Manuel Seçim" : "GPS Konumu").append("\n");

        if (nearestKaza != null) {
            infoText.append("\n📍 Yakın Kaza Bilgileri:\n\n");
            infoText.append("🏢 İlçe: ").append(nearestKaza.ilce).append("\n");
            infoText.append("🏘️ Mahalle: ").append(nearestKaza.mahalle).append("\n");
            infoText.append("🛣️ Yol: ").append(nearestKaza.yol != null ? nearestKaza.yol : "Bilinmiyor").append("\n");
            infoText.append("🌩️ Kaza Anı Hava Durumu: ").append(nearestKaza.havaDurumu).append("\n");
            infoText.append("⚠️ Kaza Türü: ").append(nearestKaza.kazaTuru.equals("olumlu") ? "Ölümlü" : "Yaralı").append("\n");

            // 💡 RİSK HESAPLAMA
            double risk = calculateAccidentRepeatProbability(
                    MapsActivity.getSimulatedSpeed(),
                    roadSpeedLimit,
                    currentWeatherDescription,
                    nearestKaza.havaDurumu
            );
            int riskPercentage = (int) (risk * 100);
            infoText.append("📊 Kaza Tekrar Riski: %").append(riskPercentage).append("\n");
        } else {
            infoText.append("\nℹ️ Yakında kaza verisi bulunmuyor\n");
        }

        new androidx.appcompat.app.AlertDialog.Builder(context)
                .setTitle("🌍 Konum Bilgileri")
                .setMessage(infoText.toString())
                .setPositiveButton("Tamam", null)
                .show();
    }

    public String getCurrentWeatherDescription() {
        return currentWeatherDescription;
    }


    static double calculateAccidentRepeatProbability(float currentSpeed, Integer speedLimit,
                                                     String currentWeatherDescription, String accidentWeather) {
        double risk = 0.0;

        // Hava durumu benzerliği
        String currentType = MapManager.determineWeatherType(currentWeatherDescription);
        String accidentType = MapManager.determineWeatherType(accidentWeather);

        if (!currentType.equals("unknown") && currentType.equals(accidentType)) {
            risk += 0.3;
        }

        // Ekstrem hava durumu varsa riski artır
        if (currentType.equals("storm") || currentType.equals("fog") ||
                currentType.equals("severe") || currentType.equals("snow")) {
            risk += 0.2;
        }

        // Hız etkisi (daha güçlü kademeli sistem)
        if (speedLimit != null && speedLimit > 0) {
            double speedRatio = currentSpeed / (double) speedLimit;

            if (speedRatio > 1.0) {
                // Hız limiti aşılmış — oran ne kadar yüksekse risk artışı o kadar fazla
                double overRatio = speedRatio - 1.0;
                // max katkı 0.4, ama hız arttıkça daha da yaklaşır
                double speedRisk = Math.min(0.4, overRatio * 0.6);
                risk += speedRisk;
            } else {
                // Hız limitinin altında sürüyorsa — çok düşükse risk azalsın
                double underRatio = 1.0 - speedRatio;
                double decrease = Math.min(0.2, underRatio * 0.4); // max düşüş 0.2
                risk -= decrease;
            }
        }

        // Toplam riski 0.0 - 1.0 aralığına sıkıştır
        return Math.max(0.0, Math.min(1.0, risk));
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

    private Integer getSpeedLimitForCurrentRoad() {
        if (currentLocation == null || kazaDataList == null) return null;

        KazaData nearest = findNearestAccident();
        if (nearest == null || nearest.yol == null || nearest.yol.equals("Bilinmiyor")) return null;

        String currentRoad = nearest.yol;
        int total = 0, count = 0;

        for (KazaData kaza : kazaDataList) {
            if (currentRoad.equals(kaza.yol) && kaza.yasalHizLimiti != null) {
                total += kaza.yasalHizLimiti;
                count++;
            }
        }

        if (count > 0) {
            return Math.round((float) total / count);
        }

        return null;
    }



}