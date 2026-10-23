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

    // runs the tests from the files of unitTests, without Gradle. It is the version of the launcher that kotlin-test brings
    testRuntimeOnly("org.junit.platform:junit-platform-console:1.10.1")
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
    maxParallelForks = 1
    maxHeapSize = "512m"
}

// The tests, and the files they need to run without building anything: the classes, the resources and the libraries, as test.yml of
// GitHub runs them on every platform. The classes stay as directories, since the tests look for the classes of the model there
val unitTests by tasks.registering(Sync::class) {
    dependsOn(tasks.testClasses)
    into(layout.buildDirectory.dir("unit-tests"))
    from(sourceSets.main.get().output.classesDirs) { into("classes/main") }
    from(sourceSets.test.get().output.classesDirs) { into("classes/test") }
    from(layout.buildDirectory.dir("resources/main")) { into("resources/main") }
    from(layout.buildDirectory.dir("resources/test")) { into("resources/test") }
    from(configurations.testRuntimeClasspath.map { classpath -> classpath.filter { it.isFile } }) { into("lib") }
}
