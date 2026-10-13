plugins {
    java
    application
}

group = "dev.akinetri"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("net.minestom:minestom:2026.10.07-26.2")
    implementation("com.google.code.gson:gson:2.14.0")
    implementation("org.slf4j:slf4j-simple:2.0.17")

    testImplementation("net.minestom:testing:2026.10.07-26.2")
    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    jvmArgs("-Djava.awt.headless=true", "-Dminestom.inside-test=true")
}

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 25
}

application {
    mainClass = "dev.akinetri.osumania.Main"
}

tasks.named<JavaExec>("run") {
    workingDir = layout.projectDirectory.dir("run").asFile.also { it.mkdirs() }
    standardInput = System.`in`
}

val fatJar by tasks.registering(Jar::class) {
    group = "build"
    archiveClassifier = "all"
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest { attributes["Main-Class"] = application.mainClass.get() }
    from(sourceSets.main.get().output)
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) }) {
        exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
    }
}

tasks.build { dependsOn(fatJar) }
