plugins {
    id("java-library")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("maven-publish")
}
description = "Starter for using Studio Platform User"
val mapstructVersion: String = project.findProperty("mapstructVersion") as String? ?: "0.11.5"
tasks.named<Jar>("jar") {
    enabled = true
    archiveClassifier.set("")
}
tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    enabled = false
}

dependencies { 
    compileOnly(project(":studio-platform-autoconfigure"))
    compileOnly(project(":studio-platform-identity"))
    compileOnly(project(":starter:studio-platform-starter"))
    api(project(":studio-platform-user")) 
    compileOnly(project(":studio-platform-user-default"))
    compileOnly("org.springframework.boot:spring-boot-starter-web")
    compileOnly("org.springframework.boot:spring-boot-starter-data-jpa")
    compileOnly("org.springframework.boot:spring-boot-starter-validation")
    compileOnly("org.springframework.boot:spring-boot-starter-security")
    compileOnly("org.mapstruct:mapstruct:$mapstructVersion")
    testImplementation(project(":studio-platform"))
    testImplementation(project(":studio-platform-autoconfigure"))
    testImplementation(project(":studio-platform-identity"))
    testImplementation(project(":studio-platform-user-default"))
    testImplementation("org.springframework.boot:spring-boot-starter-web")
    testImplementation("org.springframework.data:spring-data-commons")
    testImplementation("org.springframework:spring-jdbc")
    testImplementation("org.springframework.security:spring-security-core")
    testImplementation("org.springframework.security:spring-security-crypto")
}
