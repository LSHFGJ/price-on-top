import java.io.File
import java.util.Properties
import org.gradle.api.GradleException

plugins {
    id("com.android.application")
}

android {
    namespace = "dev.priceontop"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.priceontop"
        minSdk = 31
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            merges += "META-INF/xposed/*"
        }
    }
}

dependencies {
    compileOnly("io.github.libxposed:api:101.0.1")
    testImplementation("junit:junit:4.13.2")
}

fun normalizedMetadataLines(file: File): List<String> =
    file.readLines()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }

tasks.register("verifyXposedMetadata") {
    group = "verification"
    description = "Verifies modern LSPosed API 101 metadata and rejects legacy Xposed metadata."

    val metadataDir = layout.projectDirectory.dir("src/main/resources/META-INF/xposed")
    val moduleProp = metadataDir.file("module.prop").asFile
    val scopeList = metadataDir.file("scope.list").asFile
    val javaInitList = metadataDir.file("java_init.list").asFile

    inputs.files(moduleProp, scopeList, javaInitList)

    doLast {
        fun requireMetadata(condition: Boolean, message: String) {
            if (!condition) throw GradleException(message)
        }

        requireMetadata(moduleProp.isFile, "Missing modern Xposed metadata: ${moduleProp.relativeTo(projectDir)}")
        requireMetadata(scopeList.isFile, "Missing modern Xposed metadata: ${scopeList.relativeTo(projectDir)}")
        requireMetadata(javaInitList.isFile, "Missing modern Xposed metadata: ${javaInitList.relativeTo(projectDir)}")

        val moduleProperties = Properties().apply {
            moduleProp.inputStream().use { load(it) }
        }
        requireMetadata(
            moduleProperties.getProperty("minApiVersion") == "101",
            "module.prop must set minApiVersion=101"
        )
        requireMetadata(
            moduleProperties.getProperty("targetApiVersion") == "101",
            "module.prop must set targetApiVersion=101"
        )

        requireMetadata(
            normalizedMetadataLines(scopeList) == listOf("com.android.systemui"),
            "scope.list must contain exactly com.android.systemui"
        )
        requireMetadata(
            normalizedMetadataLines(javaInitList) == listOf("dev.priceontop.xposed.PriceOnTopModule"),
            "java_init.list must contain exactly dev.priceontop.xposed.PriceOnTopModule"
        )

        val legacyAsset = layout.projectDirectory.file("src/main/assets/xposed_init").asFile
        requireMetadata(!legacyAsset.exists(), "Legacy assets/xposed_init must not exist")

        val forbiddenLegacyTokens = listOf(
            "xposed" + "minversion",
            "xposed" + "module",
            "xposed" + "description"
        )
        val legacyTokenOffenders = layout.projectDirectory.dir("src/main").asFile
            .walkTopDown()
            .filter { it.isFile }
            .mapNotNull { file ->
                val content = file.readText()
                forbiddenLegacyTokens.firstOrNull { token -> token in content }?.let { token ->
                    "${file.relativeTo(projectDir)} contains $token"
                }
            }
            .toList()
        requireMetadata(legacyTokenOffenders.isEmpty(), legacyTokenOffenders.joinToString(separator = "\n"))
    }
}

tasks.named("check") {
    dependsOn("verifyXposedMetadata")
}
