plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Имя выходного APK: deepal-player-<buildType>.apk (вместо app-<buildType>.apk).
base { archivesName.set("deepal-player") }

android {
    namespace = "com.deepal.videocast"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.deepal.videocast"
        // S09 = HarmonyOS 4.2 / Android 12 base. minSdk 31 поднимаем
        // чтобы спокойно использовать WindowInsetsController и Display API.
        minSdk = 31
        targetSdk = 34
        versionCode = 1
        versionName = "0.1"
    }

    signingConfigs {
        create("release") {
            // Самоподписной keystore — не для Play Store, только для
            // sideload'a знакомым. Сам файл и пароли в репо не коммитятся.
            // Положи keystore в app/release.keystore, а пароли — в
            // ~/.gradle/gradle.properties:
            //   RELEASE_STORE_PASSWORD=...
            //   RELEASE_KEY_PASSWORD=...
            //   RELEASE_KEY_ALIAS=deepal-player
            storeFile = file("release.keystore")
            storePassword = (project.findProperty("RELEASE_STORE_PASSWORD") as String?)
                ?: System.getenv("RELEASE_STORE_PASSWORD") ?: ""
            keyAlias = (project.findProperty("RELEASE_KEY_ALIAS") as String?)
                ?: System.getenv("RELEASE_KEY_ALIAS") ?: "deepal-player"
            keyPassword = (project.findProperty("RELEASE_KEY_PASSWORD") as String?)
                ?: System.getenv("RELEASE_KEY_PASSWORD") ?: ""
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            // R8 пока выключен — libaums/nextlib/media3 пакуют свои
            // proguard-consumer-rules, теоретически работает; включим
            // после стабилизации.
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
    }
    // composeOptions больше не нужен — Compose-плагин K2 сам управляет
    // версией компилятора (привязан к версии Kotlin).
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")

    // Compose BOM — все Compose-артефакты выровнены одной версией.
    val composeBom = platform("androidx.compose:compose-bom:2024.09.02")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    debugImplementation("androidx.compose.ui:ui-tooling")

    val media3 = "1.7.1"
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-ui:$media3")
    implementation("androidx.media3:media3-datasource:$media3")
    // FFmpeg-based software audio + video decoders (EAC3 / DTS / TrueHD / FLAC /
    // Opus / Vorbis / ALAC / MP3 / AAC / AC4, plus AV1 / H.265 / VP9 fallback).
    // На Cockpit-mixer'ах часто нет EAC3 у платформы — без этого пакета на
    // WEB-DL / BDRip контенте получаем "no sound".
    implementation("io.github.anilbeesetti:nextlib-media3ext:1.7.1-0.9.0")

    // Android USB Mass Storage — парсит FAT32/exFAT в Java поверх
    // UsbDeviceConnection. Это путь который использует MX Player
    // (liblibusb.so + свой FAT parser). Не зависит от vold/system mount,
    // работает на S09 где vold флешку не показывает.
    implementation("me.jahnen.libaums:core:0.10.0")
    // нативный libusb backend (быстрее, обходит UsbDeviceConnection JNI overhead)
    implementation("me.jahnen.libaums:libusbcommunication:0.3.0")
}
