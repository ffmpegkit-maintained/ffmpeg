package dev.ffmpegkit.smoke

import android.app.Activity
import android.os.Bundle
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.ReturnCode
import java.io.File

/**
 * Tests d'acceptation : est-ce que le produit **fait son travail** ?
 *
 * Le harnais de fumée ([SmokeActivity]) répond à « le filtre manquant est-il revenu »
 * et « le processus survit-il ». Aucun des deux n'encode une seconde de vidéo. Une
 * bibliothèque peut inscrire 483 filtres dans sa table et produire des fichiers vides.
 *
 * Chaque épreuve :
 *  - lance une commande que ferait une vraie application,
 *  - vérifie le **fichier produit**, pas le code de retour — un `ffmpeg` qui rend 0 en
 *    écrivant 0 octet est exactement le genre de succès qui trompe,
 *  - et, quand c'est possible, fait relire le résultat par `ffprobe`.
 *
 * ## ⚠️ Pourquoi on interroge les tables avant de lancer quoi que ce soit
 *
 * Les paliers ne portent pas les mêmes codecs : `min` n'a pas x264, le palier gratuit
 * n'a pas libmp3lame. Une épreuve qui exige ce que le palier n'a jamais promis n'est
 * pas un échec du produit.
 *
 * La première version classait `ABSENT` en **cherchant des motifs dans le texte
 * d'erreur de ffmpeg** — « Unknown encoder », « No such filter ». C'est deviner, et ça
 * s'est vu : `VIGNETTE_SEEK` et `FILTRE_SOUSTITRES` rendaient `ECHEC` alors que le
 * palier n'avait simplement pas l'encodeur PNG ni libass, parce que leur message ne
 * figurait pas dans ma liste.
 *
 * Pire, `SURVIE_APRES_ERREURS` écrivait un PNG : sur un palier sans encodeur PNG,
 * l'épreuve censée détecter un **plantage** échouait pour une raison qui n'a rien à
 * voir. Une épreuve qui ne peut pas distinguer « mort » de « codec absent » ne dit
 * rien du tout.
 *
 * Désormais : on lit `-encoders`, `-filters`, `-muxers` et `-demuxers` **une fois**, et
 * chaque épreuve déclare ce dont elle a besoin. Ce qui manque est `ABSENT` sans être
 * lancé ; ce qui est présent doit marcher, sinon c'est `ECHEC`. Et la survie ne
 * dépend plus d'aucun codec : `-f null -` n'écrit rien et existe partout.
 *
 * ```
 * adb shell am start -S -n dev.ffmpegkit.smoke/.AcceptanceActivity
 * adb pull .../files/acceptation.txt
 * ```
 */
class AcceptanceActivity : Activity() {

