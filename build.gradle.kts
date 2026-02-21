plugins {
    kotlin("jvm") version "1.9.24"
    kotlin("plugin.spring") version "1.9.24"
    id("io.spring.dependency-management") version "1.1.7"
    `java-library`
    `maven-publish`
}

group = "com.github.MuhammadAounAnwar"
version = "1.0.5"
description = "Logging auto-configuration library for Kotlin Spring Boot"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    withSourcesJar()
}

repositories {
    mavenCentral()
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:3.2.5")
    }
}

dependencies {

    // ===== Spring Boot AutoConfiguration =====
    compileOnly("org.springframework.boot:spring-boot-autoconfigure")

    // ===== Spring Core =====
    compileOnly("org.springframework:spring-context")

    // ===== WebFlux =====
    compileOnly("org.springframework.boot:spring-boot-starter-webflux")

    // ===== R2DBC =====
    compileOnly("org.springframework.boot:spring-boot-starter-data-r2dbc")

    // ===== AOP =====
    compileOnly("org.springframework.boot:spring-boot-starter-aop")
    compileOnly("org.aspectj:aspectjweaver")

    // ===== Micrometer =====
    compileOnly("io.micrometer:micrometer-core")

    // ===== Logbook =====
    val logbookVersion = "3.9.0"
    api("org.zalando:logbook-core:$logbookVersion")
    api("org.zalando:logbook-json:$logbookVersion")
    api("org.zalando:logbook-spring-webflux:$logbookVersion")

    // ===== Logging =====
    api("org.slf4j:slf4j-api:2.0.12")

    // ===== Testing =====
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webflux")
    testImplementation(kotlin("test"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = freeCompilerArgs + "-java-parameters"
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = "logginglibrary-spring-boot-starter"
            from(components["java"])
        }
    }
}