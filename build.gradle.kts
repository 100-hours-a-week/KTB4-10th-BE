plugins {
    java
    checkstyle
    id("org.springframework.boot") version "3.5.16"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.github.spotbugs") version "6.5.11"
}

group = "com.ktb10"
version = "0.0.1-SNAPSHOT"
description = "KGB Backend"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("com.the13haven:push2u-core:0.3.0")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.session:spring-session-data-redis")
    implementation("com.github.ben-manes.caffeine:caffeine")
    implementation(platform("software.amazon.awssdk:bom:2.55.10"))
    implementation("software.amazon.awssdk:s3")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-mysql")
    runtimeOnly("com.mysql:mysql-connector-j")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:mysql")
    testRuntimeOnly("com.h2database:h2")
    testCompileOnly("org.projectlombok:lombok")
    testAnnotationProcessor("org.projectlombok:lombok")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

checkstyle {
    toolVersion = "14.1.0"
    configFile = rootProject.file("config/checkstyle/checkstyle.xml")
    isIgnoreFailures = true
    isShowViolations = true
}

spotbugs {
    toolVersion = "4.9.8"
    excludeFilter = rootProject.file("config/spotbugs/excludeFilter.xml")
    ignoreFailures = false
}

tasks.named("checkstyleTest") {
    enabled = false
}

tasks.named("spotbugsTest") {
    enabled = false
}

tasks.named<com.github.spotbugs.snom.SpotBugsTask>("spotbugsMain") {
    reports.create("html") {
        required = true
        outputLocation = layout.buildDirectory.file("reports/spotbugs/main.html")
        setStylesheet("fancy-hist.xsl")
    }
}
