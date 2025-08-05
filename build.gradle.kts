plugins {
	java
	id("org.springframework.boot") apply false
    id("io.spring.dependency-management") apply false
	id("org.owasp.dependencycheck") apply false
}

allprojects {
	group = project.findProperty("buildApplicationGroup") as String
	version = project.findProperty("buildApplicationVersion") as String
    description = project.findProperty("buildApplicationName") as? String ?: project.name
	repositories {
        mavenCentral()
        mavenLocal()
		maven { url = uri("https://maven.egovframe.go.kr/maven/") }
    }
}

subprojects {
	apply(plugin = "java")
	apply(plugin = "org.owasp.dependencycheck")
	afterEvaluate{
		the<org.owasp.dependencycheck.gradle.extension.DependencyCheckExtension>().apply {
			autoUpdate = false  // 폐쇄망이므로 false
			failBuildOnCVSS = 10.0F // 7.0f // CVSS 점수가 7.0 이상인 경우 빌드 실패
			suppressionFile = "${rootDir}/dependency-check-suppressions.xml"
			scanSet = listOf(file("src/main/java")) // ⬅️ 소스 코드만 스캔
			analyzers.assemblyEnabled = false
			analyzers.nodeAuditEnabled = false 
			cveValidForHours = 72
    	}
	}
	java {
		val sourceCompatibilityValue = project.findProperty("sourceCompatibility") as String?
		sourceCompatibility = JavaVersion.toVersion(sourceCompatibilityValue ?: "1.8")
		targetCompatibility = JavaVersion.toVersion(sourceCompatibilityValue ?: "1.8")
	}
	configurations {
        compileOnly {
            extendsFrom(configurations.annotationProcessor.get())
        }
    }	
	tasks.named<Jar>("jar") {
    	enabled = true
    	manifest {
			attributes(
				"Implementation-Title" to description,
				"Implementation-Version" to version
			)
    	}
	}

	val lombokVersion: String = project.findProperty("lombokVersion") as String? ?: "1.18.30"

	dependencies {
		// 1. Lombok
        compileOnly("org.projectlombok:lombok:$lombokVersion")
        annotationProcessor("org.projectlombok:lombok:$lombokVersion")
		testCompileOnly("org.projectlombok:lombok:$lombokVersion")
        testAnnotationProcessor("org.projectlombok:lombok:$lombokVersion")
		// 2.MapStruct
		 
        // 3. srping boot test
		testImplementation("org.springframework.boot:spring-boot-starter-test") {
    		exclude(group = "org.mockito", module = "mockito-core")
		}

	}
	tasks.withType<Test> {
        useJUnitPlatform()
    }
}