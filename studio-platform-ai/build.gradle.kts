plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

java {
    withSourcesJar()
}

val bootJar = tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar")
bootJar.configure {
    enabled = false
}

tasks.named<org.gradle.jvm.tasks.Jar>("jar").configure {
    enabled = true
}

dependencies {
    implementation(project(":studio-platform")) 

}
