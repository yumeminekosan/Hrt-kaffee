import org.gradle.api.tasks.testing.Test
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

@OptIn(ExperimentalWasmDsl::class)
kotlin {
    jvmToolchain(17)

    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
            allWarningsAsErrors.set(true)
            progressiveMode.set(true)
        }
    }

    wasmJs {
        browser {
            commonWebpackConfig {
                outputFileName = "hrt-kaffee-ar.js"
            }
        }
        binaries.executable()
        compilerOptions {
            allWarningsAsErrors.set(true)
            progressiveMode.set(true)
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation("org.jetbrains.compose.runtime:runtime:1.11.1")
            implementation("org.jetbrains.compose.foundation:foundation:1.11.1")
            implementation("org.jetbrains.compose.material3:material3:1.11.0-alpha07")
            implementation("org.jetbrains.compose.ui:ui:1.11.1")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
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
