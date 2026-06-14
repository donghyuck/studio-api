description = "Studio Platform Markdown Knowledge Pipeline"

plugins {
    id("java-library")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("maven-publish")
}

tasks.named<Jar>("jar") {
    enabled = true
    archiveClassifier.set("")
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    enabled = false
}

dependencies {
    api(project(":studio-platform"))
    compileOnly(project(":studio-platform-document-convert"))
    compileOnly("org.springframework.boot:spring-boot-starter-web")
    compileOnly("org.springframework.boot:spring-boot-starter-security")
    compileOnly("org.springframework.boot:spring-boot-starter-validation")
    compileOnly("org.springframework:spring-jdbc")
    compileOnly("org.springframework:spring-tx")
    compileOnly("com.fasterxml.jackson.core:jackson-databind")

    testImplementation("org.springframework:spring-jdbc")
    testImplementation("org.springframework:spring-tx")
    testImplementation("com.fasterxml.jackson.core:jackson-databind")
    testRuntimeOnly("com.h2database:h2")
}
