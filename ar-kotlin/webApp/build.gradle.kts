import org.gradle.api.tasks.testing.Test
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
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

    wasmJs {
        moduleName = "hrtKaffeeAr"
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
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
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
