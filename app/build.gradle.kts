import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.example.valuefinder"
    compileSdk = 36

    val googleCseApiKey = providers.gradleProperty("VALUEFINDER_GOOGLE_CSE_API_KEY").orNull
        ?: System.getenv("VALUEFINDER_GOOGLE_CSE_API_KEY")
    val googleCseCx = providers.gradleProperty("VALUEFINDER_GOOGLE_CSE_CX").orNull
        ?: System.getenv("VALUEFINDER_GOOGLE_CSE_CX")
    val versionBase = providers.gradleProperty("VALUEFINDER_VERSION_BASE").orNull ?: "1.0"
    val buildCounterPropertyName = "VALUEFINDER_BUILD_COUNTER"
    val gradlePropertiesFile = rootProject.file("gradle.properties")
    val releaseTaskRequested = gradle.startParameter.taskNames.any { taskName ->
        taskName.contains("release", ignoreCase = true)
    }
    val explicitBuildCounter = gradle.startParameter.projectProperties[buildCounterPropertyName]?.toIntOrNull()

    fun readPersistedBuildCounter(): Int {
        if (!gradlePropertiesFile.exists()) return 1
        val props = Properties()
        gradlePropertiesFile.inputStream().use { props.load(it) }
        return props.getProperty(buildCounterPropertyName)?.toIntOrNull() ?: 1
    }

    fun writePersistedBuildCounter(counter: Int) {
        if (!gradlePropertiesFile.exists()) return
        val lines = gradlePropertiesFile.readLines().toMutableList()
        val replacement = "$buildCounterPropertyName=$counter"
        val idx = lines.indexOfFirst { line -> line.trimStart().startsWith("$buildCounterPropertyName=") }
        if (idx >= 0) {
            lines[idx] = replacement
        } else {
            lines += replacement
        }
        gradlePropertiesFile.writeText(lines.joinToString(separator = "\n", postfix = "\n"))
    }

    val buildCounter = when {
        explicitBuildCounter != null -> explicitBuildCounter
        releaseTaskRequested -> {
            val nextBuildCounter = readPersistedBuildCounter() + 1
            writePersistedBuildCounter(nextBuildCounter)
            logger.lifecycle("Auto-incremented VALUEFINDER_BUILD_COUNTER to $nextBuildCounter for release build")
            nextBuildCounter
        }
        else -> readPersistedBuildCounter()
    }

    // Versioning rule:
    // - versionCode 53 -> 1.0.53
    // - versionCode 99 -> 1.0.99
    // - versionCode 100 -> 1.2.0
    val majorVersion = versionBase.substringBefore('.').toIntOrNull() ?: 1
    val minorVersion = (buildCounter / 100) * 2
    val patchVersion = buildCounter % 100
    val computedVersionName = "$majorVersion.$minorVersion.$patchVersion"

    defaultConfig {
        applicationId = "com.example.valuefinder.unified"
        minSdk = 26
        targetSdk = 36
        versionCode = buildCounter
        versionName = computedVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField(
            "String",
            "GOOGLE_CSE_API_KEY",
            "\"${googleCseApiKey.orEmpty().replace("\\", "\\\\").replace("\"", "\\\"")}\""
        )
        buildConfigField(
            "String",
            "GOOGLE_CSE_CX",
            "\"${googleCseCx.orEmpty().replace("\\", "\\\\").replace("\"", "\\\"")}\""
        )
        buildConfigField("int", "APK_BUILD_COUNTER", buildCounter.toString())
    }

    val defaultReleaseKeystore = rootProject.file("valuefinder-release.jks")
    val releaseStoreFilePath = providers.gradleProperty("VALUEFINDER_STORE_FILE").orNull
        ?: System.getenv("VALUEFINDER_STORE_FILE")
        ?: defaultReleaseKeystore.takeIf { it.exists() }?.absolutePath
    val releaseStorePassword = providers.gradleProperty("VALUEFINDER_STORE_PASSWORD").orNull
        ?: System.getenv("VALUEFINDER_STORE_PASSWORD")
    val releaseKeyAlias = providers.gradleProperty("VALUEFINDER_KEY_ALIAS").orNull
        ?: System.getenv("VALUEFINDER_KEY_ALIAS")
    val releaseKeyPassword = providers.gradleProperty("VALUEFINDER_KEY_PASSWORD").orNull
        ?: System.getenv("VALUEFINDER_KEY_PASSWORD")
    val hasReleaseSigning = !releaseStoreFilePath.isNullOrBlank() &&
        !releaseStorePassword.isNullOrBlank() &&
        !releaseKeyAlias.isNullOrBlank() &&
        !releaseKeyPassword.isNullOrBlank()

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseStoreFilePath!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        buildConfig = true
        compose = true
        resValues = true
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
    sourceSets {
        getByName("androidTest") {
            assets.srcDirs("$projectDir/schemas")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

if (!project.hasProperty("VALUEFINDER_STORE_PASSWORD") && System.getenv("VALUEFINDER_STORE_PASSWORD").isNullOrBlank()) {
    logger.lifecycle("Release signing credentials are not fully configured. Set VALUEFINDER_* properties for signed release builds.")
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.coil.compose)
    implementation(libs.gson)
    implementation(libs.jsoup)
    implementation(libs.mlkit.image.labeling)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.androidx.exifinterface)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
