description = "Starter for Studio Application Web Knowledge Service"

plugins {
    id("java-library")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("maven-publish")
}

group = project.findProperty("buildStarterGroup") as String

tasks.named<Jar>("jar") {
    enabled = true
    archiveClassifier.set("")
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    enabled = false
}

dependencies {
    api(project(":studio-platform-autoconfigure"))
    api(project(":studio-platform-identity"))
    api(project(":studio-platform-workspace"))
    api(project(":studio-platform-ai"))
    api(project(":studio-platform-chunking"))
    api(project(":studio-application-modules:web-knowledge-service"))
    compileOnly(project(":starter:studio-platform-starter-ai"))
    compileOnly(project(":starter:studio-platform-starter-ai-web"))
    compileOnly(project(":starter:studio-platform-starter-chunking"))
    compileOnly(project(":studio-platform-chunking-runtime"))
    compileOnly(project(":studio-platform-textract"))
    compileOnly("org.jsoup:jsoup:${property("jsoupVersion")}")
    compileOnly("org.springframework.boot:spring-boot-starter-webmvc")
    compileOnly("org.springframework.boot:spring-boot-starter-data-jpa")
    compileOnly("org.springframework.boot:spring-boot-starter-security")
    compileOnly("org.springframework.boot:spring-boot-starter-validation")

    testImplementation(project(":starter:studio-platform-starter-ai"))
    testImplementation(project(":starter:studio-platform-starter-ai-web"))
    testImplementation(project(":starter:studio-platform-starter-chunking"))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc")
    testRuntimeOnly("com.h2database:h2")
}
