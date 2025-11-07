plugins {
    id("java-library")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("maven-publish")        
}
tasks.named<Jar>("jar") {
    enabled = true
    archiveClassifier.set("")        
}
tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    enabled = false
}
val awssdkS3Version: String = project.findProperty("awssdkS3Version") as String? ?: "2.37.3"
val oracleOciSdkVersion: String = project.findProperty("oracleOciSdkVersion") as String? ?: "3.44.1"
dependencies {
    implementation(project(":studio-platform")) 
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa") 
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-security")

    compileOnly ("software.amazon.awssdk:s3:$awssdkS3Version")
    compileOnly("com.oracle.oci.sdk:oci-java-sdk:$oracleOciSdkVersion")
    compileOnly("com.oracle.oci.sdk:oci-java-sdk-objectstorage:$oracleOciSdkVersion")
    compileOnly("com.oracle.oci.sdk:oci-java-sdk-common-httpclient-jersey:$oracleOciSdkVersion")  

}