plugins {
    kotlin("jvm") version "1.9.24"
    kotlin("plugin.spring") version "1.9.24"

    id("io.spring.dependency-management") version "1.1.7"

    `java-library`
    `maven-publish`
}

group = "com.github.MuhammadAounAnwar"
version = "1.0.3"
description = "Logging auto-configuration library for Kotlin Spring Boot"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }

    withSourcesJar()
    withJavadocJar()
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

    /* ============================
     * Spring Boot (compile-time)
     * ============================ */
    compileOnly("org.springframework.boot:spring-boot")
    compileOnly("org.springframework.boot:spring-boot-autoconfigure")

    /* ============================
     * Spring Framework (public API)
     * ============================ */
    api("org.springframework:spring-context")
    api("org.springframework:spring-aop")

    /* ============================
     * Logging (public contract)
     * ============================ */
    api("org.zalando:logbook-spring-boot-starter:3.9.0")
    api("org.zalando:logbook-json:3.9.0")

    /* ============================
     * Optional integrations
     * ============================ */
    compileOnly("org.springframework.boot:spring-boot-starter-web")
    compileOnly("org.springframework.boot:spring-boot-starter-data-jpa")

    /* ============================
     * Internal utilities
     * ============================ */
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    /* ============================
     * Testing
     * ============================ */
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xjsr305=strict",
            "-Xannotation-default-target=param-property"
        )
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

/* ============================
 * Library JAR (NOT executable)
 * ============================ */
tasks.jar {
    enabled = true
}


/* ============================
 * Maven Publication (JitPack)
 * ============================ */
publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = "logginglibrary"
            from(components["java"])
        }
    }
}