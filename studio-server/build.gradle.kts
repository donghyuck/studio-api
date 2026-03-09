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

    //////////////////////////////////////////////////////////////// 
    // Studio One Platform starters
    // 1. studio platform starter 
    implementation(project(":starter:studio-platform-starter")) 

    // 2. studio platform jasypt crypto starter
    implementation(project(":starter:studio-platform-starter-jasypt"))  
    
    // 3. studio platform user starter 
    implementation(project(":starter:studio-platform-starter-user")) 

    // 4. studio platform security starter
    implementation(project(":starter:studio-platform-starter-security")) 

    // 4. studio platform security acl starter
    implementation(project(":starter:studio-platform-starter-security-acl")) 

    // 5. studio platform ai starter
    implementation(project(":starter:studio-platform-starter-ai")) 

    // 6. studio platform objectstorage starter
    implementation(project(":starter:studio-platform-starter-objectstorage")) 
    implementation(project(":starter:studio-platform-starter-objectstorage-aws")) 
    
    // 7. studio platform realtime starter
    implementation(project(":starter:studio-platform-starter-realtime")) 
    implementation("org.springframework.boot:spring-boot-starter-websocket")

    //////////////////////////////////////////////////////////////// 
    // Studio One Application starters
    // 
    // 1. avatar module starter
    implementation(project(":starter:studio-application-starter-avatar"))
    // 2. attachment module starter
    //implementation(project(":starter:studio-application-starter-attachment"))
    // 3. mail moudele starter
    implementation(project(":starter:studio-application-starter-mail"))
    // 4. template module starter
    implementation(project(":starter:studio-application-starter-template"))
    // 5. embedding pipeline starter ( depends on : ai + attachment )
    //implementation(project(":studio-application-modules:content-embedding-pipeline"))

    // platform dependencies
    implementation("org.apache.pdfbox:pdfbox:${property("apachePdfBoxVersion")}")
    implementation("org.apache.poi:poi-ooxml:${property("apachePoiVersion")}")
    implementation("org.apache.poi:poi:${property("apachePoiVersion")}")

    //implementation(project(":studio-platform-user"))  
    //implementation(project(":studio-platform-security-acl"))  
    // srping starters
    implementation("org.springframework.boot:spring-boot-starter-logging")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.security:spring-security-crypto")
    implementation("org.springframework.boot:spring-boot-starter-mail")

    // database driver
    implementation("org.postgresql:postgresql:${project.findProperty("postgresqlVersion")}")    
    implementation("org.bgee.log4jdbc-log4j2:log4jdbc-log4j2-jdbc4.1:${project.findProperty("log4jdbcLog4j2Version")}")
    implementation("org.flywaydb:flyway-core")

    // 
    //implementation ("org.freemarker:freemarker:2.3.32")

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
        val jasyptPassword = (project.findProperty("JASYPT_ENCRYPTOR_PASSWORD") as String?)
            ?: System.getenv("JASYPT_ENCRYPTOR_PASSWORD")
        if (!jasyptPassword.isNullOrBlank()) {
            systemProperty("JASYPT_ENCRYPTOR_PASSWORD", jasyptPassword)
        }
    }
}
