plugins {
    id("java-library")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("maven-publish")
}
description = "Starter for using Studio Platform ObjectType"
tasks.named<Jar>("jar") {
    enabled = true
    archiveClassifier.set("")
}
tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    enabled = false
}

dependencies {
    compileOnly(project(":studio-platform-autoconfigure"))
    compileOnly(project(":starter:studio-platform-starter"))
    api(project(":studio-platform-objecttype"))
    compileOnly("org.springframework.boot:spring-boot-starter-web")
    compileOnly("org.springframework.boot:spring-boot-starter-validation")
    compileOnly("org.springframework.boot:spring-boot-starter-data-jpa")
    compileOnly("org.yaml:snakeyaml")
    testImplementation("org.junit.jupiter:junit-jupiter-api")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
    testImplementation("org.springframework:spring-core")
    testImplementation("org.yaml:snakeyaml")
    testImplementation(project(":studio-platform"))
}
