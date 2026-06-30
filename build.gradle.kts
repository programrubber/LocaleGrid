plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.0"
    id("org.jetbrains.intellij") version "1.15.0"
}

group = "com.localegrid"
version = "0.8.0"

repositories {
    mavenCentral()
}

intellij {
    version.set("2023.3.5")
    type.set("PY")
    plugins.set(listOf())
}

val validatePluginDescriptionEncoding by tasks.registering {
    val pluginXml = layout.projectDirectory.file("src/main/resources/META-INF/plugin.xml")
    inputs.file(pluginXml)

    doLast {
        val content = pluginXml.asFile.readText(Charsets.UTF_8)
        val requiredPhrase = "\uB2E4\uAD6D\uC5B4 \uC5D0\uB514\uD130"
        val badTokens = listOf("\uFFFD", "LocaleGrid??")
        val badToken = badTokens.firstOrNull { content.contains(it) }
        if (badToken != null) {
            throw GradleException("plugin.xml appears to contain mojibake token: '$badToken'")
        }
        if (!content.contains(requiredPhrase)) {
            throw GradleException("plugin.xml is missing the required Korean description phrase.")
        }
    }
}

dependencies {
    implementation("org.json:json:20210307")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.2")
}

tasks {
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
        options.encoding = "UTF-8"
    }

    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "17"
    }

    test {
        useJUnitPlatform()
    }

    patchPluginXml {
        sinceBuild.set("233")
        untilBuild.set("999.*")
    }

    signPlugin {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
        privateKey.set(System.getenv("PRIVATE_KEY"))
        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
    }

    publishPlugin {
        token.set(System.getenv("PUBLISH_TOKEN"))
    }
}

tasks.named("patchPluginXml") {
    dependsOn(validatePluginDescriptionEncoding)
}
