plugins {
    kotlin("jvm") version "2.1.10"
    `java-gradle-plugin`
    id("maven-publish")
}

group = "dev.marcelsoftware.apklink"
version = "1.0.0"

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
        register("apklink") {
            id = "dev.marcelsoftware.apklink-gradle"
            implementationClass = "dev.marcelsoftware.apklink.ApkLinkPlugin"
        }
    }
}