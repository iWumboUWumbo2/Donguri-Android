plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

// Release signing is driven by the environment so CI can supply a keystore
// without one ever living in the repo. With none configured the release build
// still succeeds and simply produces an unsigned APK.
val releaseKeystorePath = providers.environmentVariable("ANDROID_KEYSTORE_FILE").orNull
val releaseKeystorePassword = providers.environmentVariable("ANDROID_KEYSTORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("ANDROID_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("ANDROID_KEY_PASSWORD").orNull
val releaseSigningValues = listOf(
    releaseKeystorePath,
    releaseKeystorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
)
val isReleaseSigningRequested = releaseSigningValues.any { !it.isNullOrBlank() }
val isReleaseSigningConfigured = releaseSigningValues.all { !it.isNullOrBlank() } &&
    releaseKeystorePath?.let { file(it).isFile } == true

// Half-configured signing is always a mistake — fail loudly rather than
// shipping an unsigned APK that looks like a signed one.
if (isReleaseSigningRequested && !isReleaseSigningConfigured) {
    throw GradleException(
        "Release signing needs ANDROID_KEYSTORE_FILE, ANDROID_KEYSTORE_PASSWORD, " +
            "ANDROID_KEY_ALIAS and ANDROID_KEY_PASSWORD, and the keystore file must exist.",
    )
}

val releaseVersionName = providers.gradleProperty("releaseVersionName").orNull
val releaseVersionCode = providers.gradleProperty("releaseVersionCode").orNull?.toIntOrNull()
if (providers.gradleProperty("releaseVersionCode").isPresent && releaseVersionCode == null) {
    throw GradleException("releaseVersionCode must be an integer.")
}

android {
    namespace = "world.wumbo.donguri"
    ndkVersion = "29.0.14206865"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "world.wumbo.donguri"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
        releaseVersionCode?.let { versionCode = it }
        releaseVersionName?.let { versionName = it }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        externalNativeBuild {
            cmake {
                targets += "hoshidicts_jni"
            }
        }
    }

    if (isReleaseSigningConfigured) {
        signingConfigs {
            create("release") {
                storeFile = file(releaseKeystorePath!!)
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            manifestPlaceholders["appLabel"] = "Donguri Debug"
            ndk {
                abiFilters += listOf("arm64-v8a", "x86_64")
            }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            manifestPlaceholders["appLabel"] = "どんぐり"
            ndk {
                abiFilters += listOf("arm64-v8a")
            }
            if (isReleaseSigningConfigured) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    androidResources {
        generateLocaleConfig = true
    }
    lint {
        // The ankidroid-api artifact ships AnkiDroid's own lint checks, which
        // encode conventions internal to that app: its collection clock
        // (DirectSystemCurrentTimeMillisUsage) and its CrowdIn translation
        // workflow (DuplicateCrowdInStrings). Neither applies here.
        disable += listOf("DirectSystemCurrentTimeMillisUsage", "DuplicateCrowdInStrings")
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.31.6"
        }
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.webkit)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.work)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.google.dagger.hilt.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ankidroid.api)

    ksp(libs.androidx.hilt.compiler)
    ksp(libs.google.dagger.hilt.android.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

// hoshidicts_jni.cpp constructs the classes in `de.manhhao.hoshi` by their JVM
// names and constructor signatures. A copy of those bindings that has drifted
// from the submodule's C++ still compiles — and then aborts the whole process
// at the first native call, with a JNI NoSuchMethodError. AGP's built-in Kotlin
// will not compile sources from outside the module, so the file is copied in
// and this check keeps the copy honest.
val checkHoshiDictsBindings by tasks.registering {
    // Captured as plain values: referring to script properties from inside
    // doLast is not serializable into the configuration cache.
    val bridgeBindings = rootProject.file(
        "third_party/hoshidicts-kotlin-bridge/app/src/main/java/de/manhhao/hoshi/HoshiDicts.kt",
    )
    val localBindings = file("src/main/java/de/manhhao/hoshi/HoshiDicts.kt")
    val bridgeRelative = bridgeBindings.relativeTo(rootDir).path
    val localRelative = localBindings.relativeTo(rootDir).path

    inputs.file(localBindings).withPropertyName("localBindings")
    outputs.upToDateWhen { bridgeBindings.isFile && bridgeBindings.readText() == localBindings.readText() }

    doLast {
        if (!bridgeBindings.isFile) {
            throw GradleException(
                "third_party/hoshidicts-kotlin-bridge is missing. " +
                    "Run: git submodule update --init --recursive",
            )
        }
        if (bridgeBindings.readText() != localBindings.readText()) {
            throw GradleException(
                "$localRelative has drifted from the hoshidicts bridge submodule. The JNI layer " +
                    "resolves these classes by signature, so a mismatch aborts at runtime. " +
                    "Copy the submodule's file over it:\n  cp $bridgeRelative $localRelative",
            )
        }
    }
}

tasks.named("preBuild") { dependsOn(checkHoshiDictsBindings) }
