package dev.ffmpegkit.smoke

import android.app.Activity
import android.os.Bundle
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.ReturnCode
import java.io.File

/**
 * Mesure de performance, comparable entre deux versions.
 *
 * La question est étroite : le passage de **n8.1.2 à n8.1.3**, et l'ajout de HarfBuzz,
 * ont-ils ralenti quelque chose ? Un correctif qui rend le produit deux fois plus lent
 * est une régression, même si toutes les épreuves fonctionnelles passent.
 *
 * ## ⚠️ Ce qu'il faut pour qu'un chiffre veuille dire quelque chose
 *
 * Un téléphone n'est pas un banc de mesure. Trois précautions, sans lesquelles l'écart
 * mesuré serait du bruit :
 *
 *  - **un échauffement** avant de compter — le premier passage paie le chargement des
 *    bibliothèques, l'allocation, les caches froids
 *  - **plusieurs répétitions et la médiane**, pas la moyenne : une seule interruption
 *    du système décale une moyenne, pas une médiane
 *  - **l'étendue rapportée** (min…max). Un écart entre versions plus petit que
 *    l'étendue d'une même version ne prouve rien, et il faut pouvoir le voir.
 *
 * La limitation thermique se traite à l'extérieur, par le lanceur : il alterne les
 * deux versions (A, B, A, B) au lieu de mesurer l'une puis l'autre. Si le téléphone
 * chauffe, il chauffe pour les deux.
 *
 * Sortie tabulaire, une ligne par opération :
 *
 *     NOM|mediane_ms|min_ms|max_ms|n
 *
 * ```
 * adb shell am start -S -n dev.ffmpegkit.smoke/.PerfActivity
 * adb pull .../files/perf.txt
 * ```
 */
class PerfActivity : Activity() {

    private val lignes = StringBuilder()
    private val REPETITIONS = 5

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val out = getExternalFilesDir(null) ?: filesDir
        Thread { lancer(out) }.start()
    }

    /** Lance la commande REPETITIONS fois après un échauffement, rend la médiane. */
    private fun mesure(nom: String, sortie: File, commande: () -> String) {
        // Échauffement : jamais compté.
        sortie.delete()
        val chauffe = FFmpegKit.execute(commande())
        if (!ReturnCode.isSuccess(chauffe.returnCode)) {
            lignes.append(nom).append("|ABSENT|0|0|0\n")
            Log.i("PERF", String.format("%-20s ABSENT", nom))
            return
        }

        val temps = ArrayList<Long>(REPETITIONS)
        repeat(REPETITIONS) {
            sortie.delete()
            val t0 = System.nanoTime()
            val s = FFmpegKit.execute(commande())
            val ms = (System.nanoTime() - t0) / 1_000_000
            if (ReturnCode.isSuccess(s.returnCode)) temps.add(ms)
        }
        if (temps.isEmpty()) {
            lignes.append(nom).append("|ECHEC|0|0|0\n")
            Log.i("PERF", String.format("%-20s ECHEC", nom))
            return
        }
        temps.sort()
        val mediane = temps[temps.size / 2]
        lignes.append(nom).append('|').append(mediane).append('|')
            .append(temps.first()).append('|').append(temps.last()).append('|')
            .append(temps.size).append('\n')
        Log.i(
            "PERF",
            String.format(
                "%-20s %6d ms   (%d..%d, n=%d)",
                nom, mediane, temps.first(), temps.last(), temps.size,
            ),
        )
    }

    private fun lancer(out: File) {
        val version = runCatching { FFmpegKitConfig.getFFmpegVersion() }.getOrElse { "?" }
        lignes.append("##### version ").append(version).append('\n')
        Log.i("PERF", "--- PERF sur $version, $REPETITIONS repetitions par operation ---")

        val police = listOf(
            "/system/fonts/Roboto-Regular.ttf",
            "/system/fonts/DroidSans.ttf",
        ).firstOrNull { File(it).exists() }

        // Source : la vraie vidéo H.264 si elle est là, sinon une source générée.
        // Dans les deux cas, identique d'une version à l'autre.
        val reel = File(out, "reel.mp4")
        val source = if (reel.exists()) reel.absolutePath else {
            val gen = File(out, "perf-source.mp4")
            if (!gen.exists()) {
                FFmpegKit.execute(
                    "-f lavfi -i testsrc=size=640x480:rate=30:duration=10 " +
                        "-c:v mpeg4 -y " + gen.absolutePath,
                )
            }
            gen.absolutePath
        }
        lignes.append("##### source ").append(File(source).name).append(' ')
            .append(File(source).length()).append('\n')

        fun f(n: String) = File(out, "perf-$n")

        // Décodage seul : le socle. `-f null -` n'écrit rien, donc ne mesure que
        // décoder et filtrer.
        val nul = File(out, "perf-null")
        mesure("DECODE_SEUL", nul) { "-i $source -t 5 -f null -" }

        mesure("SCALE", nul) { "-i $source -t 5 -vf scale=320:-2 -f null -" }

        if (police != null) {
            mesure("DRAWTEXT", nul) {
                "-i $source -t 5 -vf drawtext=fontfile='$police':text='perf':" +
                    "fontsize=24:fontcolor=white -f null -"
            }
        } else {
            lignes.append("DRAWTEXT|ABSENT|0|0|0\n")
        }

        val x264 = f("x264.mp4")
        mesure("ENCODE_H264", x264) {
            "-i $source -t 3 -c:v libx264 -preset veryfast -crf 28 -an -y " + x264.absolutePath
        }

        val x265 = f("x265.mp4")
        mesure("ENCODE_H265", x265) {
            "-i $source -t 3 -c:v libx265 -preset ultrafast -x265-params log-level=none " +
                "-crf 30 -an -y " + x265.absolutePath
        }

        val vp9 = f("vp9.webm")
        mesure("ENCODE_VP9", vp9) {
            "-i $source -t 3 -c:v libvpx-vp9 -pix_fmt yuv420p -b:v 300k -cpu-used 8 " +
                "-an -y " + vp9.absolutePath
        }

        val aac = f("aac.m4a")
        mesure("ENCODE_AAC", aac) {
            "-i $source -t 5 -vn -c:a aac -b:a 128k -y " + aac.absolutePath
        }

        val copie = f("copie.mp4")
        mesure("REMUX_COPY", copie) { "-i $source -t 5 -c copy -y " + copie.absolutePath }

        val fichier = File(out, "perf.txt")
        fichier.writeText(lignes.toString())
        Log.i("PERF", "--- FINI -> " + fichier.absolutePath)
    }
}
