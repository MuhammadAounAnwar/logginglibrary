plugins {
    kotlin("jvm") version "1.9.24"
    kotlin("plugin.spring") version "1.9.24"
    id("io.spring.dependency-management") version "1.1.7"
    `java-library`
    `maven-publish`
}

group = "com.github.MuhammadAounAnwar"
version = "1.0.12"
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

    // ===== Spring Security (compileOnly — for AccessDeniedException/AuthenticationException in exception handlers) =====
    compileOnly("org.springframework.boot:spring-boot-starter-security")

    // ===== Servlet/MVC (for Servlet exception handler and correlation filter) =====
    compileOnly("org.springframework.boot:spring-boot-starter-web")
    compileOnly("jakarta.servlet:jakarta.servlet-api")

    // ===== R2DBC =====
    compileOnly("org.springframework.boot:spring-boot-starter-data-r2dbc")

    // ===== AOP =====
    compileOnly("org.springframework.boot:spring-boot-starter-aop")
    compileOnly("org.aspectj:aspectjweaver")

    // ===== Micrometer =====
    compileOnly("io.micrometer:micrometer-core")
    compileOnly("io.micrometer:context-propagation:1.1.1")

    // ===== Messaging and clients the correlation id travels through (compileOnly: a consumer gets the hook only when it has the class) =====
    compileOnly("org.springframework.kafka:spring-kafka")
    compileOnly("org.springframework.boot:spring-boot-starter-amqp")
    compileOnly("com.zaxxer:HikariCP")

    // ===== Logbook (compileOnly — consumers must add logbook themselves when they want HTTP logging) =====
    val logbookVersion = "3.9.0"
    compileOnly("org.zalando:logbook-core:$logbookVersion")
    compileOnly("org.zalando:logbook-json:$logbookVersion")
    compileOnly("org.zalando:logbook-spring-webflux:$logbookVersion")
    compileOnly("org.zalando:logbook-spring-boot-starter:$logbookVersion")

    // ===== Logging =====
    api("org.slf4j:slf4j-api:2.0.12")

    // ===== Loki log shipping (compileOnly — consumers must add loki-logback-appender
    // themselves to activate the LOKI appender in logback-spring.xml below; 1.6.0 is the
    // last line compatible with Logback 1.4.x, which is what Spring Boot 3.2.x ships) =====
    compileOnly("com.github.loki4j:loki-logback-appender:1.6.0")

    // ===== Testing =====
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webflux")
    testImplementation("org.springframework.boot:spring-boot-starter-web")
    // The exception handlers reference Spring Security types (compileOnly above); the tests need them on the classpath.
    testImplementation("org.springframework.boot:spring-boot-starter-security")
    testImplementation("org.springframework.kafka:spring-kafka")
    testImplementation("org.springframework.boot:spring-boot-starter-amqp")
    testImplementation("org.springframework:spring-jdbc")
    testImplementation("io.micrometer:context-propagation:1.1.1")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:rabbitmq")
    testImplementation("org.testcontainers:kafka")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.postgresql:postgresql")
    testImplementation("com.zaxxer:HikariCP")
    testImplementation("io.projectreactor:reactor-test")
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

// Spring Boot 3.2.5 manages Testcontainers 1.19.7, whose docker-java client cannot negotiate the Docker Engine API of Docker 29;
// 1.21.4 is the first release that does.
ext["testcontainers.version"] = "1.21.4"
