plugins {
    id("com.diffplug.spotless")
    id("org.jetbrains.kotlin.jvm")
    id("com.github.node-gradle.node")
    id("maven-publish")
}

val interfaceGroup: String by project
val projectVersion: String by project
val vertxVersion: String by project
val slf4jVersion: String by project

group = interfaceGroup
version = project.properties["interfaceVersion"] as String? ?: projectVersion

repositories {
    mavenCentral()
    maven(url = "https://pkg.sourceplus.plus/sourceplusplus/protocol")
}

configure<PublishingExtension> {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/sourceplusplus/interface-booster-ui")
            credentials {
                username = System.getenv("GH_PUBLISH_USERNAME")?.toString()
                password = System.getenv("GH_PUBLISH_TOKEN")?.toString()
            }
        }
    }

    publishing {
        publications {
            create<MavenPublication>("maven") {
                groupId = interfaceGroup
                artifactId = "interface-booster-ui"
                version = project.version.toString()

                from(components["kotlin"])
            }
        }
    }
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation("io.vertx:vertx-core:$vertxVersion")
    implementation("io.vertx:vertx-web:$vertxVersion")
    implementation("io.vertx:vertx-lang-kotlin:$vertxVersion")
    implementation("io.vertx:vertx-lang-kotlin-coroutines:$vertxVersion")
    implementation("org.slf4j:slf4j-api:$slf4jVersion")
    implementation("plus.sourceplus:protocol:$projectVersion")
}

tasks {
    node {
        download.set(true)
        version.set("16.15.0")
    }

    register<com.github.gradle.node.npm.task.NpmTask>("buildSkyWalkingUI") {
        dependsOn("npmInstall")
        npmCommand.set(listOf("run", "build"))
        node.nodeProjectDir.set(file("./src/main/skywalking-booster-ui"))
    }
    register<Copy>("moveSkyWalkingUI") {
        shouldRunAfter("buildSkyWalkingUI")
        from(file("./src/main/skywalking-booster-ui/dist"))
        into(file("./src/main/resources/webroot"))
    }
    getByName("processResources") {
        dependsOn("buildSkyWalkingUI", "moveSkyWalkingUI")
    }
}
