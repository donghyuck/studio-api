plugins {
    id("java-library")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("maven-publish")
}
description = "Starter for Studio Platform Realtime (WebSocket/STOMP + Redis)"
tasks.named<Jar>("jar") {
    enabled = true
    archiveClassifier.set("")
}
tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    enabled = false
}

dependencies { 
    implementation(project(":studio-platform-autoconfigure"))
    implementation(project(":starter:studio-platform-starter"))
    api(project(":studio-platform-realtime")) 
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    compileOnly("org.springframework.boot:spring-boot-starter-security")
    compileOnly(project(":studio-platform-security"))  
}
