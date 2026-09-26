package dev.ffmpegkit.smoke

import android.app.Activity
import android.os.Bundle
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegSession
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.ReturnCode
import com.arthenica.ffmpegkit.SessionState
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Les quatre choses que la batterie d'acceptation ne couvrait pas.
 *
 * `AcceptanceActivity` exerce 23 capacités sur les 1 775 que déclare `full-gpl`, avec
 * des sources synthétiques de trois secondes. Quatre absences comptaient plus que les
 * autres, parce qu'elles portent sur ce qu'on **vend** ou sur ce que fait une vraie
 * application :
 *
 *  1. **TLS** — l'argument central des paliers `-https`, jamais essayé
 *  2. **un fichier réel** — H.264/AAC produit par un vrai encodeur, pas `testsrc`
 *  3. **l'API asynchrone et `cancel()`** — le chemin que prend toute application
 *  4. **Whisper** — le différenciateur à $34 du palier payant 8.1
 *
 * ⚠️ Chaque épreuve n'est lancée que si le palier porte la capacité. TLS n'existe que
 * dans `https`/`https-gpl` ; Whisper, dans aucun palier gratuit. Exiger d'un palier ce
 * qu'il n'a jamais promis rendrait le banc inutilisable.
 *
 * Fichiers attendus sur l'appareil (poussés par le lanceur) :
 * `reel.mp4`, `jfk.wav`, `modele.bin` dans le répertoire de fichiers externes.
 *
 * ```
 * adb shell am start -S -n dev.ffmpegkit.smoke/.ExtraActivity
 * adb pull .../files/extra.txt
 * ```
 */
class ExtraActivity : Activity() {

