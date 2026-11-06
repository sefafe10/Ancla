package com.ancla.safespace.presentation

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import com.ancla.safespace.data.local.SecureStore
import com.ancla.safespace.data.local.UserData
import com.ancla.safespace.domain.model.EmergencyHotlines
import com.ancla.safespace.presentation.theme.AnclaColors
import com.ancla.safespace.presentation.theme.AnclaTheme
import com.ancla.safespace.presentation.places.SupportMapScreen
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

private enum class Screen { ONBOARDING, HOME, BREATHING, GROUNDING, PLAN, MAP }

private val planPrompts = listOf(
    "Señales de alerta", "Estrategias que puedo hacer por mi cuenta", "Lugares y personas para distraerme",
    "Personas a las que puedo pedir ayuda", "Profesionales y servicios de salud", "Cómo hacer más seguro mi entorno",
)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun AnclaApp() {
    val context = LocalContext.current
    val store = remember(context) { SecureStore(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var screen by remember { mutableStateOf(Screen.ONBOARDING) }
    var userData by remember { mutableStateOf(UserData()) }
    var loaded by remember { mutableStateOf(false) }
    var showCrisis by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(store) {
        runCatching { store.load() }
            .onSuccess { userData = it; screen = if (it.countryCode.isBlank()) Screen.ONBOARDING else Screen.HOME }
            .onFailure { saveError = "No se pudieron leer los datos cifrados. Puedes volver a configurar la app." }
        loaded = true
    }

    BackHandler(enabled = screen != Screen.HOME && screen != Screen.ONBOARDING) { screen = Screen.HOME }

    fun persist(data: UserData, next: Screen? = null) {
        scope.launch {
            runCatching { store.save(data) }
                .onSuccess { userData = data; if (next != null) screen = next; saveError = null }
                .onFailure { saveError = "No se pudieron guardar los datos de forma segura en este dispositivo." }
        }
    }

    AnclaTheme {
        if (!loaded) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                topBar = {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background,
                            titleContentColor = MaterialTheme.colorScheme.onBackground,
                        ),
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Box(
                                    Modifier.size(34.dp).clip(CircleShape)
                                        .background(Brush.linearGradient(listOf(AnclaColors.Teal, Color(0xFF77A88B))), CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Image(
                                        painter = painterResource(com.ancla.safespace.R.drawable.ic_ancla_logo),
                                        contentDescription = null,
                                        modifier = Modifier.size(25.dp),
                                    )
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                                    Text("ancla", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                    Text("un respiro a la vez", color = AnclaColors.MutedInk, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        },
                        actions = {
                            if (screen !in listOf(Screen.ONBOARDING, Screen.HOME)) {
                                TextButton(onClick = { screen = Screen.HOME }) { Text("Inicio", color = AnclaColors.Teal) }
                            }
                            if (screen != Screen.ONBOARDING) {
                                TextButton(onClick = { showCrisis = true }) {
                                    Text("Ayuda ahora", color = AnclaColors.Rose, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        },
                    )
                },
            ) { padding ->
                AnimatedContent(
                    targetState = screen,
                    transitionSpec = {
                        (fadeIn(tween(260)) + slideInVertically(tween(260)) { it / 24 }) togetherWith
                            (fadeOut(tween(150)) + slideOutVertically(tween(150)) { -it / 24 })
                    },
                    label = "transición de pantalla",
                ) { destination ->
                    when (destination) {
                        Screen.ONBOARDING -> OnboardingScreen(
                            initial = userData,
                            modifier = Modifier.padding(padding),
                            onContinue = { persist(it, Screen.HOME) },
                        )
                        Screen.HOME -> HomeScreen(
                            modifier = Modifier.padding(padding),
                            onCrisis = { showCrisis = true },
                            onBreathing = { screen = Screen.BREATHING },
                            onGrounding = { screen = Screen.GROUNDING },
                            onPlan = { screen = Screen.PLAN },
                            onMap = { screen = Screen.MAP },
                        )
                        Screen.BREATHING -> BreathingScreen(Modifier.padding(padding))
                        Screen.GROUNDING -> GroundingScreen(Modifier.padding(padding))
                        Screen.PLAN -> SafetyPlanScreen(
                            initial = userData.safetyPlan,
                            modifier = Modifier.padding(padding),
                            onSave = { plan -> persist(userData.copy(safetyPlan = plan)) },
                        )
                        Screen.MAP -> SupportMapScreen(Modifier.padding(padding))
                    }
                }
            }
            saveError?.let { message ->
                AlertDialog(
                    onDismissRequest = { saveError = null },
                    title = { Text("No se pudo completar") },
                    text = { Text(message) },
                    confirmButton = { TextButton(onClick = { saveError = null }) { Text("Entendido") } },
                )
            }
            if (showCrisis) {
                CrisisDialog(userData = userData, onDismiss = { showCrisis = false })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OnboardingScreen(initial: UserData, modifier: Modifier, onContinue: (UserData) -> Unit) {
    var selectedCode by remember { mutableStateOf(initial.countryCode.ifBlank { "ES" }) }
    var customNumber by remember { mutableStateOf(initial.customNumber) }
    var customEmergency by remember { mutableStateOf(initial.customEmergencyNumber) }
    var expanded by remember { mutableStateOf(false) }
    var inputError by remember { mutableStateOf(false) }
    val selected = EmergencyHotlines.countries.find { it.code == selectedCode } ?: EmergencyHotlines.custom
    val isCustom = selected.code == "OT"

    Column(
        modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(AnclaColors.Canvas, Color(0xFFFFF2E7), AnclaColors.Canvas)),
        ).verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        WelcomeBanner(
            title = "Un lugar para volver a ti",
            detail = "No tienes que resolverlo todo ahora. Vamos paso a paso.",
        )
        Text("Empecemos por lo esencial", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        InfoCard("Tu espacio, a tu ritmo", "Tus datos se guardan cifrados en este dispositivo. Puedes cambiar estas opciones más adelante.", AnclaColors.TealTint, "✦")
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = selected.country,
                onValueChange = {},
                readOnly = true,
                label = { Text("País o región") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                (EmergencyHotlines.countries + EmergencyHotlines.custom).forEach { country ->
                    DropdownMenuItem(
                        text = { Text(country.country) },
                        onClick = { selectedCode = country.code; expanded = false; inputError = false },
                    )
                }
            }
        }
        if (isCustom) {
            OutlinedTextField(
                value = customNumber,
                onValueChange = { customNumber = it; inputError = false },
                label = { Text("Teléfono local de apoyo") },
                supportingText = { Text("Escribe una línea de ayuda que conozcas en tu zona.") },
                modifier = Modifier.fillMaxWidth(),
                isError = inputError && customNumber.isBlank(),
            )
            OutlinedTextField(
                value = customEmergency,
                onValueChange = { customEmergency = it; inputError = false },
                label = { Text("Emergencias de tu zona") },
                supportingText = { Text("Por ejemplo, el número local de emergencias.") },
                modifier = Modifier.fillMaxWidth(),
                isError = inputError && customEmergency.isBlank(),
            )
        } else {
            InfoCard("Línea de apoyo", "${selected.service}  ·  ${selected.number}", AnclaColors.TealTint, "♡")
            InfoCard("Emergencias", selected.emergencyNumber, AnclaColors.GoldTint, "✦")
        }
        Text("Si existe peligro inmediato, contacta con emergencias locales o acude a urgencias. Esta app no sustituye a un profesional.", style = MaterialTheme.typography.bodySmall, color = AnclaColors.MutedInk)
        Button(
            onClick = {
                if (isCustom && (customNumber.isBlank() || customEmergency.isBlank())) inputError = true
                else onContinue(UserData(selectedCode, customNumber.trim(), customEmergency.trim(), initial.safetyPlan))
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(18.dp),
        ) { Text("Continuar") }
    }
}

@Composable
private fun HomeScreen(
    modifier: Modifier,
    onCrisis: () -> Unit,
    onBreathing: () -> Unit,
    onGrounding: () -> Unit,
    onPlan: () -> Unit,
    onMap: () -> Unit,
) {
    Column(
        modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(AnclaColors.Canvas, Color(0xFFFFF4E9), AnclaColors.Canvas)),
        ).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp),
    ) {
        WelcomeBanner("Hola. Estás aquí, ahora.", "Qué bueno que te hayas dado este momento.")
        Text("¿Qué necesitas hoy?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        StaggeredEntry(0) {
            Button(
                onClick = onCrisis,
                modifier = Modifier.fillMaxWidth().height(58.dp),
                shape = RoundedCornerShape(20.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = AnclaColors.Rose,
                    contentColor = Color.White,
                ),
            ) {
                Text("♡    Necesito ayuda ahora", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
        }
        StaggeredEntry(90) {
            ActionCard("Respirar conmigo", "Un ritmo suave, a tu propio paso.", "◌", AnclaColors.TealTint, AnclaColors.Teal, onBreathing)
        }
        StaggeredEntry(180) {
            ActionCard("Volver a mis sentidos", "Un ejercicio guiado 5–4–3–2–1.", "✿", AnclaColors.PeachTint, Color(0xFF9B5C3D), onGrounding)
        }
        StaggeredEntry(270) {
            ActionCard("Mi plan de seguridad", "Tus apoyos y pasos que te sirven.", "♡", AnclaColors.LilacTint, AnclaColors.Lilac, onPlan)
        }
        StaggeredEntry(360) {
            ActionCard("Encontrar apoyo cerca", "Psicología, centros de salud y ayuda comunitaria.", "⌖", AnclaColors.GoldTint, Color(0xFF9B772F), onMap)
        }
        StaggeredEntry(450) {
            InfoCard("Un paso pequeño también cuenta", "Puedes acercarte a alguien de confianza o pedir ayuda profesional.", AnclaColors.GoldTint, "☀")
        }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun CrisisDialog(userData: UserData, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val selected = EmergencyHotlines.countries.find { it.code == userData.countryCode }
    val hotlineNumber = if (selected == null) userData.customNumber else selected.number
    val emergencyNumber = if (selected == null) userData.customEmergencyNumber else selected.emergencyNumber
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("No tienes que pasar esto a solas") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Si estás en peligro inmediato, llama a emergencias. También puedes contactar con la línea de apoyo.")
                Text("Emergencias: $emergencyNumber", fontWeight = FontWeight.SemiBold)
                Text("Línea de apoyo: $hotlineNumber", fontWeight = FontWeight.SemiBold)
                if (selected?.sourceUrl?.isNotBlank() == true) Text("Servicio nacional de ${selected.country}", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                Button(onClick = { dial(context, emergencyNumber) }) { Text("Llamar a emergencias") }
                TextButton(onClick = { dial(context, hotlineNumber) }, enabled = hotlineNumber.isNotBlank()) { Text("Llamar a la línea de apoyo") }
                TextButton(onClick = onDismiss) { Text("Cerrar") }
            }
        },
    )
}

private fun dial(context: android.content.Context, number: String) {
    val normalized = number.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }
    if (normalized.isBlank()) return
    try {
        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", normalized, null)))
    } catch (_: ActivityNotFoundException) {
        // En algunos dispositivos no hay aplicación de teléfono configurada; no se inicia una llamada oculta.
    }
}

@Composable
private fun BreathingScreen(modifier: Modifier) {
    val scale = remember { Animatable(0.76f) }
    var phase by remember { mutableStateOf("Inhala") }
    var paused by remember { mutableStateOf(false) }
    LaunchedEffect(paused) {
        if (!paused) {
            while (true) {
                phase = "Inhala despacio"
                scale.animateTo(1f, tween(4_000, easing = FastOutSlowInEasing))
                phase = "Exhala suavemente"
                scale.animateTo(0.76f, tween(6_000, easing = FastOutSlowInEasing))
            }
        }
    }
    Column(
        modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFFF2FAF4), Color(0xFFFFF5E9), Color(0xFFF4F5EC))),
        ).padding(horizontal = 24.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(17.dp, Alignment.CenterVertically),
    ) {
        Text("Un momento para ti", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = AnclaColors.Ink)
        Text("Sigue el movimiento, sin forzar la respiración.", textAlign = TextAlign.Center, color = AnclaColors.MutedInk)
        androidx.compose.animation.AnimatedContent(
            targetState = phase,
            transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
            label = "fase de respiración",
        ) { currentPhase ->
            Text(
                currentPhase,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (currentPhase.startsWith("Inhala")) AnclaColors.Teal else Color(0xFFAA704C),
            )
        }
        Box(Modifier.size(320.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(AnclaColors.Teal.copy(alpha = 0.08f), radius = size.minDimension * 0.48f)
                drawCircle(AnclaColors.Teal.copy(alpha = 0.12f), radius = size.minDimension * 0.39f, style = Stroke(2.dp.toPx()))
                drawCircle(AnclaColors.Peach.copy(alpha = 0.22f), radius = size.minDimension * 0.29f, style = Stroke(2.dp.toPx()))
            }
            Canvas(
                Modifier.size((212 * scale.value).dp)
                    .shadow(18.dp, CircleShape, ambientColor = AnclaColors.Teal.copy(alpha = 0.18f), spotColor = AnclaColors.Teal.copy(alpha = 0.18f))
                    .clip(CircleShape),
            ) {
                drawCircle(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFFB4DCC7), Color(0xFF78B39A), Color(0xFF4F8E7D)),
                        center = center.copy(x = center.x * 0.78f, y = center.y * 0.72f),
                        radius = size.minDimension * 0.86f,
                    ),
                )
                drawCircle(Color.White.copy(alpha = 0.28f), radius = size.minDimension * 0.44f, style = Stroke(2.dp.toPx()))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf(AnclaColors.Teal, AnclaColors.Peach, AnclaColors.Gold).forEachIndexed { index, color ->
                Box(
                    Modifier.size(if (index == 1) 10.dp else 7.dp).clip(CircleShape).background(color),
                )
            }
            Text(" 4 s al inhalar  ·  6 s al exhalar", style = MaterialTheme.typography.labelLarge, color = AnclaColors.MutedInk)
        }
        OutlinedButton(
            onClick = { paused = !paused },
            shape = RoundedCornerShape(18.dp),
        ) { Text(if (paused) "Reanudar" else "Pausar", color = AnclaColors.Teal) }
        Text("Si concentrarte en la respiración no te ayuda, puedes parar y elegir otra opción.", textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall, color = AnclaColors.MutedInk)
    }
}

