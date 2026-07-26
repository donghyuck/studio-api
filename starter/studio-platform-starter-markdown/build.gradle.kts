description = "Starter for Studio Platform Markdown Knowledge Pipeline"

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
    api(project(":studio-platform-markdown"))
    compileOnly(project(":studio-platform-autoconfigure"))
    compileOnly(project(":studio-platform-document-convert"))
    compileOnly(project(":studio-platform-textract"))
    compileOnly("org.apache.pdfbox:pdfbox:${property("apachePdfBoxVersion")}")
    compileOnly(project(":studio-platform-ai"))
    compileOnly(project(":studio-platform-chunking"))
    compileOnly(project(":studio-platform-skillgraph"))
    compileOnly(project(":studio-application-modules:attachment-service"))
    compileOnly("org.springframework.boot:spring-boot-starter")
    compileOnly("org.springframework.boot:spring-boot-starter-web")
    compileOnly("org.springframework.boot:spring-boot-starter-security")
    compileOnly("org.springframework.boot:spring-boot-starter-validation")
    compileOnly("org.springframework:spring-jdbc")
    compileOnly("org.springframework:spring-tx")

    testImplementation(project(":studio-platform-document-convert"))
    testImplementation(project(":studio-platform-textract"))
    testImplementation("org.apache.pdfbox:pdfbox:${property("apachePdfBoxVersion")}")
    testImplementation(project(":studio-platform-ai"))
    testImplementation(project(":studio-platform-chunking"))
    testImplementation(project(":studio-platform-skillgraph"))
    testImplementation(project(":studio-application-modules:attachment-service"))
    testImplementation("org.springframework.boot:spring-boot-test")
    testImplementation("org.springframework.boot:spring-boot-starter-web")
    testImplementation("org.springframework.boot:spring-boot-starter-security")
    testImplementation("org.springframework:spring-jdbc")
    testImplementation("org.mockito:mockito-core")
}
