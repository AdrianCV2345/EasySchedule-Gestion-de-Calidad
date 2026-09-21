plugins {
	java
	id("org.springframework.boot") version "3.5.13"
	id("io.spring.dependency-management") version "1.1.7"
	jacoco
	id("org.sonarqube") version "7.3.1.8318"
	pmd
}

group = "com.easyschedule"
version = "0.0.1-SNAPSHOT"
description = "Demo project for Spring Boot"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(17)
	}
}

repositories {
	mavenCentral()
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("com.github.librepdf:openpdf:1.3.33")
	developmentOnly("org.springframework.boot:spring-boot-devtools")
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testRuntimeOnly("com.h2database:h2")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	runtimeOnly("org.postgresql:postgresql")
	implementation("com.github.librepdf:openpdf:1.3.30")
	implementation("com.google.api-client:google-api-client:2.7.2")
	implementation("com.google.http-client:google-http-client-gson:1.45.0")
}

pmd {
	toolVersion = "7.13.0"
	rulesMinimumPriority = 5
}

tasks.withType<Test> {
	useJUnitPlatform()
}

tasks.withType<JavaCompile> {
	options.compilerArgs.add("-parameters")
}

tasks.jacocoTestReport {
	dependsOn(tasks.test)
	reports {
		xml.required.set(true)
		html.required.set(true)
		csv.required.set(false)
	}
}

tasks.jacocoTestCoverageVerification {
	dependsOn(tasks.test)
	violationRules {
		rule {
			limit {
				counter = "LINE"
				value = "COVEREDRATIO"
				minimum = "0.60".toBigDecimal()
			}
		}
	}
}

val registroCoverageIncludes = listOf(
	"com/easyschedule/backend/auth/**",
	"com/easyschedule/backend/shared/exception/**",
	"com/easyschedule/backend/shared/config/SecurityConfig*"
)

tasks.register<org.gradle.testing.jacoco.tasks.JacocoReport>("jacocoRegistroReport") {
	dependsOn(tasks.test)

	classDirectories.setFrom(
		sourceSets.main.get().output.asFileTree.matching {
			include(registroCoverageIncludes)
		}
	)
	sourceDirectories.setFrom(sourceSets.main.get().allSource.srcDirs)
	executionData.setFrom(fileTree(layout.buildDirectory).include("jacoco/test.exec", "jacoco/test*.exec"))

	reports {
		xml.required.set(true)
		xml.outputLocation.set(layout.buildDirectory.file("reports/jacoco/registro/jacocoRegistroReport.xml"))
		html.required.set(true)
		html.outputLocation.set(layout.buildDirectory.dir("reports/jacoco/registro/html"))
		csv.required.set(false)
	}
}

tasks.register<org.gradle.testing.jacoco.tasks.JacocoCoverageVerification>("jacocoRegistroCoverageVerification") {
	dependsOn(tasks.test)

	classDirectories.setFrom(
		sourceSets.main.get().output.asFileTree.matching {
			include(registroCoverageIncludes)
		}
	)
	sourceDirectories.setFrom(sourceSets.main.get().allSource.srcDirs)
	executionData.setFrom(fileTree(layout.buildDirectory).include("jacoco/test.exec", "jacoco/test*.exec"))

	violationRules {
		rule {
			limit {
				counter = "LINE"
				value = "COVEREDRATIO"
				minimum = "0.65".toBigDecimal()
			}
		}
	}
}

tasks.withType<JavaCompile> {
    options.compilerArgs.add("-parameters")
}

sonarqube {
    properties {
		property("sonar.projectKey", System.getenv("SONAR_PROJECT_KEY") ?: "EasySchedule")
		property("sonar.projectName", System.getenv("SONAR_PROJECT_NAME") ?: "EasySchedule")
        property("sonar.host.url", System.getenv("SONAR_HOST_URL") ?: "http://localhost:9000")
        property("sonar.token", System.getenv("SONAR_TOKEN") ?: "")
        property("sonar.sources", "src/main/java")
        property("sonar.tests", "src/test/java")
        property("sonar.junit.reportPaths", "build/test-results/test")
        property("sonar.coverage.jacoco.xmlReportPaths", "build/reports/jacoco/test/jacocoTestReport.xml")
    }
}

tasks.named("sonar") {
    dependsOn(tasks.test, tasks.jacocoTestReport)
}
