# WhisperKit — On-device speech recognition for FFmpegKit 8.1

**Available in:** FFmpegKit 8.1 LTS — Full tier ($34) and Full GPL tier ($49)
**Underlying engine:** [Whisper.cpp v1.7.5](https://github.com/ggml-org/whisper.cpp) (ggml-org)

---

## What is WhisperKit?

WhisperKit brings on-device speech recognition to Android via a clean Java API layered on top of [Whisper.cpp](https://github.com/ggml-org/whisper.cpp). It processes audio locally — **no audio ever leaves the device**, no API key required for transcription, and it works fully offline.

> **This pipeline is proven.** [SubtitleEdit](https://github.com/SubtitleEdit/subtitleedit), one of the most widely-used open-source subtitle editors (desktop), uses the exact same architecture: Whisper for transcription, then DeepL or Google Translate for translation, as a separate pipeline from FFmpeg. WhisperKit brings that same workflow to Android in a single library.

Key capabilities:

| Feature | API method |
|---|---|
| Transcribe audio → plain text | `transcribe(float[])` |
| Transcribe audio → SRT subtitles | `transcribeToSrt(float[])` |
| Transcribe + translate to English (Whisper built-in) | `translate(float[])` |
| Transcribe + translate to English → SRT | `translateToSrt(float[])` |
| Transcribe + translate to **any language** via external service | `transcribeAndTranslate(float[], provider, "fr")` |
| Transcribe + translate to any language → SRT | `transcribeToSrtAndTranslate(float[], provider, "fr")` |

---

## Prerequisites

### 1. Use the Full or Full GPL tier

WhisperKit is only included in the **8.1 Full** and **8.1 Full GPL** AARs. The Free and Basic tiers do not include `libwhisperkit.so`. Calling `WhisperKit.createFromFile()` on a Free/Basic AAR will throw a descriptive `IOException` rather than crashing.

### 2. Download a Whisper GGML model

Whisper.cpp uses GGML-format model files (`.bin`). Download one from HuggingFace:

- [`ggml-tiny.bin`](https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.bin) — 75 MB, fastest, lowest accuracy
- [`ggml-base.bin`](https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.bin) — 142 MB, good balance
- [`ggml-small.bin`](https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small.bin) — 466 MB, better accuracy
- [`ggml-medium.bin`](https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-medium.bin) — 1.5 GB, high accuracy (slow on mobile)

Store the model in `context.getFilesDir()` or any accessible path. Do **not** bundle it inside the APK — it would make your app huge. Download it on first launch.

### 3. Understand the expected audio format

WhisperKit expects **16 kHz, mono, 32-bit float PCM** samples. That is exactly what FFmpegKit can produce from any audio or video source (see [Getting PCM from a video](#getting-pcm-from-a-video-with-ffmpegkit) below).

---

## Getting PCM from a video with FFmpegKit

```java
String pcmPath = context.getCacheDir() + "/audio.pcm";

FFmpegKit.executeAsync(
    "-i /path/to/input.mp4 -ar 16000 -ac 1 -f f32le " + pcmPath,
    session -> {
        if (ReturnCode.isSuccess(session.getReturnCode())) {
            try {
                float[] pcm = loadPcmAsFloatArray(pcmPath);
                transcribeWithWhisper(pcm);
            } catch (IOException e) {
                Log.e("WhisperKit", "Failed to load PCM", e);
            }
        }
    }
);

private float[] loadPcmAsFloatArray(String path) throws IOException {
    byte[] bytes = java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(path));
    java.nio.FloatBuffer fb = java.nio.ByteBuffer.wrap(bytes)
        .order(java.nio.ByteOrder.LITTLE_ENDIAN)
        .asFloatBuffer();
    float[] samples = new float[fb.remaining()];
    fb.get(samples);
    return samples;
}
```

---

## Basic transcription

```java
String modelPath = context.getFilesDir() + "/ggml-base.bin";

try (WhisperKit wk = WhisperKit.createFromFile(modelPath)) {

    // Plain text — auto-detects the spoken language
    String text = wk.transcribe(pcm);

    // With an explicit language hint (faster, slightly more accurate)
    String frenchText = wk.transcribe(pcm, "fr");

    // SRT subtitles with Whisper's segment timestamps
    String srt = wk.transcribeToSrt(pcm);

} catch (IOException e) {
    Log.e("WhisperKit", "Transcription failed", e);
}
```

`WhisperKit` implements `Closeable` — use try-with-resources to release native memory.

---

## Built-in English translation (Whisper)

Whisper can transcribe **and** translate to English in a single pass, with no external service:

```java
try (WhisperKit wk = WhisperKit.createFromFile(modelPath)) {

    // Translate any language → English text
    String englishText = wk.translate(pcm);

    // Translate any language → English SRT
    String englishSrt = wk.translateToSrt(pcm);
}
```

---

## External translation to any language

For languages other than English, plug in any translation API via `TranslationProvider`.
Two ready-to-use implementations are included:

### DeepL (recommended for quality)

Get a free API key at [deepl.com/pro-api](https://www.deepl.com/pro-api) (500 000 characters/month free).

```java
TranslationProvider deepl = new DeepLTranslationProvider("YOUR_DEEPL_API_KEY");

try (WhisperKit wk = WhisperKit.createFromFile(modelPath)) {

    // Transcribe then translate full text to French
    String frenchText = wk.transcribeAndTranslate(pcm, deepl, "FR");

    // Transcribe then translate each subtitle segment to Spanish
    // (timestamps stay in sync with the original audio)
    String spanishSrt = wk.transcribeToSrtAndTranslate(pcm, deepl, "ES");
}
```

`DeepLTranslationProvider` auto-selects the Free API endpoint (`api-free.deepl.com`) when the key ends in `:fx`, and the paid endpoint otherwise.

### Google Translate (Cloud Translation API)

First 500 000 characters/month are free. [Get an API key](https://console.cloud.google.com) (enable "Cloud Translation API" for your project).

```java
TranslationProvider google = new GoogleTranslateProvider("YOUR_GOOGLE_API_KEY");

try (WhisperKit wk = WhisperKit.createFromFile(modelPath)) {
    String japaneseText = wk.transcribeAndTranslate(pcm, google, "ja");
    String chineseSrt   = wk.transcribeToSrtAndTranslate(pcm, google, "zh-CN");
}
```

### LibreTranslate (open-source, self-hostable)

[LibreTranslate](https://github.com/LibreTranslate/LibreTranslate) is a free, open-source translation server you can run yourself — no vendor lock-in, no per-character costs.

```java
// Public instance (no key required, rate-limited)
TranslationProvider lt = new LibreTranslateProvider();

// Your own self-hosted instance
TranslationProvider lt = new LibreTranslateProvider("https://your-server.example.com");

// Self-hosted instance with an API key
TranslationProvider lt = new LibreTranslateProvider("https://your-server.example.com", "API_KEY");

try (WhisperKit wk = WhisperKit.createFromFile(modelPath)) {
    String germanSrt = wk.transcribeToSrtAndTranslate(pcm, lt, "de");
}
```

### Custom provider (any service)

`TranslationProvider` is a functional interface — implement it inline for any REST API:

```java
// Example: Google Cloud Translation
TranslationProvider google = (text, targetLang) -> {
    String url = "https://translation.googleapis.com/language/translate/v2"
               + "?key=YOUR_GOOGLE_API_KEY";
    // Build JSON body, make HTTPS POST, parse response["data"]["translations"][0]["translatedText"]
    return translatedText;
};
```

---

## Burn subtitles into the video (the complete pipeline)

The Python reference pipeline for desktop is:

```python
# Transcribe + translate with Whisper CLI, then burn subtitles with FFmpeg
whisper video.mp4 --task translate --language fr --output_format srt
ffmpeg -i video.mp4 -vf subtitles=video.srt final.mp4
```

With FFmpegKit 8.1 Full (or Full GPL), the same end-to-end pipeline runs entirely on Android:

```java
String videoPath  = "/path/to/video.mp4";
String pcmPath    = context.getCacheDir() + "/audio.pcm";
String srtPath    = context.getCacheDir() + "/subtitles.srt";
String outputPath = context.getCacheDir() + "/final.mp4";
String modelPath  = context.getFilesDir() + "/ggml-base.bin";

// Step 1: extract audio as 16 kHz mono f32 PCM
FFmpegKit.executeAsync(
    "-i " + videoPath + " -ar 16000 -ac 1 -f f32le " + pcmPath,
    session -> {
        if (!ReturnCode.isSuccess(session.getReturnCode())) return;
        try {
            // Step 2: load PCM and transcribe → SRT
            byte[] bytes = java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(pcmPath));
            java.nio.FloatBuffer fb = java.nio.ByteBuffer.wrap(bytes)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN).asFloatBuffer();
            float[] pcm = new float[fb.remaining()];
            fb.get(pcm);

            String srt;
            try (WhisperKit wk = WhisperKit.createFromFile(modelPath)) {
                // Option A — translate to English (offline, no API key)
                srt = wk.translateToSrt(pcm);

                // Option B — translate to any language via DeepL
                // TranslationProvider deepl = new DeepLTranslationProvider("YOUR_KEY");
                // srt = wk.transcribeToSrtAndTranslate(pcm, deepl, "FR");
            }

            // Step 3: write SRT to disk
            java.nio.file.Files.write(java.nio.file.Paths.get(srtPath),
                srt.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            // Step 4: burn subtitles into the video (requires Full / Full GPL tier for libass)
            FFmpegKit.executeAsync(
                "-i " + videoPath + " -vf subtitles=" + srtPath + " " + outputPath,
                burnSession -> {
                    if (ReturnCode.isSuccess(burnSession.getReturnCode())) {
                        Log.i("WhisperKit", "Done: " + outputPath);
                    }
                }
            );

        } catch (IOException e) {
            Log.e("WhisperKit", "Pipeline failed", e);
        }
    }
);
```

> **Subtitle burning requires the Full or Full GPL tier** — the `subtitles` FFmpeg filter uses `libass`, which is only included in those two tiers. The `translate` step (Whisper built-in or TranslationProvider) is independent of this.

---

## Full end-to-end example

```java
public void transcribeAndTranslateVideo(Context context, String videoPath, String targetLang) {
    String pcmPath   = context.getCacheDir() + "/audio.pcm";
    String modelPath = context.getFilesDir() + "/ggml-base.bin";

    // Step 1: extract audio as 16 kHz mono f32 PCM
    FFmpegKit.executeAsync(
        "-i " + videoPath + " -ar 16000 -ac 1 -f f32le " + pcmPath,
        session -> {
            if (!ReturnCode.isSuccess(session.getReturnCode())) return;
            try {
                // Step 2: load PCM samples
                byte[] bytes = java.nio.file.Files.readAllBytes(
                    java.nio.file.Paths.get(pcmPath));
                java.nio.FloatBuffer fb = java.nio.ByteBuffer.wrap(bytes)
                    .order(java.nio.ByteOrder.LITTLE_ENDIAN).asFloatBuffer();
                float[] pcm = new float[fb.remaining()];
                fb.get(pcm);

                // Step 3: transcribe and translate to SRT
                TranslationProvider deepl =
                    new DeepLTranslationProvider("YOUR_DEEPL_API_KEY");

                try (WhisperKit wk = WhisperKit.createFromFile(modelPath)) {
                    String srt = wk.transcribeToSrtAndTranslate(pcm, deepl, targetLang);
                    // Write SRT alongside the video, display, or upload
                    Log.i("WhisperKit", srt);
                }
            } catch (IOException e) {
                Log.e("WhisperKit", "Error", e);
            }
        }
    );
}
```

---

## Thread safety and performance

- A `WhisperKit` instance is **not thread-safe**. Create one instance per concurrent job.
- On a modern Android device (Cortex-A76 class), a 1-minute audio clip takes ~5–15 seconds with `ggml-base.bin` and 4 threads (the default). Use `ggml-tiny.bin` if real-time processing is required.
- The model is loaded once in `createFromFile()` and kept in native memory until `close()`. Reuse the same instance for multiple calls rather than re-loading the model each time.

```java
// Reuse across multiple clips (more efficient than createFromFile each time)
WhisperKit wk = WhisperKit.createFromFile(modelPath);

for (float[] clip : audioClips) {
    String text = wk.transcribe(clip);
    processResult(text);
}

wk.close();
```

---

## Checking availability at runtime

```java
if (WhisperKit.getSystemInfo() != null) {
    // libwhisperkit.so is loaded — we're on a Full or Full GPL build
    Log.i("WhisperKit", WhisperKit.getSystemInfo()); // prints CPU capabilities
}
```

Actually, `getSystemInfo()` will throw `UnsatisfiedLinkError` on Free/Basic since the static block already caught the load failure. The reliable way to check:

```java
try {
    WhisperKit wk = WhisperKit.createFromFile(modelPath);
    // WhisperKit is available
    wk.close();
} catch (IOException e) {
    if (e.getMessage() != null && e.getMessage().contains("requires the Full")) {
        // Running on Free or Basic tier — WhisperKit not available
    } else {
        // Model file not found or failed to load
    }
}
```

---

## Supported model languages

Whisper supports 99 languages. The model auto-detects the language unless you pass an explicit hint. Passing a hint is slightly faster and avoids rare misdetections on short clips.

Common language codes: `"en"`, `"fr"`, `"es"`, `"de"`, `"ja"`, `"zh"`, `"ar"`, `"pt"`, `"ru"`, `"ko"`, `"it"`, `"nl"`, `"pl"`, `"tr"`, `"sv"`, `"da"`, `"fi"`, `"nb"`, `"he"`, `"hi"`, `"id"`, `"vi"`, `"uk"`, `"cs"`.
