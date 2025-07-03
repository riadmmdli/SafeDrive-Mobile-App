package com.example.kazauyarisistemi;

import android.content.Context;
import android.location.Location;
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

public class MapManager {
    private static final String TAG = "MapManager";
    private static final float PROXIMITY_THRESHOLD_METERS = 50;
    private GoogleMap mMap;
    private LatLng currentLocation;
    private Marker userMarker;
    private LinearLayout warningLayout;
    private List<KazaData> kazaDataList;
    private boolean manualLocationChange = false;
    private Context context;
    private MapsActivity mapsActivity;
    // WeatherSpeedInfoManager referansı eklendi
    private WeatherSpeedInfoManager weatherSpeedInfoManager;

    public MapManager(Context context, GoogleMap googleMap, LinearLayout warningLayout) {
        this.context = context;
        this.mMap = googleMap;
        this.warningLayout = warningLayout;
        this.mapsActivity = (MapsActivity) context;

        mMap.setOnMarkerClickListener(marker -> {
            String title = marker.getTitle();
            if (title != null && title.contains("KAZA")) {
                showKazaDetails((KazaData) marker.getTag(), marker.getPosition());
                return true;
            }
            return false;
        });
    }

    // WeatherSpeedInfoManager'ı set etmek için method eklendi
    public void setWeatherSpeedInfoManager(WeatherSpeedInfoManager weatherSpeedInfoManager) {
        this.weatherSpeedInfoManager = weatherSpeedInfoManager;
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

        // Manuel konum mu otomatik konum mu göster
        float markerColor = manualLocationChange ?
                BitmapDescriptorFactory.HUE_GREEN :
                BitmapDescriptorFactory.HUE_BLUE;

        String markerTitle = manualLocationChange ?
                "Manuel Konum" :
                "Benim Konumum";

        userMarker = mMap.addMarker(new MarkerOptions()
                .position(currentLocation)
                .title(markerTitle)
                .icon(BitmapDescriptorFactory.defaultMarker(markerColor)));

        // WeatherSpeedInfoManager'ı güncelle - BU SATIRLARI EKLEDİK
        if (weatherSpeedInfoManager != null) {
            weatherSpeedInfoManager.updateLocation(location, kazaDataList);
            Log.d(TAG, "WeatherSpeedInfoManager updated with new location");
        }

        checkProximityToAccidents();
    }

    public void onMapClick(LatLng latLng) {
        Log.d(TAG, "Map clicked: " + latLng.latitude + ", " + latLng.longitude);

        // Manuel konum değişikliği bayrağını ayarla
        manualLocationChange = true;

        // MapsActivity'ye manuel konum değişikliğini bildir
        if (mapsActivity != null) {
            mapsActivity.setManualLocationChange(true);
            mapsActivity.stopSimulatedMovement(); // Simülasyonu durdur
        }

        Location newLocation = new Location("manual");
        newLocation.setLatitude(latLng.latitude);
        newLocation.setLongitude(latLng.longitude);

        hideWarning();

        updateLocationOnMap(newLocation, kazaDataList);

        // Manuel konum değişikliğinde hava durumunu zorla güncelle
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

        // Yeni alanlar
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

            int warningColor = kaza.kazaTuru.equals("olumlu") ?
                    ContextCompat.getColor(context, android.R.color.holo_red_light) :
                    ContextCompat.getColor(context, android.R.color.holo_orange_light);

            warningView.setBackgroundColor(warningColor);

            String warningText = "⚠️ UYARI: Yakınlarda " +
                    (kaza.kazaTuru.equals("olumlu") ? "ÖLÜMLÜ" : "YARALI") +
                    " kaza!\nMesafe: " + Math.round(distance) + "m\n" +
                    "Konum: " + kaza.ilce + " - " + kaza.mahalle;

            // Hava durumu ve hız limiti bilgilerini burada ekleyin
            if (kaza.havaDurumu != null && !kaza.havaDurumu.equals("Bilinmiyor")) {
                warningText += "\n🌤️ Hava: " + kaza.havaDurumu;
            }
            if (kaza.yasalHizLimiti != null) {
                warningText += "\n🚗 Hız Limiti: " + kaza.yasalHizLimiti + " km/h";
            }

            warningTextView.setText(warningText);

            closeWarning.setOnClickListener(v -> hideWarning());

            warningLayout.addView(warningView);
            warningLayout.setVisibility(View.VISIBLE);

            Log.d(TAG, "WARNING DISPLAYED: " + warningText);

            Toast.makeText(context, warningText, Toast.LENGTH_LONG).show();
        });
    }

    public void hideWarning() {
        if (warningLayout != null) {
            warningLayout.setVisibility(View.GONE);
        }
    }

    public void checkProximityToAccidents() {
        if (currentLocation == null || kazaDataList == null || kazaDataList.isEmpty()) {
            Log.d(TAG, "checkProximityToAccidents: currentLocation or kazaDataList is null or empty.");
            return;
        }

        Log.d(TAG, "Current location: " + currentLocation.latitude + ", " + currentLocation.longitude);
        Log.d(TAG, "Checking proximity with " + kazaDataList.size() + " kaza data");

        for (KazaData kaza : kazaDataList) {
            float[] results = new float[1];
            Location.distanceBetween(currentLocation.latitude, currentLocation.longitude,
                    kaza.y, kaza.x, results);

            float distance = results[0];
            Log.d(TAG, "Distance to " + kaza.ilce + " (" + kaza.y + "," + kaza.x + "): " + distance + "m");

            if (distance <= PROXIMITY_THRESHOLD_METERS) {
                Log.d(TAG, "PROXIMITY ALERT! Distance: " + distance + "m to " + kaza.ilce);
                showWarning(kaza, distance);
                return; // İlk uyarıyı göster ve çık
            }
        }
    }

    public void testProximitySystem() {
        Log.d(TAG, "=== PROXIMITY SYSTEM TEST ===");
        Log.d(TAG, "Current location: " + (currentLocation != null ?
                currentLocation.latitude + "," + currentLocation.longitude : "NULL"));
        Log.d(TAG, "Kaza data count: " + (kazaDataList != null ? kazaDataList.size() : 0));
        Log.d(TAG, "Proximity threshold: " + PROXIMITY_THRESHOLD_METERS + "m");

        if (currentLocation != null && kazaDataList != null && !kazaDataList.isEmpty()) {
            Log.d(TAG, "Testing proximity for first 5 accidents:");
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