plugins {
    java
    idea
    jacoco
    checkstyle
    `jvm-test-suite`
    `jacoco-report-aggregation`
    id("org.springframework.boot") version "3.4.2"
    id("io.spring.dependency-management") version "1.1.7"
}

checkstyle {
    toolVersion = "10.12.1"
    configFile = file("${rootDir}/config/checkstyle/checkstyle.xml")
    sourceSets = listOf(project.sourceSets.main.get())
}

val applicationVersion: String by project
val junitVersion: String by project
val junitPlatformVersion: String by project
val cucumberVersion: String by project
val burgerCommonsVersion: String by project

group = "pl.codehouse.restaurant"
version = applicationVersion

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
    maven {
        url = uri("https://maven.pkg.github.com/C0deH0use/burger-commons")
        credentials {
            username = project.findProperty("github.user") as String? ?: System.getenv("GITHUB_USER")
            password = project.findProperty("github.token") as String? ?: System.getenv("GITHUB_TOKEN")
        }
    }
}

idea {
    module {
        testSources.from(file("src/integrationTest/java"), file("src/test/java"))
        testResources.from(file("src/integrationTest/resources"), file("src/test/resources"))
    }
}

dependencies {
    runtimeOnly("org.postgresql:postgresql")
    runtimeOnly("org.postgresql:r2dbc-postgresql")

    implementation("pl.codehouse.commons:burger-commons:$burgerCommonsVersion")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")

    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")

    implementation("org.springframework.kafka:spring-kafka")
    developmentOnly("org.springframework.boot:spring-boot-devtools")

    implementation("org.apache.commons:commons-lang3:3.17.0")

    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
}

testing {
    suites {
        withType<JvmTestSuite> {
            useJUnitJupiter(junitVersion)
            dependencies {
                implementation("io.projectreactor:reactor-test")

                implementation("org.springframework.boot:spring-boot-starter-test") {
                    exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
                }
                implementation("org.springframework.boot:spring-boot-starter-webflux")
                implementation("org.springframework.boot:spring-boot-starter-data-jpa")
                implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")
                implementation("org.springframework.kafka:spring-kafka-test")

                implementation("org.junit.jupiter:junit-jupiter:$junitVersion")
                implementation("org.junit.platform:junit-platform-suite:$junitPlatformVersion")
                implementation("org.junit.platform:junit-platform-launcher:$junitPlatformVersion")

                implementation("io.cucumber:cucumber-java:$cucumberVersion")
                implementation("io.cucumber:cucumber-junit:$cucumberVersion")
                implementation("io.cucumber:cucumber-spring:$cucumberVersion")
                implementation("io.cucumber:cucumber-junit-platform-engine:$cucumberVersion")

                runtimeOnly("org.junit.platform:junit-platform-launcher:$junitPlatformVersion")
            }
        }

        val integrationTest by registering(JvmTestSuite::class) {
            testType.set(TestSuiteType.INTEGRATION_TEST)
            sources {
                java {
                    setSrcDirs(listOf("src/integrationTest/java"))
                }
                resources {
                    setSrcDirs(listOf("src/integrationTest/resources", "src/test/resources"))
                }
            }
            dependencies {
                implementation(project())
                implementation(sourceSets.test.get().output)
                implementation(sourceSets.test.get().runtimeClasspath)
                implementation(project.dependencies.platform("org.springframework.boot:spring-boot-dependencies:3.4.2"))

                implementation("org.flywaydb:flyway-core")
                implementation("org.flywaydb:flyway-database-postgresql")

                implementation("org.springframework.boot:spring-boot-testcontainers")

                implementation("org.testcontainers:junit-jupiter")
                implementation("org.testcontainers:kafka")
                implementation("org.testcontainers:postgresql")
                implementation("org.testcontainers:r2dbc")

                implementation("io.rest-assured:rest-assured:5.5.0")
                implementation("io.rest-assured:json-path:5.5.0")
                implementation("io.rest-assured:json-schema-validator:5.5.0")
                implementation("io.rest-assured:spring-web-test-client:5.5.0")
            }
        }
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
    systemProperty("cucumber.junit-platform.naming-strategy", "long")
    finalizedBy(tasks.jacocoTestReport)

    testLogging {
        events("passed", "skipped", "failed")
    }
}

tasks.named<Test>("integrationTest") {
    useJUnitPlatform()
    systemProperty("cucumber.junit-platform.naming-strategy", "long")
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    reports {
        xml.required = true
        csv.required = true
        html.outputLocation = layout.buildDirectory.dir("jacocoHtml")
    }
}

tasks.withType<JacocoReport>().configureEach {
    dependsOn(project.tasks.withType<Test>())
    // execution data needs to be aggregated from all exec files in the project for multi jvm test suite testing
    tasks.withType<Test>().forEach(::executionData) // confusing
}

tasks.withType<JacocoCoverageVerification>().configureEach {
    dependsOn(project.tasks.withType<JacocoReport>())
    // execution data needs to be aggregated from all exec files in the project for multi jvm test suite testing
    executionData(project.tasks.withType<JacocoReport>().map { it.executionData })
}

tasks.withType<Test>().configureEach {
    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).takeIf { it > 0 } ?: 1
    setForkEvery(100)
    reports.html.required.set(true)
}
