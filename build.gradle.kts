plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.treg.llmpersonalization"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.treg.llmpersonalization"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Pixel 10 is arm64-v8a only. Excluding other ABIs cuts native
        // lib size significantly and speeds the build.
        ndk {
            abiFilters += "arm64-v8a"
        }

        externalNativeBuild {
            cmake {
                cppFlags += listOf(
                    "-std=c++17",
                    "-O3",
                    "-fexceptions",
                    "-frtti",
                )
                arguments += listOf(
                    "-DANDROID_STL=c++_shared",
                )
            }
        }
    }

    // NDK AGP 9.4 defaults to. Falls back to 27.x if the llama.cpp
    // build throws a toolchain error — llama.cpp's Android example
    // was tested against NDK r27.
    ndkVersion = "28.2.13676358"

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    // Do NOT compress GGUF or ONNX. Uncompressed assets get a real fd
    // via AssetManager.openFd(), letting us stream-copy to filesDir
    // and letting llama.cpp mmap the result.
    androidResources {
        noCompress += listOf("gguf", "onnx")
    }

    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    // ONNX Runtime Mobile — arm64 only ABI bundled
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.20.0")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}