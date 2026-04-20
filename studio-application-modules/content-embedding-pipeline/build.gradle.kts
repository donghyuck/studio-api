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
    compileOnly("org.springframework.boot:spring-boot-starter-web")
    compileOnly("jakarta.validation:jakarta.validation-api")
    compileOnly("org.springframework.boot:spring-boot-starter-data-jpa")
    compileOnly("org.springframework.boot:spring-boot-starter-security")
    compileOnly(project(":studio-platform")) 
    compileOnly(project(":studio-platform-data")) 
    compileOnly(project(":studio-platform-textract"))
    compileOnly(project(":studio-platform-user")) 
    compileOnly(project(":studio-platform-security"))  
    compileOnly(project(":studio-platform-ai"))  
    compileOnly(project(":studio-application-modules:attachment-service"))

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-validation")
    testImplementation("org.springframework.boot:spring-boot-starter-web")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa")
    testImplementation("org.springframework.boot:spring-boot-starter-security")
    testImplementation(project(":studio-platform"))
    testImplementation(project(":studio-platform-data"))
    testImplementation(project(":studio-platform-textract"))
    testImplementation(project(":studio-platform-ai"))
    testImplementation(project(":studio-application-modules:attachment-service"))
}
