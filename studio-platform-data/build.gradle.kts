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
    implementation(project(":studio-platform"))
    compileOnly("org.springframework.boot:spring-boot-starter-web")
    compileOnly("org.springframework.boot:spring-boot-starter-data-jpa")  
    implementation ("org.freemarker:freemarker:${property("freemarkerVersion")}")  
    compileOnly("org.apache.pdfbox:pdfbox:${property("apachePdfBoxVersion")}")
    compileOnly("net.sourceforge.tess4j:tess4j:${property("tesseractVersion")}")
    compileOnly("org.jsoup:jsoup:${property("jsoupVersion")}")
    compileOnly("org.apache.poi:poi-ooxml:${property("apachePoiVersion")}")
    compileOnly("org.apache.poi:poi:${property("apachePoiVersion")}")

}