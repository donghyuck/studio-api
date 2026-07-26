description = "Starter for using Studio Platform Document Convert"

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
    compileOnly(project(":studio-platform-autoconfigure"))
    compileOnly(project(":studio-platform"))
    api(project(":studio-platform-document-convert"))
    compileOnly(project(":studio-platform-storage"))
    compileOnly(project(":studio-application-modules:attachment-service"))
    compileOnly("org.springframework.boot:spring-boot-starter")
    compileOnly("org.springframework.boot:spring-boot-starter-webmvc")
    compileOnly("org.springframework.boot:spring-boot-starter-validation")
    compileOnly("org.springframework:spring-jdbc")

    testImplementation(project(":studio-platform-storage"))
    testImplementation(project(":studio-application-modules:attachment-service"))
    testImplementation("org.springframework.boot:spring-boot-test")
    testImplementation("org.springframework:spring-jdbc")
}
