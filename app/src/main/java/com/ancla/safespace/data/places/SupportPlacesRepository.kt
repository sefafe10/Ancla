package com.ancla.safespace.data.places

import android.net.Uri
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

data class GeoPoint(val latitude: Double, val longitude: Double, val label: String)

data class SupportPlace(
    val id: String,
    val name: String,
    val category: String,
    val address: String,
    val phone: String,
    val email: String,
    val website: String,
    val openingHours: String,
    val latitude: Double,
    val longitude: Double,
    val distanceMeters: Float,
)

/** Directorio a petición del usuario. No mantiene seguimiento ni consulta de fondo. */
object SupportPlacesRepository {
    private const val USER_AGENT = "AnclaAndroid/0.2.0 (support-place-search)"
    private const val NOMINATIM_URL = "https://nominatim.openstreetmap.org/search"
    private const val PHOTON_URL = "https://photon.komoot.io/api/"
    private val overpassEndpoints = listOf(
        "https://overpass-api.de/api/interpreter",
        "https://overpass.private.coffee/api/interpreter",
        "https://maps.mail.ru/osm/tools/overpass/api/interpreter",
    )
    private val nominatimMutex = Mutex()
    private var lastNominatimAt = 0L

    suspend fun geocode(address: String): GeoPoint = withContext(Dispatchers.IO) {
        require(address.isNotBlank()) { "Escribe una localidad o dirección." }
        nominatimMutex.withLock {
            val remainingWait = 1_000L - (System.currentTimeMillis() - lastNominatimAt)
            if (remainingWait > 0) delay(remainingWait)
            val encoded = URLEncoder.encode(address.trim(), Charsets.UTF_8.name())
            val response = request("$NOMINATIM_URL?format=jsonv2&limit=1&q=$encoded")
            lastNominatimAt = System.currentTimeMillis()
            val first = org.json.JSONArray(response).optJSONObject(0)
                ?: error("No se encontró esa ubicación. Prueba con una ciudad o código postal.")
            GeoPoint(
                latitude = first.getString("lat").toDouble(),
                longitude = first.getString("lon").toDouble(),
                label = first.optString("display_name", address).substringBefore(",", address),
            )
        }
    }

    suspend fun findNearby(point: GeoPoint, radiusMeters: Int): List<SupportPlace> = withContext(Dispatchers.IO) {
        require(radiusMeters in 1_000..20_000) { "El radio debe estar entre 1 y 20 km." }
        val lat = String.format(Locale.US, "%.5f", point.latitude)
        val lon = String.format(Locale.US, "%.5f", point.longitude)
        val photonResults = runCatching { findWithPhoton(point, radiusMeters) }
            .onFailure { android.util.Log.w("SupportPlaces", "Photon search failed; trying Nominatim", it) }
            .getOrNull()
        if (!photonResults.isNullOrEmpty()) return@withContext photonResults

        val nominatimResults = runCatching { findWithNominatim(point, radiusMeters) }
            .onFailure { android.util.Log.w("SupportPlaces", "Nominatim POI search failed", it) }
            .getOrNull()
        if (!nominatimResults.isNullOrEmpty()) return@withContext nominatimResults
        // Both independent indexes completed successfully and found no matching
        // resources. Show an empty result instead of waiting on overloaded mirrors.
        if (photonResults != null && nominatimResults != null) return@withContext emptyList()

        val overpassResults = runCatching {
            val query = """
                [out:json][timeout:20];
                (
                  nwr["healthcare"~"psychotherapist|psychiatrist|psychologist|counselling|counseling|mental_health|clinic|doctor|centre|center"](around:$radiusMeters,$lat,$lon);
                  nwr["healthcare:speciality"~"psychiatry|psychology|mental_health"](around:$radiusMeters,$lat,$lon);
                  nwr["amenity"~"clinic|hospital|doctors|social_facility|community_centre"](around:$radiusMeters,$lat,$lon);
                  nwr["social_facility"~"outreach|shelter|group_home|food_bank"]["social_facility:for"~"mental_health|homeless|social"](around:$radiusMeters,$lat,$lon);
                );
                out center tags 100;
            """.trimIndent()
            val body = "data=${URLEncoder.encode(query, Charsets.UTF_8.name())}"
            parseOverpass(JSONObject(requestOverpass(body)).optJSONArray("elements"), point)
        }.onFailure { android.util.Log.w("SupportPlaces", "Overpass search failed", it) }
            .getOrNull()

        if (!overpassResults.isNullOrEmpty()) return@withContext overpassResults
        throw IllegalStateException("No pudimos consultar los directorios de lugares. Comprueba tu conexión e inténtalo de nuevo.")
    }

