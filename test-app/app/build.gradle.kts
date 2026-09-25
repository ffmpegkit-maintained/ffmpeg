plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
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
