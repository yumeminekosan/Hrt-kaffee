plugins {
    kotlin("jvm") version "2.4.10" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10" apply false
    id("org.jetbrains.compose") version "1.11.1" apply false
}

allprojects {
    group = "dev.hrtkaffee.ar"
    version = "0.1.0"
}
