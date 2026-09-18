plugins {
  id("uk.gov.justice.hmpps.gradle-spring-boot") version "11.0.9"
  kotlin("plugin.spring") version "2.4.20"
  kotlin("plugin.jpa") version "2.4.20"
  jacoco
}

configurations {
  testImplementation { exclude(group = "org.junit.vintage") }
  named("ktlint") {
    // Since plugin.spring and plugin.jpa upgrade from 2.4.10 to 2.4.20, the hmpps.gradle-spring-boot plugin is
    // causing a compatibility issue with KtLint, which only works with an older version of Kotlin.
    // This is a workaround until the plugin is updated.
    // When gradle runKtlintFormatOverTestSourceSet and gradle runKtlintFormatOverMainSourceSet
    // build successfully without this workaround, this can be removed.
    resolutionStrategy.eachDependency {
      if (requested.group == "org.jetbrains.kotlin") {
        useVersion("2.4.10")
      }
    }
  }
}

dependencies {
  implementation("uk.gov.justice.service.hmpps:hmpps-kotlin-spring-boot-starter:3.0.1")
  implementation("io.micrometer:micrometer-registry-prometheus")
  implementation("org.springframework.boot:spring-boot-starter-webclient")
  implementation("uk.gov.justice.service.hmpps:hmpps-sqs-spring-boot-starter:7.4.1")
  implementation("org.springframework.boot:spring-boot-starter-webflux")
  implementation("org.springframework.boot:spring-boot-starter-flyway")
  implementation("org.springframework.boot:spring-boot-starter-data-jpa")
  implementation("org.springframework.boot:spring-boot-starter-mail")
  implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.1")
  implementation("software.amazon.awssdk:athena:2.55.0")
  implementation("software.amazon.awssdk:s3:2.55.0")
  implementation("org.apache.commons:commons-csv:1.14.1")
  implementation("org.json:json:20260814")
  implementation("uk.gov.service.notify:notifications-java-client:6.2.0-RELEASE")
  implementation("org.locationtech.proj4j:proj4j:1.4.3")
  implementation("org.locationtech.proj4j:proj4j-epsg:1.4.3")
  implementation("io.flipt:flipt-client-java:1.3.4")

  runtimeOnly("org.postgresql:postgresql")
  runtimeOnly("org.flywaydb:flyway-database-postgresql")

  testImplementation("com.h2database:h2:2.5.250")
  testImplementation("uk.gov.justice.service.hmpps:hmpps-kotlin-spring-boot-starter-test:3.0.1")
  testImplementation("org.mockito:mockito-core:5.23.0")
  testImplementation("org.mockito.kotlin:mockito-kotlin:6.3.0")
  testImplementation("org.springframework.boot:spring-boot-starter-webflux-test")
  testImplementation("org.wiremock:wiremock-standalone:3.13.2")
  testImplementation("org.testcontainers:postgresql:1.21.4")
  testImplementation("io.swagger.parser.v3:swagger-parser:2.1.48") {
    exclude(group = "io.swagger.core.v3")
  }
  testImplementation("org.skyscreamer:jsonassert:1.5.3")
}

kotlin {
  jvmToolchain(25)
}

tasks {
  withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions.jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_25
  }

  withType<Test> {
    finalizedBy("jacocoTestReport") // report is always generated after tests run
  }

  named<JacocoReport>("jacocoTestReport") {
    dependsOn("test")

    reports { html.required.set(true) }

    classDirectories.setFrom(fileTree(projectDir) { include("build/classes/kotlin/main/**") })
    sourceDirectories.setFrom(files("src/main/kotlin"))
    executionData.setFrom(files("build/jacoco/test.exec"))
  }
}
