plugins {
    kotlin("multiplatform") version "1.8.21"
    id("com.android.application") version "7.4.1" apply false
    id("org.jetbrains.kotlin.android") version "1.9.0" apply false
    id("convention.publication")
}

version = "1.1.7"
group = "io.github.andrew0000"

kotlin {

    jvmToolchain(8)
    jvm {
        compilations.all {
            kotlinOptions.jvmTarget = "1.8"
        }
        withJava()
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
    }

    val hostOs = System.getProperty("os.name")
    val isMingwX64 = hostOs.startsWith("Windows")
    val nativeTarget = when {
        hostOs == "Mac OS X" -> macosX64("native")
        hostOs == "Linux" -> linuxX64("native")
        isMingwX64 -> mingwX64("native")
        else -> throw GradleException("Host OS is not supported in Kotlin/Native.")
    }

    iosArm64 {
        binaries {
            framework {
                baseName = "library"
            }
        }
    }
    iosX64 {
        binaries {
            framework {
                baseName = "library"
            }
        }
    }
    iosSimulatorArm64 {
        binaries {
            framework {
                baseName = "library"
            }
        }
    }

    sourceSets {

        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.2")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.2")
            }
        }

        val jvmMain by getting
        val jvmTest by getting

        val nativeMain by getting
        val nativeTest by getting

        val iosArm64Main by getting
        val iosArm64Test by getting

        val iosX64Main by getting
        val iosX64Test by getting

        val iosSimulatorArm64Main by getting
    }
}
