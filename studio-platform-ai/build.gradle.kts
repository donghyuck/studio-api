plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

java {
    withSourcesJar()
}

dependencyManagement {
    imports {
        mavenBom("io.github.resilience4j:resilience4j-bom:${property("resilience4jVersion")}")
    }
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
    
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework:spring-jdbc")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.pgvector:pgvector:${property("pgvectorVersion")}")

    implementation("dev.langchain4j:langchain4j:${property("langchain4jVersion")}")
    implementation("dev.langchain4j:langchain4j-open-ai:${property("langchain4jVersion")}")
    implementation("dev.langchain4j:langchain4j-ollama:${property("langchain4jVersion")}")
    implementation("dev.langchain4j:langchain4j-google-ai-gemini:${property("langchain4jVersion")}")
    implementation("com.github.ben-manes.caffeine:caffeine:${property("caffeineVersion")}")

    implementation("io.github.resilience4j:resilience4j-spring-boot2:${property("resilience4jVersion")}")
    
    runtimeOnly("org.postgresql:postgresql:${property("postgresqlVersion")}")
    
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.mockito:mockito-core")
    testImplementation("org.testcontainers:postgresql:${property("testcontainersVersion")}")
    testImplementation("org.testcontainers:junit-jupiter:${property("testcontainersVersion")}")

}
