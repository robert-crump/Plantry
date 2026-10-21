import java.io.ByteArrayOutputStream
import javax.inject.Inject
import org.gradle.process.ExecOperations

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.example.plantry"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.plantry"
        minSdk = 37
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    // Form helpers format with the default locale; pin it so JVM tests don't depend on the machine.
    testOptions.unitTests.all {
        it.systemProperty("user.language", "de")
        it.systemProperty("user.country", "DE")
    }
    // Exported Room schemas, read by MigrationTestHelper.
    sourceSets.getByName("androidTest").assets.directories.add("$projectDir/schemas")
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.graphics.shapes)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.anthropic.java)
    implementation(libs.play.services.code.scanner)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

// README screenshots from the emulator fixture (androidTest ReadmeScreenshots), on a running
// emulator: ./gradlew readmeScreenshots. Not part of CI.
abstract class ReadmeScreenshotsTask : DefaultTask() {
    @get:Inject abstract val execOperations: ExecOperations

    @get:Internal abstract val adbExecutable: Property<File>
    @get:Internal abstract val appApk: RegularFileProperty
    @get:Internal abstract val testApk: RegularFileProperty
    @get:Internal abstract val applicationId: Property<String>
    @get:Internal abstract val testClass: Property<String>
    @get:Internal abstract val pullDir: DirectoryProperty
    @get:Internal abstract val screenshotsDir: DirectoryProperty

    @TaskAction
    fun capture() {
        val serial = emulatorSerial()
        val app = applicationId.get()
        logger.lifecycle("Taking README screenshots on $serial")
        adb(serial, "install", "-r", "-t", appApk.get().asFile.absolutePath)
        adb(serial, "install", "-r", "-t", testApk.get().asFile.absolutePath)
        // Emulator data is disposable: the debug build reseeds the fixture on the next launch.
        adb(serial, "shell", "pm", "clear", app)

        // Demo mode draws broken icons on the Android 17 image; leave any leftover of it.
        adb(serial, "shell", "am", "broadcast", "-a", "com.android.systemui.demo", "-e", "command", "exit")
        val output: String
        try {
            // Pixel-class 1080x2400 @ 420 dpi, whatever the AVD's own profile.
            adb(serial, "shell", "wm", "size", "1080x2400")
            adb(serial, "shell", "wm", "density", "420")
            adb(serial, "shell", "cmd", "statusbar", "send-disable-flag", "notification-icons")
            output = adb(
                serial, "shell", "am", "instrument", "-w",
                "-e", "readmeScreenshots", "true", "-e", "class", testClass.get(),
                "$app.test/androidx.test.runner.AndroidJUnitRunner"
            )
        } finally {
            adb(serial, "shell", "cmd", "statusbar", "send-disable-flag", "none")
            adb(serial, "shell", "wm", "size", "reset")
            adb(serial, "shell", "wm", "density", "reset")
        }
        logger.lifecycle(output.trim())
        // am instrument exits 0 even when a test fails.
        if (output.contains("FAILURES!!!") || !output.contains("OK (")) {
            throw GradleException("The README screenshot test failed; see the output above.")
        }

        val pulled = pullDir.get().asFile
        pulled.deleteRecursively()
        pulled.mkdirs()
        adb(serial, "pull", "/sdcard/Android/data/$app/files/readme-screenshots", pulled.absolutePath)
        val pngs = File(pulled, "readme-screenshots").listFiles { f -> f.name.endsWith(".png") }.orEmpty()
        if (pngs.isEmpty()) throw GradleException("No screenshots were pulled from $serial.")

        val target = screenshotsDir.get().asFile
        target.mkdirs()
        target.listFiles { f -> f.name.endsWith(".png") }?.forEach { it.delete() }
        pngs.forEach { it.copyTo(File(target, it.name), overwrite = true) }
        logger.lifecycle("Saved ${pngs.map { it.name }.sorted().joinToString()} to $target")
    }

    /** The one running emulator (or ANDROID_SERIAL, which must be an emulator); real devices are never used. */
    private fun emulatorSerial(): String {
        System.getenv("ANDROID_SERIAL")?.takeIf { it.isNotBlank() }?.let { requested ->
            if (adb(requested, "shell", "getprop", "ro.kernel.qemu").trim() != "1") {
                throw GradleException("ANDROID_SERIAL=$requested is not an emulator; readmeScreenshots never runs on a physical device.")
            }
            return requested
        }
        val emulators = adb(null, "devices").lines()
            .map { it.split("\t") }
            .filter { it.size == 2 && it[1].trim() == "device" && it[0].startsWith("emulator-") }
            .map { it[0] }
        if (emulators.isEmpty()) throw GradleException("readmeScreenshots needs a running emulator; none found.")
        if (emulators.size > 1) {
            throw GradleException("Several emulators are running ($emulators); pick one with ANDROID_SERIAL.")
        }
        return emulators.single()
    }

    private fun adb(serial: String?, vararg args: String): String {
        val commandLine = buildList {
            add(adbExecutable.get().absolutePath)
            if (serial != null) addAll(listOf("-s", serial))
            addAll(args)
        }
        val out = ByteArrayOutputStream()
        execOperations.exec {
            commandLine(commandLine)
            standardOutput = out
        }
        return out.toString(Charsets.UTF_8)
    }
}

tasks.register<ReadmeScreenshotsTask>("readmeScreenshots") {
    group = "documentation"
    description = "Takes the README screenshots from the emulator fixture on a running emulator."
    dependsOn("assembleDebug", "assembleDebugAndroidTest")
    adbExecutable.set(androidComponents.sdkComponents.adb.map { it.asFile })
    appApk.set(layout.buildDirectory.file("outputs/apk/debug/app-debug.apk"))
    testApk.set(layout.buildDirectory.file("outputs/apk/androidTest/debug/app-debug-androidTest.apk"))
    applicationId.set(android.defaultConfig.applicationId)
    testClass.set("com.example.plantry.readme.ReadmeScreenshots")
    pullDir.set(layout.buildDirectory.dir("readme-screenshots"))
    screenshotsDir.set(rootProject.layout.projectDirectory.dir("docs/screenshots"))
    outputs.upToDateWhen { false }
}
