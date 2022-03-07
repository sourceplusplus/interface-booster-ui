plugins {
    id("com.diffplug.spotless")
    id("org.jetbrains.kotlin.jvm")
}

val platformGroup: String by project
val projectVersion: String by project
val vertxVersion: String by project

group = platformGroup
version = projectVersion

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation("io.vertx:vertx-core:$vertxVersion")
    implementation("io.vertx:vertx-web:$vertxVersion")
    implementation("io.vertx:vertx-lang-kotlin:$vertxVersion")
    implementation("io.vertx:vertx-lang-kotlin-coroutines:$vertxVersion")
}
