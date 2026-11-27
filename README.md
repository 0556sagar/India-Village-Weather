# India Village Weather 🌦️

A native, fast, lightweight Android weather application built in **100% Kotlin** delivering hyper-local village, town, tehsil, and city weather forecasts across India with real-time location auto-sync, dynamic visual atmospheric rendering, custom UI components, and an interactive cat companion mascot.

---

## ✨ Key Features

### 📍 Village-Level Search & GPS Location Sync
- **Pan-India Search:** Search weather conditions for thousands of Indian villages, towns, tehsils, and cities via OpenStreetMap Nominatim geocoding.
- **Automatic GPS Location Sync:** Automatically detects device location using Android `LocationManager` and `Geocoder` to load hyper-local village weather.
- **Census Population Lookup:** Displays village/town census details via OpenStreetMap/Wikidata integration (`P1082`).

### 🌡️ Real-Time & Extended Forecasts
- **Current Conditions:** Real-time temperature (°C), "Feels Like" index, min/max range, and descriptive weather states.
- **Detailed Atmospheric Metrics:** Air Quality (AQI), UV Index, Humidity (%), Wind Speed (km/h), Pressure (hPa), Visibility (km), Sunrise, and Sunset times.
- **24-Hour Hourly Forecast:** Hourly temperature, weather icons, and precipitation trends.
- **7-Day Daily Forecast:** Extended multi-day forecasts with daily high/low visual indicators.

### 🎨 Custom UI & Interactive Views
- **`CatView` Mascot:** An interactive, animated cat companion mascot reacting dynamically to touch interactions, meows, and live weather conditions.
- **`TempRangeBarView`:** A custom-designed gradient temperature spectrum bar visually displaying current temperature relative to daily min/max boundaries.
- **`WeatherBackgroundView`:** A procedural dynamic background render engine displaying animated atmospheric effects (raindrops, cloud cover, sun rays, night stars, fog/mist).
- **Material 3 Design:** Built with Android Material Design guidelines, CardView layouts, and responsive components.

---

## 🛠️ Tech Stack & Architecture

- **Language:** Kotlin
- **Platform:** Android (Native)
- **Target SDK:** API level 35 (Android 15)
- **Compile SDK:** API level 35
- **Minimum SDK:** API level 29 (Android 10)
- **Architecture & Libraries:**
  - `androidx.core:core-ktx`
  - `androidx.appcompat:appcompat`
  - `com.google.android.material:material`
  - `androidx.constraintlayout:constraintlayout` & `androidx.cardview:cardview`
  - `androidx.lifecycle:lifecycle-livedata-ktx` & `lifecycle-viewmodel-ktx`
  - `LocationManager` & `Geocoder` APIs
  - Open-Meteo & OpenStreetMap Nominatim APIs

---

## 🚀 CI/CD & Automated Releases

The repository features an automated GitHub Actions CI/CD pipeline defined in `.github/workflows/build-apk.yml`:
- **Automated Builds:** Triggered on every push to `main` branch or manual `workflow_dispatch`.
- **JDK & Build Environment:** Java 17 (Temurin), Gradle 8.5 on `ubuntu-latest`.
- **Automated Release Tagging:** Automatically calculates incremental version codes and names (`v1.0.X`).
- **GitHub Release Publishing:** Publishes compiled debug and release APKs (`India-Village-Weather-1.0.X.apk`) directly to GitHub Releases via `softprops/action-gh-release`.

---

## 💻 Local Setup & Building

1. **Clone the Repository:**
   ```bash
   git clone https://github.com/0556sagar/India-Village-Weather.git
   cd India-Village-Weather
   ```

2. **Open in Android Studio:**
   - Open Android Studio (Ladybug / Jellyfish or newer).
   - Ensure JDK 17 is selected in Project Structure settings.

3. **Build & Run:**
   - Sync Gradle project files (`./gradlew sync`).
   - Build debug APK: `./gradlew assembleDebug`
   - Run on an Android emulator or connected device (API 29+).
