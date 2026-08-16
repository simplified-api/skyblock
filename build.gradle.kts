plugins {
    id("java-library")
    idea
}

group = "dev.sbs"
version = "0.1.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
    maven(url = "https://central.sonatype.com/repository/maven-snapshots")
    maven(url = "https://jitpack.io")
}

dependencies {
    // Simplified Annotations
    compileOnly(libs.simplified.annotations)
    annotationProcessor(libs.simplified.annotations)
    testCompileOnly(libs.simplified.annotations)
    testAnnotationProcessor(libs.simplified.annotations)

    // Tests
    testImplementation(libs.hamcrest)
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.junit.platform.launcher)

    // Sibling API modules (composite-build substitutes by project name)
    api("com.github.simplified-api:github") { version { strictly("3beceab") } }

    // Simplified Libraries (github.com/simplified-dev)
    api("com.github.simplified-dev:collections") { version { strictly("8ca6cb8") } }
    api("com.github.simplified-dev:utils") { version { strictly("821499b") } }
    api("com.github.simplified-dev:reflection") { version { strictly("6c3b7c5") } }
    api("com.github.simplified-dev:gson-extras") { version { strictly("f143dc1") } }
    api("com.github.simplified-dev:persistence") { version { strictly("8ba3942") } }

    // Minecraft-Library (github.com/minecraft-library)
    // StatCategory, Rarity, BestiaryCategory, etc. store ChatColor values.
    api("com.github.minecraft-library:text") { version { strictly("117775e") } }

    // Gson - @GsonType-annotated inner classes plus direct Deserializer/TypeAdapter usage
    api(libs.gson)
}

idea {
    module {
        excludeDirs.addAll(listOf(
            layout.projectDirectory.dir(".schema").asFile
        ))
    }
}

tasks {
    test {
        useJUnitPlatform()

        // the corpus is a tracked directory of this project, and a forked test JVM inherits no
        // notion of where the project is
        systemProperty("skyblock.corpus.root", layout.projectDirectory.asFile.absolutePath)
    }
}
