plugins {
    application
    id("org.jetbrains.kotlin.jvm") version "2.2.20"
}

repositories {
    mavenCentral()
}

dependencies {
    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

    implementation("org.jfree:jfreechart:1.5.6")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(24)
    }
}

sourceSets {
    val main by getting {
        java.setSrcDirs(listOf("src/main/java+kotlin"))
    }
}

application {
    mainClass = "tech.bedson.MainKt"
}
