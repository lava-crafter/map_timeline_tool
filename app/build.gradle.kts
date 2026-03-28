import java.io.File
import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val signingPropertiesFile = File(System.getProperty("user.home"), ".android/release-signing.properties")
val signingProperties = Properties().apply {
    if (signingPropertiesFile.isFile) {
        signingPropertiesFile.inputStream().use { load(it) }
    }
}

fun signingValue(name: String, envName: String): String? {
    return signingProperties.getProperty(name)
        ?: providers.gradleProperty(name).orNull
        ?: System.getenv(envName)
}

val releaseStoreFile = File(
    signingProperties.getProperty(
        "storeFile",
        File(System.getProperty("user.home"), ".android/my-release-key.jks").absolutePath
    )
)
val releaseStorePassword = signingValue("storePassword", "RELEASE_STORE_PASSWORD")
val releaseKeyAlias = signingValue("keyAlias", "RELEASE_KEY_ALIAS")
val releaseKeyPassword = signingValue("keyPassword", "RELEASE_KEY_PASSWORD")
val releaseSigningReady = releaseStoreFile.isFile &&
    !releaseStorePassword.isNullOrBlank() &&
    !releaseKeyAlias.isNullOrBlank() &&
    !releaseKeyPassword.isNullOrBlank()

plugins {
    id("com.android.application")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.lavacrafter.maptimelinetool"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.lavacrafter.maptimelinetool"
        minSdk = 24
        targetSdk = 36
        versionCode = 5
        versionName = "0.1.6"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    buildTypes {
        release {
            check(releaseSigningReady) {
                "Release signing is not configured. Create ~/.android/release-signing.properties with storePassword, keyAlias, and keyPassword, or set RELEASE_STORE_PASSWORD / RELEASE_KEY_ALIAS / RELEASE_KEY_PASSWORD. The keystore defaults to ~/.android/my-release-key.jks."
            }

            val resolvedReleaseStorePassword = requireNotNull(releaseStorePassword)
            val resolvedReleaseKeyAlias = requireNotNull(releaseKeyAlias)
            val resolvedReleaseKeyPassword = requireNotNull(releaseKeyPassword)

            signingConfig = signingConfigs.create("release").apply {
                storeFile = releaseStoreFile
                storePassword = resolvedReleaseStorePassword
                keyAlias = resolvedReleaseKeyAlias
                keyPassword = resolvedReleaseKeyPassword
            }

            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("androidx.compose.ui:ui:1.7.1")
    implementation("androidx.compose.runtime:runtime:1.7.1")
    implementation("androidx.compose.material3:material3:1.3.0")
    implementation("androidx.compose.material:material-icons-extended:1.7.1")
    implementation("androidx.compose.ui:ui-tooling-preview:1.7.1")
    implementation("com.google.android.material:material:1.12.0")

    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")

    implementation("org.osmdroid:osmdroid-android:6.1.18")
    
    implementation("com.google.android.gms:play-services-oss-licenses:17.1.0")

    testImplementation("junit:junit:4.13.2")
}

