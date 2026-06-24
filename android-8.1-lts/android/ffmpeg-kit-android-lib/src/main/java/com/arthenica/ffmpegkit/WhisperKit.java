/*
 * WhisperKit — on-device speech recognition for FFmpegKit 8.1 LTS.
 *
 * Available only in the Full and Full GPL tiers.  Wraps Whisper.cpp v1.7.5
 * via a JNI bridge (whisperkitjni.c / libwhisperkit.so).
 *
 * Usage:
 *
 *   // Load a GGML model file (e.g. ggml-base.bin downloaded from HuggingFace)
 *   WhisperKit wk = WhisperKit.createFromFile("/data/local/tmp/ggml-base.bin");
 *
 *   // Supply 16 kHz mono PCM float samples (e.g. decoded via FFmpegKit)
 *   float[] pcm = ...;
 *
 *   String text = wk.transcribe(pcm);           // plain text
 *   String srt  = wk.transcribeToSrt(pcm);      // SRT subtitles
 *   String eng  = wk.translate(pcm);            // auto-translate to English
 *   String esrt = wk.translateToSrt(pcm);       // translated SRT
 *
 *   wk.close(); // release native memory
 *
 * Thread safety: a single WhisperKit instance is NOT thread-safe.
 * Create one instance per concurrent transcription job.
 */
