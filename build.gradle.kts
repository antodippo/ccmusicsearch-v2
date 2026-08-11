import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
	id("org.springframework.boot") version "4.1.0"
	kotlin("jvm") version "2.4.10"
	kotlin("plugin.spring") version "2.4.10"
	id("info.solidsoft.pitest") version "1.19.0"
}

group = "com.antodippo"
version = "0.0.1"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(17)
	}
}

repositories {
	mavenCentral()
}

dependencies {
	// Gradle's own BOM support instead of the io.spring.dependency-management plugin. That
	// plugin forces the BOM's version onto every managed dependency, including ones we ask
	// for by name — it was quietly pulling coroutines back to the version Boot ships while
	// leaving the modules we name at the version we asked for, i.e. a split classpath. A
	// platform states preferences, so an explicit version above the BOM's still wins.
	val springBootBom = platform("org.springframework.boot:spring-boot-dependencies:4.1.0")
	implementation(springBootBom)
	compileOnly(springBootBom)

	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("org.springframework.boot:spring-boot-starter-mustache")
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.22.1")
	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:1.11.0")
	implementation("io.projectreactor:reactor-core")
	implementation("io.github.cdimascio:dotenv-kotlin:6.5.1")
	implementation("io.github.oshai:kotlin-logging-jvm:8.0.4")
	compileOnly("org.springframework.boot:spring-boot-devtools")
	testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.withType<KotlinCompile>().configureEach {
	compilerOptions {
		freeCompilerArgs.add("-Xjsr305=strict")
		jvmTarget = JvmTarget.JVM_17
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
}

pitest {
	setProperty("pitestVersion", "1.25.9")
	setProperty("junit5PluginVersion", "1.2.3")
	setProperty("targetClasses", listOf("com.antodippo.ccmusicsearch.*"))
	setProperty("outputFormats", listOf("HTML"))
	setProperty("threads", 2)
	setProperty("mutationThreshold", 29)
	// withHistory is gone: current pitest keeps incremental analysis in a separate
	// (commercial) history plugin and errors out if you ask for history without one.
	// CI starts from an empty workspace every run, so there was no history to reuse
	// there anyway.
}
