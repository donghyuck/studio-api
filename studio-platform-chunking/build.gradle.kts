description = "Studio One Platform Chunking Contracts"

plugins {
    id("java-library")
    id("io.spring.dependency-management")
}

java {
    withSourcesJar()
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:${property("springBootVersion")}")
    }
}

dependencies {
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
