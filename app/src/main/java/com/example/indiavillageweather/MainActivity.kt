package com.vinpex.weather

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { WeatherScreen() } }
    }
}

data class Place(
    val name: String,
    val admin: String,
    val latitude: Double,
    val longitude: Double,
    val pincode: String = ""
)

@Composable
fun WeatherScreen() {
    var query by remember { mutableStateOf("") }
    var places by remember { mutableStateOf<List<Place>>(emptyList()) }
    var weather by remember { mutableStateOf("Search a village, post office, PIN code, town or city to see its forecast.") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val transition = rememberInfiniteTransition(label = "weatherGlow")
    val glow by transition.animateFloat(
        initialValue = 0.18f, targetValue = 0.34f,
        animationSpec = infiniteRepeatable(tween(4200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow"
    )
    val glass = Color.White.copy(alpha = 0.105f)
    val border = Color.White.copy(alpha = 0.18f)

    Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF172A46), Color(0xFF46536A), Color(0xFF252B3A))))) {
        Box(Modifier.align(Alignment.TopEnd).size(290.dp).offset(x = 100.dp, y = (-65).dp).background(Color(0xFF9AB9D9).copy(alpha = glow), CircleShape))
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 18.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("＋", color = Color.White, style = MaterialTheme.typography.headlineLarge)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Vinpex Weather", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text("India · Village weather", color = Color.White.copy(alpha = .68f), style = MaterialTheme.typography.labelMedium)
                }
                Text("⋮", color = Color.White, style = MaterialTheme.typography.headlineLarge)
            }
            Card(colors = CardDefaults.cardColors(containerColor = glass), border = BorderStroke(1.dp, border), shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                        label = { Text("Village / post office / PIN code") }, placeholder = { Text("e.g. Sanganer or 302029") },
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                            focusedBorderColor = Color.White.copy(alpha = .65f), unfocusedBorderColor = border,
                            focusedLabelColor = Color.White, unfocusedLabelColor = Color.White.copy(alpha = .72f),
                            cursorColor = Color.White, focusedPlaceholderColor = Color.White.copy(alpha = .5f), unfocusedPlaceholderColor = Color.White.copy(alpha = .5f)))
                    Button(onClick = {
                        scope.launch {
                            busy = true; places = emptyList()
                            try {
                                val found = searchPlaces(query.trim())
                                places = found
                                weather = if (found.isEmpty()) "No matching place found. Try post office + district or PIN code." else "Choose a location below to load its weather."
                            } catch (_: Exception) { weather = "Search failed. Check your internet connection and try again." }
                            busy = false
                        }
                    }, enabled = query.isNotBlank() && !busy, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = .2f), contentColor = Color.White)) {
                        Text(if (busy) "Searching…" else "Search locations")
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("WEATHER & FORECAST", color = Color.White.copy(alpha = .65f), style = MaterialTheme.typography.labelMedium, letterSpacing = 1.5.sp)
            Spacer(Modifier.height(8.dp))
            Card(colors = CardDefaults.cardColors(containerColor = glass), border = BorderStroke(1.dp, border), shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Text("Your selected location", color = Color.White.copy(alpha = .7f), style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(8.dp))
                    Text(weather, color = Color.White, style = MaterialTheme.typography.bodyLarge)
                }
            }
            Spacer(Modifier.height(12.dp))
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 18.dp)) {
                items(places) { place ->
                    Card(colors = CardDefaults.cardColors(containerColor = glass), border = BorderStroke(1.dp, border), shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth(), onClick = {
                        scope.launch { busy = true; weather = try { fetchWeather(place) } catch (_: Exception) { "Weather unavailable. Check your connection and try again." }; busy = false }
                    }) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(place.name, color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(place.admin, color = Color.White.copy(alpha = .72f), style = MaterialTheme.typography.bodySmall)
                                if (place.pincode.isNotBlank()) Text("PIN ${place.pincode}", color = Color.White.copy(alpha = .72f), style = MaterialTheme.typography.labelSmall)
                            }
                            Text("›", color = Color.White, style = MaterialTheme.typography.headlineMedium)
                        }
                    }
                }
            }
            Text("Weather data · Open-Meteo   |   Post offices · India Post", color = Color.White.copy(alpha = .52f), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(bottom = 10.dp).align(Alignment.CenterHorizontally))
        }
    }
}

