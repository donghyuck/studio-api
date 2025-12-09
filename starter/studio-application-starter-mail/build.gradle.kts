plugins {
    id("java-library")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("maven-publish")
}
group = project.findProperty("buildStarterGroup") as String
description = "Starter for using Studio Application Mail Service (IMAP sync)"
tasks.named<Jar>("jar") {
    enabled = true
    archiveClassifier.set("")
}
tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    enabled = false
}

dependencies { 
    implementation(project(":studio-platform"))
    implementation(project(":studio-platform-autoconfigure"))
    implementation(project(":starter:studio-platform-starter"))
    api(project(":studio-application-modules:mail-service"))
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("com.sun.mail:jakarta.mail:2.0.1")
}
