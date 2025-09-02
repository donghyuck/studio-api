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

val mapstructVersion: String = project.findProperty("mapstructVersion") as String? ?: "0.11.5"
dependencies {
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation(project(":studio-platform")) 

    implementation ("org.mapstruct:mapstruct:$mapstructVersion")
    annotationProcessor ("org.mapstruct:mapstruct-processor:$mapstructVersion")
    annotationProcessor ("org.projectlombok:lombok-mapstruct-binding:0.2.0")
}