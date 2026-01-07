plugins {
    id("java-library")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("maven-publish")
}
group = project.findProperty("buildStarterGroup") as String
description = "Starter for using Studio Application Module Template Service"
tasks.named<Jar>("jar") {
    enabled = true
    archiveClassifier.set("")
}
tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    enabled = false
}

dependencies { 
    compileOnly(project(":studio-platform-user"))
    compileOnly(project(":studio-platform-autoconfigure"))
    compileOnly(project(":starter:studio-platform-starter"))
    api(project(":studio-application-modules:template-service"))
    compileOnly("org.springframework.boot:spring-boot-starter-validation")
    compileOnly("org.springframework.boot:spring-boot-starter-data-jpa")
    compileOnly("org.springframework.boot:spring-boot-starter-web")
    compileOnly ("org.freemarker:freemarker:2.3.32")  
}