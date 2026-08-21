import java.net.URI

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        google()
        ivy {
            name = "Node.js distributions"
            url = URI("https://nodejs.org/dist")
            patternLayout {
                artifact("v[revision]/[artifact](-v[revision]-[classifier]).[ext]")
            }
            metadataSources {
                artifact()
            }
            content {
                includeModule("org.nodejs", "node")
            }
        }
        ivy {
            name = "Binaryen distributions"
            url = URI("https://github.com/WebAssembly/binaryen/releases/download")
            patternLayout {
                artifact("version_[revision]/binaryen-version_[revision]-[classifier].[ext]")
            }
            metadataSources {
                artifact()
            }
            content {
                includeModule("com.github.webassembly", "binaryen")
            }
        }
    }
}

rootProject.name = "hrt-kaffee-ar"

include(":rigor-core")
include(":composeApp")
include(":webApp")
include(":embeddedEngine")
