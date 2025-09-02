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
    compileOnly(project(":studio-platform"))
    compileOnly("org.springframework.boot:spring-boot-starter-web")
    compileOnly("org.springframework.boot:spring-boot-starter-validation")
}