package com.example.kazauyarisistemi;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Haritayı Aç butonu
        Button btnOpenMap = findViewById(R.id.btnOpenMap);
        btnOpenMap.setOnClickListener(v -> {
            if (checkLocationPermissions()) {
                openMapsActivity();
            } else {
                requestLocationPermissions();
            }
        });

        // Hakkında butonu
        Button btnAbout = findViewById(R.id.btnAbout);
        btnAbout.setOnClickListener(v -> showAboutDialog());
    }

    private boolean checkLocationPermissions() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED;
    }

    private void requestLocationPermissions() {
        ActivityCompat.requestPermissions(this,
                new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                },
                LOCATION_PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openMapsActivity();
            } else {
                Toast.makeText(this, "Konum izni verilmedi. Harita özelliği çalışmayabilir.",
                        Toast.LENGTH_LONG).show();

            }
        }
    }

    private void openMapsActivity() {
        Intent intent = new Intent(this, MapsActivity.class);
        startActivity(intent);
    }

    private void showAboutDialog() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("🚨 Kaza Uyarı Sistemi")
                .setMessage("Bu uygulama Kayseri'deki trafik kazalarını harita üzerinde gösterir.\n\n" +
                        "🔴 Kırmızı işaretler: Ölümlü kazalar\n" +
                        "🟡 Sarı işaretler: Yaralı kazalar\n\n" +
                        "Herhangi bir işaret üzerine tıklayarak detayları görebilirsiniz.")
                .setPositiveButton("Tamam", null)
                .show();
    }
}