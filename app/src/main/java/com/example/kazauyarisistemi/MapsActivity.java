package com.example.kazauyarisistemi;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

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

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.List;
import java.util.Random;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback, GoogleMap.OnMyLocationChangeListener, GoogleMap.OnMapClickListener {

    private static final String TAG = "MapsActivity";
    private GoogleMap mMap;
    private MapManager mapManager;
    private FirebaseDataManager firebaseDataManager;
    private LinearLayout loadingPanel;
    private FloatingActionButton fabBack;
    private TextView tvOlumluCount, tvYaraliCount, tvTotalCount;
    private static final LatLng KAYSERI_CENTER = new LatLng(38.7312, 35.4787);
    private static final long SIMULATED_MOVEMENT_INTERVAL_MS = 2000; // 2 saniye

    private FusedLocationProviderClient fusedLocationClient;
    private Marker userMarker;
    private LatLng currentLocation;
    private Handler handler = new Handler();
    private Runnable simulateMovement;
    private boolean locationUpdatesEnabled = false;
    private boolean manualLocationChange = false;
    private Location lastKnownLocation;

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;
    private LinearLayout warningLayout;
    private boolean isLocationUpdateInProgress = false;
    private FloatingActionButton fabMyLocation, fabProximityToggle;
    private WeatherSpeedInfoManager weatherSpeedManager;
    private View infoPanelContainer;
    private ImageView weatherIcon, speedIcon, infoButton;
    private TextView weatherText, speedText;
    private FloatingActionButton fabToggleInfo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);


        tvOlumluCount = findViewById(R.id.tvOlumluCount);
        tvYaraliCount = findViewById(R.id.tvYaraliCount);
        tvTotalCount = findViewById(R.id.tvTotalCount);
        loadingPanel = findViewById(R.id.loadingPanel);
        fabBack = findViewById(R.id.fabBack);
        warningLayout = findViewById(R.id.warningLayoutContainer);
        infoPanelContainer = findViewById(R.id.infoPanelContainer);
        weatherIcon = findViewById(R.id.weatherIcon);
        weatherText = findViewById(R.id.weatherText);
        speedIcon = findViewById(R.id.speedIcon);
        speedText = findViewById(R.id.speedText);
        infoButton = findViewById(R.id.infoButton);
        fabToggleInfo = findViewById(R.id.fabToggleInfo);

        weatherSpeedManager = new WeatherSpeedInfoManager(
                this, infoPanelContainer, weatherIcon, weatherText, speedIcon, speedText
        );



        firebaseDataManager = new FirebaseDataManager(this);


        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);


        fabBack.setOnClickListener(v -> finish());

        fabToggleInfo.setOnClickListener(v -> weatherSpeedManager.toggleInfoPanel());

        infoButton.setOnClickListener(v -> weatherSpeedManager.showDetailedInfo());

        findViewById(R.id.fabMyLocation).setOnClickListener(v -> {
            if (manualLocationChange) {
                // Manuel konumdan gerçek konuma dön
                manualLocationChange = false;
                locationUpdatesEnabled = true;
                getLastKnownLocation();
                startSimulatedMovement();
                Toast.makeText(this, "Gerçek konumunuza döndünüz", Toast.LENGTH_SHORT).show();
            } else {
                // Normal işlem - mevcut konuma odaklan
                mapManager.testProximitySystem();
                if (currentLocation != null) {
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLocation, 15));
                }
            }
        });


        requestLocationPermission();

    }

    private void requestLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
        } else {
            // İzin zaten verilmişse konum güncellemelerini başlat
            getLastKnownLocation();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // İzin verildi, konum güncellemelerine devam et
                getLastKnownLocation();
            } else {
                // İzin reddedildi, kullanıcıya bilgi ver
                Toast.makeText(this, "Konum izni gereklidir.", Toast.LENGTH_SHORT).show();
            }
        }
    }



    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;
        mapManager = new MapManager(this, mMap, warningLayout);

        mMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
        mMap.getUiSettings().setZoomControlsEnabled(true);
        mMap.getUiSettings().setCompassEnabled(true);
        mMap.getUiSettings().setMyLocationButtonEnabled(true);
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(KAYSERI_CENTER, 12));


        mMap.setOnMapClickListener(this);
        mMap.setOnMyLocationChangeListener(this);

        showLoading(true);
        firebaseDataManager.loadKazaData(kazaDataList -> {
            for (KazaData kaza : kazaDataList) {
                mapManager.addKazaMarker(kaza);
            }
            showLoading(false);
            updateStatistics(kazaDataList); // İstatistikleri güncelle
            Toast.makeText(MapsActivity.this, "Kaza verileri yüklendi.", Toast.LENGTH_SHORT).show();
        });

        enableMyLocationIfPermitted();

    }

    private void enableMyLocationIfPermitted() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mMap.setMyLocationEnabled(true);
            startLocationUpdates(); // Konum izni varsa güncellemeleri başlat
        }
    }

    private void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        if (isLocationUpdateInProgress) return;
        isLocationUpdateInProgress = true;
        fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
            if (location != null) {
                lastKnownLocation = location;
                updateLocationOnMap(location);
                locationUpdatesEnabled = true;
                startSimulatedMovement();
            } else {
                Log.e(TAG, "getLastLocation returned null.");
            }
            isLocationUpdateInProgress = false;
        }).addOnFailureListener(e -> {
            Log.e(TAG, "getLastLocation failed: " + e.getMessage());
            isLocationUpdateInProgress = false;
        });
    }


    private void updateLocationOnMap(Location location) {
        mapManager.updateLocationOnMap(location, firebaseDataManager.getKazaDataList());
        weatherSpeedManager.updateLocation(location, firebaseDataManager.getKazaDataList());

        // Manuel konum değişikliği değilse lastKnownLocation'ı güncelle
        if (!manualLocationChange) {
            lastKnownLocation = location;
        }
    }

    @Override
    public void onMapClick(LatLng latLng) {
        mapManager.onMapClick(latLng);

        // Manuel konum değişikliği için weatherSpeedManager'ı güncelle
        manualLocationChange = true;
        stopSimulatedMovement();

        // weatherSpeedManager'a manuel konum bilgisini gönder
        weatherSpeedManager.updateLocationFromMapSelection(
                latLng.latitude,
                latLng.longitude,
                firebaseDataManager.getKazaDataList()
        );

        Toast.makeText(this, "Manuel konum seçildi: " +
                        String.format("%.6f, %.6f", latLng.latitude, latLng.longitude),
                Toast.LENGTH_SHORT).show();
    }

    private void showLoading(boolean show) {
        loadingPanel.setVisibility(show ? View.VISIBLE : View.GONE);
    }


    private void updateStatistics(List<KazaData> kazaDataList) {
        int olumluCount = 0;
        int yaraliCount = 0;

        for (KazaData kaza : kazaDataList) {
            if ("olumlu".equals(kaza.kazaTuru)) {
                olumluCount++;
            } else if ("yarali".equals(kaza.kazaTuru)) {
                yaraliCount++;
            }
        }

        tvOlumluCount.setText(String.valueOf(olumluCount));
        tvYaraliCount.setText(String.valueOf(yaraliCount));
        tvTotalCount.setText(String.valueOf(olumluCount + yaraliCount));
    }



    private void startSimulatedMovement() {
        // Eğer manuel konum değişikliği yapıldıysa simülasyonu başlatma
        if (manualLocationChange) {
            Log.d(TAG, "Simulated movement not started - manual location change active");
            return;
        }

        simulateMovement = () -> {
            if (!locationUpdatesEnabled || lastKnownLocation == null || manualLocationChange || isLocationUpdateInProgress) {
                return;
            }

            Random random = new Random();
            double latOffset = (random.nextDouble() - 0.5) / 1000;
            double lngOffset = (random.nextDouble() - 0.5) / 1000;

            double newLat = lastKnownLocation.getLatitude() + latOffset;
            double newLng = lastKnownLocation.getLongitude() + lngOffset;

            Location simulatedLocation = new Location("simulated");
            simulatedLocation.setLatitude(newLat);
            simulatedLocation.setLongitude(newLng);

            isLocationUpdateInProgress = true;
            updateLocationOnMap(simulatedLocation);
            isLocationUpdateInProgress = false;

            handler.postDelayed(simulateMovement, SIMULATED_MOVEMENT_INTERVAL_MS);
        };

        handler.postDelayed(simulateMovement, SIMULATED_MOVEMENT_INTERVAL_MS);
    }

    public void setManualLocationChange(boolean isManual) {
        this.manualLocationChange = isManual;
    }

    public void stopSimulatedMovement() {
        if (simulateMovement != null) {
            handler.removeCallbacks(simulateMovement);
            locationUpdatesEnabled = false;
            Log.d(TAG, "Simulated movement stopped due to manual location change");
        }
    }

    @Override
    public void onMyLocationChange(Location location) {
        if (!manualLocationChange) { // Manuel konum değişikliği değilse güncelle
            updateLocationOnMap(location);
        }
    }


    @Override
    protected void onPause() {
        super.onPause();
        mapManager.hideWarning();
        handler.removeCallbacks(simulateMovement);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mapManager.hideWarning();
        handler.removeCallbacks(simulateMovement);
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
}