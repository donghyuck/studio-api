plugins {
    id("java-library")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("maven-publish")
}
group = project.findProperty("buildStarterGroup") as String
description = "Starter for using Studio Platform"
tasks.named<Jar>("jar") {
    enabled = true
    archiveClassifier.set("")
}
tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    enabled = false
}
java { 
    withSourcesJar() 
}
dependencies {
    api(project(":studio-platform"))
    api(project(":studio-platform-data"))
    api(project(":starter:studio-platform-textract-starter"))
    api(project(":starter:studio-platform-thumbnail-starter"))
    api(project(":studio-platform-autoconfigure")) 
    compileOnly("org.springframework.boot:spring-boot-starter-webmvc")
    compileOnly("org.springframework.boot:spring-boot-starter-data-jpa")
    compileOnly("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    testImplementation("org.springframework:spring-jdbc")
    testImplementation("org.springframework.data:spring-data-commons")
    testImplementation(project(":studio-platform-ai"))
    testImplementation(project(":studio-platform-document-convert"))
    testImplementation(project(":studio-platform-markdown"))
    testImplementation(project(":studio-platform-objecttype"))
    testImplementation(project(":studio-platform-security"))
    testImplementation(project(":studio-platform-security-acl"))
    testImplementation(project(":studio-platform-skillgraph"))
    testImplementation(project(":studio-platform-user-default"))
    testImplementation(project(":studio-platform-workspace-default"))
    testImplementation(project(":studio-application-modules:attachment-service"))
    testImplementation(project(":studio-application-modules:avatar-service"))
    testImplementation(project(":studio-application-modules:mail-service"))
    testImplementation(project(":studio-application-modules:template-service"))
    testImplementation(project(":studio-application-modules:wiki-service"))
    testImplementation("org.testcontainers:testcontainers-junit-jupiter:${property("testcontainersVersion")}")
    testImplementation("org.testcontainers:testcontainers-mariadb:${property("testcontainersVersion")}")
    testImplementation("org.testcontainers:testcontainers-mysql:${property("testcontainersVersion")}")
    testImplementation("org.testcontainers:testcontainers-postgresql:${property("testcontainersVersion")}")
    testImplementation("org.flywaydb:flyway-core")
    testImplementation("org.flywaydb:flyway-database-postgresql")
    testImplementation("org.flywaydb:flyway-mysql")
    testRuntimeOnly("org.mariadb.jdbc:mariadb-java-client")
    testRuntimeOnly("com.mysql:mysql-connector-j")
    testRuntimeOnly("org.postgresql:postgresql")
}
