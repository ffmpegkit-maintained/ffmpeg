/*
 * DeepLTranslationProvider — ready-to-use TranslationProvider backed by the DeepL REST API.
 *
 * Works with both the DeepL Free tier (api-free.deepl.com) and the paid tier
 * (api.deepl.com). Requires only your DeepL Authentication Key — no extra
 * dependencies: uses Android's built-in HttpsURLConnection and org.json.
 *
 * Usage:
 *
 *   TranslationProvider deepl = new DeepLTranslationProvider("YOUR_DEEPL_API_KEY");
 *   // or, for the paid DeepL API:
 *   TranslationProvider deepl = new DeepLTranslationProvider("YOUR_DEEPL_API_KEY", false);
 *
 *   String frenchText = whisperKit.transcribeAndTranslate(pcm, deepl, "FR");
 *   String frenchSrt  = whisperKit.transcribeToSrtAndTranslate(pcm, deepl, "FR");
 *
 * Target language codes follow DeepL's convention (uppercase BCP-47 codes):
 *   "FR" = French, "ES" = Spanish, "DE" = German, "JA" = Japanese, "ZH" = Chinese, etc.
 * See https://developers.deepl.com/docs/resources/supported-languages for the full list.
 *
 * Free API key format ends in ":fx" — get one at https://www.deepl.com/pro-api
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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class DeepLTranslationProvider implements TranslationProvider {

    private static final String FREE_API_URL  = "https://api-free.deepl.com/v2/translate";
    private static final String PAID_API_URL  = "https://api.deepl.com/v2/translate";
    private static final int    TIMEOUT_MS    = 15_000;

    private final String apiKey;
    private final String endpoint;

    /**
     * Create a provider that auto-selects the Free or Paid endpoint based on
     * whether the key ends in {@code ":fx"} (DeepL Free tier convention).
     *
     * @param apiKey your DeepL Authentication Key
     */
    public DeepLTranslationProvider(String apiKey) {
        this(apiKey, apiKey.endsWith(":fx"));
    }

    /**
     * Create a provider with an explicit endpoint selection.
     *
     * @param apiKey  your DeepL Authentication Key
     * @param freeApi {@code true} to use the Free API endpoint, {@code false} for the paid endpoint
     */
    public DeepLTranslationProvider(String apiKey, boolean freeApi) {
        this.apiKey   = apiKey;
        this.endpoint = freeApi ? FREE_API_URL : PAID_API_URL;
    }

    @Override
    public String translate(String text, String targetLanguage) throws IOException {
        String body = "auth_key=" + URLEncoder.encode(apiKey, "UTF-8")
                + "&text=" + URLEncoder.encode(text, "UTF-8")
                + "&target_lang=" + URLEncoder.encode(targetLanguage.toUpperCase(), "UTF-8");

        byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);

        HttpURLConnection conn = (HttpURLConnection) new URL(endpoint).openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setRequestProperty("Content-Length", String.valueOf(bodyBytes.length));
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setDoOutput(true);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(bodyBytes);
            }

            int code = conn.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                throw new IOException("DeepL API returned HTTP " + code
                        + " for target language '" + targetLanguage + "'");
            }

            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
            }

            // Response: {"translations":[{"detected_source_language":"EN","text":"..."}]}
            JSONArray translations = new JSONObject(sb.toString())
                    .getJSONArray("translations");
            return translations.getJSONObject(0).getString("text");

        } catch (Exception e) {
            if (e instanceof IOException) throw (IOException) e;
            throw new IOException("DeepL translation failed: " + e.getMessage(), e);
        } finally {
            conn.disconnect();
        }
    }
}
