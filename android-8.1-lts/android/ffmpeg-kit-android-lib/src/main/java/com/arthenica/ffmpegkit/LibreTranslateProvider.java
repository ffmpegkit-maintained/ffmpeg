/*
 * LibreTranslateProvider — ready-to-use TranslationProvider backed by any
 * LibreTranslate-compatible REST API (self-hosted or a public instance).
 *
 * LibreTranslate is open-source and free to self-host:
 *   https://github.com/LibreTranslate/LibreTranslate
 *
 * Several public instances exist (some free, some require an API key).
 * By default this class targets https://libretranslate.com.
 *
 * Usage — public instance (no key):
 *
 *   TranslationProvider lt = new LibreTranslateProvider();
 *   String frenchSrt = whisperKit.transcribeToSrtAndTranslate(pcm, lt, "fr");
 *
 * Usage — custom server with an API key:
 *
 *   TranslationProvider lt = new LibreTranslateProvider(
 *       "https://your-server.example.com", "YOUR_API_KEY");
 *
 * Target language codes are standard BCP-47 lowercase ISO 639-1 codes:
 *   "fr" = French, "es" = Spanish, "de" = German, "ja" = Japanese, etc.
 * See https://libretranslate.com/languages for the supported list on the
 * default public instance.
 */
package com.arthenica.ffmpegkit;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class LibreTranslateProvider implements TranslationProvider {

    private static final String DEFAULT_SERVER = "https://libretranslate.com";
    private static final int    TIMEOUT_MS     = 15_000;

    private final String serverUrl;
    private final String apiKey;

    /** Use the default public LibreTranslate instance without an API key. */
    public LibreTranslateProvider() {
        this(DEFAULT_SERVER, null);
    }

    /**
     * Use a specific LibreTranslate server without an API key.
     *
     * @param serverUrl base URL of the LibreTranslate instance, e.g.
     *                  {@code "https://your-server.example.com"}
     */
    public LibreTranslateProvider(String serverUrl) {
        this(serverUrl, null);
    }

    /**
     * Use a specific LibreTranslate server with an API key.
     *
     * @param serverUrl base URL of the LibreTranslate instance
     * @param apiKey    the API key for the instance, or {@code null} if none
     */
    public LibreTranslateProvider(String serverUrl, String apiKey) {
        this.serverUrl = serverUrl.endsWith("/") ? serverUrl.substring(0, serverUrl.length() - 1) : serverUrl;
        this.apiKey    = apiKey;
    }

    @Override
    public String translate(String text, String targetLanguage) throws IOException {
        JSONObject payload = new JSONObject();
        try {
            payload.put("q",       text);
            payload.put("source",  "auto");
            payload.put("target",  targetLanguage.toLowerCase());
            payload.put("format",  "text");
            if (apiKey != null && !apiKey.isEmpty()) {
                payload.put("api_key", apiKey);
            }
        } catch (Exception e) {
            throw new IOException("Failed to build LibreTranslate request: " + e.getMessage(), e);
        }

        byte[] bodyBytes = payload.toString().getBytes(StandardCharsets.UTF_8);
        String translateUrl = serverUrl + "/translate";

        HttpURLConnection conn = (HttpURLConnection) new URL(translateUrl).openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
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
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null) err.append(line);
                }
                throw new IOException("LibreTranslate returned HTTP " + code + ": " + err);
            }

            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
            }

            // Response: {"translatedText":"..."}
            return new JSONObject(sb.toString()).getString("translatedText");

        } catch (Exception e) {
            if (e instanceof IOException) throw (IOException) e;
            throw new IOException("LibreTranslate translation failed: " + e.getMessage(), e);
        } finally {
            conn.disconnect();
        }
    }
}
