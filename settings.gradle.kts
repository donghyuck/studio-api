pluginManagement {
    val springBootVersion: String by settings
    val springDependencyManagementVersion: String by settings
    val owaspDependencycheckVersion: String by settings
    plugins {
        id("org.springframework.boot") version springBootVersion
        id("io.spring.dependency-management") version springDependencyManagementVersion
        id("org.owasp.dependencycheck")  version owaspDependencycheckVersion
    }
    repositories {
        gradlePluginPortal()
        mavenCentral()
        mavenLocal()
    }
}
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        mavenLocal()
        maven { url = uri("https://maven.egovframe.go.kr/maven/") }
    }
}
rootProject.name = providers.gradleProperty("buildApplicationName").get()
logger.lifecycle("🛠 ${rootProject.name} 프로젝트의 설정과 구성 정보를 정의")

include("core")
include("core:platform")
include("core:platform-jpa")
include("core:platform-starter")
include("studio-server")

include("studio-api-core")
include("studio-api-user")
include("studio-api-web")
include("studio-api-server")

