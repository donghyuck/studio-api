plugins {
    id("java-library")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("maven-publish")
}
group = project.findProperty("buildStarterGroup") as String
description = "Starter for using Studio Application Module Template Service"
tasks.named<Jar>("jar") {
    enabled = true
    archiveClassifier.set("")
}
tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    enabled = false
}

dependencies {
    val freemarkerVersion: String = project.findProperty("freemarkerVersion") as String? ?: "2.3.34"
    implementation(project(":studio-platform-autoconfigure"))
    compileOnly(project(":starter:studio-platform-starter"))
    api(project(":studio-application-modules:template-service"))
    compileOnly("org.springframework.boot:spring-boot-starter-validation")
    compileOnly("org.springframework.boot:spring-boot-starter-data-jpa")
    compileOnly("org.springframework.boot:spring-boot-starter-web")
    compileOnly("org.freemarker:freemarker:$freemarkerVersion")

    testImplementation(project(":studio-platform-autoconfigure"))
    testImplementation(project(":studio-platform-data"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa")
    testImplementation("org.springframework.boot:spring-boot-starter-freemarker")
    testImplementation("org.springframework:spring-webmvc")
    testImplementation("org.springframework:spring-jdbc")
    testImplementation("org.springframework:spring-test")
    testImplementation("jakarta.servlet:jakarta.servlet-api")
    testImplementation("org.springframework.boot:spring-boot-starter-validation")
}
