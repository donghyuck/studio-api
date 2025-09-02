plugins {
    id("java-library")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}
description = "Starter for using Studio Platform User"
tasks.named<Jar>("jar") {
    enabled = true
}
tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    enabled = false
}

dependencies { 
    compileOnly(project(":starter:studio-platform-starter"))
    compileOnly(project(":studio-platform-user"))
    api(project(":studio-platform-security"))
    compileOnly("org.springframework.boot:spring-boot-starter-web")
    compileOnly("org.springframework.boot:spring-boot-starter-data-jpa")
    compileOnly("org.springframework.boot:spring-boot-starter-validation")
    compileOnly("org.springframework.boot:spring-boot-starter-security")
}