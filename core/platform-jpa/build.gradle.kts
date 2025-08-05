//core/platform-jpa/build.gradle.kts
// platform/build.gradle.kts
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
    implementation(project(":core:platform"))
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
}