plugins {
    id("java-library")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("maven-publish")
}

description = "Studio Platform thumbnail generation SPI"

tasks.named<Jar>("jar") {
    enabled = true
    archiveClassifier.set("")
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    enabled = false
}

dependencies {
    api(project(":studio-platform"))
    compileOnly("org.apache.pdfbox:pdfbox:${property("apachePdfBoxVersion")}")
    compileOnly("org.apache.poi:poi-ooxml:${property("apachePoiVersion")}")

    testImplementation("org.apache.pdfbox:pdfbox:${property("apachePdfBoxVersion")}")
    testImplementation("org.apache.poi:poi-ooxml:${property("apachePoiVersion")}")
}