    private fun parseOverpass(elements: org.json.JSONArray?, point: GeoPoint): List<SupportPlace> {
        if (elements == null) return emptyList()
        return buildList {
            for (index in 0 until elements.length()) {
                val element = elements.optJSONObject(index) ?: continue
                val tags = element.optJSONObject("tags") ?: JSONObject()
                val coordinates = element.optJSONObject("center") ?: element
                val latitude = coordinates.optDouble("lat", Double.NaN)
                val longitude = coordinates.optDouble("lon", Double.NaN)
                placeFrom(tags, latitude, longitude, point, "${element.optString("type")}-${element.optLong("id")}")?.let(::add)
            }
        }.distinctBy { it.id }.sortedBy { it.distanceMeters }
    }

    private suspend fun findWithNominatim(point: GeoPoint, radiusMeters: Int): List<SupportPlace> = withContext(Dispatchers.IO) {
        val searchPhrases = listOf(
            "Centro de salud cerca de", "Hospital cerca de", "Centro médico cerca de", "Centro comunitario cerca de",
        )
        var successfulResponses = 0
        val found = buildList {
            for (phrase in searchPhrases) {
                val response = runCatching {
                    nominatimMutex.withLock {
                        val remainingWait = 1_000L - (System.currentTimeMillis() - lastNominatimAt)
                        if (remainingWait > 0) delay(remainingWait)
                        val query = "$phrase ${point.latitude},${point.longitude}"
                        val url = Uri.parse(NOMINATIM_URL).buildUpon()
                            .appendQueryParameter("format", "jsonv2")
                            .appendQueryParameter("q", query)
                            .appendQueryParameter("limit", "10")
                            .appendQueryParameter("addressdetails", "1")
                            .appendQueryParameter("extratags", "1")
                            .appendQueryParameter("namedetails", "1")
                            .build()
                        request(url.toString()).also { lastNominatimAt = System.currentTimeMillis() }
                    }
                }.onFailure { android.util.Log.w("SupportPlaces", "Nominatim POI phrase failed: $phrase", it) }.getOrNull() ?: continue

                successfulResponses++
                val results = org.json.JSONArray(response)
                for (index in 0 until results.length()) {
                    val result = results.optJSONObject(index) ?: continue
                    val tags = JSONObject()
                    val mainKey = result.optString("category").ifBlank { result.optString("class") }
                    val mainValue = result.optString("type")
                    if (mainKey.isNotBlank()) {
                        tags.put(mainKey, mainValue)
                    } else if (mainValue in setOf("clinic", "hospital", "doctors", "community_centre", "social_facility")) {
                        // Some Nominatim JSON responses expose type without the
                        // legacy class/category field. Preserve the known OSM type.
                        tags.put("amenity", mainValue)
                    }
                    val extraTags = result.optJSONObject("extratags")
                    if (extraTags != null) {
                        extraTags.keys().forEach { key -> tags.put(key, extraTags.optString(key)) }
                    }
                    val address = result.optJSONObject("address") ?: JSONObject()
                    val namedetails = result.optJSONObject("namedetails") ?: JSONObject()
                    val latitude = result.optString("lat").toDoubleOrNull() ?: continue
                    val longitude = result.optString("lon").toDoubleOrNull() ?: continue
                    val osmType = result.optString("osm_type")
                    val osmId = result.optString("osm_id")
                    val id = if (osmType.isNotBlank() && osmId.isNotBlank()) "nominatim-$osmType-$osmId"
                        else "nominatim-$latitude-$longitude"
                    val place = placeFrom(
                        tags = tags,
                        latitude = latitude,
                        longitude = longitude,
                        point = point,
                        id = id,
                        name = namedetails.optString("name").ifBlank { result.optString("name") }
                            .ifBlank { result.optString("display_name").substringBefore(",") },
                        addressParts = listOf(
                            address.optString("road"), address.optString("house_number"),
                            address.optString("suburb"), address.optString("city").ifBlank { address.optString("town") },
                            address.optString("postcode"),
                        ),
                    )
                    if (place != null && place.distanceMeters <= radiusMeters) add(place)
                }
            }
        }
        if (successfulResponses == 0) error("No se pudo consultar el buscador geográfico de respaldo.")
        found.distinctBy { it.id }.sortedBy { it.distanceMeters }
    }

