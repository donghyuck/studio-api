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
    implementation(project(":studio-platform"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    compileOnly("org.springframework.security:spring-security-oauth2-jose")
    compileOnly(project(":studio-platform-security")) // JwtTokenProvider for handshake (optional)
}
