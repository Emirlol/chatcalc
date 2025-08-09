plugins {
	alias(libs.plugins.loom)
	alias(libs.plugins.kotlin)
	alias(libs.plugins.modPublish)
	alias(libs.plugins.ksp)
	`maven-publish`
}

version = property("mod_version") as String
group = property("maven_group") as String
val minecraftVersion = libs.versions.minecraft.get()

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

	ksp(libs.config.processor)
	compileOnly(libs.config.annotation)

	api(libs.pods4k) // Rimelib already includes the modules of this, we don't need to include it again
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
			"fabric_api_version" to libs.versions.fabricApi.get(),
			"minecraft_version" to minecraftVersion,
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
		
    """.trimIndent()
	modrinth {
		accessToken = providers.environmentVariable("MODRINTH_TOKEN")
		projectId = "o2oFdqXS"
		minecraftVersions.addAll(minecraftVersion)
		requires("fabric-language-kotlin")
		requires("fabric-api")
		embeds("rimelib")
		featured = true
	}
}