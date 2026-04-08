description = "Studio Platform User Default Implementation"

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

val mapstructVersion: String = project.findProperty("mapstructVersion") as String? ?: "0.11.5"

dependencies {
    compileOnly("org.springframework.boot:spring-boot-starter")
    compileOnly("org.springframework.boot:spring-boot-starter-web")
    compileOnly("org.springframework.boot:spring-boot-starter-data-jpa")
    compileOnly("org.springframework.boot:spring-boot-starter-security")
    compileOnly("org.springframework.boot:spring-boot-starter-validation")
    compileOnly(project(":studio-platform"))
    compileOnly(project(":studio-platform-user"))
    compileOnly(project(":studio-platform-identity"))
    compileOnly("org.mapstruct:mapstruct:$mapstructVersion")
    annotationProcessor("org.mapstruct:mapstruct-processor:$mapstructVersion")
    annotationProcessor("org.projectlombok:lombok-mapstruct-binding:0.2.0")

    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito:mockito-core")
    testImplementation("org.mockito:mockito-junit-jupiter")
    testImplementation("org.mapstruct:mapstruct:$mapstructVersion")
    testImplementation("org.springframework.boot:spring-boot-starter-web")
    testImplementation("org.springframework.data:spring-data-commons")
    testImplementation("org.springframework:spring-jdbc")
    testImplementation("org.springframework.security:spring-security-core")
    testImplementation(project(":studio-platform"))
    testImplementation(project(":studio-platform-user"))
}
