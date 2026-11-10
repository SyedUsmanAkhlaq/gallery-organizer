import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("kotlin-android")
    id("com.google.devtools.ksp")
    // The Flutter Gradle Plugin must be applied after the Android and Kotlin plugins.
    id("dev.flutter.flutter-gradle-plugin")
}

android {
    namespace = "dev.personal.galleryorganizer"
    compileSdk = 36
    ndkVersion = flutter.ndkVersion

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    defaultConfig {
        applicationId = "dev.personal.galleryorganizer"
        minSdk = 26
        targetSdk = 36
        versionCode = flutter.versionCode
        versionName = flutter.versionName
        // Phones are arm64; keeps ONNX Runtime / ML Kit native libs small.
        ndk { abiFilters += listOf("arm64-v8a") }
    }

    packaging {
        jniLibs {
            // abiFilters alone did not strip the AAR libs of ONNX Runtime /
            // ML Kit (build-5 shipped x86_64 + armeabi-v7a too: +80 MB).
            excludes += listOf("lib/x86/**", "lib/x86_64/**", "lib/armeabi-v7a/**")
            // Compress .so files in the APK (smaller download).
            useLegacyPackaging = true
        }
    }

    // Models are copied to app storage once and memory-mapped by ONNX Runtime.
    androidResources {
        noCompress += listOf("onnx")
    }

    signingConfigs {
        // Throwaway key committed to the repo (android/app/debug.keystore) so
        // every CI build has the same signature and installs over the
        // previous one, keeping the app's database. NOT a secret.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storeType = "pkcs12"
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.isReturnDefaultValues = true
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("debug")
            // No R8: keeps reflection-heavy native libs (Room, ONNX Runtime,
            // ML Kit) working without keep rules. Size is not a concern here.
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

flutter {
    source = "../.."
}

ksp {
    // Schema JSON is not exported; migrations are written by hand and covered
    // by unit tests when the schema version changes.
    arg("room.generateKotlin", "true")
}

val roomVersion = "2.8.5"

dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.work:work-runtime-ktx:2.11.2")
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    // On-device inference (CPU/XNNPACK/NNAPI) for MobileCLIP and MobileFaceNet.
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.31.1")
    // Face detection with the model bundled in the APK (works offline).
    implementation("com.google.mlkit:face-detection:16.1.7")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16.1")
    testImplementation("androidx.test:core:1.7.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}
