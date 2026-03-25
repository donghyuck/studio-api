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
    implementation(project(":studio-platform-data")) 
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework:spring-jdbc")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.pgvector:pgvector:${property("pgvectorVersion")}")
    implementation("com.github.ben-manes.caffeine:caffeine:${property("caffeineVersion")}")
    implementation("io.github.resilience4j:resilience4j-retry:${property("resilience4jVersion")}")
    implementation("com.github.spullara.mustache.java:compiler:${property("mustacheVersion")}")

    runtimeOnly("org.postgresql:postgresql")    
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.mockito:mockito-core")
    testImplementation("org.testcontainers:postgresql:${property("testcontainersVersion")}")
    testImplementation("org.testcontainers:junit-jupiter:${property("testcontainersVersion")}")

}
