plugins {
    kotlin("jvm") version "2.1.10"
    `java-gradle-plugin`
    id("maven-publish")
}

val projectVersion = "1.0.2"
val projectGroup = "dev.marcelsoftware.apklink"
val pluginId = "$projectGroup-gradle"

group = projectGroup
version = projectVersion


repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))

    implementation("de.femtopedia.dex2jar:dex2jar:2.4.28")
    implementation("org.json:json:20231013")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(11)
}

gradlePlugin {
    plugins {
        register(pluginId) {
            version = projectVersion
            id = pluginId
            implementationClass = "$projectGroup.ApkLinkPlugin"
        }
    }
}