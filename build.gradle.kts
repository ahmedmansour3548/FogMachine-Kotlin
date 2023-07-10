
import org.jetbrains.dokka.DokkaConfiguration.Visibility
import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.8.21"
    id("org.jetbrains.dokka")  version "1.8.20"
    application
}

group = "org.fogmachine"
version = "1.0-SNAPSHOT"



repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    implementation("org.json:json:20230618")
    implementation(kotlin("stdlib-jdk8"))
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

application {
    mainClass.set("FogMachineKt")
}
tasks.withType<DokkaTask>().configureEach {
    dokkaSourceSets.configureEach {
        documentedVisibilities.set(setOf(Visibility.PUBLIC, Visibility.INTERNAL, Visibility.PROTECTED, Visibility.PRIVATE))
        skipEmptyPackages.set(true)
        noStdlibLink.set(true)
        outputDirectory.set(file("$buildDir/docs/api"))
        perPackageOption {
            matchingRegex.set("[\\w]+")
            skipEmptyPackages.set(true)
        }

    }
}
tasks.jar {
    manifest {
        attributes["Main-Class"] = "FogMachineKt"
    }
}


val compileKotlin: KotlinCompile by tasks
compileKotlin.kotlinOptions {
    jvmTarget = "1.8"
}
val compileTestKotlin: KotlinCompile by tasks
compileTestKotlin.kotlinOptions {
    jvmTarget = "1.8"
}