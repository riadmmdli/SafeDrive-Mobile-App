package com.example.kazauyarisistemi;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class FirebaseDataManager {
    private static final String TAG = "FirebaseDataManager";
    private DatabaseReference mDatabase;
    private int olumluMarkerCount = 0;
    private int yaraliMarkerCount = 0;
    public List<KazaData> kazaDataList = new ArrayList<>(); // public yapıldı
    private Context context;

    public FirebaseDataManager(Context context) {
        this.context = context;
        mDatabase = FirebaseDatabase.getInstance().getReference();
    }

    public void loadKazaData(OnKazaDataLoadedListener listener) {
        olumluMarkerCount = 0;
        yaraliMarkerCount = 0;
        kazaDataList.clear();

        loadOlumluKazalar(listener);
    }

    private void loadOlumluKazalar(OnKazaDataLoadedListener listener) {
        DatabaseReference olumluRef = mDatabase.child("kazalar").child("olumlu");
        Log.d(TAG, "Ölümlü kaza referansı: " + olumluRef.toString());

        olumluRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                Log.d(TAG, "Ölümlü kaza snapshot alındı. Çocuk sayısı: " + dataSnapshot.getChildrenCount());

                if (!dataSnapshot.exists()) {
                    Log.w(TAG, "Ölümlü kaza verisi bulunamadı!");
                }

                for (DataSnapshot batchSnapshot : dataSnapshot.getChildren()) {
                    Log.d(TAG, "Ölümlü batch işleniyor: " + batchSnapshot.getKey() +
                            ", Çocuk sayısı: " + batchSnapshot.getChildrenCount());
                    for (DataSnapshot kazaSnapshot : batchSnapshot.getChildren()) {
                        addKazaMarker(kazaSnapshot, "olumlu");
                    }
                }
                Log.d(TAG, "Toplam ölümlü marker: " + olumluMarkerCount);


                loadYaraliKazalar(listener);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e(TAG, "Ölümlü kaza yükleme hatası", databaseError.toException());
                Toast.makeText(context, // Context eklendi
                        "Ölümlü kaza yükleme hatası: " + databaseError.getMessage(),
                        Toast.LENGTH_LONG).show();
                loadYaraliKazalar(listener);
            }
        });
    }

    private void loadYaraliKazalar(OnKazaDataLoadedListener listener) {
        DatabaseReference yaraliRef = mDatabase.child("kazalar").child("yarali");
        Log.d(TAG, "Yaralı kaza referansı: " + yaraliRef.toString());

        yaraliRef.limitToFirst(1).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                Log.d(TAG, "Yaralı kaza snapshot alındı. Çocuk sayısı: " + dataSnapshot.getChildrenCount());

                if (!dataSnapshot.exists()) {
                    Log.w(TAG, "Yaralı kaza verisi bulunamadı!");
                    listener.onKazaDataLoaded(kazaDataList);
                    return;
                }


                for (DataSnapshot batchSnapshot : dataSnapshot.getChildren()) {
                    Log.d(TAG, "Yaralı batch işleniyor: " + batchSnapshot.getKey() +
                            ", Çocuk sayısı: " + batchSnapshot.getChildrenCount());

                    for (DataSnapshot kazaSnapshot : batchSnapshot.getChildren()) {
                        addKazaMarker(kazaSnapshot, "yarali");

                    }
                }



                listener.onKazaDataLoaded(kazaDataList);

            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e(TAG, "Yaralı kaza yükleme hatası", databaseError.toException());
                Toast.makeText(context, // context değişkeni burada da kullanılıyor
                        "Yaralı kaza yükleme hatası: " + databaseError.getMessage(),
                        Toast.LENGTH_LONG).show();
                listener.onKazaDataLoaded(kazaDataList);
            }
        });
    }
    public List<KazaData> getKazaDataList() {
        return kazaDataList;
    }

    private boolean addKazaMarker(DataSnapshot kazaSnapshot, String kazaTuru) {
        try {
            Object xObj = kazaSnapshot.child("X").getValue();
            Object yObj = kazaSnapshot.child("Y").getValue();

            if (xObj == null || yObj == null) {
                Log.w(TAG, "Koordinat bilgisi eksik: " + kazaSnapshot.getKey());
                return false;
            }

            double x, y;
            x = xObj instanceof String ? Double.parseDouble((String) xObj) : ((Number) xObj).doubleValue();
            y = yObj instanceof String ? Double.parseDouble((String) yObj) : ((Number) yObj).doubleValue();


            if (x < 34.0 || x > 37.0 || y < 37.5 || y > 39.5) {
                Log.w(TAG, kazaTuru + " kaza - Kayseri sınırları dışında koordinat: " + x + "," + y);
                return false;
            }


            KazaData kazaData = new KazaData(kazaSnapshot, kazaTuru);
            kazaDataList.add(kazaData);

            if (kazaTuru.equals("olumlu")) {
                olumluMarkerCount++;
            } else {
                yaraliMarkerCount++;
            }

            return true;

        } catch (Exception e) {
            Log.e(TAG, kazaTuru + " marker ekleme hatası: " + e.getMessage(), e);
            return false;
        }
    }



    public interface OnKazaDataLoadedListener {
        void onKazaDataLoaded(List<KazaData> kazaDataList);
    }
}