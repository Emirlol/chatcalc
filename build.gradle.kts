plugins {
    id("fabric-loom") version "1.6-SNAPSHOT"
    id("maven-publish")
    kotlin("jvm") version "1.9.24"
    id("me.modmuss50.mod-publish-plugin") version "0.5.1"
}

version = project.property("mod_version")!!
group = project.property("maven_group")!!

dependencies {
    minecraft("com.mojang:minecraft:${properties["minecraft_version"]}")
    mappings("net.fabricmc:yarn:${properties["yarn_mappings"]}:v2")
    modImplementation("net.fabricmc:fabric-loader:${properties["loader_version"]}")
    modImplementation("net.fabricmc:fabric-language-kotlin:${properties["fabric_kotlin_version"]}")
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
        }
    }
}

tasks {
    processResources {
        filteringCharset = "UTF-8"
        val propertyMap = mapOf(
            "version" to project.version,
            "fabric_kotlin_version" to project.properties["fabric_kotlin_version"]
        )

        inputs.properties(propertyMap)
        filesMatching("fabric.mod.json") {
            expand(propertyMap)
        }
    }

    jar {
        from("LICENSE") {
            rename { "${it}_${archiveBaseName}" }
        }
    }
}

kotlin {
    jvmToolchain(21)
}

publishMods {
    file = tasks.remapJar.get().archiveFile
    modLoaders.add("fabric")
    type = STABLE
    changelog = "Fixed custom functions parsing allowing malformed functions that broke the config."
    modrinth {
        accessToken = providers.environmentVariable("MODRINTH_TOKEN")
        projectId = "o2oFdqXS"
        minecraftVersionRange {
            start = "1.20.5"
            end = "1.21"
        }
        requires("fabric-language-kotlin")
    }
}