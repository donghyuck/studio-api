plugins {
    id("java-library")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("maven-publish")
}
description = "Starter for using Studio One Platform Stroage OCI"
tasks.named<Jar>("jar") {
    enabled = true
    archiveClassifier.set("")
}
tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    enabled = false
}
val awssdkS3Version: String = project.findProperty("awssdkS3Version") as String? ?: "2.37.3"
dependencies {  
    implementation("com.oracle.oci.sdk:oci-java-sdk:${project.findProperty("oracleOciSdkVersion")}")
    implementation("com.oracle.oci.sdk:oci-java-sdk-objectstorage:${project.findProperty("oracleOciSdkVersion")}")
    implementation("com.oracle.oci.sdk:oci-java-sdk-common-httpclient-jersey:${project.findProperty("oracleOciSdkVersion")}")
}