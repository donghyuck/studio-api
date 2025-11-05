plugins {
    id("java-library")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("maven-publish")
}
description = "Starter for using Studio Application Module Avatar Service"
tasks.named<Jar>("jar") {
    enabled = true
    archiveClassifier.set("")
}
tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    enabled = false
}
val awssdkS3Version: String = project.findProperty("awssdkS3Version") as String? ?: "2.37.3"

dependencies { 

    implementation(project(":studio-platform-user"))
    implementation(project(":studio-platform-autoconfigure"))
    implementation(project(":studio-platform-objectstorage"))
    implementation(project(":starter:studio-platform-starter"))
    
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation ("software.amazon.awssdk:s3:${awssdkS3Version}")

    //implementation("com.oracle.oci.sdk:oci-java-sdk:${project.findProperty("oracleOciSdkVersion")}")
    //implementation("com.oracle.oci.sdk:oci-java-sdk-objectstorage:${project.findProperty("oracleOciSdkVersion")}")
    //implementation("com.oracle.oci.sdk:oci-java-sdk-common-httpclient-jersey:${project.findProperty("oracleOciSdkVersion")}")
}