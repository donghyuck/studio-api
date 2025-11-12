plugins {
    id("java-library")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("maven-publish")
}
description = "Starter for using Studio Platform Security Acl"
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
    api(project(":studio-platform-ai")) 
    
    compileOnly("org.springframework.boot:spring-boot-starter-web")
    compileOnly("org.springframework.boot:spring-boot-starter-data-jpa")
    compileOnly("org.springframework.boot:spring-boot-starter-validation")
    compileOnly("org.springframework.boot:spring-boot-starter-security")
    compileOnly("org.springframework.security:spring-security-acl")

    implementation("com.pgvector:pgvector:${property("pgvectorVersion")}") 
    implementation("dev.langchain4j:langchain4j:${property("langchain4jVersion")}")
    implementation("dev.langchain4j:langchain4j-open-ai:${property("langchain4jVersion")}")
    implementation("dev.langchain4j:langchain4j-ollama:${property("langchain4jVersion")}")
    implementation("dev.langchain4j:langchain4j-google-ai-gemini:${property("langchain4jVersion")}")
    implementation("com.github.ben-manes.caffeine:caffeine:${property("caffeineVersion")}")
    implementation("io.github.resilience4j:resilience4j-spring-boot2:${property("resilience4jVersion")}")
}