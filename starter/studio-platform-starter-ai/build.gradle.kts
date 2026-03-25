plugins {
    id("java-library")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("maven-publish")
}
description = "Starter for using Studio Platform AI"
tasks.named<Jar>("jar") {
    enabled = true
    archiveClassifier.set("")
}
tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    enabled = false
}

dependencies {  
    implementation(platform("org.springframework.ai:spring-ai-bom:1.1.2"))
    compileOnly(project(":studio-platform-autoconfigure")) 
    compileOnly(project(":starter:studio-platform-starter")) 
    api(project(":studio-platform-ai")) 
    compileOnly("org.springframework:spring-jdbc")
    
    implementation("com.pgvector:pgvector:${property("pgvectorVersion")}") 
    implementation("org.springframework.ai:spring-ai-starter-model-openai")
    implementation("org.springframework.ai:spring-ai-google-genai")
    implementation("org.springframework.ai:spring-ai-ollama")
    implementation("org.springframework.ai:spring-ai-google-genai-embedding")
    implementation("com.github.ben-manes.caffeine:caffeine:${property("caffeineVersion")}")
    implementation("io.github.resilience4j:resilience4j-spring-boot2:${property("resilience4jVersion")}")
    implementation("com.github.spullara.mustache.java:compiler:${property("mustacheVersion")}")

    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito:mockito-core")
    testImplementation(project(":studio-platform"))
} 
