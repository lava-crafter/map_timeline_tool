import java.io.File
import java.io.ByteArrayOutputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.w3c.dom.Element
import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget


data class OssDependencyLicense(
    val group: String,
    val name: String,
    val version: String,
    val licenses: List<Pair<String, String?>>
)

data class OssManualNotice(
    val displayName: String,
    val licenseName: String,
    val licenseUrl: String?,
    val notice: String?
)

fun parsePomLicenses(pomFile: File): List<Pair<String, String?>> {
    return runCatching {
        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val document = pomFile.inputStream().use { input -> builder.parse(input) }
        val licenseNodes = document.getElementsByTagName("license")
        buildList {
            for (index in 0 until licenseNodes.length) {
                val node = licenseNodes.item(index) as? Element ?: continue
                val name = node.getElementsByTagName("name").item(0)?.textContent?.trim().orEmpty()
                val url = node.getElementsByTagName("url").item(0)?.textContent?.trim().orEmpty()
                if (name.isNotBlank() || url.isNotBlank()) {
                    add(name.ifBlank { "Unknown License" } to url.ifBlank { null })
                }
            }
        }
    }.getOrDefault(emptyList())
}

fun locatePomInGradleCache(gradleUserHome: File, group: String, module: String, version: String): File? {
    val moduleDir = File(gradleUserHome, "caches/modules-2/files-2.1/$group/$module/$version")
    if (!moduleDir.exists()) return null
    return moduleDir.walkTopDown().firstOrNull { file ->
        file.isFile && file.extension == "pom"
    }
}

fun parseManualOssNotices(file: File): List<OssManualNotice> {
    if (!file.exists()) return emptyList()

    return file.readLines(Charsets.UTF_8).mapIndexedNotNull { index, rawLine ->
        val line = rawLine.trim()
        if (line.isBlank() || line.startsWith("#")) {
            return@mapIndexedNotNull null
        }

        val parts = line.split('|', limit = 4).map { it.trim() }
        require(parts.size == 4) {
            "Invalid manual OSS notice format in ${file.path}:${index + 1}. Expected: displayName|licenseName|licenseUrl|notice"
        }

        OssManualNotice(
            displayName = parts[0],
            licenseName = parts[1],
            licenseUrl = parts[2].ifBlank { null },
            notice = parts[3].ifBlank { null }
        )
    }
}

val generateOssMenuResources by tasks.registering {
    val outputResDir = layout.buildDirectory.dir("generated/oss_menu_resources/res")
    

    doLast {
        val runtimeConfig = configurations.getByName("releaseRuntimeClasspath")
        val modules = runtimeConfig.incoming.resolutionResult.allComponents
            .mapNotNull { component ->
                (component.id as? ModuleComponentIdentifier)?.let { id ->
                    Triple(id.group, id.module, id.version)
                }
            }
            .distinct()
            .sortedWith(compareBy({ it.first }, { it.second }, { it.third }))

        val gradleUserHome = gradle.gradleUserHomeDir
        val dependencies = modules.map { (group, name, version) ->
            val pomFile = locatePomInGradleCache(gradleUserHome, group, name, version)
            val licenses = pomFile?.let(::parsePomLicenses).orEmpty()
            OssDependencyLicense(group = group, name = name, version = version, licenses = licenses)
        }
        val manualNotices = parseManualOssNotices(projectDir.resolve("src/main/oss/manual_notices.csv"))

        val outputDir = projectDir.resolve("src/main/res/raw")
        outputDir.mkdirs()
        val licensesFile = File(outputDir, "third_party_licenses")
        val metadataFile = File(outputDir, "third_party_license_metadata")

        val licenseBlob = ByteArrayOutputStream()
        val metadataLines = mutableListOf<String>()
        var offset = 0

        dependencies.forEach { dependency ->
            val id = "${dependency.group}:${dependency.name}:${dependency.version}"
            val entryText = buildString {
                append(id).append('\n')
                if (dependency.licenses.isEmpty()) {
                    append("License information is unavailable in the artifact POM.")
                } else {
                    dependency.licenses.forEach { (licenseName, licenseUrl) ->
                        append(licenseName)
                        if (!licenseUrl.isNullOrBlank()) {
                            append(" - ").append(licenseUrl)
                        }
                        append('\n')
                    }
                }
                append('\n')
            }
            val bytes = entryText.toByteArray(Charsets.UTF_8)
            metadataLines.add("$offset:${bytes.size} $id")
            licenseBlob.write(bytes)
            offset += bytes.size
        }

        manualNotices.forEach { notice ->
            val entryText = buildString {
                append(notice.displayName).append('\n')
                append(notice.licenseName)
                if (!notice.licenseUrl.isNullOrBlank()) {
                    append(" - ").append(notice.licenseUrl)
                }
                append('\n')
                if (!notice.notice.isNullOrBlank()) {
                    append(notice.notice).append('\n')
                }
                append('\n')
            }
            val bytes = entryText.toByteArray(Charsets.UTF_8)
            metadataLines.add("$offset:${bytes.size} ${notice.displayName}")
            licenseBlob.write(bytes)
            offset += bytes.size
        }

        if (dependencies.isEmpty() && manualNotices.isEmpty()) {
            val fallbackText = "No third-party dependency metadata found.\n\n"
            val bytes = fallbackText.toByteArray(Charsets.UTF_8)
            metadataLines.add("0:${bytes.size} Open Source Licenses")
            licenseBlob.write(bytes)
        }

        licensesFile.writeBytes(licenseBlob.toByteArray())
        metadataFile.writeText(metadataLines.joinToString("\n"), Charsets.UTF_8)
    }
}

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
        versionCode = 13
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
        jniLibs {
            keepDebugSymbols += "**/libandroidx.graphics.path.so"
        }
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


tasks.whenTaskAdded {
    if (name.startsWith("generate") && name.endsWith("Resources")) {
        dependsOn("generateOssMenuResources")
    }
    if (name.startsWith("process") && name.endsWith("NavigationResources")) {
        dependsOn("generateOssMenuResources")
    }
}
