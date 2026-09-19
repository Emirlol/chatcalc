plugins {
	alias(libs.plugins.loom)
	alias(libs.plugins.kotlin)
	alias(libs.plugins.ksp)
}

version = property("mod_version") as String
group = property("maven_group") as String
val minecraftVersion = libs.versions.minecraft.get()

repositories {
	mavenCentral()
	exclusiveContent {
		forRepositories(
			maven("https://maven.rimeorreason.org/releases") {
				name = "RimeOrReason"
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
	implementation(libs.fabricLoader)
	implementation(libs.fabricLanguageKotlin)
	implementation(libs.fabricApi)
	include(implementation(libs.rimelib.get())!!)
	ksp(libs.config.processor)
	compileOnly(libs.config.annotation)
	api(libs.pods4k)
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
	jvmToolchain(25)
}