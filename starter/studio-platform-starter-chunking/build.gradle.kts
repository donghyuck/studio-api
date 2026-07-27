plugins {
    id("java-library")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("maven-publish")
}

description = "Starter for Studio Platform chunking strategies"

tasks.named<Jar>("jar") {
    enabled = true
    archiveClassifier.set("")
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    enabled = false
}

dependencies {
    api(project(":studio-platform-chunking"))
    api(project(":studio-platform-chunking-runtime"))
    compileOnly(project(":studio-platform-ai"))
    compileOnly(project(":studio-platform-textract"))
    compileOnly(project(":studio-platform-autoconfigure"))
    compileOnly(project(":starter:studio-platform-starter"))
    compileOnly("org.springframework.boot:spring-boot-autoconfigure")
    compileOnly("org.springframework:spring-context")
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core")
    testImplementation(project(":studio-platform-textract"))
    testImplementation(project(":studio-platform-ai"))
    testImplementation(project(":studio-platform-chunking-runtime"))
    testImplementation("org.springframework.boot:spring-boot-autoconfigure")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