    private fun findWithPhoton(point: GeoPoint, radiusMeters: Int): List<SupportPlace> {
        val tagsToSearch = listOf("healthcare", "amenity:clinic", "amenity:hospital", "amenity:doctors", "social_facility")
        var successfulResponses = 0
        val found = buildList {
            for (osmTag in tagsToSearch) {
                runCatching {
                val url = Uri.parse(PHOTON_URL.replace("/api/", "/reverse")).buildUpon()
                    .appendQueryParameter("lat", point.latitude.toString())
                    .appendQueryParameter("lon", point.longitude.toString())
                    .appendQueryParameter("radius", (radiusMeters / 1_000.0).toString())
                    .appendQueryParameter("osm_tag", osmTag)
                    .appendQueryParameter("limit", "50")
                    .build()
                val features = JSONObject(request(url.toString())).optJSONArray("features") ?: org.json.JSONArray()
                successfulResponses++
                for (index in 0 until features.length()) {
                    val feature = features.optJSONObject(index) ?: continue
                    val properties = feature.optJSONObject("properties") ?: JSONObject()
                    val coordinates = feature.optJSONObject("geometry")?.optJSONArray("coordinates") ?: continue
                    if (coordinates.length() < 2) continue
                    val tags = JSONObject()
                    val osmKey = properties.optString("osm_key")
                    if (osmKey.isNotBlank()) tags.put(osmKey, properties.optString("osm_value"))
                    listOf("healthcare:speciality", "social_facility:for", "phone", "email", "website", "opening_hours").forEach { key ->
                        if (properties.has(key)) tags.put(key, properties.optString(key))
                    }
                    val osmType = properties.optString("osm_type")
                    val osmId = properties.optString("osm_id")
                    val id = if (osmType.isNotBlank() && osmId.isNotBlank()) "$osmType-$osmId"
                        else "photon-${coordinates.optDouble(0)}-${coordinates.optDouble(1)}"
                    val place = placeFrom(
                        tags = tags,
                        latitude = coordinates.optDouble(1, Double.NaN),
                        longitude = coordinates.optDouble(0, Double.NaN),
                        point = point,
                        id = id,
                        name = properties.optString("name"),
                        addressParts = listOf(
                            properties.optString("street"), properties.optString("housenumber"),
                            properties.optString("district"), properties.optString("city"), properties.optString("postcode"),
                        ),
                    )
                    if (place != null && place.distanceMeters <= radiusMeters) add(place)
                }
                }.onFailure { android.util.Log.w("SupportPlaces", "Photon search failed for tag $osmTag", it) }
            }
        }
        if (successfulResponses == 0) error("No se pudo consultar el índice de lugares de respaldo.")
        return found.distinctBy { it.id }.sortedBy { it.distanceMeters }
    }

