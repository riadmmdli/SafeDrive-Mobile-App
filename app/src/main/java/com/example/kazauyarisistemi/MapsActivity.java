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
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.View;
import android.widget.Button;
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
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.List;
import java.util.Locale;
import java.util.Random;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback,
        GoogleMap.OnMyLocationChangeListener, GoogleMap.OnMapClickListener {

    private static final String TAG = "MapsActivity";
    private static final LatLng KAYSERI_CENTER = new LatLng(38.7312, 35.4787);
    private static final long SIMULATED_MOVEMENT_INTERVAL_MS = 2000; // 2 saniye
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;

    // Core components
    private GoogleMap mMap;
    private MapManager mapManager;
    private FirebaseDataManager firebaseDataManager;
    private WeatherSpeedInfoManager weatherSpeedManager;
    private FusedLocationProviderClient fusedLocationClient;
    private TextToSpeech tts;

    // UI elements
    private LinearLayout loadingPanel;
    private FloatingActionButton fabBack;
    private TextView tvOlumluCount, tvYaraliCount, tvTotalCount;
    private LinearLayout warningLayout;
    private View infoPanelContainer;
    private ImageView weatherIcon, speedIcon, infoButton;
    private TextView weatherText, speedText;
    private FloatingActionButton fabToggleInfo;

    // Location management
    private LatLng currentLocation;
    private Location lastKnownLocation;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable simulateMovement;
    private boolean locationUpdatesEnabled = false;
    private boolean manualLocationChange = false;
    private boolean isLocationUpdateInProgress = false;
    private boolean ttsReady = false;

    private static int simulatedSpeed = 0;
    private TextView tvCurrentSpeed;
    private Button btnIncreaseSpeed, btnDecreaseSpeed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            setContentView(R.layout.activity_maps);
            initializeComponents();
            requestLocationPermissionIfNeeded();
            Log.d(TAG, "MapsActivity başarıyla oluşturuldu");
        } catch (Exception e) {
            Log.e(TAG, "onCreate'te hata oluştu: " + e.getMessage(), e);
            showErrorAndFinish("Uygulama başlatılırken hata oluştu");
        }
    }

    private void initializeComponents() {
        initializeViews();
        initializeManagers();
        initializeMapFragment();
        setupClickListeners();
        initializeTextToSpeech();
    }

    private void initializeViews() {
        try {
            // Statistics views
            tvOlumluCount = findViewById(R.id.tvOlumluCount);
            tvYaraliCount = findViewById(R.id.tvYaraliCount);
            tvTotalCount = findViewById(R.id.tvTotalCount);

            // Layout views
            loadingPanel = findViewById(R.id.loadingPanel);
            warningLayout = findViewById(R.id.warningLayoutContainer);
            infoPanelContainer = findViewById(R.id.infoPanelContainer);

            // Button views
            fabBack = findViewById(R.id.fabBack);
            fabToggleInfo = findViewById(R.id.fabToggleInfo);
            infoButton = findViewById(R.id.infoButton);

            // Weather and speed info views
            weatherIcon = findViewById(R.id.weatherIcon);
            weatherText = findViewById(R.id.weatherText);
            speedIcon = findViewById(R.id.speedIcon);
            speedText = findViewById(R.id.speedText);
            tvCurrentSpeed = findViewById(R.id.tvCurrentSpeed);
            btnIncreaseSpeed = findViewById(R.id.btnIncreaseSpeed);
            btnDecreaseSpeed = findViewById(R.id.btnDecreaseSpeed);

            updateSpeedDisplay();

            btnIncreaseSpeed.setOnClickListener(v -> {
                simulatedSpeed += 10;
                updateSpeedDisplay();
            });

            btnDecreaseSpeed.setOnClickListener(v -> {
                simulatedSpeed = Math.max(0, simulatedSpeed - 10);
                updateSpeedDisplay();
            });

            validateCriticalViews();
        } catch (Exception e) {
            Log.e(TAG, "View initialization hatası: " + e.getMessage(), e);
            throw new RuntimeException("Kritik view'lar yüklenemedi", e);
        }
    }

    private void updateSpeedDisplay() {
        tvCurrentSpeed.setText("Speed: " + simulatedSpeed + " km/h");
    }
    public static float getSimulatedSpeed() {
        return simulatedSpeed;
    }


    private void validateCriticalViews() {
        if (loadingPanel == null) {
            throw new IllegalStateException("loadingPanel bulunamadı");
        }
        if (fabBack == null) {
            throw new IllegalStateException("fabBack bulunamadı");
        }
    }

    private void initializeManagers() {
        try {
            firebaseDataManager = new FirebaseDataManager(this);
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
            Log.d(TAG, "Manager'lar başarıyla başlatıldı");
        } catch (Exception e) {
            Log.e(TAG, "Manager initialization hatası: " + e.getMessage(), e);
            throw new RuntimeException("Manager'lar başlatılamadı", e);
        }
    }

    private void initializeMapFragment() {
        try {
            SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                    .findFragmentById(R.id.map);

            if (mapFragment == null) {
                throw new IllegalStateException("Map fragment bulunamadı");
            }

            mapFragment.getMapAsync(this);
        } catch (Exception e) {
            Log.e(TAG, "Map fragment initialization hatası: " + e.getMessage(), e);
            showErrorAndFinish("Harita yüklenemedi");
        }
    }

    private void setupClickListeners() {
        try {
            setupBackButton();
            setupMyLocationButton();
            setupWeatherInfoButtons();
        } catch (Exception e) {
            Log.e(TAG, "Click listener setup hatası: " + e.getMessage(), e);
        }
    }

    private void setupBackButton() {
        if (fabBack != null) {
            fabBack.setOnClickListener(v -> finish());
        }
    }

    private void setupMyLocationButton() {
        View fabMyLocation = findViewById(R.id.fabMyLocation);
        if (fabMyLocation != null) {
            fabMyLocation.setOnClickListener(v -> handleMyLocationClick());
        }
    }

    private void setupWeatherInfoButtons() {
        if (fabToggleInfo != null) {
            fabToggleInfo.setOnClickListener(v -> {
                if (weatherSpeedManager != null) {
                    weatherSpeedManager.toggleInfoPanel();
                }
            });
        }

        if (infoButton != null) {
            infoButton.setOnClickListener(v -> {
                if (weatherSpeedManager != null) {
                    weatherSpeedManager.showDetailedInfo();
                }
            });
        }
    }

    private void handleMyLocationClick() {
        try {
            if (manualLocationChange) {
                returnToRealLocation();
            } else {
                focusOnCurrentLocation();
            }
        } catch (Exception e) {
            Log.e(TAG, "My location click hatası: " + e.getMessage(), e);
            Toast.makeText(this, "Konum işlemi başarısız", Toast.LENGTH_SHORT).show();
        }
    }

    private void returnToRealLocation() {
        manualLocationChange = false;
        locationUpdatesEnabled = true;
        getLastKnownLocation();
        startSimulatedMovement();
        Toast.makeText(this, "Gerçek konumunuza döndünüz", Toast.LENGTH_SHORT).show();
    }

    private void focusOnCurrentLocation() {
        if (mapManager != null) {
            mapManager.testProximitySystem();
        }

        LatLng targetLocation = getCurrentLocationForCamera();
        if (targetLocation != null && mMap != null) {
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(targetLocation, 15));
        } else {
            Toast.makeText(this, "Konum bilgisi bulunamadı", Toast.LENGTH_SHORT).show();
        }
    }

    private LatLng getCurrentLocationForCamera() {
        if (currentLocation != null) {
            return currentLocation;
        }
        if (lastKnownLocation != null) {
            return new LatLng(lastKnownLocation.getLatitude(), lastKnownLocation.getLongitude());
        }
        return null;
    }

    private void initializeTextToSpeech() {
        try {
            tts = new TextToSpeech(this, status -> {
                if (status == TextToSpeech.SUCCESS) {
                    setupTurkishLanguage();
                } else {
                    Log.e(TAG, "TTS başlatılamadı");
                    ttsReady = false;
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "TTS initialization hatası: " + e.getMessage(), e);
        }
    }

    private void setupTurkishLanguage() {
        int result = tts.setLanguage(new Locale("tr", "TR"));
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.e(TAG, "TTS: Türkçe dili desteklenmiyor");
            ttsReady = false;
        } else {
            ttsReady = true;
            Log.d(TAG, "TTS başarıyla başlatıldı");
        }
    }

    private void requestLocationPermissionIfNeeded() {
        if (!hasLocationPermission()) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
        } else {
            getLastKnownLocation();
        }
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getLastKnownLocation();
            } else {
                Toast.makeText(this, "Konum izni gereklidir", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        try {
            Log.d(TAG, "onMapReady çağrıldı");
            mMap = googleMap;

            setupMapManagers();
            configureMap();
            loadKazaData();
            enableMyLocationIfPermitted();

        } catch (Exception e) {
            Log.e(TAG, "onMapReady hatası: " + e.getMessage(), e);
            Toast.makeText(this, "Harita yüklenirken hata oluştu", Toast.LENGTH_SHORT).show();
        }
    }

    private void setupMapManagers() {
        if (warningLayout != null) {
            mapManager = new MapManager(this, mMap, warningLayout);
            setupWeatherSpeedManager();
        }
    }

    private void setupWeatherSpeedManager() {
        if (infoPanelContainer != null && weatherIcon != null && weatherText != null &&
                speedIcon != null && speedText != null) {

            weatherSpeedManager = new WeatherSpeedInfoManager(
                    this, infoPanelContainer, weatherIcon, weatherText, speedIcon, speedText
            );

            // Cross-references
            mapManager.setWeatherSpeedInfoManager(weatherSpeedManager);
            weatherSpeedManager.setMapManager(mapManager);

            // Weather warning listener
            weatherSpeedManager.setWeatherWarningListener(this::handleWeatherWarning);

            // Weather warning layout
            LinearLayout weatherWarningLayout = findViewById(R.id.weatherWarningLayout);
            if (weatherWarningLayout != null) {
                mapManager.setWeatherWarningLayout(weatherWarningLayout);
            }
        }
    }

    private void handleWeatherWarning(String weatherDescription) {
        String message = "Hava durumu " + weatherDescription + ". Lütfen dikkatli sürünüz.";
        showWeatherWarningCard(message);
        speakWeatherWarning("Hava " + weatherDescription + ". Lütfen dikkatli sürünüz.");
    }

    private void configureMap() {
        mMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
        mMap.getUiSettings().setZoomControlsEnabled(true);
        mMap.getUiSettings().setCompassEnabled(true);
        mMap.getUiSettings().setMyLocationButtonEnabled(true);
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(KAYSERI_CENTER, 12));

        mMap.setOnMapClickListener(this);
        mMap.setOnMyLocationChangeListener(this);
    }

    private void loadKazaData() {
        showLoading(true);

        if (firebaseDataManager != null) {
            firebaseDataManager.loadKazaData(this::onKazaDataLoaded);
        }
    }

    private void onKazaDataLoaded(List<KazaData> kazaDataList) {
        try {
            if (mapManager != null && kazaDataList != null) {
                for (KazaData kaza : kazaDataList) {
                    mapManager.addKazaMarker(kaza);
                }
            }

            showLoading(false);
            updateStatistics(kazaDataList);
            Toast.makeText(this, "Kaza verileri yüklendi", Toast.LENGTH_SHORT).show();

            playWelcomeMessage();

        } catch (Exception e) {
            Log.e(TAG, "Kaza verileri yükleme hatası: " + e.getMessage(), e);
            showLoading(false);
            Toast.makeText(this, "Kaza verileri yüklenirken hata oluştu", Toast.LENGTH_SHORT).show();
        }
    }

    private void playWelcomeMessage() {
        if (ttsReady && tts != null) {
            tts.speak("Yolunuz açık olsun", TextToSpeech.QUEUE_FLUSH, null, "YolunuzAcikOlsunID");
        }
    }

    private void enableMyLocationIfPermitted() {
        try {
            if (hasLocationPermission()) {
                if (mMap != null) {
                    if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        // TODO: Consider calling
                        //    ActivityCompat#requestPermissions
                        // here to request the missing permissions, and then overriding
                        //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                        //                                          int[] grantResults)
                        // to handle the case where the user grants the permission. See the documentation
                        // for ActivityCompat#requestPermissions for more details.
                        return;
                    }
                    mMap.setMyLocationEnabled(true);
                }
                startLocationUpdates();
            }
        } catch (Exception e) {
            Log.e(TAG, "enableMyLocationIfPermitted hatası: " + e.getMessage(), e);
        }
    }

    private void startLocationUpdates() {
        if (!hasLocationPermission() || isLocationUpdateInProgress || fusedLocationClient == null) {
            return;
        }

        try {
            isLocationUpdateInProgress = true;
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return;
            }
            fusedLocationClient.getLastLocation()
                    .addOnSuccessListener(this, this::onLocationReceived)
                    .addOnFailureListener(this::onLocationFailed);
        } catch (Exception e) {
            Log.e(TAG, "startLocationUpdates hatası: " + e.getMessage(), e);
            isLocationUpdateInProgress = false;
        }
    }

    private void onLocationReceived(Location location) {
        try {
            if (location != null) {
                lastKnownLocation = location;
                updateLocationOnMap(location);
                locationUpdatesEnabled = true;
                startSimulatedMovement();
            } else {
                Log.w(TAG, "getLastLocation returned null");
            }
        } catch (Exception e) {
            Log.e(TAG, "Location success callback hatası: " + e.getMessage(), e);
        } finally {
            isLocationUpdateInProgress = false;
        }
    }

    private void onLocationFailed(Exception e) {
        Log.e(TAG, "getLastLocation failed: " + e.getMessage());
        isLocationUpdateInProgress = false;
    }

    private void updateLocationOnMap(Location location) {
        try {
            if (mapManager != null && firebaseDataManager != null) {
                mapManager.updateLocationOnMap(location, firebaseDataManager.getKazaDataList());
            }

            if (weatherSpeedManager != null && firebaseDataManager != null) {
                weatherSpeedManager.updateLocation(location, firebaseDataManager.getKazaDataList());
            }

            if (!manualLocationChange) {
                lastKnownLocation = location;
                currentLocation = new LatLng(location.getLatitude(), location.getLongitude());
            }
        } catch (Exception e) {
            Log.e(TAG, "updateLocationOnMap hatası: " + e.getMessage(), e);
        }
    }

    @Override
    public void onMapClick(LatLng latLng) {
        try {
            if (mapManager != null) {
                mapManager.onMapClick(latLng);
            }

            handleManualLocationSelection(latLng);
        } catch (Exception e) {
            Log.e(TAG, "onMapClick hatası: " + e.getMessage(), e);
        }
    }

    private void handleManualLocationSelection(LatLng latLng) {
        manualLocationChange = true;
        stopSimulatedMovement();

        if (weatherSpeedManager != null && firebaseDataManager != null) {
            weatherSpeedManager.updateLocationFromMapSelection(
                    latLng.latitude, latLng.longitude, firebaseDataManager.getKazaDataList()
            );
        }

        Toast.makeText(this, String.format("Manuel konum seçildi: %.6f, %.6f",
                latLng.latitude, latLng.longitude), Toast.LENGTH_SHORT).show();
    }

    private void showLoading(boolean show) {
        try {
            if (loadingPanel != null) {
                loadingPanel.setVisibility(show ? View.VISIBLE : View.GONE);
            }
        } catch (Exception e) {
            Log.e(TAG, "showLoading hatası: " + e.getMessage(), e);
        }
    }

    private void updateStatistics(List<KazaData> kazaDataList) {
        try {
            if (kazaDataList == null) return;

            int olumluCount = 0;
            int yaraliCount = 0;

            for (KazaData kaza : kazaDataList) {
                if (kaza != null) {
                    if ("olumlu".equals(kaza.kazaTuru)) {
                        olumluCount++;
                    } else if ("yarali".equals(kaza.kazaTuru)) {
                        yaraliCount++;
                    }
                }
            }

            updateStatisticsUI(olumluCount, yaraliCount);
        } catch (Exception e) {
            Log.e(TAG, "updateStatistics hatası: " + e.getMessage(), e);
        }
    }

    private void updateStatisticsUI(int olumluCount, int yaraliCount) {
        if (tvOlumluCount != null) {
            tvOlumluCount.setText(String.valueOf(olumluCount));
        }
        if (tvYaraliCount != null) {
            tvYaraliCount.setText(String.valueOf(yaraliCount));
        }
        if (tvTotalCount != null) {
            tvTotalCount.setText(String.valueOf(olumluCount + yaraliCount));
        }
    }

    private void startSimulatedMovement() {
        if (manualLocationChange || lastKnownLocation == null) {
            Log.d(TAG, "Simulated movement not started - conditions not met");
            return;
        }

        try {
            simulateMovement = this::performSimulatedMovement;
            handler.postDelayed(simulateMovement, SIMULATED_MOVEMENT_INTERVAL_MS);
        } catch (Exception e) {
            Log.e(TAG, "startSimulatedMovement hatası: " + e.getMessage(), e);
        }
    }

    private void performSimulatedMovement() {
        try {
            if (!shouldContinueSimulation()) return;

            Location simulatedLocation = generateSimulatedLocation();

            isLocationUpdateInProgress = true;
            updateLocationOnMap(simulatedLocation);
            isLocationUpdateInProgress = false;

            scheduleNextSimulation();
        } catch (Exception e) {
            Log.e(TAG, "Simulated movement hatası: " + e.getMessage(), e);
            isLocationUpdateInProgress = false;
        }
    }

    private boolean shouldContinueSimulation() {
        return locationUpdatesEnabled && lastKnownLocation != null &&
                !manualLocationChange && !isLocationUpdateInProgress;
    }

    private Location generateSimulatedLocation() {
        Random random = new Random();
        double latOffset = (random.nextDouble() - 0.5) / 1000;
        double lngOffset = (random.nextDouble() - 0.5) / 1000;

        Location simulatedLocation = new Location("simulated");
        simulatedLocation.setLatitude(lastKnownLocation.getLatitude() + latOffset);
        simulatedLocation.setLongitude(lastKnownLocation.getLongitude() + lngOffset);

        return simulatedLocation;
    }

    private void scheduleNextSimulation() {
        if (handler != null && simulateMovement != null) {
            handler.postDelayed(simulateMovement, SIMULATED_MOVEMENT_INTERVAL_MS);
        }
    }

    public void stopSimulatedMovement() {
        try {
            if (simulateMovement != null && handler != null) {
                handler.removeCallbacks(simulateMovement);
                locationUpdatesEnabled = false;
                Log.d(TAG, "Simulated movement stopped");
            }
        } catch (Exception e) {
            Log.e(TAG, "stopSimulatedMovement hatası: " + e.getMessage(), e);
        }
    }

    @Override
    public void onMyLocationChange(Location location) {
        try {
            if (!manualLocationChange) {
                updateLocationOnMap(location);
            }
        } catch (Exception e) {
            Log.e(TAG, "onMyLocationChange hatası: " + e.getMessage(), e);
        }
    }

    private void getLastKnownLocation() {
        if (!hasLocationPermission()) {
            Log.w(TAG, "Konum izni verilmemiş");
            return;
        }

        try {
            if (fusedLocationClient != null) {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    // TODO: Consider calling
                    //    ActivityCompat#requestPermissions
                    // here to request the missing permissions, and then overriding
                    //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                    //                                          int[] grantResults)
                    // to handle the case where the user grants the permission. See the documentation
                    // for ActivityCompat#requestPermissions for more details.
                    return;
                }
                fusedLocationClient.getLastLocation()
                        .addOnSuccessListener(this, location -> {
                            if (location != null) {
                                updateLocationOnMap(location);
                                startSimulatedMovement();
                            } else {
                                Log.w(TAG, "getLastKnownLocation returned null");
                            }
                        })
                        .addOnFailureListener(this, e ->
                                Log.e(TAG, "getLastKnownLocation failed", e));
            }
        } catch (Exception e) {
            Log.e(TAG, "getLastKnownLocation hatası: " + e.getMessage(), e);
        }
    }

    private void speakWeatherWarning(String message) {
        if (ttsReady && tts != null) {
            tts.speak(message, TextToSpeech.QUEUE_FLUSH, null, "HavaUyarisiID");
        }
    }

    private void showWeatherWarningCard(String message) {
        try {
            LinearLayout weatherWarningLayout = findViewById(R.id.weatherWarningLayout);
            if (weatherWarningLayout != null) {
                weatherWarningLayout.setVisibility(View.VISIBLE);

                TextView warningTextView = findViewById(R.id.weatherWarningTextView);
                if (warningTextView != null) {
                    warningTextView.setText(message);
                }

                ImageView closeButton = findViewById(R.id.closeWeatherWarning);
                if (closeButton != null) {
                    closeButton.setOnClickListener(v ->
                            weatherWarningLayout.setVisibility(View.GONE));
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Hava uyarı kartı gösterilirken hata: " + e.getMessage(), e);
        }
    }

    private void showErrorAndFinish(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        finish();
    }

    public void setManualLocationChange(boolean isManual) {
        this.manualLocationChange = isManual;
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            pauseActivities();
        } catch (Exception e) {
            Log.e(TAG, "onPause hatası: " + e.getMessage(), e);
        }
    }

    private void pauseActivities() {
        if (mapManager != null) {
            mapManager.hideWarning();
        }
        if (handler != null && simulateMovement != null) {
            handler.removeCallbacks(simulateMovement);
        }
        if (tts != null) {
            tts.stop();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            cleanupResources();
        } catch (Exception e) {
            Log.e(TAG, "onDestroy hatası: " + e.getMessage(), e);
        }
    }

    private void cleanupResources() {
        if (mapManager != null) {
            mapManager.hideWarning();
        }
        if (handler != null && simulateMovement != null) {
            handler.removeCallbacks(simulateMovement);
        }
        if (tts != null) {
            tts.shutdown();
        }
        if (weatherSpeedManager != null) {
            weatherSpeedManager.clearWeatherWarning();
        }
    }


}