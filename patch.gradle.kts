
import java.io.ByteArrayOutputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.w3c.dom.Element

data class OssDependencyLicense(
    val group: String,
    val name: String,
    val version: String,
    val licenses: List<Pair<String, String?>>
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

val generateOssMenuResources by tasks.registering {
    val outputResDir = layout.buildDirectory.dir("generated/oss_menu_resources/res")
    outputs.dir(outputResDir)

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

        val outputDir = File(outputResDir.get().asFile, "raw")
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
                append('\n') // adding trailing newline for safety
            }
            val bytes = entryText.toByteArray(Charsets.UTF_8)
            metadataLines += "$offset:${bytes.size} $id"
            licenseBlob.write(bytes)
            offset += bytes.size
        }

        if (dependencies.isEmpty()) {
            val fallbackText = "No third-party dependency metadata found.\n\n"
            val bytes = fallbackText.toByteArray(Charsets.UTF_8)
            metadataLines += "0:${bytes.size} Open Source Licenses"
            licenseBlob.write(bytes)
        }

        licensesFile.writeBytes(licenseBlob.toByteArray())
        metadataFile.writeText(metadataLines.joinToString("\n"), Charsets.UTF_8)
    }
}

