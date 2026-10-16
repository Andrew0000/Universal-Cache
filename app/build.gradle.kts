import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}

android {
    namespace = "crocodile8.universal_cache_app"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "crocodile8.universal_cache_app"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    flavorDimensions += "libDep"
    productFlavors {
        create("libDepLocal") {
            dimension = "libDep"
        }
        create("libDepMaven") {
            dimension = "libDep"
        }
    }
}

val useLocalLib = (project.findProperty("useLocalLib")?.toString()?.toBoolean() ?: false)
    .also { println("useLocalLib: $it") }

dependencies {

    if (useLocalLib) {
        implementation(project(":lib"))
    } else {
        implementation("io.github.andrew0000:universal-cache:1.+")
    }

    //val libDepLocalImplementation by configurations
    //val libDepMavenImplementation by configurations
    //libDepLocalImplementation(project(mapOf("path" to ":lib")))
    //libDepMavenImplementation("io.github.andrew0000:universal-cache:1.+")

    implementation(libs.core.ktx)
    implementation(libs.appcompat)
    implementation(libs.material)

    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)

    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
