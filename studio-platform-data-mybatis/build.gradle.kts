plugins {
    id("java-library")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("maven-publish")
}

description = "Studio Platform Data MyBatis support"

tasks.named<Jar>("jar") {
    enabled = true
    archiveClassifier.set("")
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    enabled = false
}

dependencies {
    api("org.mybatis:mybatis:${property("mybatisVersion")}")
    compileOnly("org.springframework.boot:spring-boot")
    testImplementation("org.springframework.boot:spring-boot")
}
