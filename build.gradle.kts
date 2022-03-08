plugins {
    id("com.diffplug.spotless")
    id("org.jetbrains.kotlin.jvm")
    id("com.github.node-gradle.node")
}

val platformGroup: String by project
val projectVersion: String by project
val vertxVersion: String by project
val slf4jVersion: String by project
val logbackVersion: String by project

group = platformGroup
version = projectVersion

repositories {
    mavenCentral()
    maven(url = "https://jitpack.io") { name = "jitpack" }
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation("io.vertx:vertx-core:$vertxVersion")
    implementation("io.vertx:vertx-web:$vertxVersion")
    implementation("io.vertx:vertx-lang-kotlin:$vertxVersion")
    implementation("io.vertx:vertx-lang-kotlin-coroutines:$vertxVersion")
    implementation("org.slf4j:slf4j-api:$slf4jVersion")
    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("com.github.sourceplusplus.interface-portal:portal-jvm:4cd858772b") { isTransitive = false }
    implementation("com.github.sourceplusplus.protocol:protocol:e540bbcccb")
}

tasks {
    register<com.github.gradle.node.npm.task.NpmTask>("buildSkyWalkingUI") {
        dependsOn("npmInstall")
        npmCommand.set(listOf("run", "build"))
        node.nodeProjectDir.set(file("./src/main/skywalking-booster-ui"))
    }
    register<Copy>("moveSkyWalkingUI") {
        shouldRunAfter("buildSkyWalkingUI")
        from(file("./src/main/skywalking-booster-ui/lib"))
        into(file("./src/main/resources/webroot"))
    }
    getByName("processResources") {
        dependsOn("buildSkyWalkingUI", "moveSkyWalkingUI")
    }
}
