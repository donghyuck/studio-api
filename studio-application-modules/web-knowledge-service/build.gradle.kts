description = "Studio Application Web Knowledge Service"

plugins {
    id("java-library")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("maven-publish")
}

group = project.findProperty("buildModulesGroup") as String

tasks.named<Jar>("jar") {
    enabled = true
    archiveClassifier.set("")
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    enabled = false
}

dependencies {
    api(project(":studio-platform"))
    api(project(":studio-platform-identity"))
    api(project(":studio-platform-workspace"))
    api(project(":studio-platform-ai"))
    api(project(":studio-platform-chunking"))
    implementation(project(":studio-platform-chunking-runtime"))
    implementation(project(":studio-platform-textract"))
    implementation("org.apache.httpcomponents.client5:httpclient5")
    implementation("org.jsoup:jsoup:${property("jsoupVersion")}")

    compileOnly("org.springframework.boot:spring-boot-starter-webmvc")
    compileOnly("org.springframework.boot:spring-boot-starter-data-jpa")
    compileOnly("org.springframework.boot:spring-boot-starter-security")
    compileOnly("org.springframework.boot:spring-boot-starter-validation")

    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito:mockito-core")
    testImplementation("org.mockito:mockito-junit-jupiter")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc")
    testImplementation("org.springframework.security:spring-security-core")
    testRuntimeOnly("com.h2database:h2")
}
