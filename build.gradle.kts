plugins {
	alias(libs.plugins.loom)
	alias(libs.plugins.kotlin)
	alias(libs.plugins.modPublish)
	`maven-publish`
}

version = property("mod_version") as String
group = property("maven_group") as String

repositories {
	mavenCentral()
	exclusiveContent {
		forRepositories(
			maven("https://ancientri.me/maven/releases") {
				name = "AncientRime"
			}
		)
		filter {
			@Suppress("UnstableApiUsage")
			includeGroupAndSubgroups("me.ancientri")
		}
	}
}

dependencies {
	minecraft(libs.minecraft)
	mappings(variantOf(libs.yarnMappings) { classifier("v2") })
	modImplementation(libs.fabricLoader)
	modImplementation(libs.fabricLanguageKotlin)
	modImplementation(libs.fabricApi)
	include(modImplementation(libs.rimelib.get())!!)
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
			"fabric_kotlin_version" to libs.versions.fabricLanguageKotlin.get(),
			"fabric_api_version" to libs.versions.fabricApi.get()
		)

		inputs.properties(propertyMap)
		filesMatching("fabric.mod.json") {
			expand(propertyMap)
		}
	}

	jar {
		from("LICENSE") {
			rename { "${it}_${base.archivesName.get()}" }
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
	displayName = "Chat Calc ${project.version}"
	changelog = """
        - Added support for 1.21.5
        
        This is a metadata change only, no code changes were made.
    """.trimIndent()
	modrinth {
		accessToken = providers.environmentVariable("MODRINTH_TOKEN")
		projectId = "o2oFdqXS"
		minecraftVersions.addAll("1.21.6")
		requires("fabric-language-kotlin")
		requires("fabric-api")
		embeds("rimelib")
		featured = true
	}
}