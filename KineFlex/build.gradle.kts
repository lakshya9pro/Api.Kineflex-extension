import org.jetbrains.kotlin.konan.properties.Properties

version = 1

android {
    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
    defaultConfig {
        val localProps = project.rootProject.file("local.properties")
        val tmdbProp = if (localProps.exists()) {
            val properties = Properties()
            localProps.inputStream().use { properties.load(it) }
            properties.getProperty("TMDB_API", "") ?: ""
        } else {
            ""
        }
        buildConfigField("String", "TMDB_API", "\"$tmdbProp\"")
    }
}

cloudstream {
    language = "en"
    description = "KineFlex movies & TV streaming with TMDB metadata, configurable JSON home feeds, and authorized stream resolution."
    authors = listOf("KineFlex")
    status = 1
    tvTypes = listOf(
        "Movie",
        "TvSeries"
    )
    requiresResources = true
    iconUrl = "https://api.kineflex.site/logo.jpg"
    isCrossPlatform = false
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    val cloudstream by configurations
    cloudstream("com.lagradost:cloudstream3:pre-release")
}
