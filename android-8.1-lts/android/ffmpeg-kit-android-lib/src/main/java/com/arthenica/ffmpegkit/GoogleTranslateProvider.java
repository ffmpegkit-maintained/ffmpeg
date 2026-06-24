/*
 * GoogleTranslateProvider — ready-to-use TranslationProvider backed by the
 * Google Cloud Translation API v2 (Basic edition, also called "Cloud Translation API").
 *
 * Requires a Google Cloud API key with the Cloud Translation API enabled.
 * Pricing: https://cloud.google.com/translate/pricing
 * (first 500 000 characters/month are free under the Basic edition)
 *
 * Usage:
 *
 *   TranslationProvider google = new GoogleTranslateProvider("YOUR_GOOGLE_API_KEY");
 *
 *   String frenchSrt = whisperKit.transcribeToSrtAndTranslate(pcm, google, "fr");
 *
 * Target language codes are standard BCP-47 lowercase ISO 639-1 codes:
 *   "fr" = French, "es" = Spanish, "de" = German, "ja" = Japanese,
 *   "zh-CN" = Chinese (Simplified), "ar" = Arabic, etc.
 * See https://cloud.google.com/translate/docs/languages for the full list.
 *
 * How to get an API key:
 *   1. Go to https://console.cloud.google.com
 *   2. Enable "Cloud Translation API" for your project
 *   3. Create an API key under APIs & Services → Credentials
 *   4. Restrict the key to the Cloud Translation API for production use
 */
package com.arthenica.ffmpegkit;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class GoogleTranslateProvider implements TranslationProvider {

    private static final String API_URL   = "https://translation.googleapis.com/language/translate/v2";
    private static final int    TIMEOUT_MS = 15_000;

    private final String apiKey;

    /**
     * @param apiKey your Google Cloud API key with Cloud Translation API enabled
     */
    public GoogleTranslateProvider(String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public String translate(String text, String targetLanguage) throws IOException {
        String endpointUrl = API_URL + "?key=" + apiKey;

        JSONObject payload = new JSONObject();
        try {
            payload.put("q",      text);
            payload.put("target", targetLanguage.toLowerCase());
            payload.put("format", "text");
        } catch (Exception e) {
            throw new IOException("Failed to build Google Translate request: " + e.getMessage(), e);
        }

        byte[] bodyBytes = payload.toString().getBytes(StandardCharsets.UTF_8);

        HttpURLConnection conn = (HttpURLConnection) new URL(endpointUrl).openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("Content-Length", String.valueOf(bodyBytes.length));
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setDoOutput(true);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(bodyBytes);
            }

            int code = conn.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                StringBuilder err = new StringBuilder();
                java.io.InputStream es = conn.getErrorStream();
                if (es != null) {
                    try (BufferedReader br = new BufferedReader(
                            new InputStreamReader(es, StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = br.readLine()) != null) err.append(line);
                    }
                }
                throw new IOException("Google Translate returned HTTP " + code
                        + (err.length() > 0 ? ": " + err : ""));
            }

            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
            }

            // Response: {"data":{"translations":[{"translatedText":"...","detectedSourceLanguage":"en"}]}}
            JSONArray translations = new JSONObject(sb.toString())
                    .getJSONObject("data")
                    .getJSONArray("translations");
            return translations.getJSONObject(0).getString("translatedText");

        } catch (Exception e) {
            if (e instanceof IOException) throw (IOException) e;
            throw new IOException("Google Translate failed: " + e.getMessage(), e);
        } finally {
            conn.disconnect();
        }
    }
}
