plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    war
    java
}

val profile = project.findProperty("profile") as String? ?: "dev"
val isDev = profile == "dev"
val egovframeVersion: String = project.findProperty("egovframeVersion") as String? ?: "4.3.0"

logger.lifecycle("📦 [PROFILE] = $profile (isDev=$isDev)")

dependencies {
    if (isDev) {
        implementation("org.springframework.boot:spring-boot-starter-tomcat")
    } else {
        providedRuntime("org.springframework.boot:spring-boot-starter-tomcat")
    }
     
    // platform starters
    implementation(project(":starter:studio-platform-starter")) 
    implementation(project(":starter:studio-platform-starter-jasypt"))  
    implementation(project(":starter:studio-platform-starter-user")) 
    implementation(project(":starter:studio-platform-starter-security")) 
    implementation(project(":starter:studio-platform-starter-security-acl")) 
    implementation(project(":starter:studio-platform-starter-ai")) 
    implementation(project(":starter:studio-platform-starter-objectstorage")) 
    implementation(project(":starter:studio-platform-starter-objectstorage-aws")) 
    implementation(project(":starter:studio-application-starter-avatar"))

    // platform modules
    
    //implementation(project(":studio-platform-user"))  
    //implementation(project(":studio-platform-security-acl"))  
    // srping starters
    implementation("org.springframework.boot:spring-boot-starter-logging")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.security:spring-security-crypto")

    // database driver
    implementation("org.postgresql:postgresql:${project.findProperty("postgresqlVersion")}")    
    implementation("org.bgee.log4jdbc-log4j2:log4jdbc-log4j2-jdbc4.1:${project.findProperty("log4jdbcLog4j2Version")}")
    implementation("org.flywaydb:flyway-core")

    // crypto
    //implementation("com.github.ulisesbocchio:jasypt-spring-boot-starter:${project.findProperty("jasyptVersion")}")

    //cache 
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("com.github.ben-manes.caffeine:caffeine:3.1.8") 
    
    // mamagement
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    //implementation ("software.amazon.awssdk:s3:${project.findProperty("awssdkS3Version")}") 

}

tasks.getByName<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    enabled = isDev
}
tasks.getByName<org.springframework.boot.gradle.tasks.bundling.BootWar>("bootWar") {
    enabled = !isDev
}
tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
     jvmArgs = listOf("-Dspring.profiles.active=${profile}")
     if (isDev) {
        systemProperty("JASYPT_ENCRYPTOR_PASSWORD", project.findProperty("JASYPT_ENCRYPTOR_PASSWORD") )
    }
}