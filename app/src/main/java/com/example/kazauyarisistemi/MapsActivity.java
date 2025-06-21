package com.example.kazauyarisistemi;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

// GoogleMap.OnMapClickListener interface'ini eklendi
public class MapsActivity extends FragmentActivity implements OnMapReadyCallback, GoogleMap.OnMyLocationChangeListener, GoogleMap.OnMapClickListener {

    private static final String TAG = "MapsActivity";
    private GoogleMap mMap;
    private DatabaseReference mDatabase;
    private static final LatLng KAYSERI_CENTER = new LatLng(38.7312, 35.4787);
    private TextView tvOlumluCount, tvYaraliCount, tvTotalCount;
    private LinearLayout loadingPanel;
    private FloatingActionButton fabBack;
    private int olumluMarkerCount = 0;
    private int yaraliMarkerCount = 0;

    private FusedLocationProviderClient fusedLocationClient;
    private Marker userMarker;
    private LatLng currentLocation;
    private Handler handler = new Handler();
    private Runnable simulateMovement;

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;
    private static final float PROXIMITY_THRESHOLD_METERS = 50;
    private static final long SIMULATED_MOVEMENT_INTERVAL_MS = 2000; // 2 seconds

    // ... variables ...
    private boolean proximityWarningsEnabled = true;
    private FloatingActionButton fabMyLocation, fabProximityToggle;
    private boolean locationUpdatesEnabled = false; // Yeni değişken
    private boolean manualLocationChange = false;
    private Location lastKnownLocation; // Yeni değişken
    private List<KazaData> kazaDataList = new ArrayList<>();

    private LinearLayout warningLayout;
    private boolean isLocationUpdateInProgress = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        initializeViews();
        mDatabase = FirebaseDatabase.getInstance().getReference();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        fabBack.setOnClickListener(v -> finish());

