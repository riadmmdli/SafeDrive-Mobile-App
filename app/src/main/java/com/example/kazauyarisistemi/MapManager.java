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

public class MapManager {
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

            int warningColor = kaza.kazaTuru.equals("olumlu") ?
                    ContextCompat.getColor(context, android.R.color.holo_red_light) :
                    ContextCompat.getColor(context, android.R.color.holo_orange_light);

            warningView.setBackgroundColor(warningColor);

            String warningText = "⚠️ UYARI: Yakınlarda " +
                    (kaza.kazaTuru.equals("olumlu") ? "ÖLÜMLÜ" : "YARALI") +
                    " kaza!\nMesafe: " + Math.round(distance) + "m\n" +
                    "Konum: " + kaza.ilce + " - " + kaza.mahalle;

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

            speakWarning(kaza, distance);
        });
    }

    private void speakWarning(KazaData kaza, float distance) {
        if (textToSpeech == null) return;

        // Fonetik okunuşları kullan
        String ilce = getPhoneticText(kaza.ilce);
        String mahalle = getPhoneticText(kaza.mahalle);

        String speechText = "Dikkat! Yakınlarda " +
                (kaza.kazaTuru.equals("olumlu") ? "ölümlü" : "yaralanmalı") +
                " bir kaza var. Mesafe yaklaşık " + Math.round(distance) + " metre. " +
                ilce + " ilçesi, " + mahalle + " mahallesi.";

        textToSpeech.stop(); // Önceki konuşmayı durdur
        textToSpeech.speak(speechText, TextToSpeech.QUEUE_FLUSH, null, "UYARI_ID");
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
