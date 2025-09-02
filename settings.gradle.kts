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
 
include("studio-platform")
include("studio-platform-jpa")
include("studio-platform-user")
include("studio-platform-security") 
include("studio-platform-autoconfigure")


include("starter")
include("starter:studio-platform-starter") 
include("starter:studio-platform-starter-jasypt") 
include("starter:studio-platform-starter-user") 
include("starter:studio-platform-starter-security") 
include("studio-server")


