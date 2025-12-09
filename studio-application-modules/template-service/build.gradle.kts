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
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation ("org.freemarker:freemarker:2.3.32")  

    implementation(project(":studio-platform"))  
    implementation(project(":studio-platform-data")) 
    implementation(project(":studio-platform-user")) 
    
}