package com.arthenica.ffmpegkit;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class WhisperKit implements Closeable {

    private static final boolean AVAILABLE;

    static {
        boolean ok;
        try {
            System.loadLibrary("whisperkit");
            ok = true;
        } catch (UnsatisfiedLinkError e) {
            ok = false;
        }
        AVAILABLE = ok;
    }

    /** Default number of CPU threads used for inference. */
    public static final int DEFAULT_THREADS = 4;

    /** Auto-detect language (Whisper chooses based on audio content). */
    public static final String LANGUAGE_AUTO = "auto";

    private long contextHandle;

    private WhisperKit(long handle) {
        this.contextHandle = handle;
    }

    /**
     * Load a GGML Whisper model from a file path on the device.
     *
     * @param modelPath absolute path to a ggml-*.bin model file
     * @return a ready-to-use WhisperKit instance
     * @throws IOException if the model file cannot be loaded
     */
    public static WhisperKit createFromFile(String modelPath) throws IOException {
        if (!AVAILABLE) {
            throw new IOException(
                    "WhisperKit requires the Full or Full GPL tier of FFmpegKit 8.1 LTS. " +
                    "The Free and Basic tiers do not include the Whisper native library.");
        }
        long handle = nativeInitContext(modelPath);
        if (handle == 0) {
            throw new IOException("Failed to load Whisper model: " + modelPath);
        }
        return new WhisperKit(handle);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Transcription (keeps original language)
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Transcribe 16 kHz mono PCM float samples to plain text.
     * Language is auto-detected from the audio.
     */
    public String transcribe(float[] pcmSamples) {
        return transcribe(pcmSamples, LANGUAGE_AUTO, DEFAULT_THREADS);
    }

    /** Transcribe with an explicit language hint (e.g. "fr", "es", "ja"). */
    public String transcribe(float[] pcmSamples, String language) {
        return transcribe(pcmSamples, language, DEFAULT_THREADS);
    }

    /** Transcribe with explicit language and thread count. */
    public String transcribe(float[] pcmSamples, String language, int numThreads) {
        return runAndCollectText(pcmSamples, false, language, numThreads);
    }

    /**
     * Transcribe and return the result as an SRT subtitle string.
     * Timestamps are derived from Whisper's segment boundaries.
     */
    public String transcribeToSrt(float[] pcmSamples) {
        return transcribeToSrt(pcmSamples, LANGUAGE_AUTO, DEFAULT_THREADS);
    }

    /** Transcribe to SRT with an explicit language hint. */
    public String transcribeToSrt(float[] pcmSamples, String language) {
        return transcribeToSrt(pcmSamples, language, DEFAULT_THREADS);
    }

    /** Transcribe to SRT with explicit language and thread count. */
    public String transcribeToSrt(float[] pcmSamples, String language, int numThreads) {
        return runAndBuildSrt(pcmSamples, false, language, numThreads);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Translation (always outputs English)
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Transcribe and automatically translate to English in one pass.
     * No explicit source language needed — Whisper detects it.
     */
    public String translate(float[] pcmSamples) {
        return translate(pcmSamples, LANGUAGE_AUTO, DEFAULT_THREADS);
    }

    /** Translate with an explicit source-language hint. */
    public String translate(float[] pcmSamples, String sourceLanguage) {
        return translate(pcmSamples, sourceLanguage, DEFAULT_THREADS);
    }

    /** Translate with explicit source language and thread count. */
    public String translate(float[] pcmSamples, String sourceLanguage, int numThreads) {
        return runAndCollectText(pcmSamples, true, sourceLanguage, numThreads);
    }

    /** Translate and return English SRT subtitles. */
    public String translateToSrt(float[] pcmSamples) {
        return translateToSrt(pcmSamples, LANGUAGE_AUTO, DEFAULT_THREADS);
    }

    /** Translate to SRT with an explicit source-language hint. */
    public String translateToSrt(float[] pcmSamples, String sourceLanguage) {
        return translateToSrt(pcmSamples, sourceLanguage, DEFAULT_THREADS);
    }

    /** Translate to SRT with explicit source language and thread count. */
    public String translateToSrt(float[] pcmSamples, String sourceLanguage, int numThreads) {
        return runAndBuildSrt(pcmSamples, true, sourceLanguage, numThreads);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // External translation (any language target, via pluggable TranslationProvider)
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Transcribe audio with Whisper, then translate the result to any language
     * via an external {@link TranslationProvider} (DeepL, LibreTranslate, etc.).
     *
     * <p>Unlike {@link #translate} (which is Whisper's built-in English-only
     * translation), this method supports any target language.
     *
     * @param pcmSamples 16 kHz mono PCM float samples
     * @param translator the external translation backend
     * @param targetLanguage BCP-47 code of the desired output language (e.g. "fr")
     * @return translated plain text
     * @throws IOException if the translation service fails
     */
    public String transcribeAndTranslate(float[] pcmSamples,
                                         TranslationProvider translator,
                                         String targetLanguage) throws IOException {
        return transcribeAndTranslate(pcmSamples, LANGUAGE_AUTO, DEFAULT_THREADS,
                translator, targetLanguage);
    }

    /** Transcribe + external translate with an explicit source-language hint. */
    public String transcribeAndTranslate(float[] pcmSamples, String sourceLanguage,
                                         TranslationProvider translator,
                                         String targetLanguage) throws IOException {
        return transcribeAndTranslate(pcmSamples, sourceLanguage, DEFAULT_THREADS,
                translator, targetLanguage);
    }

    /** Transcribe + external translate with full control over all parameters. */
    public String transcribeAndTranslate(float[] pcmSamples, String sourceLanguage,
                                         int numThreads,
                                         TranslationProvider translator,
                                         String targetLanguage) throws IOException {
        checkOpen();
        String transcribed = runAndCollectText(pcmSamples, false, sourceLanguage, numThreads);
        if (transcribed.isEmpty()) return "";
        return translator.translate(transcribed, targetLanguage);
    }

    /**
     * Transcribe audio with Whisper, translate each subtitle segment via an
     * external {@link TranslationProvider}, and return an SRT string with the
     * translated text and the original Whisper timestamps.
     *
     * @param pcmSamples 16 kHz mono PCM float samples
     * @param translator the external translation backend
     * @param targetLanguage BCP-47 code of the desired output language (e.g. "fr")
     * @return SRT-formatted subtitles in the target language
     * @throws IOException if the translation service fails
     */
    public String transcribeToSrtAndTranslate(float[] pcmSamples,
                                              TranslationProvider translator,
                                              String targetLanguage) throws IOException {
        return transcribeToSrtAndTranslate(pcmSamples, LANGUAGE_AUTO, DEFAULT_THREADS,
                translator, targetLanguage);
    }

    /** Transcribe-to-SRT + external translate with an explicit source-language hint. */
    public String transcribeToSrtAndTranslate(float[] pcmSamples, String sourceLanguage,
                                              TranslationProvider translator,
                                              String targetLanguage) throws IOException {
        return transcribeToSrtAndTranslate(pcmSamples, sourceLanguage, DEFAULT_THREADS,
                translator, targetLanguage);
    }

    /** Transcribe-to-SRT + external translate with full control over all parameters. */
    public String transcribeToSrtAndTranslate(float[] pcmSamples, String sourceLanguage,
                                              int numThreads,
                                              TranslationProvider translator,
                                              String targetLanguage) throws IOException {
        checkOpen();
        int rc = nativeFullTranscribe(contextHandle, pcmSamples, false, sourceLanguage, numThreads);
        if (rc != 0) return "";

        int n = nativeGetSegmentCount(contextHandle);

        // Collect non-empty segments and their timestamps.
        List<long[]> times = new ArrayList<>(n);
        List<String> texts = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            String seg = nativeGetSegmentText(contextHandle, i);
            if (seg == null || seg.trim().isEmpty()) continue;
            times.add(new long[]{nativeGetSegmentT0(contextHandle, i),
                                  nativeGetSegmentT1(contextHandle, i)});
            texts.add(seg.trim());
        }

        // Translate each segment individually to keep timestamps in sync.
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < texts.size(); i++) {
            String translated = translator.translate(texts.get(i), targetLanguage);
            if (translated == null || translated.trim().isEmpty()) continue;
            sb.append(i + 1).append('\n');
            sb.append(csToSrtTime(times.get(i)[0])).append(" --> ")
              .append(csToSrtTime(times.get(i)[1])).append('\n');
            sb.append(translated.trim()).append("\n\n");
        }
        return sb.toString();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Low-level segment access (for custom rendering)
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Run inference and store results in the context.
     * Use getSegmentCount / getSegmentText / getSegmentT0 / getSegmentT1
     * to read results afterwards.
     *
     * @return 0 on success
     */
    public int fullTranscribe(float[] pcmSamples, boolean translate,
                              String language, int numThreads) {
        checkOpen();
        return nativeFullTranscribe(contextHandle, pcmSamples,
                translate, language, numThreads);
    }

    /** Number of text segments from the last {@link #fullTranscribe} call. */
    public int getSegmentCount() {
        checkOpen();
        return nativeGetSegmentCount(contextHandle);
    }

    /** Text of segment {@code index} from the last {@link #fullTranscribe} call. */
    public String getSegmentText(int index) {
        checkOpen();
        return nativeGetSegmentText(contextHandle, index);
    }

    /**
     * Start timestamp of segment {@code index}, in centiseconds (1/100 s).
     * Multiply by 10 to get milliseconds.
     */
    public long getSegmentT0(int index) {
        checkOpen();
        return nativeGetSegmentT0(contextHandle, index);
    }

    /** End timestamp of segment {@code index}, in centiseconds. */
    public long getSegmentT1(int index) {
        checkOpen();
        return nativeGetSegmentT1(contextHandle, index);
    }

    /** Whisper system information string (CPU capabilities, build flags). Null on Free/Basic tiers. */
    public static String getSystemInfo() {
        return AVAILABLE ? nativeGetSystemInfo() : null;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ──────────────────────────────────────────────────────────────────────────

    @Override
    public void close() {
        if (contextHandle != 0) {
            nativeFreeContext(contextHandle);
            contextHandle = 0;
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ──────────────────────────────────────────────────────────────────────────

    private String runAndCollectText(float[] samples, boolean translate,
                                     String language, int threads) {
        checkOpen();
        int rc = nativeFullTranscribe(contextHandle, samples, translate, language, threads);
        if (rc != 0) return "";
        int n = nativeGetSegmentCount(contextHandle);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            String seg = nativeGetSegmentText(contextHandle, i);
            if (seg != null) sb.append(seg);
        }
        return sb.toString().trim();
    }

    private String runAndBuildSrt(float[] samples, boolean translate,
                                  String language, int threads) {
        checkOpen();
        int rc = nativeFullTranscribe(contextHandle, samples, translate, language, threads);
        if (rc != 0) return "";
        int n = nativeGetSegmentCount(contextHandle);
        StringBuilder sb = new StringBuilder();
        int srtIndex = 1;
        for (int i = 0; i < n; i++) {
            String text = nativeGetSegmentText(contextHandle, i);
            if (text == null || text.trim().isEmpty()) continue;
            long t0 = nativeGetSegmentT0(contextHandle, i);
            long t1 = nativeGetSegmentT1(contextHandle, i);
            sb.append(srtIndex++).append('\n');
            sb.append(csToSrtTime(t0)).append(" --> ").append(csToSrtTime(t1)).append('\n');
            sb.append(text.trim()).append("\n\n");
        }
        return sb.toString();
    }

    /** Convert centiseconds to SRT timestamp format (HH:MM:SS,mmm). */
    private static String csToSrtTime(long cs) {
        long ms = cs * 10;
        long h  = ms / 3_600_000; ms %= 3_600_000;
        long m  = ms / 60_000;    ms %= 60_000;
        long s  = ms / 1_000;     ms %= 1_000;
        return String.format("%02d:%02d:%02d,%03d", h, m, s, ms);
    }

    private void checkOpen() {
        if (contextHandle == 0) {
            throw new IllegalStateException("WhisperKit context is closed");
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // JNI declarations
    // ──────────────────────────────────────────────────────────────────────────

    private static native long   nativeInitContext(String modelPath);
    private static native void   nativeFreeContext(long contextPtr);
    private static native int    nativeFullTranscribe(long contextPtr, float[] audioData,
                                                      boolean translate, String language,
                                                      int numThreads);
    private static native int    nativeGetSegmentCount(long contextPtr);
    private static native String nativeGetSegmentText(long contextPtr, int index);
    private static native long   nativeGetSegmentT0(long contextPtr, int index);
    private static native long   nativeGetSegmentT1(long contextPtr, int index);
    private static native String nativeGetSystemInfo();
}
