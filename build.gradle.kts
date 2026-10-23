plugins {
    kotlin("jvm") version "2.1.20"
    `java-library`
}

group = "org.libmonero"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    api("com.fasterxml.jackson.core:jackson-databind:2.17.2")
    implementation("net.java.dev.jna:jna:5.15.0")

    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
    maxParallelForks = 1
    maxHeapSize = "512m"
}
