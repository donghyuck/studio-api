plugins {
    id("java-library")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("maven-publish")
}
group = project.findProperty("buildStarterGroup") as String
description = "Starter for using Studio Application Module Attachment Service"
tasks.named<Jar>("jar") {
    enabled = true
    archiveClassifier.set("")
}
tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    enabled = false
}

dependencies {  
    compileOnly(project(":studio-platform-autoconfigure"))
    compileOnly(project(":starter:studio-platform-starter"))
    api(project(":starter:studio-platform-thumbnail-starter"))
    compileOnly(project(":studio-platform-identity"))
    compileOnly(project(":studio-platform-objecttype"))
    api(project(":studio-application-modules:attachment-service"))
    compileOnly("org.springframework.boot:spring-boot-starter-validation")
    compileOnly("org.springframework.boot:spring-boot-starter-data-jpa")
    testImplementation(project(":studio-platform-autoconfigure"))
    testImplementation(project(":starter:studio-platform-thumbnail-starter"))
    testImplementation(project(":studio-platform-identity"))
    testImplementation(project(":studio-platform-objecttype"))
    testImplementation("org.springframework.boot:spring-boot-starter-validation")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa")
}
