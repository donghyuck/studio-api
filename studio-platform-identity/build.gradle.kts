description = "Studio One Platform Identity"

plugins {
    id("java-library")
    id("io.spring.dependency-management")
    id("maven-publish")
}

the<io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension>().apply {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:${property("springBootVersion")}")
    }
}

tasks.named<Jar>("jar") {
    enabled = true
    archiveClassifier.set("")
}

dependencies {
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
