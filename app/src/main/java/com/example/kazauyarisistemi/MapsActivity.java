package com.example.kazauyarisistemi;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
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
    private static final float PROXIMITY_THRESHOLD_METERS = 500; // 500 metre
    private static final long SIMULATED_MOVEMENT_INTERVAL_MS = 2000; // 2 seconds

    // ... variables ...
    private boolean proximityWarningsEnabled = true;
    private FloatingActionButton fabMyLocation, fabProximityToggle;
    private boolean locationUpdatesEnabled = false; // Yeni değişken
    private boolean manualLocationChange = false;
    private Location lastKnownLocation; // Yeni değişken
    private List<KazaData> kazaDataList = new ArrayList<>();


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
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mMap.setMyLocationEnabled(true);
            startLocationUpdates();
        }
    }

    private void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
            if (location != null) {
                lastKnownLocation = location;
                updateLocationOnMap(location);
                locationUpdatesEnabled = true;
            }
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
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(this, location -> {
                    if (location != null) {
                        updateLocationOnMap(location);
                        startSimulatedMovement();
                    }
                });
    }

    private void updateLocationOnMap(Location location) {
        if (location == null) return;
        currentLocation = new LatLng(location.getLatitude(), location.getLongitude());
        if (userMarker != null) {
            userMarker.remove();
        }
        userMarker = mMap.addMarker(new MarkerOptions().position(currentLocation).title("Benim Konumum").icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE)));
        lastKnownLocation = location;
    }

    // onMapClick metodunu düzgün şekilde implement edin
    @Override
    public void onMapClick(LatLng latLng) {
        Log.d(TAG, "Harita tıklandı: " + latLng.latitude + ", " + latLng.longitude);

        manualLocationChange = true; // Manuel konum değişikliği olduğunu belirtin
        Location newLocation = new Location("manual");
        newLocation.setLatitude(latLng.latitude);
        newLocation.setLongitude(latLng.longitude);
        updateLocationOnMap(newLocation);
        checkProximityToAccidents();
        manualLocationChange = false; // Manuel değişikliğin bittiğini belirtin

        // Kullanıcıya geri bildirim verin
        Toast.makeText(this, "Konum güncellendi: " + String.format("%.6f, %.6f", latLng.latitude, latLng.longitude), Toast.LENGTH_SHORT).show();
    }

    private void startSimulatedMovement() {
        simulateMovement = () -> {
            if (!locationUpdatesEnabled || lastKnownLocation == null || manualLocationChange) return;

            Random random = new Random();
            double latOffset = (random.nextDouble() - 0.5) / 500; //Daha küçük hareketler
            double lngOffset = (random.nextDouble() - 0.5) / 500;

            double newLat = lastKnownLocation.getLatitude() + latOffset;
            double newLng = lastKnownLocation.getLongitude() + lngOffset;

            Location simulatedLocation = new Location("simulated");
            simulatedLocation.setLatitude(newLat);
            simulatedLocation.setLongitude(newLng);
            updateLocationOnMap(simulatedLocation);
            checkProximityToAccidents();
            handler.postDelayed(simulateMovement, SIMULATED_MOVEMENT_INTERVAL_MS);
        };

        handler.postDelayed(simulateMovement, SIMULATED_MOVEMENT_INTERVAL_MS);
    }

    @Override
    public void onMyLocationChange(Location location) {
        // Gerçek konum değiştiğinde bu fonksiyon çağrılır
        updateLocationOnMap(location);
        checkProximityToAccidents();
    }


    private void checkProximityToAccidents() {
        if (currentLocation == null || kazaDataList.isEmpty()) return;

        for (KazaData kaza : kazaDataList) {
            float[] results = new float[1];
            Location.distanceBetween(currentLocation.latitude, currentLocation.longitude, kaza.y, kaza.x, results);
            float distance = results[0];

            if (distance <= PROXIMITY_THRESHOLD_METERS) {
                String warningMessage = "Yakınlarda " + (kaza.kazaTuru.equals("olumlu") ? "ölümlü" : "yaralı") + " kaza tespit edildi! (" + Math.round(distance) + "m)";
                Toast.makeText(this, warningMessage, Toast.LENGTH_LONG).show();
                Log.d(TAG, "Yakınlık uyarısı: " + warningMessage);
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (handler != null && simulateMovement != null) {
            handler.removeCallbacks(simulateMovement);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (handler != null && simulateMovement != null) {
            handler.removeCallbacks(simulateMovement);
        }
    }
}