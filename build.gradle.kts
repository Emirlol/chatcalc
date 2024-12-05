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
    displayName = "Chat Calc ${version.get()}"
    changelog = """
        - Fix for crash with Distant Horizons config screen
        I didn't actually try it out, but technically it should work.
    """.trimIndent()
    modrinth {
        accessToken = providers.environmentVariable("MODRINTH_TOKEN")
        projectId = "o2oFdqXS"
        minecraftVersions.addAll("1.20.5", "1.20.6", "1.21", "1.21.1", "1.21.2", "1.21.3", "1.21.4")
        requires("fabric-language-kotlin")
        featured = true
    }
}