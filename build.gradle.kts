plugins {
    id("hytale-mod") version "0.+"
}

group = "LadyPaladra"
version = "0.5.0"
val javaVersion = 25

repositories {
    mavenCentral()
    maven("https://maven.hytale-modding.info/releases") {
        name = "HytaleModdingReleases"
    }
}

dependencies {
    compileOnly(libs.jetbrains.annotations)
    compileOnly(libs.jspecify)
}

hytale {
    addAssetsDependency = true
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(javaVersion)
    }
}

tasks.withType<Jar> {
    manifest {
        attributes["Specification-Title"] = project.name
        attributes["Specification-Version"] = version
        attributes["Implementation-Title"] = project.name
        attributes["Implementation-Version"] = version.toString()
    }
}
