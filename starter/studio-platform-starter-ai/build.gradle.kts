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
    // BOM as api: consumers inherit Spring AI version management without declaring it separately
    api(platform("org.springframework.ai:spring-ai-bom:1.1.2"))
    compileOnly(project(":studio-platform-autoconfigure"))
    compileOnly(project(":starter:studio-platform-starter"))
    api(project(":studio-platform-ai"))
    api(project(":studio-platform-chunking"))
    compileOnly("org.springframework:spring-jdbc")

    // Provider libraries: compileOnly so they do NOT become transitive dependencies.
    // Consumers must declare the provider library they need explicitly.
    compileOnly("org.springframework.ai:spring-ai-starter-model-openai")
    compileOnly("org.springframework.ai:spring-ai-google-genai")
    compileOnly("org.springframework.ai:spring-ai-ollama")
    compileOnly("org.springframework.ai:spring-ai-google-genai-embedding")

    implementation("com.pgvector:pgvector:${property("pgvectorVersion")}")
    implementation("com.github.ben-manes.caffeine:caffeine:${property("caffeineVersion")}")
    implementation("io.github.resilience4j:resilience4j-retry:${property("resilience4jVersion")}")
    implementation("com.github.spullara.mustache.java:compiler:${property("mustacheVersion")}")
    implementation("com.fasterxml.jackson.core:jackson-databind")

    // Tests need the provider libraries at runtime to exercise the factory implementations
    testImplementation("org.springframework.ai:spring-ai-starter-model-openai")
    testImplementation("org.springframework.ai:spring-ai-google-genai")
    testImplementation("org.springframework.ai:spring-ai-ollama")
    testImplementation("org.springframework.ai:spring-ai-google-genai-embedding")
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito:mockito-core")
    testImplementation("org.assertj:assertj-core")
    testImplementation("org.springframework:spring-jdbc")
    testImplementation("com.h2database:h2")
    testImplementation(project(":studio-platform"))
    testImplementation(project(":starter:studio-platform-starter-chunking"))
} 