    private val lignes = StringBuilder()
    private var encodeurs: Set<String> = emptySet()
    private var filtres: Set<String> = emptySet()
    private var muxers: Set<String> = emptySet()
    private var demuxers: Set<String> = emptySet()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val out = getExternalFilesDir(null) ?: filesDir
        Thread { lancer(out) }.start()
    }

    // ── Ce que le binaire declare savoir faire ────────────────────────────────

    /** Le deuxieme mot de chaque ligne de table : `V..... libx264  H.264 ...`. */
    private fun table(option: String): Set<String> {
        val s = FFmpegKit.execute("-hide_banner $option")
        return (s.output ?: "").lineSequence()
            .mapNotNull { Regex("""^\s*[A-Z.]{1,6}\s+(\S+)""").find(it)?.groupValues?.get(1) }
            .filter { it != "=" }
            .toSet()
    }

    /** Ce qui manque parmi les capacites exigees, sous forme lisible. */
    private fun manquant(besoins: List<Pair<String, String>>): String? {
        val absents = besoins.filter { (genre, nom) ->
            when (genre) {
                "enc" -> nom !in encodeurs
                "filtre" -> nom !in filtres
                "mux" -> nom !in muxers
                "demux" -> nom !in demuxers
                else -> false
            }
        }
        return if (absents.isEmpty()) null
        else absents.joinToString(", ") { it.first + " " + it.second } + " absent(s) de ce palier"
    }

    // ── Une epreuve ───────────────────────────────────────────────────────────

    private fun epreuve(
        nom: String,
        commande: String,
        besoins: List<Pair<String, String>> = emptyList(),
        verifier: () -> String?,
    ) {
        manquant(besoins)?.let { note(nom, "ABSENT", it); return }
        val s = FFmpegKit.execute(commande)
        val code = s.returnCode
        val souci = try {
            verifier()
        } catch (t: Throwable) {
            "exception " + t.javaClass.simpleName + " " + t.message
        }
        if (souci == null && ReturnCode.isSuccess(code)) {
            note(nom, "OK", "code=$code")
        } else {
            note(nom, "ECHEC", (souci ?: extraitCause(s.output)) + " code=" + code)
        }
    }

    private fun extraitCause(sortie: String?): String =
        (sortie ?: "").lineSequence().lastOrNull { it.isNotBlank() }?.take(110)
            ?: "(pas de sortie)"

    private fun note(nom: String, etat: String, detail: String) {
        lignes.append(nom).append('|').append(etat).append('|')
            .append(detail.replace('\n', ' ').replace('|', '/')).append('\n')
        Log.i("ACCEPT", String.format("%-22s %-7s %s", nom, etat, detail.take(90)))
    }

    // ── Relire le fichier produit ─────────────────────────────────────────────

    /**
     * ⚠️ En JSON. La sortie de `FFprobeKit` contient aussi les lignes de journal de la
     * session, et lire « la premiere ligne » rendait `[vp9 @ 0x…] RGB not supported`
     * comme si c'etait un nom de codec. Quatre epreuves ont accuse le produit pour ça.
     */
    private fun sonde(f: File, champ: String, flux: String = "v:0"): String? =
        champJson(f, "stream=$champ", champ, "-select_streams $flux ")

    /** Un champ au niveau conteneur : Matroska ne porte pas la duree sur le flux. */
    private fun sondeFormat(f: File, champ: String): String? =
        champJson(f, "format=$champ", champ, "")

    private fun champJson(f: File, entrees: String, champ: String, extra: String): String? {
        if (!f.exists() || f.length() == 0L) return null
        val s = FFprobeKit.execute("-v quiet $extra-show_entries $entrees -of json " + f.absolutePath)
        val t = s.output ?: return null
        val motif = "\"" + Regex.escape(champ) + "\"\\s*:\\s*\"?([^\",}\\n]+)"
        val v = Regex(motif).find(t)?.groupValues?.get(1)?.trim() ?: return null
        return if (v.isBlank() || v == "N/A") null else v
    }

    private fun pese(f: File, mini: Long): String? = when {
        !f.exists() -> "fichier absent : " + f.name
        f.length() < mini -> "fichier trop petit : " + f.length() + " octets (mini " + mini + ")"
        else -> null
    }

    // ── La batterie ───────────────────────────────────────────────────────────

    private fun lancer(out: File) {
        val version = runCatching { FFmpegKitConfig.getFFmpegVersion() }.getOrElse { "?" }
        lignes.append("##### version ").append(version).append('\n')

        encodeurs = table("-encoders")
        filtres = table("-filters")
        muxers = table("-muxers")
        demuxers = table("-demuxers")
        Log.i(
            "ACCEPT",
            "--- $version : ${encodeurs.size} encodeurs, ${filtres.size} filtres, " +
                "${muxers.size} muxers, ${demuxers.size} demuxers",
        )
        lignes.append("##### tables ").append(encodeurs.size).append(' ')
            .append(filtres.size).append(' ').append(muxers.size).append(' ')
            .append(demuxers.size).append('\n')

        out.listFiles { f -> f.name.startsWith("acc-") }?.forEach { it.delete() }
        fun fic(n: String) = File(out, "acc-$n")

        val police = listOf(
            "/system/fonts/Roboto-Regular.ttf",
            "/system/fonts/DroidSans.ttf",
        ).firstOrNull { File(it).exists() }

        // La source de tout le reste : 3 s de video et un son.
        val source = fic("source.mp4")
        epreuve(
            "SOURCE_CREEE",
            "-f lavfi -i testsrc=size=320x240:rate=25:duration=3 " +
                "-f lavfi -i sine=frequency=440:duration=3 " +
                "-c:v mpeg4 -c:a aac -shortest -y " + source.absolutePath,
            listOf("enc" to "mpeg4", "enc" to "aac", "demux" to "lavfi", "mux" to "mp4"),
        ) { pese(source, 10_000) }

        if (!source.exists() || source.length() < 10_000) {
            note("SUITE", "ECHEC", "source absente, les epreuves suivantes sont sans objet")
            ecrire(out)
            return
        }

        epreuve("PROBE_RESOLUTION", "-hide_banner -version") {
            val l = sonde(source, "width")
            val h = sonde(source, "height")
            if (l == "320" && h == "240") null else "ffprobe rend ${l}x${h}, attendu 320x240"
        }
        epreuve("PROBE_DUREE", "-hide_banner -version") {
            val d = sondeFormat(source, "duration")?.toDoubleOrNull()
            if (d != null && d > 2.5 && d < 3.5) null else "duree lue : $d, attendu ~3.0"
        }
        epreuve("PROBE_AUDIO", "-hide_banner -version") {
            if (sonde(source, "codec_name", "a:0") != null) null else "aucun flux audio lisible"
        }

        val mkv = fic("remux.mkv")
        epreuve(
            "REMUX_COPY",
            "-i ${source.absolutePath} -c copy -y ${mkv.absolutePath}",
            listOf("mux" to "matroska"),
        ) {
            pese(mkv, 8_000) ?: run {
                val d = sondeFormat(mkv, "duration")?.toDoubleOrNull()
                if (d != null && d > 2.5) null else "duree perdue au remux : $d"
            }
        }

        val h264 = fic("h264.mp4")
        epreuve(
            "ENCODE_H264",
            "-i ${source.absolutePath} -c:v libx264 -preset ultrafast -t 1 -an -y " + h264.absolutePath,
            listOf("enc" to "libx264"),
        ) {
            pese(h264, 2_000) ?: run {
                val c = sonde(h264, "codec_name")
                if (c == "h264") null else "codec ecrit : $c, attendu h264"
            }
        }

        val h265 = fic("h265.mp4")
        epreuve(
            "ENCODE_H265",
            "-i ${source.absolutePath} -c:v libx265 -preset ultrafast " +
                "-x265-params log-level=none -t 1 -an -y " + h265.absolutePath,
            listOf("enc" to "libx265"),
        ) {
            pese(h265, 1_000) ?: run {
                val c = sonde(h265, "codec_name")
                if (c == "hevc") null else "codec ecrit : $c, attendu hevc"
            }
        }

        val vp9 = fic("vp9.webm")
        epreuve(
            "ENCODE_VP9",
            "-i ${source.absolutePath} -c:v libvpx-vp9 -pix_fmt yuv420p -b:v 200k " +
                "-cpu-used 8 -t 1 -an -y " + vp9.absolutePath,
            listOf("enc" to "libvpx-vp9", "mux" to "webm"),
        ) {
            pese(vp9, 1_000) ?: run {
                val c = sonde(vp9, "codec_name")
                if (c == "vp9") null else "codec ecrit : $c, attendu vp9"
            }
        }

        val mp3 = fic("son.mp3")
        epreuve(
            "ENCODE_MP3",
            "-i ${source.absolutePath} -vn -c:a libmp3lame -b:a 64k -t 1 -y " + mp3.absolutePath,
            listOf("enc" to "libmp3lame"),
        ) {
            pese(mp3, 2_000) ?: run {
                val c = sonde(mp3, "codec_name", "a:0")
                if (c == "mp3") null else "codec ecrit : $c, attendu mp3"
            }
        }

        val opus = fic("son.opus")
        epreuve(
            "ENCODE_OPUS",
            "-i ${source.absolutePath} -vn -c:a libopus -b:a 48k -t 1 -y " + opus.absolutePath,
            listOf("enc" to "libopus"),
        ) {
            pese(opus, 1_000) ?: run {
                val c = sonde(opus, "codec_name", "a:0")
                if (c == "opus") null else "codec ecrit : $c, attendu opus"
            }
        }

        val filtre = fic("filtre.mp4")
        epreuve(
            "FILTRE_SCALE_CROP",
            "-i ${source.absolutePath} -vf scale=160:120,crop=100:80:10:10,hflip " +
                "-t 1 -an -c:v mpeg4 -y " + filtre.absolutePath,
            listOf("filtre" to "scale", "filtre" to "crop", "filtre" to "hflip"),
        ) {
            pese(filtre, 2_000) ?: run {
                val l = sonde(filtre, "width")
                if (l == "100") null else "largeur apres crop : $l, attendu 100"
            }
        }

        val overlay = fic("overlay.mp4")
        epreuve(
            "FILTRE_OVERLAY",
            "-i ${source.absolutePath} -f lavfi -i color=c=red:s=40x40:d=1 " +
                "-filter_complex \"[0:v][1:v]overlay=10:10\" -t 1 -an -c:v mpeg4 -y " +
                overlay.absolutePath,
            listOf("filtre" to "overlay"),
        ) { pese(overlay, 2_000) }

        // Le defaut d'origine de l'issue #1, exige sur un fichier reel.
        val texte = fic("drawtext.mp4")
        if (police == null) {
            note("FILTRE_DRAWTEXT", "ABSENT", "aucune police systeme sur cet appareil")
        } else {
            epreuve(
                "FILTRE_DRAWTEXT",
                "-i ${source.absolutePath} -vf drawtext=fontfile='$police':text='Jokobee':" +
                    "fontsize=24:fontcolor=white:x=10:y=10 -t 1 -an -c:v mpeg4 -y " +
                    texte.absolutePath,
                listOf("filtre" to "drawtext"),
            ) { pese(texte, 2_000) }
        }

        val sousTitre = fic("ass.mp4")
        val ass = File(out, "acc-sub.ass")
        ass.writeText(
            "[Script Info]\nScriptType: v4.00+\n\n[V4+ Styles]\n" +
                "Format: Name, Fontname, Fontsize\nStyle: Default,Arial,20\n\n" +
                "[Events]\nFormat: Layer, Start, End, Style, Text\n" +
                "Dialogue: 0,0:00:00.00,0:00:02.00,Default,Bonjour\n",
        )
        epreuve(
            "FILTRE_SOUSTITRES",
            "-i ${source.absolutePath} -vf ass='${ass.absolutePath}' " +
                "-t 1 -an -c:v mpeg4 -y " + sousTitre.absolutePath,
            listOf("filtre" to "ass"),
        ) { pese(sousTitre, 2_000) }

        val audio = fic("audio.m4a")
        epreuve(
            "AUDIO_VOLUME_ATEMPO",
            "-i ${source.absolutePath} -vn -af volume=0.5,atempo=1.5 -t 1 -c:a aac -y " +
                audio.absolutePath,
            listOf("filtre" to "volume", "filtre" to "atempo", "enc" to "aac"),
        ) { pese(audio, 1_000) }

        val vignette = fic("vignette.png")
        epreuve(
            "VIGNETTE_SEEK",
            "-ss 1.5 -i ${source.absolutePath} -frames:v 1 -y " + vignette.absolutePath,
            listOf("enc" to "png", "mux" to "image2"),
        ) {
            pese(vignette, 1_000) ?: run {
                val l = sonde(vignette, "width")
                if (l == "320") null else "vignette large de $l, attendu 320"
            }
        }

        val meta = fic("meta.mp4")
        epreuve(
            "METADONNEES",
            "-i ${source.absolutePath} -c copy -metadata title=JokobeeTest -y " + meta.absolutePath,
            listOf("mux" to "mp4"),
        ) {
            pese(meta, 8_000) ?: run {
                val t = champJson(meta, "format_tags=title", "title", "")
                if (t == "JokobeeTest") null else "titre relu : '$t', attendu 'JokobeeTest'"
            }
        }

        val liste = File(out, "acc-liste.txt")
        liste.writeText("file '${source.absolutePath}'\nfile '${source.absolutePath}'\n")
        val concat = fic("concat.mp4")
        epreuve(
            "CONCAT",
            "-f concat -safe 0 -i ${liste.absolutePath} -c copy -y " + concat.absolutePath,
            listOf("demux" to "concat"),
        ) {
            pese(concat, 15_000) ?: run {
                val d = sondeFormat(concat, "duration")?.toDoubleOrNull()
                if (d != null && d > 5.0) null else "duree apres concat : $d, attendu ~6.0"
            }
        }

        // ── Erreurs : rendre une erreur, ne pas mourir ─────────────────────────
        val s1 = FFmpegKit.execute("-i /introuvable/vraiment.mp4 -f null -")
        note(
            "ERREUR_FICHIER_ABSENT",
            if (!ReturnCode.isSuccess(s1.returnCode)) "OK" else "ECHEC",
            "code=" + s1.returnCode,
        )
        val s2 = FFmpegKit.execute(
            "-i ${source.absolutePath} -filter_complex \"[0:v]pas_un_filtre[o]\" " +
                "-map \"[o]\" -f null -",
        )
        note(
            "ERREUR_FILTRE_INCONNU",
            if (!ReturnCode.isSuccess(s2.returnCode)) "OK" else "ECHEC",
            "code=" + s2.returnCode,
        )

        // ⚠️ La survie ne doit dependre d'AUCUN codec. La premiere version ecrivait un
        // PNG : sur un palier sans encodeur PNG, l'epreuve censee detecter un plantage
        // echouait pour une raison qui n'a rien a voir, et se lisait comme un plantage.
        // `-f null -` decode, filtre, et jette le resultat. Il existe partout.
        val s3 = FFmpegKit.execute(
            "-f lavfi -i color=c=green:s=32x32:d=1 -vf scale=16:16 -f null -",
        )
        note(
            "SURVIE_APRES_ERREURS",
            if (ReturnCode.isSuccess(s3.returnCode)) "OK" else "ECHEC",
            "code=" + s3.returnCode + " (aucun encodeur requis)",
        )

        ecrire(out)
    }

    private fun ecrire(out: File) {
        val f = File(out, "acceptation.txt")
        f.writeText(lignes.toString())
        val ok = lignes.lineSequence().count { it.contains("|OK|") }
        val absent = lignes.lineSequence().count { it.contains("|ABSENT|") }
        val echec = lignes.lineSequence().count { it.contains("|ECHEC|") }
        Log.i("ACCEPT", "--- BILAN : $ok OK, $absent ABSENT, $echec ECHEC -> ${f.absolutePath}")
    }
}
