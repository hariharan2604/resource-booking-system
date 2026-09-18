plugins {
    java
    jacoco

    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.sonarqube") version "6.3.1.5724"
}

group = "com.example"
version = "1.0.0"

description = "RESTful Resource Booking System with JWT auth and RBAC"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

extra["jjwtVersion"] = "0.12.6"
extra["springdocVersion"] = "3.1.1"

dependencies {

    // Actuator
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // Web / REST
    implementation("org.springframework.boot:spring-boot-starter-webmvc")

    // Cache
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")

    // JPA
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")

    // Security
    implementation("org.springframework.boot:spring-boot-starter-security")

    // Validation
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // JWT
    implementation("io.jsonwebtoken:jjwt-api:${property("jjwtVersion")}")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:${property("jjwtVersion")}")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:${property("jjwtVersion")}")

    // Database
    runtimeOnly("com.mysql:mysql-connector-j")
    runtimeOnly("com.h2database:h2")

    // OpenAPI / Swagger
    implementation(
        "org.springdoc:springdoc-openapi-starter-webmvc-ui:${property("springdocVersion")}"
    )

    // Lombok
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // Tests
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.security:spring-security-test")

    // Prometheus
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")
}

tasks.withType<Test> {
    useJUnitPlatform()
    jvmArgs(
        "-Djdk.attach.allowAttachSelf=true",
        "-XX:+EnableDynamicAgentLoading"
    )
    systemProperty("spring.profiles.active", "test")
}


tasks.jar {
    enabled = false
}

tasks.bootJar {
    archiveFileName = "resource-booking-system.jar"
}

jacoco {
    toolVersion = "0.8.12"
}

tasks.test {
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)

    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
}

sonar {
    properties {
        property("sonar.projectKey", "resource-booking-system")
        property("sonar.projectName", "Resource Booking System")
        property(
            "sonar.host.url",
            providers.gradleProperty("sonar.host.url")
                .orElse("https://sonarcloud.io")
                .get()
        )
        property("sonar.sourceEncoding", "UTF-8")
        property(
            "sonar.coverage.jacoco.xmlReportPaths",
            layout.buildDirectory.file("reports/jacoco/test/jacocoTestReport.xml").get().asFile.absolutePath
        )

        providers.gradleProperty("sonar.organization")
            .orElse(providers.environmentVariable("SONAR_ORGANIZATION"))
            .orNull
            ?.let { property("sonar.organization", it) }
    }
}

tasks.named("sonar") {
    dependsOn(tasks.jacocoTestReport)
}