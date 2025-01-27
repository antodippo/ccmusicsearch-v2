import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
	id("org.springframework.boot") version "3.1.5"
	id("io.spring.dependency-management") version "1.1.7"
	kotlin("jvm") version "2.1.10"
	kotlin("plugin.spring") version "1.9.23"
	id("info.solidsoft.pitest") version "1.15.0"
}

group = "com.antodippo"
version = "0.0.1"

java {
	sourceCompatibility = JavaVersion.VERSION_17
}

repositories {
	mavenCentral()
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("org.springframework.boot:spring-boot-starter-mustache")
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.18.+")
	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:1.9.0")
	implementation("io.projectreactor:reactor-core")
	implementation("io.github.cdimascio:dotenv-kotlin:6.5.0")
	implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")
	compileOnly("org.springframework.boot:spring-boot-devtools")
	testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.withType<KotlinCompile> {
	kotlinOptions {
		freeCompilerArgs += "-Xjsr305=strict"
		jvmTarget = "17"
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
}

pitest {
	setProperty("pitestVersion", "1.9.0")
	setProperty("junit5PluginVersion", "1.0.0")
	setProperty("targetClasses", listOf("com.antodippo.ccmusicsearch.*"))
	setProperty("outputFormats", listOf("HTML"))
	setProperty("threads", 2)
	setProperty("mutationThreshold", 29)
	setProperty("withHistory", true)
}
