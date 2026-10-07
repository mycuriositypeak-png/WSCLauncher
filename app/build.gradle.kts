plugins { id("com.android.application") }
android {
    namespace = "dev.wsc.launcher"
    compileSdk = 35
    defaultConfig {
        applicationId = "dev.wsc.launcher"
        minSdk = 24
        targetSdk = 28 // Experimental cross-package hosting; not a Play Store release.
        versionCode = 9
        versionName = "1.0"
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
