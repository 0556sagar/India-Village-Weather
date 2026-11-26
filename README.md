# India Village Weather 🌦️

A fast, lightweight Android weather application built in **100% Kotlin** that delivers hyper-local, village-level weather forecasts, air quality, UV exposure, census population data, and automatic GitHub release updates across India.

---

## ✨ Features

### 📍 Village-Level Search & Auto GPS Sync
- **Pan-India Village & City Search:** Search any village, town, tehsil, or city across India using OpenStreetMap Nominatim geocoding.
- **Automatic Live Location Sync:** Automatically detects and syncs with your current GPS/network location on startup and when returning from Location Settings.
- **Public Census Population Lookup:** Retrieves official Census population figures via OpenStreetMap and Wikidata (`P1082`) with nearby radius and district fallbacks.

### 🌡️ Real-Time Weather & Extended Forecasts
- **Current Weather Conditions:** Live temperature, "feels like" temperature, daily high/low, humidity, and wind speed powered by Open-Meteo.
- **24-Hour Hourly Forecast:** Horizontally scrollable hour-by-hour temperature, condition icons, and rain probability (`%`).
- **7-Day Weather Forecast:** Extended weekly forecast with daily min/max temperatures, weather conditions, and precipitation chances.

### 🌿 Air Quality, UV & Solar Timings
- **Air Quality Index (AQI):** Real-time US AQI score, health category (*Good* to *Hazardous*), and PM2.5 (`µg/m³`) concentration.
- **UV Light Index:** Live UV exposure index with safety rating (*Low*, *Moderate*, *High*, *Very High*, *Extreme*).
- **Rain Prediction:** Daily precipitation probability (`%`) and expected rainfall volume (`mm`).
- **Sunrise & Sunset Times:** Local solar sunrise and sunset timings formatted in 12-hour `AM/PM` time.

### 🔔 Notifications & In-App Updates
- **Weather Summary Notifications:** Instant status-bar weather notification when location data refreshes.
- **Automatic GitHub Release Updater:** Checks GitHub Releases on launch and notifies users with a direct `.apk` download prompt whenever a newer version is published.

---

## 🛠️ Tech Stack
- **Language:** Kotlin (`JVM 17`, `compileSdk 35`, `minSdk 29`)
- **Networking & Concurrency:** OkHttp 4 & Kotlin Coroutines (`async` parallel API calls)
- **Public Data APIs (No API Keys Required):**
  - [Open-Meteo Forecast & Air Quality API](https://open-meteo.com/)
  - [OpenStreetMap Nominatim](https://nominatim.openstreetmap.org/)
  - [Wikidata & Wikipedia Public APIs](https://www.wikidata.org/)
- **CI/CD:** GitHub Actions

---

## 📦 Releases
Download the latest APK from the [Releases](https://github.com/0556sagar/India-Village-Weather/releases) page.
