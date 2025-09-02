plugins {
    id("java-library")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}
description = "Starter for using Studio Platform Jasypt"
tasks.named<Jar>("jar") {
    enabled = true
}
tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    enabled = false
}

dependencies {
    implementation(project(":starter:studio-platform-starter"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.jasypt:jasypt:1.9.3")
    implementation("org.bouncycastle:bcprov-jdk15to18:${project.findProperty("bouncycastleVersion")}")
}