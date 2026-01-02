plugins {
    id("java-library")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("maven-publish")
}
description = "Starter for using Studio Platform ObjectStorage"
tasks.named<Jar>("jar") {
    enabled = true
    archiveClassifier.set("")
}
tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    enabled = false
}
val awssdkS3Version: String = project.findProperty("awssdkS3Version") as String? ?: "2.37.3"

dependencies { 
    compileOnly(project(":studio-platform-user"))
    compileOnly(project(":studio-platform-autoconfigure"))
    implementation(project(":studio-platform-storage"))
    compileOnly(project(":starter:studio-platform-starter"))
    compileOnly("org.springframework.boot:spring-boot-starter-validation")
    compileOnly("org.springframework.boot:spring-boot-starter-data-jpa")
    compileOnly ("software.amazon.awssdk:s3:${awssdkS3Version}") 
    compileOnly("com.oracle.oci.sdk:oci-java-sdk:${project.findProperty("oracleOciSdkVersion")}")
    compileOnly("com.oracle.oci.sdk:oci-java-sdk-objectstorage:${project.findProperty("oracleOciSdkVersion")}")
    compileOnly("com.oracle.oci.sdk:oci-java-sdk-common-httpclient-jersey:${project.findProperty("oracleOciSdkVersion")}")
}