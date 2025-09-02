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

val jsonwebtokenVersion: String = project.findProperty("jsonwebtokenVersion") as String? ?: "0.11.5"
dependencies {
    implementation("org.springframework.boot:spring-boot-starter") 
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation(project(":studio-platform")) 
    implementation(project(":studio-platform-user")) 
    api("io.jsonwebtoken:jjwt-api:$jsonwebtokenVersion")
    api("io.jsonwebtoken:jjwt-impl:$jsonwebtokenVersion")
    api("io.jsonwebtoken:jjwt-jackson:$jsonwebtokenVersion")    
}