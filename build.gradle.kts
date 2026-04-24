import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import io.izzel.taboolib.gradle.*
import org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8
import io.izzel.taboolib.gradle.Basic
import io.izzel.taboolib.gradle.BukkitFakeOp
import io.izzel.taboolib.gradle.BukkitHook
import io.izzel.taboolib.gradle.BukkitUI
import io.izzel.taboolib.gradle.CommandHelper
import io.izzel.taboolib.gradle.DatabasePlayer
import io.izzel.taboolib.gradle.Bukkit
import io.izzel.taboolib.gradle.BukkitUtil


plugins {
    java
    id("io.izzel.taboolib") version "2.0.37"
    id("org.jetbrains.kotlin.jvm") version "2.2.0"
}

taboolib {
    env {
        install(Basic)
        install(BukkitFakeOp)
        install(BukkitHook)
        install(BukkitUI)
        install(CommandHelper)
        install(DatabasePlayer)
        install(Bukkit)
        install(BukkitUtil)
    }
    description {
        name = "RogueCore"
        dependencies {
            name("MythicMobs").optional(true)
            name("AttributePlus").optional(true)
        }
    }
    version { taboolib = "6.3.0-716e043" }
    relocate("ink.ptms.um", "${group}.um")
}

repositories {
    mavenCentral()
    maven("https://nexus.maplex.top/repository/maven-public/")
}

dependencies {
    taboo("ink.ptms:um:1.2.1")
    compileOnly("ink.ptms.core:v12004:12004:mapped")
    compileOnly("ink.ptms.core:v12004:12004:universal")
    compileOnly(kotlin("stdlib"))
    compileOnly(fileTree("libs"))
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        jvmTarget.set(JVM_1_8)
        freeCompilerArgs.add("-Xjvm-default=all")
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}
