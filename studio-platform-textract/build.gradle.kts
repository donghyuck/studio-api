plugins {
    id("java-library")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("maven-publish")
}

tasks.named<Jar>("jar") {
    enabled = true
    archiveClassifier.set("")
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    enabled = false
}

dependencies {
    api(project(":studio-platform"))
    compileOnly("org.springframework.boot:spring-boot-starter-web")
    compileOnly("org.apache.pdfbox:pdfbox:${property("apachePdfBoxVersion")}")
    compileOnly("net.sourceforge.tess4j:tess4j:${property("tesseractVersion")}")
    compileOnly("org.jsoup:jsoup:${property("jsoupVersion")}")
    compileOnly("org.apache.poi:poi-ooxml:${property("apachePoiVersion")}")
    compileOnly("org.apache.poi:poi:${property("apachePoiVersion")}")

    testImplementation("org.springframework.boot:spring-boot-starter-web")
    testImplementation("org.apache.pdfbox:pdfbox:${property("apachePdfBoxVersion")}")
    testImplementation("net.sourceforge.tess4j:tess4j:${property("tesseractVersion")}")
    testImplementation("org.jsoup:jsoup:${property("jsoupVersion")}")
    testImplementation("org.apache.poi:poi-ooxml:${property("apachePoiVersion")}")
    testImplementation("org.apache.poi:poi:${property("apachePoiVersion")}")
}
