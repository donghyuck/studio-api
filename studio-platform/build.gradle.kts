description = "Studio One Platform"

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
    compileOnly("com.google.code.findbugs:jsr305:3.0.2")
    compileOnly("org.springframework.boot:spring-boot-starter-web")
    compileOnly ("org.springframework.data:spring-data-commons")
    compileOnly("org.springframework.boot:spring-boot-starter-security")
    compileOnly("org.springframework.security:spring-security-acl")
    compileOnly("org.springframework.boot:spring-boot-starter-validation")
    compileOnly("org.springframework.boot:spring-boot-starter-data-jpa")
    api("commons-io:commons-io:${project.findProperty("apacheCommonsIoVersion")}") 
    api("org.apache.commons:commons-lang3:${project.findProperty("apacheCommonsLang3Version")}")
}