    private val lignes = StringBuilder()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val out = getExternalFilesDir(null) ?: filesDir
        Thread { lancer(out) }.start()
    }

    private fun note(nom: String, etat: String, detail: String) {
        lignes.append(nom).append('|').append(etat).append('|')
            .append(detail.replace('\n', ' ').replace('|', '/').take(160)).append('\n')
        Log.i("EXTRA", String.format("%-18s %-7s %s", nom, etat, detail.take(110)))
    }

    private fun protocoles(): Set<String> {
        val s = FFmpegKit.execute("-hide_banner -protocols")
        return (s.output ?: "").lineSequence().map { it.trim() }
            .filter { it.isNotEmpty() && !it.endswith(":") }.toSet()
    }

    private fun String.endswith(s: String) = this.endsWith(s)

    private fun lancer(out: File) {
        val version = runCatching { FFmpegKitConfig.getFFmpegVersion() }.getOrElse { "?" }
        lignes.append("##### version ").append(version).append('\n')
        Log.i("EXTRA", "--- EXTRA sur $version ---")

        // ── 1. TLS ────────────────────────────────────────────────────────────
        // Le palier `-https` existe pour ca et personne ne l'avait jamais lance.
        val protos = protocoles()
        val tlsSortie = File(out, "extra-tls.mp4")
        if ("https" !in protos) {
            note("TLS_HTTPS", "ABSENT", "protocole https absent de ce palier")
        } else {
            tlsSortie.delete()
            val url = "https://test-videos.co.uk/vids/bigbuckbunny/mp4/h264/360/" +
                "Big_Buck_Bunny_360_10s_1MB.mp4"
            val s = FFmpegKit.execute("-i \"$url\" -t 2 -c copy -y " + tlsSortie.absolutePath)
            val ok = ReturnCode.isSuccess(s.returnCode) && tlsSortie.length() > 20_000
            note(
                "TLS_HTTPS", if (ok) "OK" else "ECHEC",
                "code=" + s.returnCode + " octets=" + tlsSortie.length() +
                    (if (ok) "" else " | " + (s.output ?: "").takeLast(120)),
            )
        }

        // ── 2. Un fichier reel ────────────────────────────────────────────────
        // H.264/AAC sorti d'un vrai encodeur : en-tetes, B-frames, horodatages
        // qu'aucune source `testsrc` ne produit.
        val reel = File(out, "reel.mp4")
        if (!reel.exists()) {
            note("FICHIER_REEL", "ABSENT", "reel.mp4 non pousse sur l'appareil")
        } else {
            val infos = FFprobeKit.execute(
                "-v quiet -show_entries stream=codec_name,width,height -of json " +
                    reel.absolutePath,
            ).output ?: ""
            val transcode = File(out, "extra-reel-transcode.mp4")
            transcode.delete()
            val s = FFmpegKit.execute(
                "-i ${reel.absolutePath} -t 3 -vf scale=160:-2 -c:v mpeg4 -c:a aac -y " +
                    transcode.absolutePath,
            )
            val large = Regex("\"width\"\\s*:\\s*(\\d+)").find(
                FFprobeKit.execute(
                    "-v quiet -select_streams v:0 -show_entries stream=width -of json " +
                        transcode.absolutePath,
                ).output ?: "",
            )?.groupValues?.get(1)
            val ok = ReturnCode.isSuccess(s.returnCode) &&
                transcode.length() > 10_000 && large == "160"
            note(
                "FICHIER_REEL", if (ok) "OK" else "ECHEC",
                "source h264=" + infos.contains("h264") + " transcode=" +
                    transcode.length() + "o largeur=" + large,
            )
        }

        // ── 3. API asynchrone et annulation ───────────────────────────────────
        // ⚠️ Le chemin que prend toute application reelle, et qu'aucune epreuve
        // synchrone ne touche : une session lancee en arriere-plan, annulee en cours
        // de route, puis le processus qui doit continuer a repondre.
        val longue = File(out, "extra-long.mp4")
        longue.delete()
        val verrou = CountDownLatch(1)
        var etat: SessionState? = null
        var codeFinal: ReturnCode? = null
        val session: FFmpegSession = FFmpegKit.executeAsync(
            "-f lavfi -i testsrc=size=640x480:rate=30:duration=600 -c:v mpeg4 -y " +
                longue.absolutePath,
        ) { s ->
            etat = s.state
            codeFinal = s.returnCode
            verrou.countDown()
        }
        Thread.sleep(1200)
        val enCours = session.state == SessionState.RUNNING
        FFmpegKit.cancel(session.sessionId)
        val arrivee = verrou.await(20, TimeUnit.SECONDS)
        val annulee = codeFinal?.let { ReturnCode.isCancel(it) } ?: false
        note(
            "ASYNC_CANCEL",
            if (arrivee && annulee) "OK" else "ECHEC",
            "lancee=" + enCours + " rappel=" + arrivee + " etat=" + etat +
                " code=" + codeFinal + " annulee=" + annulee,
        )

        // Le processus repond-il encore apres une annulation ?
        val apres = FFmpegKit.execute("-f lavfi -i color=c=blue:s=32x32:d=1 -f null -")
        note(
            "APRES_ANNULATION",
            if (ReturnCode.isSuccess(apres.returnCode)) "OK" else "ECHEC",
            "code=" + apres.returnCode,
        )

        // ── 4. Whisper ────────────────────────────────────────────────────────
        val modele = File(out, "modele.bin")
        val parole = File(out, "jfk.wav")
        when {
            !modele.exists() || !parole.exists() ->
                note("WHISPER", "ABSENT", "modele.bin ou jfk.wav non pousse")
            else -> {
                // whisper attend du PCM flottant mono 16 kHz : ffmpeg le produit.
                val brut = File(out, "extra-parole.f32")
                brut.delete()
                val conv = FFmpegKit.execute(
                    "-i ${parole.absolutePath} -f f32le -ac 1 -ar 16000 -y " + brut.absolutePath,
                )
                if (!ReturnCode.isSuccess(conv.returnCode) || brut.length() < 1000) {
                    note("WHISPER", "ECHEC", "conversion PCM ratee code=" + conv.returnCode)
                } else {
                    try {
                        val octets = brut.readBytes()
                        val bb = ByteBuffer.wrap(octets).order(ByteOrder.LITTLE_ENDIAN)
                        val pcm = FloatArray(octets.size / 4)
                        for (i in pcm.indices) pcm[i] = bb.float
                        // ⚠️ Par reflexion, et non par un import.
                        //
                        // `WhisperKit` n'existe que sur la ligne 8.1 : mesure du
                        // 2026-09-25, les AAR 7.1 (7.1.6 publie comme 7.1.7 candidat)
                        // n'en contiennent aucune classe. Un import direct ne compile
                        // donc pas contre un AAR 7.1 ou 6.0, et les 18 inventaires
                        // ainsi que les 21 epreuves d'acceptation echouaient tous a
                        // l'etape Kotlin -- pas une seule mesure rendue sur la ligne.
                        //
                        // Un banc qu'on ne peut pas construire contre une ligne ne
                        // rend aucun verdict pour cette ligne. Une classe absente doit
                        // se lire ABSENT a l'execution, pas casser la compilation.
                        val kWhisper = Class.forName("com.arthenica.ffmpegkit.WhisperKit")
                        val instance = kWhisper
                            .getMethod("createFromFile", String::class.java)
                            .invoke(null, modele.absolutePath)
                        val fermeture = instance as AutoCloseable
                        fermeture.use { w ->
                            val texte = (
                                kWhisper.getMethod("transcribe", FloatArray::class.java)
                                    .invoke(w, pcm) as String
                                ).lowercase()
                            // Le texte connu de cet echantillon du domaine public.
                            val attendu = listOf("ask not", "fellow americans", "country")
                            val trouves = attendu.filter { texte.contains(it) }
                            note(
                                "WHISPER",
                                if (trouves.isNotEmpty()) "OK" else "ECHEC",
                                "echantillons=" + pcm.size + " trouve=" + trouves +
                                    " texte=" + texte.take(90),
                            )
                        }
                    } catch (t: Throwable) {
                        // ⚠️ Un palier gratuit REFUSE Whisper, et c'est le comportement
                        // voulu : la bibliotheque leve une IOException dont le message
                        // nomme le palier requis. Un refus explicite et actionnable
                        // n'est pas un defaut -- le classer ECHEC ferait passer une
                        // bonne conception pour une panne.
                        // La reflexion enveloppe : c'est la cause qui porte le
                        // message du palier gratuit, pas l'enveloppe.
                        val reel = (t as? java.lang.reflect.InvocationTargetException)
                            ?.targetException ?: t
                        val m = reel.message ?: ""
                        val refusVoulu = m.contains("requires the Pro", ignoreCase = true) ||
                            m.contains("Free tier", ignoreCase = true)
                        note(
                            "WHISPER",
                            if (refusVoulu || reel is UnsatisfiedLinkError ||
                                reel is NoClassDefFoundError ||
                                reel is ClassNotFoundException
                            ) "ABSENT" else "ECHEC",
                            reel.javaClass.simpleName + " " + m,
                        )
                    }
                }
            }
        }

        val f = File(out, "extra.txt")
        f.writeText(lignes.toString())
        val ok = lignes.lineSequence().count { it.contains("|OK|") }
        val abs = lignes.lineSequence().count { it.contains("|ABSENT|") }
        val ech = lignes.lineSequence().count { it.contains("|ECHEC|") }
        Log.i("EXTRA", "--- BILAN : $ok OK, $abs ABSENT, $ech ECHEC -> ${f.absolutePath}")
    }
}