/** India Post lookup first; Open-Meteo supplies coordinates for the returned locality. */
private suspend fun searchPlaces(query: String): List<Place> = withContext(Dispatchers.IO) {
    require(query.isNotBlank()) { "Search query is blank" }
    val encoded = URLEncoder.encode(query, Charsets.UTF_8.name())
    val postal = runCatching {
        val root = JSONArray(httpGet("https://api.postalpincode.in/postoffice/$encoded"))
        val first = root.optJSONObject(0)
        if (first?.optString("Status") == "Success") first.optJSONArray("PostOffice") else null
    }.getOrNull()

    val postalPlaces = mutableListOf<Place>()
    if (postal != null) {
        for (i in 0 until postal.length()) {
            val po = postal.optJSONObject(i) ?: continue
            val office = po.optString("Name").trim()
            val district = po.optString("District").trim()
            val state = po.optString("State").trim()
            val pin = po.optString("Pincode").trim()
            if (office.isBlank() || district.isBlank()) continue
            val geoQuery = URLEncoder.encode("$office $district $state", Charsets.UTF_8.name())
            val geo = runCatching {
                val result = JSONObject(httpGet("https://geocoding-api.open-meteo.com/v1/search?name=$geoQuery&count=10&language=en&format=json"))
                    .optJSONArray("results")
                (0 until (result?.length() ?: 0)).asSequence()
                    .mapNotNull { result?.optJSONObject(it) }
                    .firstOrNull { it.optString("country_code") == "IN" }
            }.getOrNull() ?: continue
            postalPlaces += Place(office, listOf(district, state, "India").filter(String::isNotBlank).distinct().joinToString(", "), geo.getDouble("latitude"), geo.getDouble("longitude"), pin)
        }
    }
    if (postalPlaces.isNotEmpty()) return@withContext postalPlaces.distinctBy { "${it.name}|${it.pincode}" }.take(30)

    val root = JSONObject(httpGet("https://geocoding-api.open-meteo.com/v1/search?name=$encoded&count=30&language=en&format=json"))
    val results = root.optJSONArray("results") ?: return@withContext emptyList()
    (0 until results.length()).mapNotNull { i ->
        val item = results.optJSONObject(i) ?: return@mapNotNull null
        if (item.optString("country_code") != "IN") return@mapNotNull null
        val name = item.optString("name").trim()
        if (name.isBlank()) return@mapNotNull null
        Place(name, listOf(item.optString("admin2"), item.optString("admin1"), "India").filter(String::isNotBlank).distinct().joinToString(", "), item.getDouble("latitude"), item.getDouble("longitude"))
    }
}

private suspend fun fetchWeather(place: Place): String = withContext(Dispatchers.IO) {
    val url = "https://api.open-meteo.com/v1/forecast?latitude=${place.latitude}&longitude=${place.longitude}&current=temperature_2m,relative_humidity_2m,apparent_temperature,precipitation,weather_code,wind_speed_10m&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max&forecast_days=5&timezone=auto"
    val root = JSONObject(httpGet(url))
    val current = root.getJSONObject("current")
    val daily = root.getJSONObject("daily")
    val dates = daily.getJSONArray("time")
    val highs = daily.getJSONArray("temperature_2m_max")
    val lows = daily.getJSONArray("temperature_2m_min")
    val rain = daily.optJSONArray("precipitation_probability_max")
    buildString {
        append("${place.name}\n")
        append("${current.getDouble("temperature_2m")}°C · Feels like ${current.getDouble("apparent_temperature")}°C\n")
        append("Humidity ${current.getInt("relative_humidity_2m")}% · Wind ${current.getDouble("wind_speed_10m")} km/h\n")
        append("Rain now ${current.getDouble("precipitation")} mm\n\n5-day forecast\n")
        for (i in 0 until dates.length()) {
            append("${dates.getString(i)}: ${lows.getDouble(i)}° / ${highs.getDouble(i)}°C")
            if (rain != null && !rain.isNull(i)) append(" · Rain chance ${rain.optInt(i)}%")
            append("\n")
        }
    }
}

private fun httpGet(address: String): String {
    val connection = (URL(address).openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 12_000
        readTimeout = 15_000
        setRequestProperty("Accept", "application/json")
        setRequestProperty("User-Agent", "VinpexWeather/1.0 (Android)")
    }
    return try {
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        if (code !in 200..299) throw IllegalStateException("HTTP $code")
        body
    } finally {
        connection.disconnect()
    }
}
