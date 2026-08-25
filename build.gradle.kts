import org.gradle.kotlin.dsl.implementation

plugins {
    java
    id("org.springframework.boot") version "4.1.0"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com"
version = "0.0.1-SNAPSHOT"
description = "backend_catcheat"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

extra["springAiVersion"] = "2.0.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation(platform("software.amazon.awssdk:bom:2.30.31"))
    implementation("software.amazon.awssdk:s3")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.flywaydb:flyway-database-postgresql")

    // JWT (jjwt) — 서비스 자체 access/refresh 토큰 발급·검증용
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")


    implementation("org.springframework.ai:spring-ai-starter-model-openai")
    compileOnly("org.projectlombok:lombok")
    developmentOnly("org.springframework.boot:spring-boot-devtools")
    runtimeOnly("org.postgresql:postgresql")
    annotationProcessor("org.projectlombok:lombok")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-starter-flyway-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testCompileOnly("org.projectlombok:lombok")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testAnnotationProcessor("org.projectlombok:lombok")
    testImplementation("org.testcontainers:junit-jupiter:1.19.8")
    testImplementation("org.testcontainers:testcontainers:1.19.8")

    implementation ("org.springframework.boot:spring-boot-starter-actuator")

    // Micrometer의 Prometheus 레지스트리
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")

    // presigned 다운로드 URL 캐시용 로컬 캐시.
    // 캐시 키가 S3 object key라 사진이 늘어난 만큼 무한히 늘어난다 —
    // 크기 상한과 만료가 없는 Map으로는 메모리 누수가 된다. 버전은 Spring Boot BOM이 관리한다
    implementation("com.github.ben-manes.caffeine:caffeine")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.ai:spring-ai-bom:${property("springAiVersion")}")
    }
}

tasks.withType<Test> {
    useJUnitPlatform {
        if (!project.hasProperty("includeExternal")) {
            excludeTags("external")
        }
    }
}
