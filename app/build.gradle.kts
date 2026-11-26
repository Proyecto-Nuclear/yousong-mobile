plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// LAN deployment (see README): `-PyousongLanHost=192.168.0.10` makes the DEBUG build talk to
// http://192.168.0.10:8080 and allows cleartext only to that host (plus the emulator and loopback hosts).
// Release builds ignore it and stay HTTPS-only. Only private IPv4 addresses (RFC 1918) are accepted.
val yousongLanHost: String = providers.gradleProperty("yousongLanHost").orElse("").get().trim()
require(yousongLanHost.isEmpty() || isPrivateIpv4(yousongLanHost)) {
    "yousongLanHost must be a private IPv4 address (10.x.x.x, 172.16-31.x.x or 192.168.x.x), got '$yousongLanHost'"
}

fun isPrivateIpv4(host: String): Boolean {
    val octets = Regex("(0|[1-9][0-9]{0,2})\\.(0|[1-9][0-9]{0,2})\\.(0|[1-9][0-9]{0,2})\\.(0|[1-9][0-9]{0,2})")
        .matchEntire(host)?.groupValues?.drop(1)?.map { it.toInt() } ?: return false
    if (octets.any { it > 255 }) return false
    val (a, b) = octets
    return a == 10 || (a == 172 && b in 16..31) || (a == 192 && b == 168)
}

/** Writes the debug network security config: cleartext only to the emulator, loopback and LAN hosts. */
abstract class DebugNetworkSecurityTask : DefaultTask() {
    @get:Input
    abstract val lanHost: Property<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun write() {
        val hosts = listOf("10.0.2.2", "127.0.0.1") + listOfNotNull(lanHost.get().ifEmpty { null })
        val domains = hosts.joinToString("\n") { "        <domain includeSubdomains=\"false\">$it</domain>" }
        val file = outputDirectory.get().file("xml/debug_network_security.xml").asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            |<?xml version="1.0" encoding="utf-8"?>
            |<network-security-config>
            |    <base-config cleartextTrafficPermitted="false" />
            |    <domain-config cleartextTrafficPermitted="true">
            |$domains
            |    </domain-config>
            |</network-security-config>
            |""".trimMargin()
        )
    }
}

val debugNetworkSecurity = tasks.register<DebugNetworkSecurityTask>("generateDebugNetworkSecurity") {
    lanHost.set(yousongLanHost)
}

androidComponents {
    onVariants(selector().withBuildType("debug")) { variant ->
        variant.sources.res?.addGeneratedSourceDirectory(debugNetworkSecurity, DebugNetworkSecurityTask::outputDirectory)
    }
}

android {
    namespace = "cue.edu.co.yousong_movile"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "cue.edu.co.yousong_movile"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "LAN_API_HOST", "\"\"")
        buildConfigField("String", "LAN_API_BASE_URL", "\"\"")
    }

    buildTypes {
        debug {
            buildConfigField("String", "LAN_API_HOST", "\"$yousongLanHost\"")
            buildConfigField("String", "LAN_API_BASE_URL", if (yousongLanHost.isEmpty()) "\"\"" else "\"http://$yousongLanHost:8080\"")
        }
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation("com.google.zxing:core:3.5.4")
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}