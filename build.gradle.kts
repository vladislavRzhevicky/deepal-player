plugins {
    // AGP 8.7+ нужен для compileSdk 35, который требует media3 1.7.x.
    // Kotlin 2.0.21 — nextlib-media3ext скомпилен 2.1, наш компайлер
    // 2.0 читает 2.1 metadata. Compose-плагин разъезжается с
    // kotlinCompilerExtensionVersion начиная с K2 → используем его.
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
}
