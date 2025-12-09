plugins {
    id("java-library")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("maven-publish")        
}
group = project.findProperty("buildModulesGroup") as String
tasks.named<Jar>("jar") {
    enabled = true
    archiveClassifier.set("")        
}
tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    enabled = false
}

val mapstructVersion: String = project.findProperty("mapstructVersion") as String? ?: "0.11.5"
dependencies { 
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation(project(":studio-platform")) 
    implementation(project(":studio-platform-user")) 
    implementation(project(":studio-platform-data"))
    implementation("com.sun.mail:jakarta.mail:2.0.1")
}
