plugins {
    kotlin("jvm") version "1.4.0"
    kotlin("plugin.serialization") version "1.4.0"
}

group = "org.itxtech"
version = "1.0.1"

kotlin {
    sourceSets {
        all {
            languageSettings.enableLanguageFeature("InlineClasses")
            languageSettings.useExperimentalAnnotation("kotlin.Experimental")
        }
    }
}

repositories {
    maven("https://mirrors.huaweicloud.com/repository/maven")
    maven("https://dl.bintray.com/him188moe/mirai")
    maven("https://dl.bintray.com/kotlin/kotlin-eap")
}

dependencies {
    api("org.jetbrains.kotlinx:atomicfu:0.14.4")

    implementation(kotlin("script-runtime"))
    implementation(kotlin("script-util"))
    implementation(kotlin("compiler-embeddable"))
    implementation(kotlin("scripting-compiler-embeddable"))
    implementation(kotlin("scripting-compiler-impl-embeddable"))
    implementation("org.jetbrains.intellij.deps:trove4j:1.0.20200330")

    implementation("net.mamoe:mirai-core:1.2.1")
    implementation("net.mamoe:mirai-console:1.0-M2")
}

tasks.named<Jar>("jar") {
    manifest {
        attributes["Name"] = "iTXTech MiraiKts"
        attributes["Revision"] = Runtime.getRuntime().exec("git rev-parse --short HEAD")
            .inputStream.bufferedReader().readText().trim()
    }

    val list = ArrayList<Any>()
    configurations.compileClasspath.get().forEach { file ->
        arrayOf("kotlin-script", "kotlin-compiler", "kotlin-daemon", "trove").forEach {
            if (file.absolutePath.contains(it)) {
                list.add(zipTree(file))
            }
        }
    }

    from(list)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}
