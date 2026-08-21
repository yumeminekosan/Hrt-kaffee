import org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsEnvSpec
import org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsPlugin

plugins {
    kotlin("jvm") version "2.4.10" apply false
    kotlin("multiplatform") version "2.4.10" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10" apply false
    id("org.jetbrains.compose") version "1.11.1" apply false
}

plugins.withType<WasmNodeJsPlugin> {
    the<WasmNodeJsEnvSpec>().apply {
        downloadBaseUrl.set(null as String?)
    }
}

allprojects {
    group = "dev.hrtkaffee.ar"
    version = "0.1.0"
}
