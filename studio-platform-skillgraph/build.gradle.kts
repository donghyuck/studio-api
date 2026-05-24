description = "Studio Platform SkillGraph"

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
    api(project(":studio-platform"))
    api("com.fasterxml.jackson.core:jackson-databind")
    api("org.springframework.data:spring-data-commons")
    compileOnly(project(":studio-platform-ai"))
    compileOnly(project(":studio-platform-chunking"))
    compileOnly("org.springframework.boot:spring-boot-starter")
    compileOnly("org.springframework.boot:spring-boot-starter-web")
    compileOnly("org.springframework.boot:spring-boot-starter-security")
    compileOnly("org.springframework.boot:spring-boot-starter-validation")
    compileOnly("org.springframework:spring-jdbc")

    compileOnly("org.apache.poi:poi-ooxml:${property("apachePoiVersion")}")
    compileOnly("org.apache.poi:poi:${property("apachePoiVersion")}")

    testImplementation(project(":studio-platform-ai"))
    testImplementation(project(":studio-platform-chunking"))
    testImplementation("com.fasterxml.jackson.core:jackson-annotations")
    testImplementation("org.springframework:spring-web")
    testImplementation("org.springframework:spring-jdbc")
    testRuntimeOnly("com.h2database:h2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
}
