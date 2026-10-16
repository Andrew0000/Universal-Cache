plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.vanniktech.mavenPublish)
}

version = "1.2.0"
group = "io.github.andrew0000"

kotlin {
    jvm()
    android {
        namespace = "crocodile8.universal_cache"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        withHostTestBuilder {}.configure {}
        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }
    }
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.coroutines.test)
        }
    }
}

mavenPublishing {
    publishToMavenCentral()

    signAllPublications()

    coordinates(group.toString(), "universal-cache", version.toString())

    pom {
        name = "Universal Cache"
        description = "Kotlin Flow caching and request sharing"
        inceptionYear = "2023"
        url = "https://github.com/Andrew0000/Universal-Cache"
        licenses {
            license {
                name = "Apache 2.0"
                url = "http://www.apache.org/licenses/LICENSE-2.0.txt"
            }
        }
        developers {
            developer {
                id = "crocodile8"
                name = "Andrei Riik"
            }
        }
        scm {
            url = "https://github.com/Andrew0000/Universal-Cache"
        }
    }
}
