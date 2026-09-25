package dev.ffmpegkit.smoke

import android.app.Activity
import android.os.Bundle
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.ReturnCode
import java.io.File

/**
 * Le test du produit, pas de la recette.
 *
 * Les deux correctifs d'issue #1 se vérifient à l'exécution ou pas du tout :
 *
 *  - `drawtext` est-il **dans** le binaire livré ? `check-filters.py` lit la table des
 *    filtres, ce qui est déjà mieux que lire les scripts de build — mais une chaîne
 *    dans un `.so` n'est pas un filtre qui rend une image.
 *  - le plantage sur un filtergraph fautif **ne se voit pas** dans un code de retour :
 *    le processus mourait. La seule preuve qu'il ne meurt plus est qu'il réponde
 *    encore après.
 *
 * D'où la troisième vérification, qui est la vraie : **survivre**. Une commande
 * ordinaire lancée APRÈS la commande fautive. Si le correctif manquait, ce test-ci
 * n'écrirait jamais sa ligne — l'application ne serait plus là pour l'écrire.
 *
 *
 * ## Ce que ce harnais ne peut PAS prouver
 *
 * Mesure du 2026-09-24, Pixel 7 Pro / Android 16, sur l'AAR fautif de juin (n8.1.2,
 * sans le correctif) : `1 DRAWTEXT ECHEC`, et `3 SURVIE OK`. Le filtre manquant se
 * reproduit ; le plantage, non — y compris avec la forme exacte du client (2b).
 *
 * C'est attendu, et il faut le dire plutot que de lire `3 SURVIE OK` comme un verdict :
 * le defaut est un `AVFilterInOut*` non initialise qu'on libere sur le chemin d'erreur.
 * La valeur liberee est ce que la pile contenait, donc le plantage depend de l'appel
 * precedent, de l'appareil et de la version d'Android. Ici elle se trouve etre nulle.
 *
 * **Une verification qui ne peut pas voir le defaut rend un verdict de succes pour ce
 * defaut-la.** La preuve du second correctif est la lecture du code et le tombstone du
 * client, qui nomme exactement `avfilter_inout_free` sous `init_complex_filtergraph` ;
 * pas cette ligne-ci.
 *
 * Ce qui reste vrai : si le harnais affiche `1 DRAWTEXT OK`, drawtext rend une image.
 *
 * ```
 * adb shell am start -S -n dev.ffmpegkit.smoke/.SmokeActivity
 * adb logcat -d -s SMOKE
 * ```
 */
class SmokeActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val out = getExternalFilesDir(null) ?: filesDir
        Thread { lancer(out) }.start()
    }

    private fun lancer(out: File) {
        dire("--- DEBUT ---")
        dire("ffmpeg    " + runCatching { FFmpegKitConfig.getFFmpegVersion() }.getOrElse { "?" })
        dire("build     " + runCatching { FFmpegKitConfig.getBuildDate() }.getOrElse { "?" })

        // 0. La question faisant autorite : ffmpeg est-il d'accord pour dire que le
        // filtre existe ? Une chaine dans un .so n'est pas une inscription dans la
        // table des filtres -- `tools/check-filters.py` compte des octets et peut donc
        // repondre "present" pour un reste de chaine. `-filters` lit la table.
        val liste = FFmpegKit.execute("-hide_banner -filters")
        val sortie = liste.output ?: ""
        val inscrits = sortie.lineSequence()
            .mapNotNull { Regex("""^\s*[A-Z.]{3}\s+(\S+)""").find(it)?.groupValues?.get(1) }
            .toSet()
        dire("0 TABLE  " + inscrits.size + " filtres inscrits")
        for (f in listOf("drawtext", "drawbox", "subtitles", "ass", "scale", "overlay")) {
            dire("   " + (if (f in inscrits) "oui " else "NON ") + f)
        }

        val police = listOf(
            "/system/fonts/Roboto-Regular.ttf",
            "/system/fonts/DroidSans.ttf",
            "/system/fonts/NotoSansCJK-Regular.ttc",
        ).firstOrNull { File(it).exists() }
        if (police == null) {
            dire("ECHEC aucune police systeme trouvee -- le test drawtext ne peut pas conclure")
            return
        }
        dire("police    $police")

        // 1. drawtext rend-il une image ?
        val png = File(out, "drawtext.png")
        png.delete()
        val r1 = FFmpegKit.execute(
            "-f lavfi -i color=c=blue:s=320x240:d=1 " +
                "-vf drawtext=fontfile='$police':text='OK':fontsize=48:fontcolor=white " +
                "-frames:v 1 -y " + png.absolutePath,
        )
        val ok1 = ReturnCode.isSuccess(r1.returnCode) && png.exists() && png.length() > 1000
        dire("1 DRAWTEXT " + (if (ok1) "OK" else "ECHEC") +
            " code=" + r1.returnCode + " octets=" + (if (png.exists()) png.length() else 0))
        if (!ok1) dire("   sortie : " + r1.output?.takeLast(400))

        // 2. un filtergraph fautif doit RENDRE une erreur, pas tuer le processus.
        val r2 = FFmpegKit.execute(
            "-f lavfi -i color=c=red:s=64x64:d=1 " +
                "-filter_complex \"[0:v]iln_y_a_pas_de_filtre_comme_ca=1[o]\" -map \"[o]\" " +
                "-frames:v 1 -y " + File(out, "jamais.png").absolutePath,
        )
        val ok2 = !ReturnCode.isSuccess(r2.returnCode)
        dire("2 MAUVAIS_FILTRE " + (if (ok2) "OK" else "ECHEC") + " code=" + r2.returnCode)

        // 2b. LA forme exacte du client : -filter_complex contenant drawtext, sur un
        // build ou drawtext n'existe pas. C'est le chemin du tombstone de l'issue #1
        // (init_complex_filtergraph -> avfilter_inout_free), et non celui de -vf, qui
        // passe par init_simple_filtergraph.
        val r2b = FFmpegKit.execute(
            "-f lavfi -i color=c=red:s=64x64:d=1 " +
                "-filter_complex \"[0:v]drawtext=fontfile='$police':text='X'[o]\" -map \"[o]\" " +
                "-frames:v 1 -y " + File(out, "complexe.png").absolutePath,
        )
        dire("2b COMPLEX_DRAWTEXT code=" + r2b.returnCode +
            " (succes attendu si drawtext existe, erreur sinon -- jamais un plantage)")

        // 3. LA preuve. Si le processus etait mort en 2, cette ligne n'existerait pas.
        val png3 = File(out, "apres.png")
        png3.delete()
        val r3 = FFmpegKit.execute(
            "-f lavfi -i color=c=green:s=32x32:d=1 -frames:v 1 -y " + png3.absolutePath,
        )
        val ok3 = ReturnCode.isSuccess(r3.returnCode) && png3.exists()
        dire("3 SURVIE " + (if (ok3) "OK" else "ECHEC") + " code=" + r3.returnCode)

        // 4. LA sonde du plantage, et la raison de sa forme.
        //
        // `graph_parse` n'ecrit `*inputs` que dans `avfilter_graph_segment_apply`, tout
        // a la fin. Un filtre inconnu echoue avant, a `segment_create_filters`, et rend
        // la main sans avoir touche le pointeur. L'appelant libere alors ce que la pile
        // contenait -- donc le plantage depend de ce qu'un appel PRECEDENT y a laisse.
        //
        // Un seul mauvais filtergraph ne prouve donc rien : sur une pile propre, la
        // valeur est nulle et `avfilter_inout_free(NULL)` ne fait rien. Il faut salir la
        // pile d'abord, avec un graphe valide et profond, puis echouer tout de suite.
        //
        // Sur un binaire NON corrige, ce test doit tuer le processus : la ligne de bilan
        // qui suit ne serait jamais ecrite. C'est ce qu'on verifie avant de s'en servir
        // comme preuve.
        val sale = "-f lavfi -i color=c=blue:s=64x64:d=1 -filter_complex " +
            "\"[0:v]split=3[a][b][c];[a]scale=32:32[x];[b]hflip[y];[c]negate[z];" +
            "[x][y]hstack[h];[h][z]hstack[o]\" -map \"[o]\" -frames:v 1 -y " +
            File(out, "sale.png").absolutePath
        // Trois formes d'echec, qui ne sortent pas de `graph_parse` au meme endroit :
        //   a) filtre inconnu des le debut   -> echec dans segment_create_filters, tot
        //   b) filtre inconnu en dernier     -> meme fonction, apres 6 filtres crees
        //   c) option inconnue sur un filtre valide -> echec plus loin, dans graph_opts_apply
        // Plus l'echec est tardif, plus la pile a ete remuee avant que l'appelant
        // libere ce qu'elle contient.
        val casses = listOf(
            "[0:v]zzz_pas_un_filtre=1[o]",
            "[0:v]split=3[a][b][c];[a]hflip[x];[b]negate[y];[c]scale=32:32[z];" +
                "[x][y]hstack[h];[h][z]hstack[m];[m]zzz_pas_un_filtre[o]",
            "[0:v]scale=option_qui_nexiste_pas=3[o]",
        ).map { g ->
            "-f lavfi -i color=c=blue:s=64x64:d=1 -filter_complex \"" + g +
                "\" -map \"[o]\" -frames:v 1 -y " + File(out, "jamais2.png").absolutePath
        }

        var tours = 0
        for (i in 1..60) {
            FFmpegKit.execute(sale)
            for (c in casses) FFmpegKit.execute(c)
            tours = i
            if (i % 20 == 0) dire("4 SONDE   $i tours sans plantage")
        }
        dire("4 SONDE   OK $tours tours, le processus repond encore")

        val tout = ok1 && ok2 && ok3 && tours == 60
        // 5. L'inventaire complet, pour comparer AVANT et APRES.
        //
        // Un pin qui bouge (n8.1.2 -> n8.1.3) est une montee amont : elle peut retirer
        // un codec, un muxer, un protocole, sans que rien ne le signale. Le harnais
        // ci-dessus verifie six filtres ; il ne dit RIEN des 477 autres.
        //
        // On ecrit donc tout ce que le binaire declare savoir faire, et on compare le
        // fichier produit par l'ancienne version et par la nouvelle. Ce qui etait la
        // et n'y est plus est une regression, quel que soit l'endroit.
        val inventaire = File(out, "inventaire.txt")
        // ⚠️ La version en TETE du fichier, pas seulement dans le journal.
        // Lire la version dans logcat expose a une course : le tampon peut avoir
        // tourne, ou l'activite avoir demarre avant que `logcat -c` prenne effet, et
        // la garde rend alors "aucune version" pour une mesure parfaitement valide.
        // Ecrite dans le fichier, elle voyage avec la donnee qu'elle qualifie.
        inventaire.writeText(
            "##### version " +
                runCatching { FFmpegKitConfig.getFFmpegVersion() }.getOrElse { "?" } +
                System.lineSeparator(),
        )
        inventaire.appendText(
            listOf("-filters", "-encoders", "-decoders", "-muxers", "-demuxers",
                   "-protocols", "-formats", "-bsfs", "-pix_fmts")
                .joinToString(System.lineSeparator()) { quoi ->
                    val r = FFmpegKit.execute("-hide_banner $quoi")
                    "===== " + quoi + System.lineSeparator() + (r.output ?: "")
                },
        )
        dire("5 INVENTAIRE ecrit " + inventaire.length() + " octets -> " + inventaire.absolutePath)

        dire("--- BILAN : " + (if (tout) "TOUT OK" else "AU MOINS UN ECHEC") + " ---")
    }

    private fun dire(l: String) = Log.i("SMOKE", l)
}
