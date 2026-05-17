description = "Starter for using Studio Platform SkillGraph"

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
    compileOnly(project(":studio-platform-autoconfigure"))
    compileOnly(project(":studio-platform"))
    api(project(":studio-platform-skillgraph"))
    compileOnly(project(":studio-platform-ai"))
    compileOnly(project(":studio-platform-chunking"))
    compileOnly("org.springframework.boot:spring-boot-starter")
    compileOnly("org.springframework.boot:spring-boot-starter-web")
    compileOnly("org.springframework.boot:spring-boot-starter-validation")
    compileOnly("org.springframework.boot:spring-boot-starter-security")
    compileOnly("org.springframework:spring-jdbc")

    testImplementation(project(":studio-platform"))
    testImplementation(project(":studio-platform-ai"))
    testImplementation(project(":studio-platform-skillgraph"))
    testImplementation("org.springframework.boot:spring-boot-test")
    testImplementation("org.springframework:spring-jdbc")
    testRuntimeOnly("com.h2database:h2")
}
