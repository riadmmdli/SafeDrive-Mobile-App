package com.example.kazauyarisistemi;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;
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

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback {

    private static final String TAG = "MapsActivity";
    private GoogleMap mMap;
    private DatabaseReference mDatabase;

    // Kayseri merkez koordinatları
    private static final LatLng KAYSERI_CENTER = new LatLng(38.7312, 35.4787);

    // UI elementleri
    private TextView tvOlumluCount, tvYaraliCount, tvTotalCount;
    private LinearLayout loadingPanel;
    private FloatingActionButton fabBack;

    private int olumluMarkerCount = 0;
    private int yaraliMarkerCount = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        // UI elementlerini bağla
        initializeViews();

        // Firebase referansını al
        mDatabase = FirebaseDatabase.getInstance().getReference();

        // Harita fragment'ini başlat
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        // Geri dön butonu
        fabBack.setOnClickListener(v -> finish());
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
        Log.d(TAG, "onMapReady çağrıldı");
        mMap = googleMap;

        if (mMap == null) {
            Log.e(TAG, "GoogleMap null!");
            return;
        }
        mMap = googleMap;

        // Harita ayarları
        mMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
        mMap.getUiSettings().setZoomControlsEnabled(true);
        mMap.getUiSettings().setCompassEnabled(true);
        mMap.getUiSettings().setMyLocationButtonEnabled(true);

        // Kayseri'ye odaklan
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(KAYSERI_CENTER, 12));

        // Marker tıklama olayı
        mMap.setOnMarkerClickListener(marker -> {
            String title = marker.getTitle();
            if (title != null && title.contains("KAZA")) {
                showKazaDetails(marker);
                return true;
            }
            return false;
        });

        // Kaza verilerini yükle
        loadKazaData();
    }

    private void loadKazaData() {
        showLoading(true);
        Toast.makeText(this, "Kaza verileri yükleniyor...", Toast.LENGTH_SHORT).show();

        // Önce ölümlü kazaları yükle
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

                // Yaralı kazaları yükle
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
            // Koordinatları al
            Object xObj = kazaSnapshot.child("X").getValue();
            Object yObj = kazaSnapshot.child("Y").getValue();

            if (xObj == null || yObj == null) {
                Log.w(TAG, "Koordinat bilgisi eksik: " + kazaSnapshot.getKey());
                return;
            }

            double x, y;

            // Koordinat verilerini double'a çevir
            if (xObj instanceof String) {
                x = Double.parseDouble((String) xObj);
            } else if (xObj instanceof Number) {
                x = ((Number) xObj).doubleValue();
            } else {
                Log.w(TAG, "Geçersiz X koordinatı: " + xObj);
                return;
            }

            if (yObj instanceof String) {
                y = Double.parseDouble((String) yObj);
            } else if (yObj instanceof Number) {
                y = ((Number) yObj).doubleValue();
            } else {
                Log.w(TAG, "Geçersiz Y koordinatı: " + yObj);
                return;
            }

            // Koordinat kontrolü (Kayseri sınırları)
            if (x < 34.0 || x > 37.0 || y < 37.5 || y > 39.5) {
                Log.w(TAG, "Kayseri sınırları dışında koordinat: " + x + "," + y);
                return;
            }

            LatLng position = new LatLng(y, x); // Dikkat: Y=latitude, X=longitude

            // Kaza bilgilerini al
            String ilce = getStringValue(kazaSnapshot, "ILCE");
            String mahalle = getStringValue(kazaSnapshot, "MAHALLE");
            String yol = getStringValue(kazaSnapshot, "YOL");
            String saat = getStringValue(kazaSnapshot, "KAZA SAAT");
            String dakika = getStringValue(kazaSnapshot, "KAZA DAKIKA");

            // Marker title ve snippet oluştur
            String title = kazaTuru.equals("olumlu") ? "🔴 ÖLÜMLÜ KAZA" : "🟡 YARALI KAZA";
            String snippet = String.format("%s - %s\n%s\nSaat: %s:%s",
                    ilce != null ? ilce : "Bilinmiyor",
                    mahalle != null ? mahalle : "Bilinmiyor",
                    yol != null && !yol.isEmpty() ? yol : "Yol bilgisi yok",
                    saat != null ? saat : "?",
                    dakika != null ? dakika : "?");

            // Marker rengini belirle
            float markerColor = kazaTuru.equals("olumlu") ?
                    BitmapDescriptorFactory.HUE_RED :
                    BitmapDescriptorFactory.HUE_YELLOW;

            // Marker'ı haritaya ekle
            MarkerOptions markerOptions = new MarkerOptions()
                    .position(position)
                    .title(title)
                    .snippet(snippet)
                    .icon(BitmapDescriptorFactory.defaultMarker(markerColor));

            Marker marker = mMap.addMarker(markerOptions);

            // Marker'a kaza verilerini tag olarak ekle
            if (marker != null) {
                marker.setTag(new KazaData(kazaSnapshot, kazaTuru));
            }

            // Sayacı artır
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

        // Detaylı bilgi dialog'u göster
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

    // Kaza verilerini tutan sınıf
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
}