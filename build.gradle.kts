plugins {
    kotlin("jvm") version "1.9.22"
    application
    id("org.openjfx.javafxplugin") version "0.1.0"
}

group = "com.cardgame"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

javafx {
    version = "21.0.2"
    modules("javafx.base", "javafx.graphics", "javafx.media", "javafx.swing")
}

dependencies {
    implementation("org.cosplayengine:cosplay:0.9.5")
    implementation("org.scala-lang:scala3-library_3:3.3.1")
    implementation(kotlin("stdlib"))
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass.set("com.cardgame.MainKt")
    applicationDefaultJvmArgs = listOf(
        "-Xmx512m",
        "--add-opens", "javafx.graphics/com.sun.javafx.application=ALL-UNNAMED",
        "--add-opens", "javafx.graphics/com.sun.javafx.tk=ALL-UNNAMED",
        "--add-opens", "javafx.base/com.sun.javafx=ALL-UNNAMED",
        "--add-exports", "javafx.graphics/com.sun.javafx.application=ALL-UNNAMED",
        "--add-exports", "javafx.graphics/com.sun.javafx.tk=ALL-UNNAMED",
        "--add-exports", "javafx.base/com.sun.javafx=ALL-UNNAMED",
    )
}

tasks.withType<JavaExec> {
    standardInput = System.`in`
    // Slightly smaller monospace cells so the 4×4 board fits 1080p; override per-machine if needed.
    environment(
        "COSPLAY_EMUTERM_FONT_SIZE",
        System.getenv("COSPLAY_EMUTERM_FONT_SIZE") ?: "12"
    )
}

kotlin {
    jvmToolchain(17)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("failed", "skipped")
    }
}

// Native installers via jpackage using installDist output (no merged-module step).
// Note: jpackage can only build installers for the current OS (no cross-compiling).
tasks.register<Exec>("packageNative") {
    group = "distribution"
    description = "Build native installer via jpackage. Override with -PinstallerType=dmg|pkg|msi|deb|rpm."
    dependsOn("installDist")
    dependsOn("slimCosplayForPackaging")

    doFirst {
        val os = org.gradle.internal.os.OperatingSystem.current()
        val installerType = providers.gradleProperty("installerType").orNull ?: when {
            os.isMacOsX -> "dmg"
            os.isWindows -> "msi"
            else -> "deb"
        }

        val appName = "Code 437"
        val macPackageName = "Code437"
        val appVersion = "1.0.0"
        val installRoot = layout.buildDirectory.dir("install").get().asFile
        val installedAppDir = installRoot.listFiles()
            ?.firstOrNull { it.isDirectory }
            ?: layout.buildDirectory.dir("install/${project.name}").get().asFile
        val inputDir = File(installedAppDir, "lib").absolutePath
        val outputDir = layout.buildDirectory.dir("jpackage").get().asFile.absolutePath
        val mainJar = tasks.named<Jar>("jar").get().archiveFileName.get()
        val jvmArgs = listOf(
            "-Xmx512m",
            "-DCOSPLAY_EMUTERM_FONT_SIZE=12",
            "-DCOSPLAY_FULLSCREEN_HOOK=1",
            "--add-opens=javafx.graphics/com.sun.javafx.application=ALL-UNNAMED",
            "--add-opens=javafx.graphics/com.sun.javafx.tk=ALL-UNNAMED",
            "--add-opens=javafx.base/com.sun.javafx=ALL-UNNAMED",
            "--add-exports=javafx.graphics/com.sun.javafx.application=ALL-UNNAMED",
            "--add-exports=javafx.graphics/com.sun.javafx.tk=ALL-UNNAMED",
            "--add-exports=javafx.base/com.sun.javafx=ALL-UNNAMED",
        )

        val cmd = mutableListOf(
            "jpackage",
            "--type", installerType,
            "--name", appName,
            "--app-version", appVersion,
            "--vendor", "Code 437",
            "--input", inputDir,
            "--main-jar", mainJar,
            "--main-class", "com.cardgame.MainKt",
            "--dest", outputDir,
        )
        jvmArgs.forEach { arg ->
            cmd += listOf("--java-options", arg)
        }

        if (os.isMacOsX) {
            cmd += listOf("--mac-package-name", macPackageName)
        } else if (os.isWindows) {
            cmd += listOf("--win-dir-chooser", "--win-menu", "--win-shortcut")
        }

        commandLine(cmd)
    }
}

tasks.register("slimCosplayForPackaging") {
    group = "distribution"
    description = "Remove CosPlay demo audio assets from installDist copy before jpackage."
    dependsOn("installDist")

    doLast {
        val installRoot = layout.buildDirectory.dir("install").get().asFile
        val installedAppDir = installRoot.listFiles()
            ?.firstOrNull { it.isDirectory }
            ?: throw GradleException("installDist output directory not found under ${installRoot.absolutePath}")
        val libDir = File(installedAppDir, "lib")
        val cosplayJar = libDir.listFiles()
            ?.firstOrNull { it.isFile && it.name.startsWith("cosplay-") && it.name.endsWith(".jar") }
            ?: throw GradleException("Could not find cosplay-*.jar in ${libDir.absolutePath}")

        val workDir = layout.buildDirectory.dir("tmp/slim-cosplay").get().asFile
        if (workDir.exists()) workDir.deleteRecursively()
        workDir.mkdirs()

        copy {
            from(zipTree(cosplayJar))
            into(workDir)
            exclude("sounds/examples/**")
            exclude("sounds/games/**")
        }

        val slimJar = File(cosplayJar.parentFile, "${cosplayJar.nameWithoutExtension}-slim.jar")
        ant.invokeMethod(
            "zip",
            mapOf(
                "destfile" to slimJar.absolutePath,
                "basedir" to workDir.absolutePath
            )
        )

        if (cosplayJar.exists()) cosplayJar.delete()
        slimJar.renameTo(cosplayJar)
        workDir.deleteRecursively()
    }
}