    private fun placeFrom(
        tags: JSONObject,
        latitude: Double,
        longitude: Double,
        point: GeoPoint,
        id: String,
        name: String = "",
        addressParts: List<String> = listOf(
            tags.optString("addr:street"), tags.optString("addr:housenumber"), tags.optString("addr:suburb"),
            tags.optString("addr:city"), tags.optString("addr:postcode"),
        ),
    ): SupportPlace? {
        val category = classify(tags) ?: return null
        if (!latitude.isFinite() || !longitude.isFinite()) return null
        val distance = FloatArray(1)
        android.location.Location.distanceBetween(point.latitude, point.longitude, latitude, longitude, distance)
        return SupportPlace(
            id = id,
            name = name.ifBlank { tags.optString("name").ifBlank { category } },
            category = category,
            address = addressParts.filter(String::isNotBlank).joinToString(", "),
            phone = tags.optString("phone").ifBlank { tags.optString("contact:phone") },
            email = tags.optString("email").ifBlank { tags.optString("contact:email") },
            website = tags.optString("website").ifBlank { tags.optString("contact:website") },
            openingHours = tags.optString("opening_hours"),
            latitude = latitude,
            longitude = longitude,
            distanceMeters = distance[0],
        )
    }

    private fun requestOverpass(body: String): String {
        var lastFailure: Exception? = null
        for (endpoint in overpassEndpoints) {
            try {
                return request(endpoint, body)
            } catch (exception: Exception) {
                lastFailure = exception
                android.util.Log.w("SupportPlaces", "Overpass mirror failed: $endpoint", exception)
            }
        }
        val failureMessage = lastFailure?.message.orEmpty()
        if (lastFailure is SocketTimeoutException || lastFailure?.cause is SocketTimeoutException ||
            listOf("HTTP 429", "HTTP 502", "HTTP 503", "HTTP 504").any { failureMessage.contains(it) }
        ) {
            throw IllegalStateException("El directorio de apoyo está tardando en responder. Prueba un radio menor o inténtalo de nuevo.", lastFailure)
        }
        throw IllegalStateException("No se pudo conectar con el directorio de lugares. Comprueba tu conexión e inténtalo de nuevo.", lastFailure)
    }

    private fun classify(tags: JSONObject): String? {
        val healthcare = tags.optString("healthcare").lowercase(Locale.ROOT)
        val speciality = tags.optString("healthcare:speciality").lowercase(Locale.ROOT)
        val facility = tags.optString("social_facility").lowercase(Locale.ROOT)
        val amenity = tags.optString("amenity").lowercase(Locale.ROOT)
        return when {
            healthcare in setOf("psychotherapist", "psychiatrist", "psychologist", "counselling", "counseling", "mental_health") ||
                speciality.contains("psychiatry") || speciality.contains("psychology") || speciality.contains("mental_health") -> "Profesional de salud mental"
            healthcare in setOf("clinic", "doctor", "centre", "center") || amenity in setOf("clinic", "doctors") -> "Centro de salud"
            facility in setOf("outreach", "shelter", "group_home", "food_bank") ||
                amenity in setOf("social_facility", "community_centre") ||
                Regex("mental_health|homeless|social", RegexOption.IGNORE_CASE).containsMatchIn(tags.optString("social_facility:for")) -> "Apoyo comunitario"
            amenity == "hospital" || healthcare == "hospital" -> "Hospital / urgencias"
            else -> null
        }
    }

    private fun request(url: String, postBody: String? = null): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 6_000
        connection.readTimeout = 12_000
        connection.setRequestProperty("User-Agent", USER_AGENT)
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("Accept-Language", "es,en;q=0.8")
        if (postBody != null) {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            connection.outputStream.use { it.write(postBody.toByteArray(Charsets.UTF_8)) }
        }
        return try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val payload = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) error("El directorio de lugares no está disponible ahora (HTTP $status).")
            payload
        } finally {
            connection.disconnect()
        }
    }
}
