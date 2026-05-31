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
    compileOnly(project(":studio-platform-realtime"))
    compileOnly("org.springframework.boot:spring-boot-starter")
    compileOnly("org.springframework.boot:spring-boot-starter-web")
    compileOnly("org.springframework.boot:spring-boot-starter-validation")
    compileOnly("org.springframework.boot:spring-boot-starter-security")
    compileOnly("org.springframework:spring-jdbc")
    compileOnly("org.apache.poi:poi-ooxml:${property("apachePoiVersion")}")
    compileOnly("org.apache.poi:poi:${property("apachePoiVersion")}")

    testImplementation(project(":studio-platform"))
    testImplementation(project(":studio-platform-ai"))
    testImplementation(project(":studio-platform-skillgraph"))
    testImplementation(project(":studio-platform-realtime"))
    testImplementation("org.springframework.boot:spring-boot-test")
    testImplementation("org.springframework:spring-jdbc")
    testImplementation("org.apache.poi:poi-ooxml:${property("apachePoiVersion")}")
    testImplementation("org.apache.poi:poi:${property("apachePoiVersion")}")
    testRuntimeOnly("com.h2database:h2")
}