@Composable
private fun GroundingScreen(modifier: Modifier) {
    val steps = listOf(
        "Nombra 5 cosas que puedes ver.",
        "Nombra 4 cosas que puedes sentir con el tacto.",
        "Nombra 3 sonidos que puedes oír.",
        "Nombra 2 cosas que puedes oler.",
        "Nombra 1 cosa que puedes saborear.",
    )
    val stepColors = listOf(AnclaColors.TealTint, AnclaColors.PeachTint, AnclaColors.GoldTint, AnclaColors.LilacTint, AnclaColors.RoseTint)
    val stepSymbols = listOf("◉", "✋", "♫", "❀", "♡")
    var step by remember { mutableStateOf(0) }
    var response by remember(step) { mutableStateOf("") }
    Column(
        modifier.fillMaxSize().background(AnclaColors.Canvas).verticalScroll(rememberScrollState()).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text("Volver a mis sentidos", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("PASO ${step + 1} DE ${steps.size}", style = MaterialTheme.typography.labelLarge, color = AnclaColors.Teal, fontWeight = FontWeight.Bold)
            Text("${(step + 1) * 20}%", style = MaterialTheme.typography.labelLarge, color = AnclaColors.MutedInk)
        }
        LinearProgressIndicator(
            progress = { (step + 1) / steps.size.toFloat() },
            modifier = Modifier.fillMaxWidth().height(7.dp).clip(CircleShape),
            color = AnclaColors.Teal,
            trackColor = AnclaColors.TealTint,
        )
        androidx.compose.animation.AnimatedContent(
            targetState = step,
            transitionSpec = { fadeIn(tween(250)) + slideInVertically(tween(250)) { it / 12 } togetherWith fadeOut(tween(180)) },
            label = "paso de grounding",
        ) { currentStep ->
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(stepColors[currentStep]).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(stepSymbols[currentStep], fontSize = 34.sp, color = AnclaColors.Teal)
                Text("${currentStep + 1}", style = MaterialTheme.typography.displaySmall, color = AnclaColors.Teal, fontWeight = FontWeight.Bold)
                Text(steps[currentStep], style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = AnclaColors.Ink)
            }
        }
        OutlinedTextField(
            value = response,
            onValueChange = { response = it },
            label = { Text("Puedes escribirlo o solo pensarlo") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            shape = RoundedCornerShape(18.dp),
        )
        Button(
            onClick = { if (step < steps.lastIndex) step++ else step = 0 },
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(18.dp),
        ) { Text(if (step < steps.lastIndex) "Siguiente  →" else "Volver a empezar") }
        Text("No hay respuestas correctas. Si esto te incomoda, puedes parar.", style = MaterialTheme.typography.bodySmall, color = AnclaColors.MutedInk)
    }
}

