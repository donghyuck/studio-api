description = "Starter for using Studio Platform Team"

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
    api(project(":studio-platform-autoconfigure"))
    api(project(":studio-platform"))
    api(project(":studio-platform-identity"))
    api(project(":studio-platform-team"))
    api(project(":studio-platform-team-default"))
    api(project(":studio-platform-workspace"))
    implementation(project(":studio-platform-user"))
    compileOnly("org.springframework.boot:spring-boot-starter-webmvc")
    compileOnly("org.springframework.boot:spring-boot-starter-data-jpa")
    compileOnly("org.springframework.boot:spring-boot-starter-security")
    compileOnly("org.springframework.boot:spring-boot-starter-validation")

    testImplementation(project(":studio-platform-autoconfigure"))
    testImplementation(project(":studio-platform"))
    testImplementation(project(":studio-platform-identity"))
    testImplementation(project(":studio-platform-team"))
    testImplementation(project(":studio-platform-team-default"))
    testImplementation(project(":studio-platform-workspace"))
    testImplementation(project(":studio-platform-user"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc")
    testImplementation("org.springframework.security:spring-security-core")
    testRuntimeOnly("com.h2database:h2")
}
