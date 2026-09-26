plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// ⚠️ Un repertoire de sortie par palier teste, sur demande.
//
// Mesure du 2026-09-25 sur Windows : apres un passage, `app-debug.apk` reste verrouille
// par un processus Java resident (les demons Gradle affiches STOPPED laissent des
// processus vivants). Le passage suivant echoue a l'effacer --
//
//   java.io.IOException: Unable to delete directory '…/app/build/outputs/apk/debug'
//
// -- et neuf paliers ont ete perdus ainsi, pour une cause qui n'a rien a voir avec le
// produit mesure. Plutot que de lutter contre le verrou, on ne reecrit plus le meme
// chemin : `-PbuildTag=<palier>` donne a chaque passage sa propre sortie.
val buildTag: String? = providers.gradleProperty("buildTag").orNull
if (buildTag != null) {
    layout.buildDirectory.set(layout.projectDirectory.dir("build-$buildTag"))
}

android {
    namespace = "dev.ffmpegkit.smoke"
    compileSdk = 35
    defaultConfig {
        applicationId = "dev.ffmpegkit.smoke"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        // L'AAR 8.1 gpl est arm64 SEUL : un emulateur x86_64 ne peut pas le charger.
        // Les artefacts Maven portent arm64-v8a ET x86_64 ; on garde les deux pour
        // pouvoir passer le meme APK sur le telephone et sur l'emulateur. Le payant
        // n'a qu'arm64, et AGP se contente alors de ce qu'il trouve.
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    // ⚠️ Les .so sont EXTRAITS, pas mappes depuis l'APK.
    //
    // Mesure du 2026-09-25 sur l'emulateur x86_64 (API 34, 6 Go) : charger les dix .so
    // directement depuis l'APK echoue par intermittence, selon le palier, avec
    //
    //   dlopen failed: can't enable GNU RELRO protection for
    //   ".../base.apk!/lib/x86_64/libavdevice.so": Out of memory
    //
    // Un palier passait, le suivant non, le meme palier changeait d'avis d'un
    // passage a l'autre. Un banc dont le verdict depend de qui gagne une course ne
    // mesure rien -- et ici la course n'etait meme pas dans le produit, elle etait
    // dans la facon dont le harnais empaquete l'APK.
    //
    // L'extraction sur disque supprime cette variable. Ce que le banc doit juger
    // reste juge : les memes .so livres par l'AAR, ouverts par le meme dlopen.
    packaging { jniLibs { useLegacyPackaging = true } }
}

// L'AAR a tester arrive par sa VALEUR, jamais par une copie dans l'arbre.
//
// Ce harnais teste aussi des paliers payants. Sur un depot public, un AAR copie dans
// `app/libs/` n'est protege que par une ligne de .gitignore -- et une garde CI ne
// protegerait pas : elle s'execute APRES le push, quand le fichier est deja public,
// et une reecriture d'historique ne purge pas vraiment GitHub (le projet l'a appris
// en juillet 2026). Il faut donc que le fichier n'entre jamais dans l'arbre.
//
//     ./gradlew :app:assembleDebug -PtestAar=D:/chemin/vers/ffmpeg-kit.aar
//
// Meme geste que le module `bench` du SDK Jokobee, qui mesure l'AAR livre par
// `-PbenchAar=<chemin>` pour la meme raison.
//
// `app/libs/` reste accepte en repli pour un essai a la main, et reste gitignore.
val testAar: String? = providers.gradleProperty("testAar").orNull

dependencies {
    if (testAar != null) {
        implementation(files(testAar))
    } else {
        implementation(fileTree("libs") { include("*.aar") })
    }
    implementation("com.arthenica:smart-exception-java:0.2.1")
}
