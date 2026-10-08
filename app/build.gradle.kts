plugins { id("com.android.application") }

val releaseStoreFile = providers.environmentVariable("WSC_RELEASE_STORE_FILE").orNull
val releaseStorePassword = providers.environmentVariable("WSC_RELEASE_STORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("WSC_RELEASE_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("WSC_RELEASE_KEY_PASSWORD").orNull
val releaseSigningValues = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
)
val releaseSigningConfigured = releaseSigningValues.all { !it.isNullOrBlank() }
val releaseSigningPartiallyConfigured = releaseSigningValues.any { !it.isNullOrBlank() }
val requireReleaseSigning =
    providers.environmentVariable("WSC_REQUIRE_RELEASE_SIGNING").orNull == "true"

if (releaseSigningPartiallyConfigured && !releaseSigningConfigured) {
    throw GradleException(
        "Incomplete WSC release signing configuration. " +
            "Set WSC_RELEASE_STORE_FILE, WSC_RELEASE_STORE_PASSWORD, " +
            "WSC_RELEASE_KEY_ALIAS and WSC_RELEASE_KEY_PASSWORD."
    )
}
if (requireReleaseSigning && !releaseSigningConfigured) {
    throw GradleException("Release signing is required but no complete signing configuration was provided.")
}

android {
    namespace = "dev.wsc.launcher"
    compileSdk = 35
    defaultConfig {
        applicationId = "dev.wsc.launcher"
        minSdk = 24
        targetSdk = 28 // Experimental cross-package hosting; not a Play Store release.
        versionCode = 11
        versionName = "1.0.2"
    }

    val wscReleaseSigning = if (releaseSigningConfigured) {
        signingConfigs.create("wscRelease") {
            storeFile = file(releaseStoreFile!!)
            storePassword = releaseStorePassword!!
            keyAlias = releaseKeyAlias!!
            keyPassword = releaseKeyPassword!!
            storeType = "JKS"
        }
    } else {
        null
    }

    buildTypes {
        getByName("release") {
            wscReleaseSigning?.let { signingConfig = it }
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a")
            isUniversalApk = true
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    packaging { jniLibs.useLegacyPackaging = true }
    testOptions { unitTests.isIncludeAndroidResources = true }
    lint {
        // Intentionally old target for this sideload-only hosting experiment.
        // Keep every other lint check enabled; this is not a Play Store release.
        disable += "ExpiredTargetSdkVersion"
    }
}
dependencies {
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel:2.8.7")
    implementation("androidx.lifecycle:lifecycle-livedata:2.8.7")
    implementation(files("libs/canyie-pine.aar"))
    implementation(files("libs/shadowhook-1.0.10.aar"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("org.json:json:20240303")
}

// Name the actual AGP outputs using their ABI filters; the universal APK is built by AGP.
android.applicationVariants.all {
    val launcherVersion = versionName
    outputs.all {
        val apk = this as com.android.build.gradle.api.ApkVariantOutput
        val architecture = when (apk.getFilter(com.android.build.VariantOutput.FilterType.ABI)) {
            null -> "universal"
            "arm64-v8a" -> "arm64"
            "armeabi-v7a" -> "arm32"
            else -> error("Unexpected APK ABI filter")
        }
        apk.outputFileName = "WSCLauncher-$launcherVersion-$architecture.apk"
    }
}
