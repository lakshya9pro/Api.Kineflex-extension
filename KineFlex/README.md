# CloudStream KineFlex Extension

Official CloudStream 3 plugin for **KineFlex** media streaming with TMDB metadata integration, four custom JSON-driven home categories, and authorized KineFlex stream playback.

---

## 🌟 Features

- **Four Custom Home Feeds**:
  1. **Latest Now**: Configured with `https://www.jsonkeeper.com/b/IHDLH`
  2. **Trending Now**: Custom configurable JSON URL
  3. **Premium**: Custom configurable JSON URL
  4. **Other**: Custom configurable JSON URL
- **Full TMDB Integration**:
  - Movie and TV series title search.
  - Comprehensive metadata: overview, backdrop, poster, release date/year, vote rating, genres, runtime, cast/actors, YouTube trailer, and recommendations.
  - Complete TV season and episode breakdown with episode names, season/episode numbers, still images, and air dates.
  - Supports both **TMDB v3 API Key** and **TMDB v4 Read-Only Bearer Token**.
  - In-memory caching for snappy navigation.
- **KineFlex Stream Resolution**:
  - Movie playback resolution via `GET /v1/movie/{tmdb_id}`.
  - TV episode playback resolution via `GET /v1/tv/{tmdb_id}/{season}/{episode}`.
  - Automatic injection of required headers (`Origin`, `Referer`, `User-Agent`).
  - HLS `.m3u8` playlist parsing with direct stream fallback.
- **Settings Dialog (`SettingsDialog`)**:
  - Secure API key inputs with show/hide password masking.
  - **Live Connection Test**: Free check against `GET /v1/account` displaying live points balance without consuming points.
  - **Live TMDB Test**: Validates credential against TMDB `/authentication`.
  - Configurable category feed URLs with "Reset to Defaults" option.
  - Android TV remote D-Pad focus compatibility.
  - Persistent storage in Android `SharedPreferences`.

---

## 📋 Centralized JSON Feed Schema

All category URLs are configured centrally in `com.phisher98.kineflex.KineFlexConfig`. You can also override or set them directly from the plugin Settings Dialog inside CloudStream.

### Standard Feed Item Schema

Feeds can be provided as either a top-level JSON array `[...]` or a JSON object with a `results`, `items`, or `data` array (`{ "results": [...] }`).

#### Example Array Feed (`https://www.jsonkeeper.com/b/IHDLH`):

```json
[
  {
    "id": 27205,
    "title": "Inception",
    "year": 2010,
    "imgs": "https://image.tmdb.org/t/p/w500/oYuLEt3zVCKq57qu2F8dT7NIa6f.jpg",
    "type": "movie"
  },
  {
    "id": 1399,
    "title": "Game of Thrones",
    "year": 2011,
    "imgs": "https://image.tmdb.org/t/p/w500/1XS1oqL89opfnbLl8WnZY1O1uJx.jpg",
    "type": "tv"
  }
]
```

#### Field Specifications:

| Field | Type | Required | Supported Aliases | Description |
|---|---|---|---|---|
| `id` | Integer / String | Yes | `id`, `tmdb_id`, `tmdbId` | The valid TMDB ID of the title. |
| `title` | String | Yes | `title`, `name`, `original_title` | Display title of the movie or TV show. |
| `type` | String | Yes | `type`, `media_type` | Media type: `"movie"` or `"tv"` (also accepts `"series"`). |
| `year` | Integer / String | Optional | `year`, `release_date`, `first_air_date` | Release year (e.g. `2010` or `"2010-07-16"`). |
| `imgs` | String | Optional | `imgs`, `image`, `poster`, `poster_path`, `poster_url` | Full image URL or TMDB path (e.g. `"/oYuLEt3zVCKq57qu2F8dT7NIa6f.jpg"`). |

---

## 🔑 Obtaining API Keys

### 1. KineFlex API Key
- Visit: [https://api.kineflex.site](https://api.kineflex.site)
- Register an account to receive **100 free points**.
- Generate an API key (format: `kine_live_...`).
- Enter the key into **KineFlex Settings** in CloudStream.
- Use the **Test Connection** button to confirm connection and check your available points balance.

### 2. TMDB Credential
- Visit: [https://www.themoviedb.org/settings/api](https://www.themoviedb.org/settings/api)
- Generate a free **v3 API Key** or **v4 Read-Only Access Token**.
- Enter it into **KineFlex Settings** in CloudStream.
- Use the **Test TMDB** button to verify the credential.

---

## 🏗️ Project Architecture

```
KineFlex/
├── build.gradle.kts           # Module configuration & Cloudstream plugin metadata
├── README.md                  # Complete documentation
└── src/
    └── main/
        ├── AndroidManifest.xml
        ├── res/
        │   ├── layout/
        │   │   └── settings_dialog.xml
        │   ├── values/
        │   │   └── strings.xml
        │   └── drawable/
        │       ├── ic_visibility.xml
        │       └── ic_visibility_off.xml
        └── kotlin/
            └── com/phisher98/kineflex/
                ├── KineFlexConfig.kt      # Centralized URLs & preferences
                ├── KineFlexModels.kt      # Jackson data models
                ├── KineFlexFeedParser.kt  # JSON feed parsing & mapping
                ├── TmdbClient.kt          # TMDB v3/v4 API & caching
                ├── KineFlexResolver.kt    # KineFlex stream resolver
                ├── SettingsDialog.kt      # TV-compatible settings dialog
                ├── KineFlexProvider.kt    # MainAPI provider implementation
                └── KineFlexPlugin.kt      # CloudstreamPlugin entry point
```

---

## 🚀 Building on GitHub Actions

As configured in the root repository, builds are performed in GitHub Actions CI without needing local Java or Android installations:

1. Commit and push changes to the repository:
   ```bash
   git add KineFlex/
   git commit -m "Add KineFlex CloudStream extension"
   git push origin master
   ```
2. GitHub Actions (`.github/workflows/build.yml`) will automatically:
   - Discover the `KineFlex` module via `settings.gradle.kts`.
   - Setup JDK 17 and Android SDK.
   - Run `./gradlew make makePluginsJson`.
   - Run `./gradlew ensureJarCompatibility`.
   - Package `KineFlex.cs3` and update `plugins.json` in the `builds` branch.
3. In the CloudStream 3 app:
   - Go to **Settings** > **Extensions** > **Add Repository**.
   - Enter your repository URL (pointing to the `builds` branch `plugins.json`).
   - Install **KineFlex**.
   - Open KineFlex plugin settings and configure your API keys.

---

## 🛡️ Security and Privacy

- API keys are **never** committed or stored in public code.
- Keys are masked in the UI (`kine_live_••••••••1234`).
- Keys are transmitted exclusively via HTTPS `Authorization: Bearer` headers.
- Keys are never output in error messages or logs.
