pluginManagement {
    plugins {
        val kotlinVersion = "1.6.10"
        id("org.jetbrains.kotlin.jvm") version kotlinVersion apply false
        id("com.diffplug.spotless") version "6.2.2" apply false
        id("com.github.node-gradle.node") version "3.2.1" apply false
    }
}

rootProject.name = "spp-booster-ui"

