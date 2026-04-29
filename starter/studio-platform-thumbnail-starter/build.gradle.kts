plugins {
    id("java-library")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("maven-publish")
}

group = project.findProperty("buildStarterGroup") as String
description = "Starter for Studio Platform thumbnail generation"

tasks.named<Jar>("jar") {
    enabled = true
    archiveClassifier.set("")
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    enabled = false
}

java {
    withSourcesJar()
}

dependencies {
    api(project(":studio-platform"))
    api(project(":studio-platform-thumbnail"))
    api(project(":studio-platform-autoconfigure"))
    implementation("org.springframework.boot:spring-boot-starter-validation")
    compileOnly("org.apache.pdfbox:pdfbox:${property("apachePdfBoxVersion")}")

    testImplementation("org.apache.pdfbox:pdfbox:${property("apachePdfBoxVersion")}")
}
