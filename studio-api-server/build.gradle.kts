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

    implementation(project(":studio-api-core"))
    implementation(project(":studio-api-user"))
    implementation(project(":studio-api-web"))

    // eGovFrame runtime dependencies 
    // Note: Exclude log4j dependencies to avoid conflicts with Spring Boot's default logging (logback)
    //implementation("org.egovframe.rte:org.egovframe.rte.ptl.mvc:$egovframeVersion") 
    implementation("org.egovframe.rte:org.egovframe.rte.psl.dataaccess:$egovframeVersion") {
        exclude(group = "org.egovframe.rte", module = "org.egovframe.rte.fdl.logging")
        exclude(group = "org.apache.logging.log4j", module = "log4j-slf4j-impl")
        exclude(group = "org.apache.logging.log4j", module = "log4j-core")
    }
    implementation("org.egovframe.rte:org.egovframe.rte.fdl.idgnr:$egovframeVersion") {
        exclude(group = "org.apache.logging.log4j", module = "log4j-slf4j-impl")
        exclude(group = "org.apache.logging.log4j", module = "log4j-core")
    }
    implementation("org.egovframe.rte:org.egovframe.rte.fdl.property:$egovframeVersion"){
        exclude(group = "org.apache.logging.log4j", module = "log4j-slf4j-impl")
        exclude(group = "org.apache.logging.log4j", module = "log4j-core")
    }
    implementation("org.egovframe.rte:org.egovframe.rte.fdl.string:$egovframeVersion"){
        exclude(group = "org.apache.logging.log4j", module = "log4j-slf4j-impl")
        exclude(group = "org.apache.logging.log4j", module = "log4j-core")
    }
    implementation("org.egovframe.rte:org.egovframe.rte.fdl.crypto:$egovframeVersion"){
        exclude(group = "org.apache.logging.log4j", module = "log4j-slf4j-impl")
        exclude(group = "org.apache.logging.log4j", module = "log4j-core")
    }
    implementation("org.egovframe.rte:org.egovframe.rte.fdl.security:$egovframeVersion"){
        exclude(group = "org.apache.logging.log4j", module = "log4j-slf4j-impl")
        exclude(group = "org.apache.logging.log4j", module = "log4j-core")
    }
    
    implementation("org.egovframe.boot:org.egovframe.crypto.spring.boot.starter:1.0.0")

    implementation("org.springframework.boot:spring-boot-starter-logging")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.mybatis:mybatis-spring:${project.findProperty("mybatisSpringVersion")}")

    // datasource log
    implementation("org.bgee.log4jdbc-log4j2:log4jdbc-log4j2-jdbc4.1:${project.findProperty("log4jdbcLog4j2Version")}")

    // database driver
    implementation("org.postgresql:postgresql:${project.findProperty("postgresqlVersion")}")    
}

tasks.getByName<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    enabled = isDev
}
tasks.getByName<org.springframework.boot.gradle.tasks.bundling.BootWar>("bootWar") {
    enabled = !isDev
}
tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
     jvmArgs = listOf("-Dspring.profiles.active=${profile}")
}