plugins {
    id("java-library")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}
tasks.named<Jar>("jar") {
    enabled = true
}
tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    enabled = false
}

val egovframeVersion: String = project.findProperty("egovframeVersion") as String? ?: "4.3.0"

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    api("commons-io:commons-io:${project.findProperty("apacheCommonsIoVersion")}")
    api("org.apache.commons:commons-lang3:${project.findProperty("apacheCommonsLang3Version")}")
    implementation("org.egovframe.rte:org.egovframe.rte.psl.dataaccess:$egovframeVersion") {
        exclude(group = "org.apache.logging.log4j", module = "log4j-slf4j-impl")
        exclude(group = "org.apache.logging.log4j", module = "log4j-core")
    }
    implementation("org.mybatis:mybatis:${project.findProperty("mybatisVersion")}")
    compileOnly("org.springframework.boot:spring-boot-starter-jdbc")
}
