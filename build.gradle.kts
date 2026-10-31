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
    api("com.github.simplified-api:github") { version { strictly("fb7be00") } }

    // Simplified Libraries (github.com/simplified-dev)
    api("com.github.simplified-dev:collections") { version { strictly("6842129") } }
    api("com.github.simplified-dev:utils") { version { strictly("7cc206f") } }
    api("com.github.simplified-dev:reflection") { version { strictly("cc90b40") } }
    api("com.github.simplified-dev:gson-extras") { version { strictly("be044c2") } }
    api("com.github.simplified-dev:persistence") { version { strictly("20ef3ca") } }

    // Minecraft-Library (github.com/minecraft-library)
    // StatCategory, Rarity, BestiaryCategory, etc. store ChatColor values.
    api("com.github.minecraft-library:text") { version { strictly("45d084f") } }

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
