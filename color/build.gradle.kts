plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    jvmToolchain(21)
}
android {
    compileSdk = 34
    defaultConfig {
        minSdk = 21
    }
    namespace = "com.magen.download.color"
    // No explicit compileOptions: the kotlin jvmToolchain(21) below drives both
    // javac and kotlinc to the same JVM target. The old explicit 1.8 block
    // desynced the two compilers ("Inconsistent JVM-target compatibility").
    buildTypes {
        release {
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"
            )
            isMinifyEnabled = true
        }
    }
    lint {
        checkReleaseBuilds = false
    }
}
dependencies {
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.runtime)
    api(libs.androidx.core.ktx)
    api(libs.androidx.compose.foundation)
    api(libs.androidx.compose.material3)
}