@Composable
private fun SafetyPlanScreen(initial: List<String>, modifier: Modifier, onSave: (List<String>) -> Unit) {
    val answers = remember(initial) { mutableStateListOf<String>().apply { addAll((0..5).map { initial.getOrElse(it) { "" } }) } }
    Column(
        modifier.fillMaxSize().background(AnclaColors.Canvas).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        WelcomeBanner("Mi plan de seguridad", "Pequeños pasos para los momentos difíciles.")
        val filled = answers.count { it.isNotBlank() }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("TU PLAN", style = MaterialTheme.typography.labelLarge, color = AnclaColors.Teal, fontWeight = FontWeight.Bold)
            Text("$filled de 6 pasos", style = MaterialTheme.typography.labelLarge, color = AnclaColors.MutedInk)
        }
        LinearProgressIndicator(
            progress = { filled / 6f },
            modifier = Modifier.fillMaxWidth().height(7.dp).clip(CircleShape),
            color = AnclaColors.Teal,
            trackColor = AnclaColors.TealTint,
        )
        Text("Puedes completarlo poco a poco, cuando te resulte posible.", color = AnclaColors.MutedInk)
        planPrompts.forEachIndexed { index, prompt ->
            PlanStepCard(
                index = index,
                prompt = prompt,
                value = answers[index],
                onValueChange = { answers[index] = it },
            )
        }
        Button(
            onClick = { onSave(answers.toList()) },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(18.dp),
        ) { Text("Guardar mi plan", fontWeight = FontWeight.SemiBold) }
        Text("El plan se cifra en este dispositivo. Si lo compartes, elige a alguien de confianza.", style = MaterialTheme.typography.bodySmall, color = AnclaColors.MutedInk)
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun PlanStepCard(index: Int, prompt: String, value: String, onValueChange: (String) -> Unit) {
    val tints = listOf(AnclaColors.TealTint, AnclaColors.PeachTint, AnclaColors.GoldTint, AnclaColors.LilacTint, AnclaColors.RoseTint, AnclaColors.TealTint)
    val accents = listOf(AnclaColors.Teal, Color(0xFF9B5C3D), Color(0xFF9B772F), AnclaColors.Lilac, AnclaColors.Rose, AnclaColors.Teal)
    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, tints[index]),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier.size(38.dp).clip(CircleShape).background(tints[index]),
                    contentAlignment = Alignment.Center,
                ) { Text("${index + 1}", color = accents[index], fontWeight = FontWeight.Bold) }
                Text(prompt, style = MaterialTheme.typography.titleMedium, color = AnclaColors.Ink, fontWeight = FontWeight.SemiBold)
            }
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text("Escribe aquí, si te apetece") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = accents[index],
                    unfocusedBorderColor = tints[index],
                ),
            )
        }
    }
}

