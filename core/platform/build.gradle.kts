// core/platform/build.gradle.kts
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
    implementation("org.springframework.boot:spring-boot-starter-web")
    api("commons-io:commons-io:${project.findProperty("apacheCommonsIoVersion")}") 
    api("org.apache.commons:commons-lang3:${project.findProperty("apacheCommonsLang3Version")}")
}