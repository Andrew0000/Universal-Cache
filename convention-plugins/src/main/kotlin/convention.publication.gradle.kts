
// https://kotlinlang.org/docs/multiplatform-library.html#publish-your-library-to-maven-central

import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.`maven-publish`
import org.gradle.kotlin.dsl.signing
import java.util.*

plugins {
    `maven-publish`
    signing
}

// Stub secrets to let the project sync and build without the publication values set up
ext["signing.keyId"] = null
ext["signing.password"] = null
ext["signing.secretKeyRingFile"] = null
ext["ossrhUsername"] = null
ext["ossrhPassword"] = null

// Grabbing secrets from local.properties file or from environment variables, which could be used on CI
val secretPropsFile = project.rootProject.file("pub.properties")
if (secretPropsFile.exists()) {
    secretPropsFile.reader().use {
        Properties().apply {
            load(it)
        }
    }.onEach { (name, value) ->
        ext[name.toString()] = value
    }
} else {
    ext["signing.keyId"] = System.getenv("SIGNING_KEY_ID")
    ext["signing.password"] = System.getenv("SIGNING_PASSWORD")
    ext["signing.secretKeyRingFile"] = System.getenv("SIGNING_SECRET_KEY_RING_FILE")
    ext["ossrhUsername"] = System.getenv("OSSRH_USERNAME")
    ext["ossrhPassword"] = System.getenv("OSSRH_PASSWORD")
}

val javadocJar by tasks.registering(Jar::class) {
    archiveClassifier.set("javadoc")
}

fun getExtraString(name: String) = ext[name]?.toString()

publishing {
    // Configure maven central repository
    repositories {
        maven {
            name = "sonatype"
            setUrl("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
            credentials {
                username = getExtraString("ossrhUsername")
                password = getExtraString("ossrhPassword")
            }
        }
    }

    // Configure all publications
    publications.withType<MavenPublication> {
        // Stub javadoc.jar artifact
        artifact(javadocJar.get())

        // Provide artifacts information requited by Maven Central
        pom {
            groupId = "io.github.andrew0000"
            artifactId = "universal-cache"
            name.set("Universal Cache")
            description.set("Kotlin Flow caching and request sharing")
            url.set("https://github.com/Andrew0000/Universal-Cache")

            licenses {
                license {
                    name.set("Apache 2.0")
                    url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                }
            }
            developers {
                developer {
                    id.set("crocodile8")
                    name.set("Andrei Riik")
                }
            }
            scm {
                url.set("https://github.com/Andrew0000/Universal-Cache")
            }
        }
    }

    afterEvaluate {
        /*
         * Explicitly configure that signing comes before publishing.
         * Otherwise the task execution of "publishAllPublicationsToSonatypeRepository" will fail.
         */
        val publishIosArm64PublicationToSonatypeRepository by tasks.getting
        val signIosX64Publication by tasks.getting
        publishIosArm64PublicationToSonatypeRepository.dependsOn(signIosX64Publication)

        val publishIosX64PublicationToSonatypeRepository by tasks.getting
        val signIosArm64Publication by tasks.getting
        publishIosX64PublicationToSonatypeRepository.dependsOn(signIosArm64Publication)

        val signNativePublication by tasks.getting
        publishIosArm64PublicationToSonatypeRepository.dependsOn(signNativePublication)
        publishIosX64PublicationToSonatypeRepository.dependsOn(signNativePublication)

        val publishNativePublicationToSonatypeRepository by tasks.getting
        publishNativePublicationToSonatypeRepository.dependsOn(signIosX64Publication)
        publishNativePublicationToSonatypeRepository.dependsOn(signIosArm64Publication)

        val signJvmPublication by tasks.getting
        publishNativePublicationToSonatypeRepository.dependsOn(signJvmPublication)
        publishIosX64PublicationToSonatypeRepository.dependsOn(signJvmPublication)
        publishIosArm64PublicationToSonatypeRepository.dependsOn(signJvmPublication)

        val signKotlinMultiplatformPublication by tasks.getting
        publishIosArm64PublicationToSonatypeRepository.dependsOn(signKotlinMultiplatformPublication)
        publishIosX64PublicationToSonatypeRepository.dependsOn(signKotlinMultiplatformPublication)
        publishNativePublicationToSonatypeRepository.dependsOn(signKotlinMultiplatformPublication)

        val publishJvmPublicationToSonatypeRepository by tasks.getting
        publishJvmPublicationToSonatypeRepository.dependsOn(signIosX64Publication)
        publishJvmPublicationToSonatypeRepository.dependsOn(signIosArm64Publication)
        publishJvmPublicationToSonatypeRepository.dependsOn(signKotlinMultiplatformPublication)
        publishJvmPublicationToSonatypeRepository.dependsOn(signNativePublication)

        val publishKotlinMultiplatformPublicationToSonatypeRepository by tasks.getting
        publishKotlinMultiplatformPublicationToSonatypeRepository.dependsOn(signNativePublication)
        publishKotlinMultiplatformPublicationToSonatypeRepository.dependsOn(signJvmPublication)
        publishKotlinMultiplatformPublicationToSonatypeRepository.dependsOn(signIosX64Publication)
        publishKotlinMultiplatformPublicationToSonatypeRepository.dependsOn(signIosArm64Publication)
    }
}

// Signing artifacts. Signing.* extra properties values will be used
signing {
    sign(publishing.publications)
}