@Composable
private fun ActionCard(title: String, detail: String, symbol: String, tint: Color, accent: Color, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = AnclaColors.Surface),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.9f)),
        shape = RoundedCornerShape(23.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(Modifier.size(48.dp).clip(CircleShape).background(tint), contentAlignment = Alignment.Center) {
                Text(symbol, color = accent, fontSize = 27.sp)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = AnclaColors.Ink)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = AnclaColors.MutedInk)
            }
            Text("›", fontSize = 30.sp, color = accent, fontWeight = FontWeight.Light)
        }
    }
}

@Composable
private fun InfoCard(title: String, detail: String, tint: Color = AnclaColors.TealTint, symbol: String = "✦") {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = tint.copy(alpha = 0.66f)),
        shape = RoundedCornerShape(21.dp),
    ) {
        Row(Modifier.padding(17.dp), horizontalArrangement = Arrangement.spacedBy(13.dp), verticalAlignment = Alignment.Top) {
            Text(symbol, fontSize = 22.sp, color = AnclaColors.Teal)
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = AnclaColors.Ink)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = AnclaColors.MutedInk)
            }
        }
    }
}

@Composable
private fun WelcomeBanner(title: String, detail: String) {
    val sunMotion = rememberInfiniteTransition(label = "sol animado")
    val rotation by sunMotion.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(tween(3_800, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
        label = "balanceo del sol",
    )
    val shape = RoundedCornerShape(27.dp)
    Box(
        Modifier.fillMaxWidth().clip(shape).background(
            Brush.linearGradient(listOf(Color(0xFF4F8878), Color(0xFF75A88E), Color(0xFF9BB98E))),
        ).padding(horizontal = 20.dp, vertical = 21.dp),
    ) {
        Canvas(Modifier.matchParentSize()) {
            drawCircle(Color.White.copy(alpha = 0.08f), radius = size.minDimension * 0.44f, center = androidx.compose.ui.geometry.Offset(size.width * 0.92f, size.height * 0.06f))
            drawCircle(Color(0xFFFFE5A8).copy(alpha = 0.18f), radius = size.minDimension * 0.24f, center = androidx.compose.ui.geometry.Offset(size.width * 0.92f, size.height * 0.82f))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(title, style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Bold)
                Text(detail, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.94f))
            }
            Text("☀", modifier = Modifier.graphicsLayer { rotationZ = rotation }, fontSize = 49.sp, color = Color(0xFFFFE7A7))
        }
    }
}

@Composable
private fun StaggeredEntry(delayMillis: Long, content: @Composable () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(delayMillis)
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(320)) + slideInVertically(tween(320)) { it / 8 },
    ) { content() }
}
