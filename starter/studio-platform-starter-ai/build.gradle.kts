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
    implementation(project(":studio-platform-autoconfigure"))
    compileOnly(project(":starter:studio-platform-starter"))
    api(project(":studio-platform-ai"))
    api(project(":studio-platform-chunking"))
    compileOnly("org.springframework:spring-jdbc")
    compileOnly("org.mybatis.spring.boot:mybatis-spring-boot-starter:${property("mybatisSpringBootStarterVersion")}")

    implementation("dev.langchain4j:langchain4j:${property("langchain4jVersion")}")
    implementation("dev.langchain4j:langchain4j-open-ai:${property("langchain4jVersion")}")
    implementation("dev.langchain4j:langchain4j-ollama:${property("langchain4jVersion")}")
    implementation("dev.langchain4j:langchain4j-google-ai-gemini:${property("langchain4jVersion")}")

    implementation("com.pgvector:pgvector:${property("pgvectorVersion")}")
    implementation("com.github.ben-manes.caffeine:caffeine:${property("caffeineVersion")}")
    implementation("io.github.resilience4j:resilience4j-retry:${property("resilience4jVersion")}")
    implementation("com.github.spullara.mustache.java:compiler:${property("mustacheVersion")}")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    compileOnly("org.springframework:spring-web")

    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito:mockito-core")
    testImplementation("org.assertj:assertj-core")
    testImplementation("org.springframework:spring-web")
    testImplementation("org.springframework:spring-jdbc")
    testImplementation(project(":starter:studio-platform-starter-mybatis"))
    testImplementation("org.mybatis.spring.boot:mybatis-spring-boot-starter:${property("mybatisSpringBootStarterVersion")}")
    testImplementation("org.testcontainers:junit-jupiter:${property("testcontainersVersion")}")
    testImplementation("org.testcontainers:postgresql:${property("testcontainersVersion")}")
    testImplementation("com.h2database:h2")
    testImplementation(project(":studio-platform"))
    testImplementation(project(":starter:studio-platform-starter-chunking"))
    testRuntimeOnly("org.postgresql:postgresql")
}
