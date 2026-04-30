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

    compileOnly("org.springframework.boot:spring-boot-starter-web")
    compileOnly("org.springframework.boot:spring-boot-starter-validation")
    compileOnly("org.springframework.boot:spring-boot-starter-security")
    compileOnly("org.springframework:spring-jdbc")
    compileOnly("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.github.ben-manes.caffeine:caffeine:${property("caffeineVersion")}")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.mockito:mockito-core")
    testImplementation(platform("org.springframework.ai:spring-ai-bom:1.1.2"))
    testImplementation("org.springframework.ai:spring-ai-starter-model-openai")
    testImplementation("org.springframework.boot:spring-boot-starter-web")
    testImplementation("org.springframework.boot:spring-boot-starter-security")
    testImplementation("org.springframework.boot:spring-boot-starter-validation")
    testImplementation(project(":studio-platform"))
}
