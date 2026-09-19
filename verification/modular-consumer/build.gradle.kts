plugins { java }

val modules = providers.gradleProperty("moduleSet").getOrElse("base")
val studioVersion = providers.gradleProperty("studioVersion").getOrElse("3.0.0-rc.1")
val withTeam = modules in listOf("team", "workspace", "team-ai", "full", "rag-minimal")
val withWorkspace = modules in listOf("workspace", "full", "rag-minimal")
val withAi = modules in listOf("ai", "team-ai", "full", "rag-minimal")
require(modules in listOf("base", "team", "workspace", "ai", "team-ai", "full", "rag-minimal"))

repositories {
    maven {
        url = uri(providers.gradleProperty("studioRepository").get())
        metadataSources { mavenPom(); artifact(); ignoreGradleMetadataRedirection() }
    }
    mavenCentral()
}
java { toolchain { languageVersion.set(JavaLanguageVersion.of(17)) } }
dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:4.1.0"))
    implementation("studio.one.starter:studio-platform-starter:$studioVersion")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    if (withTeam) {
        implementation("studio.one.starter:studio-platform-starter-team:$studioVersion")
        implementation("org.springframework.boot:spring-boot-starter-data-jpa")
        runtimeOnly("com.h2database:h2")
    }
    if (withWorkspace) implementation("studio.one.starter:studio-platform-starter-workspace:$studioVersion")
    if (withAi) {
        implementation("studio.one.starter:studio-platform-starter-ai-web:$studioVersion")
        // Web adapter infrastructure is explicitly supplied by the consumer.
        implementation("org.springframework:spring-jdbc")
        implementation("org.springframework.data:spring-data-commons")
        implementation("studio.one.api:studio-platform-ai-model-catalog:$studioVersion")
    }
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.mockito:mockito-core")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
tasks.test {
    useJUnitPlatform()
    systemProperty("moduleSet", modules)
    outputs.upToDateWhen { false }
}
