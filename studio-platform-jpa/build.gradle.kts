 plugins {
    id("java-library")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

tasks.named<Jar>("jar") {
    enabled = true
}
tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    enabled = false
}

dependencies {
    implementation(project(":studio-platform"))
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
}