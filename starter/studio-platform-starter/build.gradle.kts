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
    compileOnly("org.springframework.boot:spring-boot-starter-web")
    compileOnly("org.springframework.boot:spring-boot-starter-data-jpa")
    compileOnly("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    testImplementation("org.springframework:spring-jdbc")
    testImplementation("org.springframework.data:spring-data-commons")
}
