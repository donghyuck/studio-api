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
 
dependencies { 
    compileOnly("org.springframework.boot:spring-boot-starter-web")
    compileOnly("org.springframework.boot:spring-boot-starter-data-jpa")
    compileOnly("org.springframework.boot:spring-boot-starter-security")
    compileOnly("org.springframework.boot:spring-boot-starter-validation")
    compileOnly ("org.freemarker:freemarker:2.3.32")  
    compileOnly(project(":studio-platform"))  
    compileOnly(project(":studio-platform-data")) 
    compileOnly(project(":studio-platform-identity")) 
    compileOnly(project(":studio-platform-user")) 
    compileOnly(project(":studio-platform-security")) 
}