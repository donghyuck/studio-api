plugins {
	java
	id("org.springframework.boot") apply false
    id("io.spring.dependency-management") apply false
	id("org.owasp.dependencycheck") apply false
	id("org.sonarqube") version "5.0.0.4638" apply false   
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

fun Project.hasAnySource(): Boolean {
    fun hasFiles(dir: String) =
        file(dir).exists() && file(dir).walk()
            .filter { it.isFile }
            .any { it.extension in setOf("java", "kt", "kts") }
    return hasFiles("src/main/java") ||
           hasFiles("src/main/kotlin") ||
           hasFiles("src/test/java") ||
           hasFiles("src/test/kotlin")
}

val skipPaths = setOf(":base", ":core")
val sourceCompatibilityValue = project.findProperty("sourceCompatibility") as String?
val toolchainVersion = (findProperty("java.toolchain") as String?)?.toInt() ?: 11
val javaRelease      = (findProperty("java.release")   as String?)?.toInt() ?: 11
val lombokVersion: String = project.findProperty("lombokVersion") as String? ?: "1.18.30"
logger.lifecycle(" ========= JAVA RELEASE ========== ${javaRelease}")
subprojects {
	logger.lifecycle(" ==================== ${project.path}")
	apply(plugin = "java")
	apply(plugin = "org.owasp.dependencycheck")
	if (project.path !in skipPaths) {
        plugins.apply("org.sonarqube")
        tasks.matching { it.name == "sonarqube" }.configureEach {
            onlyIf { hasAnySource() } // 소스가 없으면 자동 스킵
        }
    }
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
		toolchain.languageVersion.set(JavaLanguageVersion.of(toolchainVersion))
        sourceCompatibility = JavaVersion.toVersion(sourceCompatibilityValue ?: "11")
		targetCompatibility = JavaVersion.toVersion(sourceCompatibilityValue ?: "11")
        withSourcesJar()
       // withJavadocJar() 
	}
	tasks.withType<JavaCompile>().configureEach { 
        options.release.set(javaRelease)
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
	val publishable = true // 필요 시 특정 모듈만 배포하려면 여기서 조건부로 조정
    if (publishable) {
        apply(plugin = "maven-publish")
        plugins.withId("java") {
            apply(plugin = "maven-publish")
            extensions.configure<PublishingExtension>("publishing") {
                publications {
                    create<MavenPublication>("mavenJava") {
                        // java 컴포넌트가 없는 모듈에서의 예외 방지
                        val javaComponent = components.findByName("java")
                        requireNotNull(javaComponent) { "Java component not found in project ${project.path}" }
                        from(javaComponent)
                        pom {
                            name.set(project.name)
                            description.set(project.description ?: project.name)
                            url.set(findProperty("pom.url") as String? ?: "https://example.org")
                            licenses {
                                license {
                                    name.set(findProperty("pom.licenseName") as String? ?: "Apache-2.0")
                                    url.set(findProperty("pom.licenseUrl") as String? ?: "https://www.apache.org/licenses/LICENSE-2.0")
                                }
                            }
                            scm {
                                url.set(findProperty("pom.scmUrl") as String? ?: "https://example.org/scm")
                                connection.set(findProperty("pom.scmConnection") as String? ?: "scm:git:https://example.org/repo.git")
                                developerConnection.set(findProperty("pom.scmDevConnection") as String? ?: "scm:git:ssh://git@example.org/repo.git")
                            }
                        }
                    }
                }
                repositories {
                    val isSnapshot = version.toString().endsWith("SNAPSHOT")
                    maven {
                        name = "Nexus"
						isAllowInsecureProtocol = (findProperty("nexus.allowInsecure") as String?)?.toBoolean() ?: false
                        url = uri(
                            if (isSnapshot)
                                (findProperty("nexus.snapshotsUrl") as String??: "http://localhost:8081/repository/maven-snapshots/")
                            else
                                (findProperty("nexus.releasesUrl") as String??: "http://localhost:8081/repository/maven-releases/")
                        )
                        credentials {
                            username = (findProperty("nexus.username") as String?) ?: System.getenv("NEXUS_USERNAME")
                            password = (findProperty("nexus.password") as String?) ?: System.getenv("NEXUS_PASSWORD")
                        }
                    }
                }
            }
        }
    }
}