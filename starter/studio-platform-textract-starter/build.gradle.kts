plugins {
    id("java-library")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("maven-publish")
}

group = project.findProperty("buildStarterGroup") as String
description = "Starter for Studio Platform text extraction"

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
    api(project(":studio-platform-textract"))
    api(project(":studio-platform-autoconfigure"))
    compileOnly("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    testImplementation("org.springframework.boot:spring-boot-starter-web")
    testImplementation("org.apache.poi:poi:${property("apachePoiVersion")}")
}
