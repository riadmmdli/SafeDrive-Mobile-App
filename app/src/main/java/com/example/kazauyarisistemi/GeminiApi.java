package com.example.kazauyarisistemi;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class GeminiApi {
    private static final String TAG = "GeminiApi";
    private static final String API_KEY = "AIzaSyAalrV3bMrllyaJb20nZvwhQGljvmY4u-8";
    // API key artık URL'de değil, sadece base endpoint kullanılıyor
    private static final String ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent";
    private static final int TIMEOUT = 30000; // 30 saniye timeout

    public static String generateText(String prompt) {
        HttpURLConnection connection = null;
        try {
            Log.d(TAG, "Gemini API çağrısı başlatılıyor: " + prompt.substring(0, Math.min(100, prompt.length())));

            // JSON request body oluştur
            JSONObject requestBody = new JSONObject();

            // Generation config ekle
            JSONObject generationConfig = new JSONObject();
            generationConfig.put("temperature", 0.7);
            generationConfig.put("topK", 1);
            generationConfig.put("topP", 1);
            generationConfig.put("maxOutputTokens", 2048);
            requestBody.put("generationConfig", generationConfig);

            // Safety settings ekle
            JSONArray safetySettings = new JSONArray();
            String[] categories = {
                    "HARM_CATEGORY_HARASSMENT",
                    "HARM_CATEGORY_HATE_SPEECH",
                    "HARM_CATEGORY_SEXUALLY_EXPLICIT",
                    "HARM_CATEGORY_DANGEROUS_CONTENT"
            };
            for (String category : categories) {
                JSONObject safety = new JSONObject();
                safety.put("category", category);
                safety.put("threshold", "BLOCK_MEDIUM_AND_ABOVE");
                safetySettings.put(safety);
            }
            requestBody.put("safetySettings", safetySettings);

            // Contents array oluştur
            JSONArray contentsArray = new JSONArray();
            JSONObject contentObject = new JSONObject();

            JSONArray partsArray = new JSONArray();
            JSONObject partObject = new JSONObject();
            partObject.put("text", prompt);
            partsArray.put(partObject);

            contentObject.put("parts", partsArray);
            contentsArray.put(contentObject);
            requestBody.put("contents", contentsArray);

            Log.d(TAG, "Request body: " + requestBody.toString());

            // HTTP bağlantısı kur
            URL url = new URL(ENDPOINT);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            // API key header olarak ekleniyor!
            connection.setRequestProperty("X-goog-api-key", API_KEY);
            connection.setRequestProperty("User-Agent", "KazaUyariSistemi/1.0");
            connection.setDoOutput(true);
            connection.setConnectTimeout(TIMEOUT);
            connection.setReadTimeout(TIMEOUT);

            // İstek gövdesini gönder
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = requestBody.toString().getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
                os.flush();
            }

            // Response code kontrol et
            int responseCode = connection.getResponseCode();
            Log.d(TAG, "Response code: " + responseCode);

            BufferedReader reader;
            if (responseCode >= 200 && responseCode < 300) {
                reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
            } else {
                reader = new BufferedReader(new InputStreamReader(connection.getErrorStream(), StandardCharsets.UTF_8));
                StringBuilder errorResponse = new StringBuilder();
                String errorLine;
                while ((errorLine = reader.readLine()) != null) {
                    errorResponse.append(errorLine);
                }
                reader.close();
                Log.e(TAG, "API Error Response (" + responseCode + "): " + errorResponse.toString());

                switch (responseCode) {
                    case 400:
                        return "⚠️ Geçersiz istek. Lütfen tekrar deneyin.";
                    case 401:
                        return "⚠️ API anahtarı geçersiz.";
                    case 403:
                        return "⚠️ API erişimi reddedildi.";
                    case 404:
                        return "⚠️ API endpoint bulunamadı.";
                    case 429:
                        return "⚠️ Çok fazla istek. Daha sonra deneyin.";
                    case 500:
                        return "⚠️ Sunucu hatası. Daha sonra deneyin.";
                    default:
                        return "⚠️ Bilinmeyen hata (" + responseCode + ")";
                }
            }

            StringBuilder responseBuilder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                responseBuilder.append(line);
            }
            reader.close();

            String responseString = responseBuilder.toString();
            Log.d(TAG, "Response: " + responseString);

            JSONObject responseJson = new JSONObject(responseString);

            // candidates array kontrolü
            if (!responseJson.has("candidates")) {
                Log.e(TAG, "Response'da candidates array yok");
                return "⚠️ Geçersiz API yanıtı.";
            }

            JSONArray candidates = responseJson.getJSONArray("candidates");
            if (candidates.length() == 0) {
                Log.e(TAG, "Candidates array boş");
                return "⚠️ API'den yanıt alınamadı.";
            }

            JSONObject candidate = candidates.getJSONObject(0);

            // Finish reason kontrolü
            if (candidate.has("finishReason")) {
                String finishReason = candidate.getString("finishReason");
                if ("SAFETY".equals(finishReason)) {
                    Log.w(TAG, "Content blocked by safety filters");
                    return "⚠️ Güvenlik filtreleri tarafından engellenmiş içerik.";
                }
            }

            // Content kontrolü
            if (!candidate.has("content")) {
                Log.e(TAG, "Candidate'da content yok");
                return "⚠️ İçerik bulunamadı.";
            }

            JSONObject content = candidate.getJSONObject("content");
            if (!content.has("parts")) {
                Log.e(TAG, "Content'da parts yok");
                return "⚠️ İçerik parçaları bulunamadı.";
            }

            JSONArray parts = content.getJSONArray("parts");
            if (parts.length() == 0) {
                Log.e(TAG, "Parts array boş");
                return "⚠️ İçerik parçaları boş.";
            }

            JSONObject part = parts.getJSONObject(0);
            if (!part.has("text")) {
                Log.e(TAG, "Part'da text yok");
                return "⚠️ Metin bulunamadı.";
            }

            String resultText = part.getString("text").trim();
            Log.d(TAG, "Gemini API başarılı: " + resultText);

            return resultText.isEmpty() ? "⚠️ Boş yanıt alındı." : resultText;

        } catch (java.net.SocketTimeoutException e) {
            Log.e(TAG, "Timeout hatası: " + e.getMessage());
            return "⚠️ Bağlantı zaman aşımı.";
        } catch (java.net.UnknownHostException e) {
            Log.e(TAG, "Network hatası: " + e.getMessage());
            return "⚠️ İnternet bağlantısı yok.";
        } catch (Exception e) {
            Log.e(TAG, "Gemini API çağrısında hata: " + e.getMessage(), e);
            return "⚠️ Yapay zeka uyarı mesajı alınamadı.";
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    // Test metodu
    public static void testApi() {
        new Thread(() -> {
            String result = generateText("Test mesajı: Merhaba, bu bir test.");
            Log.d(TAG, "Test result: " + result);
        }).start();
    }
}
