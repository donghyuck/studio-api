description = "Studio One Platform Chunking Runtime Implementations"

plugins {
    id("java-library")
    id("io.spring.dependency-management")
}

java {
    withSourcesJar()
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:${property("springBootVersion")}")
    }
}

dependencies {
    api(project(":studio-platform-chunking"))

    compileOnly(project(":studio-platform-ai"))
    compileOnly(project(":studio-platform-textract"))
    compileOnly("org.springframework.boot:spring-boot")

    implementation("com.knuddels:jtokkit:${property("jtokkitVersion")}")
    implementation("tools.jackson.core:jackson-databind")

    testImplementation(project(":studio-platform-ai"))
    testImplementation(project(":studio-platform-textract"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core")
    testImplementation("org.springframework.boot:spring-boot")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
