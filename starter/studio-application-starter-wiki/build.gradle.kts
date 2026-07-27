description = "Starter for using Studio Application Workspace Wiki Service"

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
    api(project(":studio-platform"))
    api(project(":studio-platform-identity"))
    api(project(":studio-platform-workspace"))
    api(project(":studio-application-modules:wiki-service"))
    compileOnly("org.springframework.boot:spring-boot-starter-web")
    compileOnly("org.springframework.boot:spring-boot-starter-data-jpa")
    compileOnly("org.springframework.boot:spring-boot-starter-security")
    compileOnly("org.springframework.boot:spring-boot-starter-validation")

    testImplementation(project(":studio-platform-autoconfigure"))
    testImplementation(project(":studio-platform"))
    testImplementation(project(":studio-platform-identity"))
    testImplementation(project(":studio-platform-workspace"))
    testImplementation(project(":studio-platform-workspace-default"))
    testImplementation(project(":starter:studio-platform-starter-workspace"))
    testImplementation(project(":studio-application-modules:wiki-service"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito:mockito-core")
    testImplementation("org.mockito:mockito-junit-jupiter")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa")
    testImplementation("org.springframework.boot:spring-boot-starter-web")
    testRuntimeOnly("com.h2database:h2")
}