        // Test butonu ekleyin (geçici)
        findViewById(R.id.fabMyLocation).setOnClickListener(v -> {
            testProximitySystem();
            if (currentLocation != null) {
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLocation, 15));
            }
        });

        requestLocationPermission();
    }

    private void requestLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, proceed with location updates
                getLastKnownLocation();
            } else {
                Toast.makeText(this, "Konum izni gereklidir.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void initializeViews() {
        tvOlumluCount = findViewById(R.id.tvOlumluCount);
        tvYaraliCount = findViewById(R.id.tvYaraliCount);
        tvTotalCount = findViewById(R.id.tvTotalCount);
        loadingPanel = findViewById(R.id.loadingPanel);
        fabBack = findViewById(R.id.fabBack);
        warningLayout = findViewById(R.id.warningLayoutContainer);

    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;
        mMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
        mMap.getUiSettings().setZoomControlsEnabled(true);
        mMap.getUiSettings().setCompassEnabled(true);
        mMap.getUiSettings().setMyLocationButtonEnabled(true);
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(KAYSERI_CENTER, 12));

        mMap.setOnMarkerClickListener(marker -> {
            String title = marker.getTitle();
            if (title != null && title.contains("KAZA")) {
                showKazaDetails(marker);
                return true;
            }
            return false;
        });

        // OnMapClickListener'ı burada set edin
        mMap.setOnMapClickListener(this);
        mMap.setOnMyLocationChangeListener(this);

        loadKazaData();
        enableMyLocationIfPermitted();
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mMap.setMyLocationEnabled(true);
            startLocationUpdates();
        }
    }
    private void enableMyLocationIfPermitted() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mMap.setMyLocationEnabled(true);
            startLocationUpdates();
        }
    }

    private void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        if (isLocationUpdateInProgress) return; // Prevent multiple simultaneous updates
        isLocationUpdateInProgress = true;
        fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
            if (location != null) {
                lastKnownLocation = location;
                updateLocationOnMap(location);
                locationUpdatesEnabled = true;
                startSimulatedMovement(); // Start simulation after successful location retrieval
            } else {
                Log.e(TAG, "getLastLocation returned null.");
            }
            isLocationUpdateInProgress = false; // Update complete
        }).addOnFailureListener(e -> {
            Log.e(TAG, "getLastLocation failed: " + e.getMessage());
            isLocationUpdateInProgress = false;
        });
    }


    private void loadKazaData() {
        showLoading(true);
        Toast.makeText(this, "Kaza verileri yükleniyor...", Toast.LENGTH_SHORT).show();

        loadOlumluKazalar();
    }


    private void showLoading(boolean show) {
        loadingPanel.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void updateStatistics() {
        tvOlumluCount.setText(String.valueOf(olumluMarkerCount));
        tvYaraliCount.setText(String.valueOf(yaraliMarkerCount));
        tvTotalCount.setText(String.valueOf(olumluMarkerCount + yaraliMarkerCount));
    }

    private void loadOlumluKazalar() {
        DatabaseReference olumluRef = mDatabase.child("kazalar").child("olumlu");
        olumluRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                Log.d(TAG, "Ölümlü kaza verileri alındı");
                for (DataSnapshot batchSnapshot : dataSnapshot.getChildren()) {
                    for (DataSnapshot kazaSnapshot : batchSnapshot.getChildren()) {
                        addKazaMarker(kazaSnapshot, "olumlu");
                    }
                }
                Log.d(TAG, "Toplam ölümlü marker: " + olumluMarkerCount);
                updateStatistics();
                loadYaraliKazalar();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e(TAG, "Ölümlü kaza yükleme hatası", databaseError.toException());
                Toast.makeText(MapsActivity.this,
                        "Ölümlü kaza yükleme hatası: " + databaseError.getMessage(),
                        Toast.LENGTH_LONG).show();
                showLoading(false);
            }
        });
    }

    private void loadYaraliKazalar() {
        DatabaseReference yaraliRef = mDatabase.child("kazalar").child("yarali");
        yaraliRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                Log.d(TAG, "Yaralı kaza verileri alındı");
                for (DataSnapshot batchSnapshot : dataSnapshot.getChildren()) {
                    for (DataSnapshot kazaSnapshot : batchSnapshot.getChildren()) {
                        addKazaMarker(kazaSnapshot, "yarali");
                    }
                }
                Log.d(TAG, "Toplam yaralı marker: " + yaraliMarkerCount);
                updateStatistics();
                showLoading(false);
                Toast.makeText(MapsActivity.this,
                        String.format("Toplam %d kaza yüklendi (%d ölümlü, %d yaralı)",
                                olumluMarkerCount + yaraliMarkerCount, olumluMarkerCount, yaraliMarkerCount),
                        Toast.LENGTH_LONG).show();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e(TAG, "Yaralı kaza yükleme hatası", databaseError.toException());
                Toast.makeText(MapsActivity.this,
                        "Yaralı kaza yükleme hatası: " + databaseError.getMessage(),
                        Toast.LENGTH_LONG).show();
                showLoading(false);
            }
        });
    }

    private void addKazaMarker(DataSnapshot kazaSnapshot, String kazaTuru) {
        try {
            Object xObj = kazaSnapshot.child("X").getValue();
            Object yObj = kazaSnapshot.child("Y").getValue();

            if (xObj == null || yObj == null) {
                Log.w(TAG, "Koordinat bilgisi eksik: " + kazaSnapshot.getKey());
                return;
            }

            double x, y;
            x = xObj instanceof String ? Double.parseDouble((String) xObj) : ((Number) xObj).doubleValue();
            y = yObj instanceof String ? Double.parseDouble((String) yObj) : ((Number) yObj).doubleValue();


            if (x < 34.0 || x > 37.0 || y < 37.5 || y > 39.5) {
                Log.w(TAG, "Kayseri sınırları dışında koordinat: " + x + "," + y);
                return;
            }

            LatLng position = new LatLng(y, x);

            String ilce = getStringValue(kazaSnapshot, "ILCE");
            String mahalle = getStringValue(kazaSnapshot, "MAHALLE");
            String yol = getStringValue(kazaSnapshot, "YOL");
            String saat = getStringValue(kazaSnapshot, "KAZA SAAT");
            String dakika = getStringValue(kazaSnapshot, "KAZA DAKIKA");

            String title = kazaTuru.equals("olumlu") ? "🔴 ÖLÜMLÜ KAZA" : "🟡 YARALI KAZA";
            String snippet = String.format("%s - %s\n%s\nSaat: %s:%s",
                    ilce != null ? ilce : "Bilinmiyor",
                    mahalle != null ? mahalle : "Bilinmiyor",
                    yol != null && !yol.isEmpty() ? yol : "Yol bilgisi yok",
                    saat != null ? saat : "?",
                    dakika != null ? dakika : "?");

            float markerColor = kazaTuru.equals("olumlu") ?
                    BitmapDescriptorFactory.HUE_RED :
                    BitmapDescriptorFactory.HUE_YELLOW;

            MarkerOptions markerOptions = new MarkerOptions()
                    .position(position)
                    .title(title)
                    .snippet(snippet)
                    .icon(BitmapDescriptorFactory.defaultMarker(markerColor));

            Marker marker = mMap.addMarker(markerOptions);
            if (marker != null) {
                marker.setTag(new KazaData(kazaSnapshot, kazaTuru));
                kazaDataList.add(new KazaData(kazaSnapshot, kazaTuru));
            }

            if (kazaTuru.equals("olumlu")) {
                olumluMarkerCount++;
            } else {
                yaraliMarkerCount++;
            }

            Log.d(TAG, String.format("Marker eklendi: %s - %s,%s", title, x, y));

        } catch (Exception e) {
            Log.e(TAG, "Marker ekleme hatası: " + e.getMessage(), e);
        }
    }

    private String getStringValue(DataSnapshot snapshot, String key) {
        Object value = snapshot.child(key).getValue();
        if (value == null) return null;
        return value.toString().trim();
    }

    private void showKazaDetails(Marker marker) {
        KazaData kazaData = (KazaData) marker.getTag();
        if (kazaData == null) return;

        StringBuilder details = new StringBuilder();
        details.append("🏢 İlçe: ").append(kazaData.ilce).append("\n");
        details.append("🏘️ Mahalle: ").append(kazaData.mahalle).append("\n");
        details.append("🛣️ Yol: ").append(kazaData.yol).append("\n");
        details.append("⏰ Saat: ").append(kazaData.saat).append(":").append(kazaData.dakika).append("\n");
        details.append("📍 Koordinat: ").append(String.format("%.6f, %.6f", kazaData.x, kazaData.y)).append("\n");
        details.append("⚠️ Tür: ").append(kazaData.kazaTuru.equals("olumlu") ? "Ölümlü" : "Yaralı");

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("🚨 Kaza Detayları")
                .setMessage(details.toString())
                .setPositiveButton("Tamam", null)
                .setNeutralButton("Haritada Göster", (dialog, which) -> {
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(marker.getPosition(), 16));
                })
                .show();
    }

    private static class KazaData {
        String ilce, mahalle, yol, saat, dakika, kazaTuru;
        double x, y;

        KazaData(DataSnapshot snapshot, String kazaTuru) {
            this.kazaTuru = kazaTuru;
            this.ilce = getStringValue(snapshot, "ILCE");
            this.mahalle = getStringValue(snapshot, "MAHALLE");
            this.yol = getStringValue(snapshot, "YOL");
            this.saat = getStringValue(snapshot, "KAZA SAAT");
            this.dakika = getStringValue(snapshot, "KAZA DAKIKA");

            Object xObj = snapshot.child("X").getValue();
            Object yObj = snapshot.child("Y").getValue();

            this.x = xObj instanceof String ? Double.parseDouble((String) xObj) : ((Number) xObj).doubleValue();
            this.y = yObj instanceof String ? Double.parseDouble((String) yObj) : ((Number) yObj).doubleValue();
        }

        private static String getStringValue(DataSnapshot snapshot, String key) {
            Object value = snapshot.child(key).getValue();
            return value != null ? value.toString().trim() : "Bilinmiyor";
        }
    }

    private void getLastKnownLocation() {
        if (!locationPermissionsGranted()) {
            Log.w(TAG, "Konum izni verilmemiş. getLastKnownLocation çağrılmadı.");
            return;
        }

        try {
            fusedLocationClient.getLastLocation()
                    .addOnSuccessListener(this, location -> {
                        if (location != null) {
                            updateLocationOnMap(location);
                            startSimulatedMovement();
                        } else {
                            Log.e(TAG, "getLastKnownLocation returned null");
                        }
                    })
                    .addOnFailureListener(this, e -> Log.e(TAG, "getLastKnownLocation failed", e));
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException: Konum izni yok. getLastLocation başarısız.", e);
        }
    }
    private boolean locationPermissionsGranted() {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void updateLocationOnMap(Location location) {
        if (location == null) return;

        currentLocation = new LatLng(location.getLatitude(), location.getLongitude());
        Log.d(TAG, "Location updated: " + currentLocation.latitude + ", " + currentLocation.longitude);

        if (userMarker != null) {
            userMarker.remove();
        }

        userMarker = mMap.addMarker(new MarkerOptions()
                .position(currentLocation)
                .title("Benim Konumum")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE)));

        lastKnownLocation = location;

        // Uyarı kontrolünü her konum güncellemesinde yap
        checkProximityToAccidents();
    }

    // onMapClick metodunu düzgün şekilde implement edin
    @Override
    public void onMapClick(LatLng latLng) {
        Log.d(TAG, "Map clicked: " + latLng.latitude + ", " + latLng.longitude);

        manualLocationChange = true;
        Location newLocation = new Location("manual");
        newLocation.setLatitude(latLng.latitude);
        newLocation.setLongitude(latLng.longitude);

        // Eski uyarıyı kapat
        hideWarning();

        // Yeni konumu güncelle
        updateLocationOnMap(newLocation);
        manualLocationChange = false;

        Toast.makeText(this, "Konum güncellendi: " +
                        String.format("%.6f, %.6f", latLng.latitude, latLng.longitude),
                Toast.LENGTH_SHORT).show();
    }

    private void testProximitySystem() {
        Log.d(TAG, "=== PROXIMITY SYSTEM TEST ===");
        Log.d(TAG, "Current location: " + (currentLocation != null ?
                currentLocation.latitude + "," + currentLocation.longitude : "NULL"));
        Log.d(TAG, "Kaza data count: " + kazaDataList.size());
        Log.d(TAG, "Proximity threshold: " + PROXIMITY_THRESHOLD_METERS + "m");

        if (currentLocation != null && !kazaDataList.isEmpty()) {
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

    private void startSimulatedMovement() {
        simulateMovement = () -> {
            if (!locationUpdatesEnabled || lastKnownLocation == null || manualLocationChange || isLocationUpdateInProgress) return;

            Random random = new Random();
            double latOffset = (random.nextDouble() - 0.5) / 1000; // Smaller movement
            double lngOffset = (random.nextDouble() - 0.5) / 1000;

            double newLat = lastKnownLocation.getLatitude() + latOffset;
            double newLng = lastKnownLocation.getLongitude() + lngOffset;

            Location simulatedLocation = new Location("simulated");
            simulatedLocation.setLatitude(newLat);
            simulatedLocation.setLongitude(newLng);

            isLocationUpdateInProgress = true; // Indicate update is in progress
            updateLocationOnMap(simulatedLocation);
            isLocationUpdateInProgress = false; // Update complete

            handler.postDelayed(simulateMovement, SIMULATED_MOVEMENT_INTERVAL_MS);
        };

        handler.postDelayed(simulateMovement, SIMULATED_MOVEMENT_INTERVAL_MS);
    }

    @Override
    public void onMyLocationChange(Location location) {
        // Gerçek konum değiştiğinde bu fonksiyon çağrılır
        updateLocationOnMap(location);

    }


    private void checkProximityToAccidents() {
        if (currentLocation == null || kazaDataList.isEmpty()) {
            Log.d(TAG, "checkProximityToAccidents: currentLocation or kazaDataList is null or empty.");
            return;
        }

        Log.d(TAG, "Current location: " + currentLocation.latitude + ", " + currentLocation.longitude);
        Log.d(TAG, "Checking proximity with " + kazaDataList.size() + " kaza data");

        for (KazaData kaza : kazaDataList) {
            float[] results = new float[1];
            // Koordinat düzeni düzeltildi: kaza.y = latitude, kaza.x = longitude
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
    private void showWarning(KazaData kaza, float distance) {
        if (warningLayout == null) {
            Log.e(TAG, "warningLayout null!");
            return;
        }

        try {
            // UI thread'de çalıştığından emin ol
            runOnUiThread(() -> {
                warningLayout.removeAllViews();
                LayoutInflater inflater = LayoutInflater.from(this);
                View warningView = inflater.inflate(R.layout.warning_layout, warningLayout, false);

                TextView warningTextView = warningView.findViewById(R.id.warningTextView);
                ImageView closeWarning = warningView.findViewById(R.id.closeWarning);

                // Arka plan rengi
                int warningColor = kaza.kazaTuru.equals("olumlu") ?
                        ContextCompat.getColor(this, android.R.color.holo_red_light) :
                        ContextCompat.getColor(this, android.R.color.holo_orange_light);

                warningView.setBackgroundColor(warningColor);

                // Uyarı metni
                String warningText = "⚠️ UYARI: Yakınlarda " +
                        (kaza.kazaTuru.equals("olumlu") ? "ÖLÜMLÜ" : "YARALI") +
                        " kaza!\nMesafe: " + Math.round(distance) + "m\n" +
                        "Konum: " + kaza.ilce + " - " + kaza.mahalle;

                warningTextView.setText(warningText);

                closeWarning.setOnClickListener(v -> hideWarning());

                warningLayout.addView(warningView);
                warningLayout.setVisibility(View.VISIBLE);

                // Titreşim ekle (opsiyonel)
                // Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                // if (vibrator != null) vibrator.vibrate(500);

                Log.d(TAG, "WARNING DISPLAYED: " + warningText);

                // Toast da göster
                Toast.makeText(this, warningText, Toast.LENGTH_LONG).show();
            });

        } catch (Exception e) {
            Log.e(TAG, "showWarning error: " + e.getMessage(), e);
        }
    }

    private void hideWarning() {
        if (warningLayout != null) {
            warningLayout.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        hideWarning(); // Uygulama duraklatıldığında uyarıyı kaldır
        handler.removeCallbacks(simulateMovement);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        hideWarning(); // Uygulama kapatıldığında uyarıyı kaldır
        handler.removeCallbacks(simulateMovement);
    }
}