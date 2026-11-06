package com.ancla.safespace.presentation.places

import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import com.ancla.safespace.data.places.GeoPoint
import com.ancla.safespace.data.places.SupportPlace
import com.ancla.safespace.data.places.SupportPlacesRepository
import com.ancla.safespace.presentation.theme.AnclaColors
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.launch
import java.util.Locale
import java.net.SocketTimeoutException
import kotlin.math.roundToInt

private val supportCategories = listOf(
    "Profesional de salud mental",
    "Centro de salud",
    "Hospital / urgencias",
    "Apoyo comunitario",
)

@Composable
fun SupportMapScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var center by remember { mutableStateOf<GeoPoint?>(null) }
    var places by remember { mutableStateOf<List<SupportPlace>>(emptyList()) }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var loadingMessage by remember { mutableStateOf("Buscando recursos…") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var permissionDenied by remember { mutableStateOf(false) }
    var radiusKm by remember { mutableFloatStateOf(3f) }
    var activeRadiusKm by remember { mutableIntStateOf(3) }

    fun searchPoint(point: GeoPoint) {
        val requestedRadiusKm = radiusKm.roundToInt()
        center = point
        places = emptyList()
        selectedCategory = null
        activeRadiusKm = requestedRadiusKm
        loadingMessage = "Buscando recursos en un radio de ${requestedRadiusKm} km…"
        isLoading = true
        errorMessage = null
        scope.launch {
            runCatching { SupportPlacesRepository.findNearby(point, requestedRadiusKm * 1_000) }
                .onSuccess { places = it }
                .onFailure { errorMessage = friendlySearchError(it); places = emptyList() }
            isLoading = false
        }
    }

    fun locate() {
        activeRadiusKm = radiusKm.roundToInt()
        loadingMessage = "Obteniendo tu ubicación aproximada…"
        isLoading = true
        errorMessage = null
        val client = LocationServices.getFusedLocationProviderClient(context)
        runCatching {
            client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, CancellationTokenSource().token)
                .addOnSuccessListener { location ->
                    if (location == null) {
                        isLoading = false
                        errorMessage = "No se pudo obtener una ubicación aproximada. Puedes buscar una localidad manualmente."
                    } else {
                        searchPoint(GeoPoint(location.latitude, location.longitude, "Tu zona aproximada"))
                    }
                }
                .addOnFailureListener { exception ->
                    isLoading = false
                    errorMessage = exception.localizedMessage ?: "No se pudo obtener la ubicación. Prueba la búsqueda manual."
                }
        }.onFailure { exception ->
            isLoading = false
            errorMessage = exception.localizedMessage ?: "El servicio de ubicación no está disponible. Prueba la búsqueda manual."
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionDenied = !granted
        if (granted) locate()
    }

    fun findNearby() {
        val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) locate() else permissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    fun searchManual() {
        if (query.isBlank()) {
            errorMessage = "Escribe una ciudad, barrio o código postal."
            return
        }
        isLoading = true
        errorMessage = null
        center = null
        places = emptyList()
        val requestedRadiusKm = radiusKm.roundToInt()
        activeRadiusKm = requestedRadiusKm
        loadingMessage = "Buscando recursos en un radio de ${requestedRadiusKm} km…"
        scope.launch {
            runCatching {
                val result = SupportPlacesRepository.geocode(query)
                // Keep the resolved center even if the public places directory
                // is temporarily unavailable, so the user can still see the area.
                center = result
                result to SupportPlacesRepository.findNearby(result, requestedRadiusKm * 1_000)
            }.onSuccess { (point, resultPlaces) ->
                center = point
                places = resultPlaces
                selectedCategory = null
            }.onFailure { errorMessage = friendlySearchError(it); places = emptyList() }
            isLoading = false
        }
    }

    val visiblePlaces = places.filter { selectedCategory == null || it.category == selectedCategory }

    Column(
        modifier.fillMaxSize().background(AnclaColors.Canvas).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Encontrar apoyo cerca", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Psicología, salud mental, centros de salud y apoyo comunitario.", color = AnclaColors.MutedInk)
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = AnclaColors.GoldTint),
            shape = RoundedCornerShape(18.dp),
        ) {
            Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("Sobre tu ubicación", style = MaterialTheme.typography.titleSmall, color = AnclaColors.Ink, fontWeight = FontWeight.Bold)
                Text(
                    "Solo se pide ubicación aproximada al tocar «Buscar cerca de mí». La búsqueda manual envía el texto a Nominatim; para localizar recursos, las coordenadas aproximadas y el radio consultado pueden enviarse a directorios públicos de lugares. Estos servicios reciben esas peticiones; la app no guarda tu ubicación.",
                    style = MaterialTheme.typography.bodySmall,
                    color = AnclaColors.Ink,
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = AnclaColors.Surface),
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, AnclaColors.TealTint),
        ) {
            Column(Modifier.padding(horizontal = 15.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Radio de búsqueda", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text("${radiusKm.roundToInt()} km", style = MaterialTheme.typography.titleMedium, color = AnclaColors.Teal, fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = radiusKm,
                    onValueChange = { radiusKm = it.roundToInt().toFloat() },
                    valueRange = 1f..20f,
                    steps = 18,
                    enabled = !isLoading,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("1 km", style = MaterialTheme.typography.labelSmall, color = AnclaColors.MutedInk)
                    Text("20 km", style = MaterialTheme.typography.labelSmall, color = AnclaColors.MutedInk)
                }
                if (center == null) {
                    Text("Este radio se aplicará en tu próxima búsqueda.", style = MaterialTheme.typography.bodySmall, color = AnclaColors.MutedInk)
                } else if (activeRadiusKm != radiusKm.roundToInt()) {
                    TextButton(onClick = { center?.let(::searchPoint) }, enabled = !isLoading) {
                        Text("Aplicar radio y actualizar resultados", color = AnclaColors.Teal)
                    }
                }
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Ciudad, barrio o código postal") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(17.dp),
            trailingIcon = {
                TextButton(onClick = ::searchManual, enabled = !isLoading) { Text("Buscar", color = AnclaColors.Teal) }
            },
        )
        Button(
            onClick = ::findNearby,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(18.dp),
            enabled = !isLoading,
        ) { Text("◎    Buscar cerca de mí", fontWeight = FontWeight.SemiBold) }
        if (permissionDenied) {
            Text("Puedes seguir sin permiso: busca una ciudad o barrio arriba.", color = AnclaColors.MutedInk, style = MaterialTheme.typography.bodySmall)
        }

        if (isLoading) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape),
                color = AnclaColors.Teal,
                trackColor = AnclaColors.TealTint,
            )
            Text(loadingMessage, color = AnclaColors.MutedInk, style = MaterialTheme.typography.bodySmall)
        }
        errorMessage?.let { message ->
            Card(colors = CardDefaults.cardColors(containerColor = AnclaColors.RoseTint), shape = RoundedCornerShape(16.dp)) {
                Text(message, Modifier.padding(14.dp), color = AnclaColors.Rose)
            }
        }

        if (center != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(center!!.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = AnclaColors.Ink)
                Text("Radio ${activeRadiusKm} km", style = MaterialTheme.typography.labelMedium, color = AnclaColors.MutedInk)
            }
            if (places.isNotEmpty()) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    FilterChip(selected = selectedCategory == null, onClick = { selectedCategory = null }, label = { Text("Todos") })
                    supportCategories.forEach { category ->
                        if (places.any { it.category == category }) {
                            FilterChip(
                                selected = selectedCategory == category,
                                onClick = { selectedCategory = if (selectedCategory == category) null else category },
                                label = { Text(category) },
                            )
                        }
                    }
                }
                Text("${visiblePlaces.size} recursos cercanos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                visiblePlaces.forEach { place ->
                    PlaceCard(
                        place = place,
                        onCall = { dial(context, place.phone) },
                        onWebsite = { openWebsite(context, place.website) },
                        onEmail = { openEmail(context, place.email) },
                        onDirections = { openDirections(context, place) },
                    )
                }
            } else if (!isLoading && errorMessage == null) {
                Card(colors = CardDefaults.cardColors(containerColor = AnclaColors.TealTint), shape = RoundedCornerShape(19.dp)) {
                    Text(
                        "No se han encontrado centros ni profesionales en este radio. La cobertura del directorio puede ser incompleta; prueba a ampliar la distancia o buscar en otra zona.",
                        Modifier.padding(16.dp),
                        color = AnclaColors.Ink,
                    )
                }
            }
        } else {
            Box(
                Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(24.dp)).background(AnclaColors.TealTint),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Text("♡", fontSize = 42.sp, color = AnclaColors.Teal)
                    Text("Tu red de apoyo está cerca", fontWeight = FontWeight.SemiBold, color = AnclaColors.Ink)
                    Text("Busca una ciudad o usa tu ubicación para ver centros y profesionales.", color = AnclaColors.MutedInk, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                }
            }
        }

        Text(
            "Los datos de los establecimientos pueden faltar o cambiar. Llama antes de desplazarte. Si hay peligro inmediato, usa el botón «Ayuda ahora».",
            style = MaterialTheme.typography.bodySmall,
            color = AnclaColors.MutedInk,
        )
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun PlaceCard(
    place: SupportPlace,
    onCall: () -> Unit,
    onWebsite: () -> Unit,
    onEmail: () -> Unit,
    onDirections: () -> Unit,
) {
    val tint = when (place.category) {
        "Hospital / urgencias" -> AnclaColors.RoseTint
        "Centro de salud" -> AnclaColors.GoldTint
        "Apoyo comunitario" -> AnclaColors.LilacTint
        else -> AnclaColors.TealTint
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = AnclaColors.Surface),
        border = BorderStroke(1.dp, tint),
    ) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(11.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(tint), contentAlignment = Alignment.Center) {
                    Text(if (place.category.startsWith("Profesional")) "♡" else if (place.category.startsWith("Hospital")) "+" else "⌖", color = AnclaColors.Teal, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(place.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = AnclaColors.Ink)
                    Text(place.category, style = MaterialTheme.typography.labelMedium, color = AnclaColors.Teal)
                }
                Text(formatDistance(place.distanceMeters), style = MaterialTheme.typography.labelMedium, color = AnclaColors.MutedInk)
            }
            Text(
                place.address.ifBlank { "Dirección no disponible en el directorio" },
                style = MaterialTheme.typography.bodySmall,
                color = AnclaColors.MutedInk,
            )
            if (place.phone.isNotBlank()) Text("Teléfono: ${place.phone}", style = MaterialTheme.typography.bodySmall, color = AnclaColors.Ink)
            if (place.email.isNotBlank()) TextButton(onClick = onEmail) { Text("✉  ${place.email}", color = AnclaColors.Teal) }
            if (place.website.isNotBlank()) TextButton(onClick = onWebsite) { Text("Sitio web  ↗", color = AnclaColors.Teal) }
            if (place.openingHours.isNotBlank()) Text("Horario: ${place.openingHours}", style = MaterialTheme.typography.bodySmall, color = AnclaColors.MutedInk)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (place.phone.isNotBlank()) {
                    TextButton(onClick = onCall) { Text("Llamar", color = AnclaColors.Teal) }
                }
            }
            Button(onClick = onDirections, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(15.dp)) {
                Text("Abrir ruta en Google Maps  →", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

private fun formatDistance(meters: Float): String = if (meters < 1_000) {
    "${meters.toInt()} m"
} else {
    String.format(Locale.getDefault(), "%.1f km", meters / 1_000)
}

private fun friendlySearchError(error: Throwable): String {
    var current: Throwable? = error
    while (current != null) {
        val message = current.message.orEmpty()
        if (current is SocketTimeoutException || message.contains("timeout", ignoreCase = true) ||
            listOf("HTTP 429", "HTTP 502", "HTTP 503", "HTTP 504").any { message.contains(it) }
        ) {
            return "El directorio público de lugares no responde ahora. Puede estar temporalmente saturado; prueba en unos minutos o reduce el radio de búsqueda."
        }
        current = current.cause
    }
    return error.message ?: "No se pudo completar la búsqueda. Comprueba tu conexión e inténtalo de nuevo."
}

private fun dial(context: android.content.Context, number: String) {
    val normalized = number.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }
    if (normalized.isBlank()) return
    runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", normalized, null))) }
}

private fun openDirections(context: android.content.Context, place: SupportPlace) {
    val destination = "${place.latitude},${place.longitude}"
    val directionsUrl = Uri.parse("https://www.google.com/maps/dir/").buildUpon()
        .appendQueryParameter("api", "1")
        .appendQueryParameter("destination", destination)
        .appendQueryParameter("dir_action", "navigate")
        .build()
    val googleMapsIntent = Intent(
        Intent.ACTION_VIEW,
        directionsUrl,
    ).setPackage("com.google.android.apps.maps")
    runCatching { context.startActivity(googleMapsIntent) }.onFailure {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, directionsUrl)) }
    }
}

private fun openWebsite(context: android.content.Context, website: String) {
    if (website.isBlank()) return
    val normalized = if (website.startsWith("http://") || website.startsWith("https://")) website else "https://$website"
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(normalized))) }
}

private fun openEmail(context: android.content.Context, email: String) {
    if (email.isBlank()) return
    runCatching { context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${Uri.encode(email)}"))) }
}
