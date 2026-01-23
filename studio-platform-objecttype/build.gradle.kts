description = "Studio Platform ObjectType"

plugins {
    id("java-library")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("maven-publish")
}

tasks.named<Jar>("jar") {
    enabled = true
    archiveClassifier.set("")
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    enabled = false
}

dependencies {
    compileOnly(project(":studio-platform"))
    compileOnly(project(":studio-platform-data"))
    compileOnly("org.springframework.boot:spring-boot-starter")
    compileOnly("org.springframework.boot:spring-boot-starter-web")
    compileOnly("org.springframework.boot:spring-boot-starter-validation")
    compileOnly("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("com.github.ben-manes.caffeine:caffeine:${property("caffeineVersion")}")
    testImplementation("org.junit.jupiter:junit-jupiter-api")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
    testImplementation("org.mockito:mockito-core")
    testImplementation("org.springframework:spring-test")
    testImplementation("org.springframework:spring-jdbc")
    testImplementation(project(":studio-platform"))
    testImplementation(project(":studio-platform-data"))
    testImplementation("org.springframework:spring-web")
    testImplementation("org.springframework.data:spring-data-commons")
}
