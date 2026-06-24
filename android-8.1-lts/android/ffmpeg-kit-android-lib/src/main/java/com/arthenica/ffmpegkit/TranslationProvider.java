/*
 * TranslationProvider — pluggable external translation backend for WhisperKit.
 *
 * Implement this interface to connect any translation API (DeepL, LibreTranslate,
 * Google Cloud Translation, etc.) to WhisperKit's transcribeAndTranslate / SRT
 * pipeline.
 *
 * Example — DeepL free API:
 *
 *   TranslationProvider deepl = (text, targetLang) -> {
 *       // POST to https://api-free.deepl.com/v2/translate
 *       // Return the translated string
 *   };
 *
 *   String srt = whisperKit.transcribeToSrtAndTranslate(pcm, deepl, "fr");
 */
package com.arthenica.ffmpegkit;

import java.io.IOException;

public interface TranslationProvider {

    /**
     * Translate {@code text} into the requested language.
     *
     * <p>The implementation is responsible for the HTTP call, authentication,
     * and JSON parsing for whichever service it wraps. The source language is
     * intentionally omitted — let the service auto-detect it (this matches
     * Whisper's own auto-detect behaviour for the audio).
     *
     * @param text         the source text to translate (may contain multiple sentences)
     * @param targetLanguage BCP-47 language code, e.g. {@code "fr"}, {@code "es"}, {@code "de"}
     * @return the translated text
     * @throws IOException if the translation service is unreachable or returns an error
     */
    String translate(String text, String targetLanguage) throws IOException;
}
