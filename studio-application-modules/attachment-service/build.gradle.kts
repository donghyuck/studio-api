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
    compileOnly("org.springframework.boot:spring-boot-starter-data-jpa")
    compileOnly("org.springframework.boot:spring-boot-starter-security")
    compileOnly(project(":studio-platform")) 
    compileOnly(project(":studio-platform-objecttype"))
    compileOnly(project(":studio-platform-identity"))
    compileOnly(project(":studio-platform-data")) 
    compileOnly(project(":studio-platform-textract"))
    api(project(":studio-platform-thumbnail"))

    testImplementation("org.mockito:mockito-core")
    testImplementation("org.mockito:mockito-junit-jupiter")
    testImplementation("org.springframework.boot:spring-boot-starter-web")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa")
    testImplementation("org.springframework.data:spring-data-commons")
    testImplementation("org.springframework.security:spring-security-core")
    testImplementation("org.apache.pdfbox:pdfbox:${property("apachePdfBoxVersion")}")
    testImplementation(project(":studio-platform"))
    testImplementation(project(":studio-platform-data"))
    testImplementation(project(":studio-platform-textract"))
    testImplementation(project(":studio-platform-thumbnail"))
    testImplementation(project(":studio-platform-identity"))
    testImplementation(project(":studio-platform-objecttype"))
}
