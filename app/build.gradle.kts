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

tasks.register("verifyNoSecretsAndNoUiThreadNetwork") {
    group = "verification"
    description = "Verifies no production secrets, Yahoo default endpoint, legacy metadata, or UI-thread network paths."

    val appSourceDir = layout.projectDirectory.dir("src").asFile
    val scriptsDir = rootProject.layout.projectDirectory.dir("scripts").asFile
    val guardedRoots = listOf(appSourceDir, scriptsDir).filter { it.exists() }
    val guardedConfigFiles = listOf(
        layout.projectDirectory.file("gradle.properties").asFile,
        rootProject.layout.projectDirectory.file("build.gradle.kts").asFile,
        rootProject.layout.projectDirectory.file("settings.gradle.kts").asFile,
        rootProject.layout.projectDirectory.file("gradle.properties").asFile
    ).filter { it.exists() }

    inputs.files(guardedRoots, guardedConfigFiles)

    doLast {
        fun requireGuard(condition: Boolean, message: String) {
            if (!condition) throw GradleException(message)
        }

        fun relativePath(file: File): String =
            file.relativeToOrSelf(projectDir).path.replace(File.separatorChar, '/')

        val excludedTestPathMarker = "src/test"
        fun isExcludedTestFile(file: File): Boolean =
            relativePath(file).startsWith("$excludedTestPathMarker/")

        val textExtensions = setOf("java", "xml", "properties", "prop", "list", "gradle", "kts", "sh", "json", "yml", "yaml")
        val guardedFiles = guardedRoots
            .asSequence()
            .flatMap { root -> root.walkTopDown() }
            .filter { file -> file.isFile }
            .filterNot { file -> isExcludedTestFile(file) }
            .filter { file -> file.extension.lowercase() in textExtensions }
            .plus(guardedConfigFiles.asSequence())
            .distinctBy { file -> file.absoluteFile.normalize() }
            .toList()

        val forbiddenYahooTokens = listOf(
            "query1.finance.",
            "query2.finance.",
            "finance.yahoo",
            "yahoo"
        )
        val yahooOffenders = guardedFiles.mapNotNull { file ->
            val content = file.readText()
            forbiddenYahooTokens.firstOrNull { token -> content.contains(token, ignoreCase = true) }?.let { token ->
                "${relativePath(file)} contains forbidden Yahoo token $token"
            }
        }
        requireGuard(yahooOffenders.isEmpty(), yahooOffenders.joinToString(separator = "\n"))

        val secretPatterns = listOf(
            Regex("""(?i)\b(api[_-]?key|apikey|token|secret|authorization)\b\s*[:=]\s*["'][A-Za-z0-9._~+/=-]{16,}["']"""),
            Regex("""(?i)\bBearer\s+[A-Za-z0-9._~+/=-]{20,}""")
        )
        val secretOffenders = guardedFiles.mapNotNull { file ->
            val content = file.readText()
            secretPatterns.firstOrNull { pattern -> pattern.containsMatchIn(content) }?.let {
                "${relativePath(file)} contains likely production secret literal"
            }
        }
        requireGuard(secretOffenders.isEmpty(), secretOffenders.joinToString(separator = "\n"))

        val legacyAsset = layout.projectDirectory.file("src/main/assets/xposed_init").asFile
        requireGuard(!legacyAsset.exists(), "Legacy assets/xposed_init must not exist")

        val forbiddenLegacyTokens = listOf(
            "xposed" + "minversion",
            "xposed" + "module",
            "xposed" + "description"
        )
        val legacyTokenOffenders = guardedFiles.mapNotNull { file ->
            val content = file.readText()
            forbiddenLegacyTokens.firstOrNull { token -> token in content }?.let { token ->
                "${relativePath(file)} contains legacy Xposed metadata token $token"
            }
        }
        requireGuard(legacyTokenOffenders.isEmpty(), legacyTokenOffenders.joinToString(separator = "\n"))

        val uiThreadBoundaryFiles = listOf(
            "src/main/java/dev/priceontop/xposed/PriceOnTopModule.java",
            "src/main/java/dev/priceontop/xposed/SystemUiPriceController.java",
            "src/main/java/dev/priceontop/xposed/ClockTextDecorator.java",
            "src/main/java/dev/priceontop/xposed/adapter/ClockTargetAdapter.java",
            "src/main/java/dev/priceontop/xposed/adapter/AospClockAdapter.java",
            "src/main/java/dev/priceontop/xposed/adapter/MiuiHyperOsClockAdapter.java"
        )
        val forbiddenUiNetworkTokens = listOf(
            "HttpURLConnection",
            "openConnection(",
            "HttpTransport",
            "FinnhubProvider",
            "CustomJsonProvider",
            ".fetch(",
            "new Thread(",
            "Executors."
        )
        val uiThreadNetworkOffenders = uiThreadBoundaryFiles.flatMap { path ->
            val file = layout.projectDirectory.file(path).asFile
            requireGuard(file.isFile, "Missing UI-thread network guard source file: $path")
            val content = file.readText()
            forbiddenUiNetworkTokens.mapNotNull { token ->
                if (token in content) "$path contains forbidden UI-thread network token $token" else null
            }
        }
        requireGuard(uiThreadNetworkOffenders.isEmpty(), uiThreadNetworkOffenders.joinToString(separator = "\n"))
    }
}

tasks.register("verifyNoPrototypeRuntimeMarkers") {
    group = "verification"
    description = "Verifies status bar prototype markers are absent from runtime and smoke scope."

    val runtimeRoot = layout.projectDirectory.dir("src/main/java/dev/priceontop/xposed").asFile
    val smokeScript = rootProject.layout.projectDirectory.file("scripts/smoke-rooted.sh").asFile

    inputs.files(runtimeRoot)
    inputs.file(smokeScript)

    doLast {
        fun relativePath(file: File): String =
            file.relativeToOrSelf(projectDir).path.replace(File.separatorChar, '/')

        val forbiddenPrototypeTokens = listOf(
            "StatusBarPriceAreaAdapter",
            "systemui-price-area-hook-installed",
            "controller rendered price area",
            "NotificationIconContainer",
            "StatusIconContainer",
            "PhoneStatusBarView",
            ".addView("
        )

        val runtimeOffenders = runtimeRoot
            .walkTopDown()
            .filter { it.isFile }
            .filter { it.extension.lowercase() == "java" }
            .flatMap { file ->
                val content = file.readText()
                forbiddenPrototypeTokens.mapNotNull { token ->
                    if (token in content) listOf(
                        "${relativePath(file)} contains forbidden token '$token'"
                    ) else emptyList()
                }
                    .flatten()
            }
            .toList()

        val smokeOffenders = if (smokeScript.isFile) {
            val content = smokeScript.readText()
            forbiddenPrototypeTokens.mapNotNull { token ->
                if (token in content) "${relativePath(smokeScript)} contains forbidden token '$token'" else null
            }
        } else {
            listOf("Missing smoke script: ${relativePath(smokeScript)}")
        }

        val offenders = runtimeOffenders + smokeOffenders
        if (offenders.isNotEmpty()) {
            throw GradleException(offenders.joinToString(separator = "\n"))
        }

        println("NO_PROTOTYPE_MARKERS")
    }
}

tasks.named("check") {
    dependsOn("verifyXposedMetadata")
    dependsOn("verifyNoSecretsAndNoUiThreadNetwork")
    dependsOn("verifyNoPrototypeRuntimeMarkers")
}
