pluginManagement {
    plugins {
        val kotlinVersion = "1.6.10"
        id("org.jetbrains.kotlin.jvm") version kotlinVersion apply false
        id("com.diffplug.spotless") version "6.6.1" apply false
        id("com.github.node-gradle.node") version "3.3.0" apply false
    }
}

rootProject.name = "spp-interface-booster-ui"

