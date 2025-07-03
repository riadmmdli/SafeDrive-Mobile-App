package com.example.kazauyarisistemi;

import com.google.firebase.database.DataSnapshot;

public class KazaData {
    public String ilce, mahalle, yol, saat, dakika, kazaTuru;
    public String kazaTarihi, havaDurumu;
    public Integer yasalHizLimiti;
    public double x, y;

    public KazaData(DataSnapshot snapshot, String kazaTuru) {
        this.kazaTuru = kazaTuru;
        this.ilce = getStringValue(snapshot, "ILCE");
        this.mahalle = getStringValue(snapshot, "MAHALLE");
        this.yol = getStringValue(snapshot, "YOL");
        this.saat = getStringValue(snapshot, "KAZA SAAT");
        this.dakika = getStringValue(snapshot, "KAZA DAKIKA");
        this.kazaTarihi = getStringValue(snapshot, "KAZA TARIHI");
        this.havaDurumu = getStringValue(snapshot, "HAVA DURUMU");

        Object hizObj = snapshot.child("YASAL HIZ LIMITI").getValue();
        if (hizObj != null) {
            try {
                this.yasalHizLimiti = hizObj instanceof String ? Integer.parseInt((String) hizObj) : ((Number) hizObj).intValue();
            } catch (Exception e) {
                this.yasalHizLimiti = null;
            }
        }

        Object xObj = snapshot.child("X").getValue();
        Object yObj = snapshot.child("Y").getValue();

        this.x = xObj instanceof String ? Double.parseDouble((String) xObj) : ((Number) xObj).doubleValue();
        this.y = yObj instanceof String ? Double.parseDouble((String) yObj) : ((Number) yObj).doubleValue();
    }

    private static String getStringValue(DataSnapshot snapshot, String key) {
        Object value = snapshot.child(key).getValue();
        if (value != null && !value.toString().trim().isEmpty() && !value.toString().equals("null")) {
            return value.toString().trim();
        }
        return "Bilinmiyor";
    }
}