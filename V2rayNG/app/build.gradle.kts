plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.jaredsburrows.license")
}

android {
    namespace = "com.v2ray.ang"
    compileSdk = 37
    ndkVersion = "30.0.16248370"

    defaultConfig {
        applicationId = "com.derafsh.vpn"
        minSdk = 24
        targetSdk = 37
        versionCode = 48
        versionName = "1.2.44"

        // Ad placement provider: "house" = first-party Derafsh promo surfaces (current);
        // "admob" = Google AdMob with the IDs below (enable when production IDs are ready).
        buildConfigField("String", "ADS_PROVIDER", "\"house\"")

        // Iranian ad leg of the waterfall (Tapsell, rial-settled). Blank key = leg disabled
        // and every placement falls through to AdMob (dollar) then the house ad. Fill after
        // the Tapsell panel registration; the SDK seam lives in handler/TapsellAds.kt.
        buildConfigField("String", "TAPSELL_APP_KEY", "\"\"")
        buildConfigField("String", "TAPSELL_INTERSTITIAL_ID", "\"\"")
        buildConfigField("String", "TAPSELL_REWARDED_ID", "\"\"")
        buildConfigField("String", "TAPSELL_BANNER_ID", "\"\"")

        // AdMob IDs — single source of truth (VAULT.md §4). Currently Google TEST IDs;
        // swapping in the real Derafsh production IDs is an edit ONLY here (one place).
        val admobAppId = "ca-app-pub-3940256099942544~3347511713"
        manifestPlaceholders["admobAppId"] = admobAppId
        buildConfigField("String", "ADMOB_APP_ID", "\"$admobAppId\"")
        buildConfigField("String", "ADMOB_APP_OPEN_UNIT", "\"ca-app-pub-3940256099942544/9257395921\"")
        buildConfigField("String", "ADMOB_INTERSTITIAL_UNIT", "\"ca-app-pub-3940256099942544/1033173712\"")
        buildConfigField("String", "ADMOB_REWARDED_UNIT", "\"ca-app-pub-3940256099942544/5224354917\"")
        buildConfigField("String", "ADMOB_BANNER_UNIT", "\"ca-app-pub-3940256099942544/6300978111\"")

        val abiFilterList = (properties["ABI_FILTERS"] as? String)?.split(';')
        splits {
            abi {
                isEnable = true
                reset()
                if (!abiFilterList.isNullOrEmpty()) {
                    include(*abiFilterList.toTypedArray())
                } else {
                    include(
                        "arm64-v8a",
                        "armeabi-v7a",
                        "x86_64",
                        "x86"
                    )
                }
                isUniversalApk = abiFilterList.isNullOrEmpty()
            }
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    flavorDimensions.add("distribution")
    productFlavors {
        create("fdroid") {
            dimension = "distribution"
            applicationIdSuffix = ".fdroid"
            buildConfigField("String", "DISTRIBUTION", "\"F-Droid\"")
        }
        create("playstore") {
            dimension = "distribution"
            buildConfigField("String", "DISTRIBUTION", "\"Play Store\"")
        }
    }

    sourceSets {
        getByName("main") {
            jniLibs.srcDirs("libs")
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    applicationVariants.all {
        val variant = this
        val isFdroid = variant.productFlavors.any { it.name == "fdroid" }
        if (isFdroid) {
            val versionCodes =
                mapOf(
                    "armeabi-v7a" to 2, "arm64-v8a" to 1, "x86" to 4, "x86_64" to 3, "universal" to 0
                )

            variant.outputs
                .map { it as com.android.build.gradle.internal.api.ApkVariantOutputImpl }
                .forEach { output ->
                    val abi = output.getFilter("ABI") ?: "universal"
                    output.outputFileName = "Derafsh_${variant.versionName}-fdroid_${abi}.apk"
                    if (versionCodes.containsKey(abi)) {
                        output.versionCodeOverride =
                            (100 * variant.versionCode + versionCodes[abi]!!).plus(5000000)
                    } else {
                        return@forEach
                    }
                }
        } else {
            val versionCodes =
                mapOf("armeabi-v7a" to 4, "arm64-v8a" to 4, "x86" to 4, "x86_64" to 4, "universal" to 4)

            variant.outputs
                .map { it as com.android.build.gradle.internal.api.ApkVariantOutputImpl }
                .forEach { output ->
                    val abi = if (output.getFilter("ABI") != null)
                        output.getFilter("ABI")
                    else
                        "universal"

                    output.outputFileName = "Derafsh_${variant.versionName}_${abi}.apk"
                    if (versionCodes.containsKey(abi)) {
                        output.versionCodeOverride =
                            (1000000 * versionCodes[abi]!!).plus(variant.versionCode)
                    } else {
                        return@forEach
                    }
                }
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    androidResources {
        generateLocaleConfig = true
        localeFilters += listOf(
            "en",
            "fa"
        )
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    testOptions {
        unitTests.all {
            it.useJUnitPlatform()
            // NOTE: on JUnit Platform 6.x a critical discovery issue (e.g. an invalid
            // @Test method that returns a value) already fails the build by default
            // (severity ERROR). An earlier version explicitly set
            // `junit.platform.discovery.issue.severity.critical=WARNING`, which
            // downgraded those issues to warnings — the opposite of the intended
            // strictness — so the override is intentionally gone. Keep the default.
        }
    }

}

dependencies {
    // Core Libraries
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar", "*.jar"))))

    // AndroidX Core Libraries
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)

    // Compose Libraries
    implementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(platform(libs.androidx.compose.bom))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.coil.compose)

    debugImplementation(libs.androidx.compose.ui.tooling)

    // Data and Storage Libraries
    implementation(libs.mmkv.static)
    implementation(libs.gson)
    implementation(libs.okhttp)

    // Branded splash screen (Android 12+ API with pre-31 compat)
    implementation(libs.androidx.core.splashscreen)

    // Reactive and Utility Libraries
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)

    // QR Code: CameraX + ZXing
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.compose)
    implementation(libs.core) // zxing core

    // AndroidX Lifecycle and Architecture Components
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.lifecycle.runtime.ktx)

    // Background Task Libraries
    implementation(libs.work.runtime.ktx)
    implementation(libs.work.multiprocess)

    // Reorderable list
    implementation(libs.reorderable)

    // Ads (AdMob / Google Mobile Ads SDK): intentionally added to BOTH flavors — Derafsh does
    // not ship to F-Droid, so the fdroid flavor is not kept ad-free by design.
    implementation(libs.play.services.ads)
    // CameraX + Ads SDK classpath fix: provide a REAL ListenableFuture and collapse every
    // listenablefuture module reference onto full guava so exactly one jar carries the class.
    implementation(libs.guava.android)
    configurations.configureEach {
        resolutionStrategy {
            force("com.google.guava:listenablefuture:9999.0-empty-to-avoid-conflict-with-guava")
            force("com.google.guava:guava:33.4.8-android")
        }
    }

    // Testing Libraries
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.kotlin)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
}
