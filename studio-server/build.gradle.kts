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
     
    implementation(project(":core:platform-starter"))
    implementation("org.springframework.boot:spring-boot-starter-logging")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    
    // database driver
    implementation("org.postgresql:postgresql:${project.findProperty("postgresqlVersion")}")    
    implementation("org.bgee.log4jdbc-log4j2:log4jdbc-log4j2-jdbc4.1:${project.findProperty("log4jdbcLog4j2Version")}")

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