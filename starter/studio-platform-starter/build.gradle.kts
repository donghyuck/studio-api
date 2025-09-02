plugins {
    id("java-library")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}
description = "Starter for using Studio Platform"
tasks.named<Jar>("jar") {
    enabled = true
}
tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    enabled = false
}

dependencies {
    api(project(":studio-platform"))
    api(project(":studio-platform-jpa"))
    api(project(":studio-platform-autoconfigure")) 
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation") // javax 기반
}