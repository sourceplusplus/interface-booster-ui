plugins {
    id("com.diffplug.spotless")
    id("org.jetbrains.kotlin.jvm")
    id("com.github.node-gradle.node")
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
    getByName("build") {
        dependsOn("buildSkyWalkingUI", "moveSkyWalkingUI")
    }
}
