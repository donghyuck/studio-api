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
include(":studio-platform")
include(":studio-platform-jpa")
include(":studio-platform-user")
include(":studio-platform-security") 
include(":studio-platform-security-acl") 
include(":studio-platform-storage")
include(":studio-platform-ai")
include(":studio-application-modules")
include(":studio-application-modules:avatar-service") 
include(":studio-application-modules:attachment-service") 
include(":studio-platform-autoconfigure")
include(":starter")
include(":starter:studio-platform-starter") 
include(":starter:studio-platform-starter-jasypt") 
include(":starter:studio-platform-starter-user") 
include(":starter:studio-platform-starter-security") 
include(":starter:studio-platform-starter-security-acl") 
include(":starter:studio-platform-starter-ai") 
include(":starter:studio-platform-starter-objectstorage") 
include(":starter:studio-platform-starter-objectstorage-aws") 
include(":starter:studio-platform-starter-objectstorage-oci") 
include(":starter:studio-application-starter-avatar")
include(":starter:studio-application-starter-attachment")
include(":studio-server")