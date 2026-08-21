import org.gradle.api.tasks.testing.Test
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
}

kotlin {
    jvmToolchain(17)

    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
            allWarningsAsErrors.set(true)
            progressiveMode.set(true)
        }
    }

    js(IR) {
        browser {
            commonWebpackConfig {
                outputFileName = "hrt-kaffee-kinetics.js"
            }
        }
        binaries.executable()
        compilerOptions {
            allWarningsAsErrors.set(true)
            progressiveMode.set(true)
        }
    }

    sourceSets {
        jsMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-browser:0.3")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        jvmTest.dependencies {
            implementation(project(":rigor-core"))
            implementation(kotlin("test-junit5"))
            implementation("org.junit.platform:junit-platform-launcher:1.13.4")
        }
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
