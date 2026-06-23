/*
 * Instrumented smoke test for the FFmpeg 6.0 -> 7.1.5 port (see project
 * CLAUDE.md / PATCH-NOTES.md). Runs real native code on a device/emulator -
 * the JVM unit tests elsewhere in this module run with
 * enable.ffmpeg.kit.test.mode=true and never touch the native library.
 *
 * Three checks, each building on the last:
 *  1. getFFmpegVersion() actually reports the 7.1.5 build, not a stale one.
 *  2. A real encode (sine wave -> AAC) exercises the full session/JNI/
 *     codec pipeline, not just "the .so loaded".
 *  3. ffprobe on that output confirms the wrapper's probe path also works
 *     and produced a genuinely valid file, not just an exit code.
 */
package com.arthenica.ffmpegkit;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(AndroidJUnit4.class)
public class SmokeTest {

    @Test
    public void step1_getFFmpegVersionReportsPortedVersion() {
        String version = FFmpegKitConfig.getFFmpegVersion();
        assertNotNull("getFFmpegVersion() returned null", version);
        assertTrue("Expected an n7.1.5-based version string, got: " + version,
                version.startsWith("7.1"));
    }

    @Test
    public void step2_and_3_encodePcmToAacThenProbeIt() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File outputFile = new File(context.getCacheDir(), "smoke_test_output.m4a");
        if (outputFile.exists()) {
            outputFile.delete();
        }
        String outputPath = outputFile.getAbsolutePath();

        // lavfi sine generator avoids needing to ship a binary test asset -
        // aac is FFmpeg's own native encoder (not an external library), so
        // this exercises the same codepath regardless of which tier
        // (Free/Basic/Full/Full GPL) is under test.
        FFmpegSession encodeSession = FFmpegKit.execute(
                "-y -f lavfi -i sine=frequency=1000:duration=1 -c:a aac " + outputPath);

        if (!ReturnCode.isSuccess(encodeSession.getReturnCode())) {
            fail("Encode failed, state=" + encodeSession.getState()
                    + " rc=" + encodeSession.getReturnCode()
                    + " output=" + encodeSession.getOutput()
                    + " failTrace=" + encodeSession.getFailStackTrace());
        }
        assertTrue("Encoded output file is missing or empty: " + outputPath,
                outputFile.exists() && outputFile.length() > 0);

        MediaInformationSession probeSession = FFprobeKit.getMediaInformation(outputPath);
        MediaInformation mediaInformation = probeSession.getMediaInformation();
        assertNotNull("ffprobe produced no MediaInformation for: " + outputPath
                + " output=" + probeSession.getOutput(), mediaInformation);

        List<StreamInformation> streams = mediaInformation.getStreams();
        assertNotNull("MediaInformation had a null streams list", streams);
        assertFalse("MediaInformation reported zero streams", streams.isEmpty());
        assertEquals("Expected the single stream to be aac", "aac", streams.get(0).getCodec());
    }
}
