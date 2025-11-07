plugins {
    id("java-library")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("maven-publish")
}
description = "Starter for using Studio One Platform Stroage AWS"
tasks.named<Jar>("jar") {
    enabled = true
    archiveClassifier.set("")
}
tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    enabled = false
}
val awssdkS3Version: String = project.findProperty("awssdkS3Version") as String? ?: "2.37.3"
dependencies { 
    implementation ("software.amazon.awssdk:s3:${awssdkS3Version}")
}