plugins {
    id("java-library")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("maven-publish")
}

description = "Web starter for using Studio Platform AI"

tasks.named<Jar>("jar") {
    enabled = true
    archiveClassifier.set("")
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    enabled = false
}

dependencies {
    api(project(":starter:studio-platform-starter-ai"))
    implementation(project(":studio-platform"))
    compileOnly(project(":studio-platform-realtime"))

    compileOnly("org.springframework.boot:spring-boot-starter-webmvc")
    compileOnly("org.springframework.boot:spring-boot-starter-validation")
    compileOnly("org.springframework.boot:spring-boot-starter-security")
    compileOnly("org.springframework:spring-jdbc")
    compileOnly("org.springframework.data:spring-data-commons")
    compileOnly("org.springframework.boot:spring-boot-starter-data-redis")
    compileOnly("tools.jackson.core:jackson-databind")
    compileOnly("io.micrometer:micrometer-core")
    implementation("com.github.ben-manes.caffeine:caffeine:${property("caffeineVersion")}")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.mockito:mockito-core")
    testImplementation(platform("org.springframework.ai:spring-ai-bom:${property("springAiVersion")}"))
    testImplementation("org.springframework.ai:spring-ai-starter-model-openai")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc")
    testImplementation("org.springframework.boot:spring-boot-starter-security")
    testImplementation("org.springframework.boot:spring-boot-starter-validation")
    testImplementation("org.springframework.data:spring-data-commons")
    testImplementation("org.springframework.boot:spring-boot-starter-data-redis")
    testImplementation("io.micrometer:micrometer-core")
    testImplementation("org.testcontainers:testcontainers:${property("testcontainersVersion")}")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter:${property("testcontainersVersion")}")
    testImplementation(project(":studio-platform"))
}
