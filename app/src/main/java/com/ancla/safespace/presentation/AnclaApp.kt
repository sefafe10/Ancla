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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import com.ancla.safespace.data.local.SecureStore
import com.ancla.safespace.data.local.UserData
import com.ancla.safespace.data.local.MoodEntry
import com.ancla.safespace.data.local.JournalEntry
import com.ancla.safespace.data.local.SupportContact
import com.ancla.safespace.domain.model.EmergencyHotlines
import com.ancla.safespace.presentation.theme.AnclaColors
import com.ancla.safespace.presentation.theme.AnclaTheme
import com.ancla.safespace.presentation.places.SupportMapScreen
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

private enum class Screen { ONBOARDING, HOME, MOOD, PROGRESS, JOURNAL, SUPPORT, BREATHING, GROUNDING, PLAN, MAP, RELAX, MINDFUL, GOALS, RESOURCES }

private data class SafetyPlanPrompt(val title: String, val question: String, val hint: String)
private data class ShareablePlanSection(val number: Int, val title: String, val content: String)

private val planPrompts = listOf(
    SafetyPlanPrompt("Señales de alerta", "¿Qué cambios te indican que empieza un momento difícil y que puede ayudarte usar este plan?", "Ej.: aislarme, sentirme sin esperanza o dormir peor"),
    SafetyPlanPrompt("Cosas que puedo hacer por mi cuenta", "¿Qué paso pequeño puedes probar durante los próximos minutos para cuidarte?", "Ej.: respirar con calma, escuchar algo que me gusta o ir a un lugar común"),
    SafetyPlanPrompt("Personas o lugares para distraerme", "¿Con quién puedes hablar o a qué lugar seguro puedes ir para sentirte acompañado/a?", "Anota un nombre, teléfono o lugar que te resulte accesible"),
    SafetyPlanPrompt("Personas a quienes puedo pedir ayuda", "¿A quién puedes decirle con claridad que necesitas compañía o apoyo?", "Anota cómo contactar y qué te gustaría pedir"),
    SafetyPlanPrompt("Apoyo profesional y servicios", "¿Qué profesional, centro de salud o línea de ayuda puedes contactar?", "Anota nombres, teléfonos y horarios si los conoces"),
    SafetyPlanPrompt("Cómo hacer más seguro mi entorno", "¿Qué puedes pedir a alguien de confianza que guarde, retire o limite mientras atraviesas un momento difícil?", "Anota una petición concreta que te resulte posible"),
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
    var selectedMoodDraft by remember { mutableStateOf<String?>(null) }

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
                bottomBar = {
                    if (screen !in listOf(Screen.ONBOARDING, Screen.MOOD, Screen.BREATHING, Screen.GROUNDING, Screen.RELAX, Screen.MINDFUL, Screen.PLAN, Screen.MAP, Screen.GOALS, Screen.RESOURCES)) {
                        Row(Modifier.fillMaxWidth().background(Color.White).padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceAround) {
                            listOf(Screen.HOME to "Inicio", Screen.PROGRESS to "Progreso", Screen.JOURNAL to "Diario", Screen.SUPPORT to "Apoyo").forEach { (destination, label) ->
                                TextButton(onClick = { screen = destination }) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(when (destination) { Screen.HOME -> "⌂"; Screen.PROGRESS -> "▥"; Screen.JOURNAL -> "✎"; else -> "♡" }, fontSize = 19.sp, color = if (screen == destination) AnclaColors.Teal else AnclaColors.MutedInk)
                                        Text(label, style = MaterialTheme.typography.labelSmall, color = if (screen == destination) AnclaColors.Teal else AnclaColors.MutedInk)
                                    }
                                }
                            }
                        }
                    }
                },
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
                            moodToday = userData.moodEntries.find { it.day == LocalDate.now().toEpochDay() },
                            onMood = { mood -> selectedMoodDraft = mood; screen = Screen.MOOD },
                        )
                        Screen.BREATHING -> BreathingScreen(Modifier.padding(padding))
                        Screen.MOOD -> MoodCheckInScreen(
                            initial = userData.moodEntries.find { it.day == LocalDate.now().toEpochDay() },
                            preferredMood = selectedMoodDraft,
                            modifier = Modifier.padding(padding),
                            onSave = { entry ->
                                selectedMoodDraft = null
                                persist(userData.copy(moodEntries = (userData.moodEntries.filterNot { it.day == entry.day } + entry).sortedBy { it.day }), Screen.HOME)
                            },
                        )
                        Screen.PROGRESS -> MoodProgressScreen(userData.moodEntries, Modifier.padding(padding))
                        Screen.JOURNAL -> JournalScreen(
                            entries = userData.journalEntries,
                            modifier = Modifier.padding(padding),
                            onSave = { entry -> persist(userData.copy(journalEntries = listOf(entry) + userData.journalEntries), Screen.JOURNAL) },
                            onDelete = { entry -> persist(userData.copy(journalEntries = userData.journalEntries - entry)) },
                        )
                        Screen.SUPPORT -> SupportHubScreen(
                            contacts = userData.supportContacts, modifier = Modifier.padding(padding),
                            onPlan = { screen = Screen.PLAN }, onMap = { screen = Screen.MAP }, onBreathing = { screen = Screen.BREATHING },
                            onGrounding = { screen = Screen.GROUNDING }, onRelax = { screen = Screen.RELAX }, onMindful = { screen = Screen.MINDFUL },
                            onGoals = { screen = Screen.GOALS }, onResources = { screen = Screen.RESOURCES },
                            onSaveContact = { updated -> persist(userData.copy(supportContacts = updated)) },
                        )
                        Screen.RELAX -> GuidedPracticeScreen("Relajación muscular suave", "Puedes saltarte cualquier zona y parar si algo te incomoda.", listOf("Acomódate y nota el apoyo de tu cuerpo.", "Tensa suavemente las manos durante unos segundos; después suéltalas.", "Sube los hombros con suavidad y deja que bajen.", "Aprieta ligeramente los pies y relájalos.", "Observa cómo te sientes ahora, sin tener que cambiar nada."), Modifier.padding(padding), onFinish = { screen = Screen.HOME })
                        Screen.MINDFUL -> GuidedPracticeScreen("Pausa de atención plena", "No necesitas dejar la mente en blanco.", listOf("Haz una pausa y nota dónde estás.", "Siente el contacto de tus pies con el suelo.", "Escucha tres sonidos, cercanos o lejanos.", "Observa un pensamiento como algo que pasa por tu mente.", "Elige una pequeña acción que te cuide ahora."), Modifier.padding(padding), onFinish = { screen = Screen.HOME })
                        Screen.GOALS -> GoalsScreen(userData.completedGoals, Modifier.padding(padding), { done -> persist(userData.copy(completedGoals = done)) })
                        Screen.RESOURCES -> ResourcesScreen(Modifier.padding(padding))
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
                CrisisDialog(
                    userData = userData,
                    contacts = userData.supportContacts,
                    onDismiss = { showCrisis = false },
                    onOpenPlan = { showCrisis = false; screen = Screen.PLAN },
                )
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
        MascotMessageCard("¡Hola! Soy Anclita", "Cuando elijas tu país, busco tu línea de apoyo. Mientras, practico una voltereta.", MascotMood.GREETING)
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
                else onContinue(initial.copy(countryCode = selectedCode, customNumber = customNumber.trim(), customEmergencyNumber = customEmergency.trim()))
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
    moodToday: MoodEntry?,
    onMood: (String?) -> Unit,
) {
    Column(
        modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(AnclaColors.Canvas, Color(0xFFFFF4E9), AnclaColors.Canvas)),
        ).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp),
    ) {
        MascotMessageCard(
            title = when (moodToday?.mood) { "low", "very_low" -> "Me quedo aquí contigo."; "well" -> "¡Esa energía te sienta genial!"; else -> "Hola. Aquí tienes un ratito para ti." },
            detail = when (moodToday?.mood) { "low", "very_low" -> "No hace falta arreglarlo todo ahora. Un paso pequeño basta."; "well" -> "Celebremos lo pequeño, a tu manera."; else -> "Qué bueno que te hayas regalado este momento." },
            motion = when (moodToday?.mood) {
                "low", "very_low" -> MascotMood.CALM
                "well" -> MascotMood.JOYFUL
                else -> MascotMood.GREETING
            },
        )
        Text("¿Cómo te encuentras hoy?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Puedes expresarlo a tu ritmo, sin presiones.", color = AnclaColors.MutedInk)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Bien" to "🙂", "Regular" to "😐", "Mal" to "😞", "Muy mal" to "😣").forEachIndexed { index, item ->
                val moodCode = listOf("well", "okay", "low", "very_low")[index]
                val selected = moodToday?.mood == moodCode
                Card(
                    onClick = { onMood(moodCode) },
                    modifier = Modifier.weight(1f).height(76.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = if (selected) AnclaColors.TealTint else Color.White),
                    border = if (selected) BorderStroke(1.dp, AnclaColors.Teal) else null,
                ) {
                    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Text(item.second, fontSize = 24.sp)
                        Text(item.first, style = MaterialTheme.typography.labelMedium, color = AnclaColors.Ink)
                    }
                }
            }
        }
        OutlinedButton(onClick = { onMood(null) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
            Text(if (moodToday == null) "Registrar cómo me siento" else "Actualizar registro de hoy", color = AnclaColors.Teal)
        }
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
private fun CrisisDialog(userData: UserData, contacts: List<SupportContact>, onDismiss: () -> Unit, onOpenPlan: () -> Unit) {
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
                if (contacts.isNotEmpty()) {
                    HorizontalDivider()
                    Text("Personas de confianza", fontWeight = FontWeight.SemiBold)
                    contacts.sortedByDescending { it.isFavorite }.forEach { contact ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(if (contact.isFavorite) "★ ${contact.name}" else contact.name, Modifier.weight(1f))
                            TextButton(onClick = { dial(context, contact.phone) }) { Text("Llamar") }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                Button(onClick = { dial(context, emergencyNumber) }) { Text("Llamar a emergencias") }
                TextButton(onClick = { dial(context, hotlineNumber) }, enabled = hotlineNumber.isNotBlank()) { Text("Llamar a la línea de apoyo") }
                TextButton(onClick = onOpenPlan) { Text("Abrir mi plan de seguridad") }
                TextButton(onClick = onDismiss) { Text("Cerrar") }
            }
        },
    )
}

@Composable
private fun MoodCheckInScreen(initial: MoodEntry?, preferredMood: String?, modifier: Modifier, onSave: (MoodEntry) -> Unit) {
    var mood by remember(initial, preferredMood) { mutableStateOf(preferredMood ?: initial?.mood ?: "okay") }
    var distress by remember(initial) { mutableStateOf((initial?.distress ?: 4).toFloat()) }
    var anxiety by remember(initial) { mutableStateOf((initial?.anxiety ?: 4).toFloat()) }
    var note by remember(initial) { mutableStateOf(initial?.note.orEmpty()) }
    val choices = listOf("well" to "🙂 Bien", "okay" to "😐 Regular", "low" to "😞 Mal", "very_low" to "😣 Muy mal")
    Column(
        modifier.fillMaxSize().background(Brush.verticalGradient(listOf(AnclaColors.Canvas, Color(0xFFFFF2E7)))).verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        WelcomeBanner("Un momento para escucharte", "No hay respuestas buenas o malas. Registra solo lo que te apetezca.")
        Text("¿Cómo te sientes?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            choices.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    row.forEach { (code, label) ->
                        val chosen = mood == code
                        Card(
                            onClick = { mood = code }, modifier = Modifier.weight(1f).height(58.dp),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = if (chosen) AnclaColors.TealTint else Color.White),
                            border = if (chosen) BorderStroke(1.dp, AnclaColors.Teal) else null,
                        ) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(label, fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Normal) } }
                    }
                }
            }
        }
        MoodSlider("Intensidad del malestar", distress) { distress = it }
        MoodSlider("Ansiedad", anxiety) { anxiety = it }
        OutlinedTextField(
            value = note, onValueChange = { note = it.take(500) }, label = { Text("Una nota para ti (opcional)") },
            supportingText = { Text("Se guarda cifrada en este dispositivo.") }, modifier = Modifier.fillMaxWidth(), minLines = 3,
            shape = RoundedCornerShape(18.dp),
        )
        Button(onClick = { onSave(MoodEntry(LocalDate.now().toEpochDay(), mood, distress.toInt(), anxiety.toInt(), note.trim())) }, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(18.dp)) {
            Text("Guardar registro")
        }
        Text("Un día difícil no es una nota ni una penalización. Este registro sirve solo para observarte con amabilidad.", color = AnclaColors.MutedInk, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun MoodSlider(label: String, value: Float, onValueChange: (Float) -> Unit) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label, fontWeight = FontWeight.SemiBold)
                Text("${value.toInt()} / 10", color = AnclaColors.Teal, fontWeight = FontWeight.Bold)
            }
            Slider(value = value, onValueChange = onValueChange, valueRange = 1f..10f, steps = 8)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Suave", style = MaterialTheme.typography.labelSmall, color = AnclaColors.MutedInk)
                Text("Intenso", style = MaterialTheme.typography.labelSmall, color = AnclaColors.MutedInk)
            }
        }
    }
}

@Composable
private fun MoodProgressScreen(entries: List<MoodEntry>, modifier: Modifier) {
    var monthly by remember { mutableStateOf(false) }
    val today = LocalDate.now().toEpochDay()
    val period = if (monthly) 30 else 7
    val filtered = entries.filter { it.day in (today - period + 1)..today }
    val values = (today - period + 1..today).map { day -> filtered.find { it.day == day } }
    Column(modifier.fillMaxSize().background(AnclaColors.Canvas).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        WelcomeBanner("Tu evolución", "Un espacio para observar, sin juicios ni puntuaciones.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChipLike("7 días", !monthly) { monthly = false }
            FilterChipLike("30 días", monthly) { monthly = true }
        }
        Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Intensidad que registraste", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Cada punto refleja solo lo que decidiste anotar. Los huecos no significan nada malo.", color = AnclaColors.MutedInk, style = MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth().height(150.dp), horizontalArrangement = Arrangement.spacedBy(if (monthly) 2.dp else 8.dp), verticalAlignment = Alignment.Bottom) {
                    values.forEach { entry ->
                        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.CenterHorizontally) {
                            if (entry != null) {
                                Row(Modifier.fillMaxWidth().height(110.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom) {
                                    Box(Modifier.weight(1f).height((entry.distress * 10).dp).clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp)).background(AnclaColors.Teal))
                                    Spacer(Modifier.width(2.dp))
                                    Box(Modifier.weight(1f).height((entry.anxiety * 10).dp).clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp)).background(AnclaColors.Peach))
                                }
                            } else Spacer(Modifier.weight(1f))
                            Spacer(Modifier.height(5.dp))
                            Box(Modifier.fillMaxWidth().height(1.dp).background(AnclaColors.TealTint))
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("● Malestar", color = AnclaColors.Teal, style = MaterialTheme.typography.labelSmall)
                    Text("● Ansiedad", color = AnclaColors.Peach, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        val average = filtered.takeIf { it.isNotEmpty() }?.map { it.anxiety }.orEmpty()
        Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = AnclaColors.TealTint)) {
            Text(if (average.isEmpty()) "Aún no hay registros en este periodo. Puedes empezar cuando te apetezca." else "Has anotado ${filtered.size} ${if (filtered.size == 1) "día" else "días"} en este periodo. No hace falta registrar todos los días.", Modifier.padding(18.dp), color = AnclaColors.Ink)
        }
    }
}

@Composable
private fun FilterChipLike(label: String, selected: Boolean, onClick: () -> Unit) {
    androidx.compose.material3.FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

@Composable
private fun JournalScreen(entries: List<JournalEntry>, modifier: Modifier, onSave: (JournalEntry) -> Unit, onDelete: (JournalEntry) -> Unit) {
    val context = LocalContext.current
    var prompt by remember { mutableStateOf("¿Qué te gustaría que alguien comprendiera de este día?") }
    var text by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf<JournalEntry?>(null) }
    val prompts = listOf("¿Qué te gustaría que alguien comprendiera de este día?", "¿Qué momento fue difícil y qué te ayudó a atravesarlo?", "¿Qué necesitas ahora mismo, aunque sea algo pequeño?", "¿Qué le dirías con amabilidad a alguien en tu situación?")
    Column(modifier.fillMaxSize().background(AnclaColors.Canvas).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        WelcomeBanner("Diario privado", "Escribe para ti. Tus entradas se guardan cifradas en este dispositivo.")
        Text("Una pregunta para empezar", fontWeight = FontWeight.SemiBold)
        TextButton(onClick = { prompt = prompts[(prompts.indexOf(prompt) + 1) % prompts.size] }) { Text("↻  Cambiar pregunta", color = AnclaColors.Teal) }
        Text(prompt, color = AnclaColors.MutedInk)
        OutlinedTextField(text, { text = it.take(5000) }, modifier = Modifier.fillMaxWidth(), minLines = 5, label = { Text("Escribe lo que quieras") }, shape = RoundedCornerShape(18.dp))
        Button(onClick = { if (text.isNotBlank()) { onSave(JournalEntry(UUID.randomUUID().toString(), System.currentTimeMillis(), prompt, text.trim())); text = "" } }, enabled = text.isNotBlank(), modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(18.dp)) { Text("Guardar en mi diario") }
        HorizontalDivider()
        Text("Tus entradas", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if (entries.isEmpty()) Text("Todavía no hay entradas. Puedes volver cuando quieras.", color = AnclaColors.MutedInk)
        entries.sortedByDescending { it.createdAt }.forEach { entry ->
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm").format(java.time.Instant.ofEpochMilli(entry.createdAt).atZone(java.time.ZoneId.systemDefault())), style = MaterialTheme.typography.labelMedium, color = AnclaColors.Teal)
                    Text(entry.prompt, fontWeight = FontWeight.SemiBold)
                    Text(entry.text, maxLines = 5, overflow = TextOverflow.Ellipsis)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = {
                            val share = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_SUBJECT, "Una entrada de mi diario de Ancla"); putExtra(Intent.EXTRA_TEXT, "${entry.prompt}\n\n${entry.text}") }
                            context.startActivity(Intent.createChooser(share, "Exportar entrada"))
                        }) { Text("Exportar") }
                        TextButton(onClick = { confirmDelete = entry }) { Text("Eliminar", color = AnclaColors.Rose) }
                    }
                }
            }
        }
    }
    confirmDelete?.let { entry -> AlertDialog(onDismissRequest = { confirmDelete = null }, title = { Text("¿Eliminar esta entrada?") }, text = { Text("Se quitará de este dispositivo.") }, confirmButton = { TextButton(onClick = { onDelete(entry); confirmDelete = null }) { Text("Eliminar") } }, dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancelar") } }) }
}

@Composable
private fun SupportHubScreen(
    contacts: List<SupportContact>, modifier: Modifier, onPlan: () -> Unit, onMap: () -> Unit,
    onBreathing: () -> Unit, onGrounding: () -> Unit, onRelax: () -> Unit, onMindful: () -> Unit,
    onGoals: () -> Unit, onResources: () -> Unit, onSaveContact: (List<SupportContact>) -> Unit,
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }; var phone by remember { mutableStateOf("") }
    var markFavorite by remember { mutableStateOf(false) }
    var editingContactId by remember { mutableStateOf<String?>(null) }
    Column(modifier.fillMaxSize().background(AnclaColors.Canvas).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
        WelcomeBanner("Mi red de apoyo", "Personas y recursos que puedes elegir contactar.")
        Text("Contactos de confianza", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Se guardan cifrados. Ancla nunca envía alertas ni mensajes automáticamente.", color = AnclaColors.MutedInk, style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(name, { name = it }, modifier = Modifier.weight(1f), label = { Text("Nombre") }, singleLine = true)
            OutlinedTextField(phone, { phone = it }, modifier = Modifier.weight(1f), label = { Text("Teléfono") }, singleLine = true)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = markFavorite, onCheckedChange = { markFavorite = it })
            Text("Marcar como favorito", color = AnclaColors.Ink)
        }
        Button(onClick = {
            val targetId = editingContactId
            if (name.isNotBlank() && phone.isNotBlank()) {
                val updated = if (targetId == null) {
                    contacts + SupportContact(name.trim(), phone.trim(), markFavorite, UUID.randomUUID().toString())
                } else {
                    contacts.map { contact ->
                        if (contact.id == targetId) contact.copy(name = name.trim(), phone = phone.trim(), isFavorite = markFavorite) else contact
                    }
                }
                onSaveContact(updated)
                name = ""
                phone = ""
                markFavorite = false
                editingContactId = null
            }
        }, enabled = name.isNotBlank() && phone.isNotBlank(), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) { Text(if (editingContactId == null) "Añadir contacto" else "Guardar cambios") }
        val sortedContacts = contacts.sortedWith(compareByDescending<SupportContact> { it.isFavorite }.thenBy { it.name.lowercase() })
        sortedContacts.forEach { contact ->
            val favoriteTint = Color(0xFFEAF4EE)
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = if (contact.isFavorite) favoriteTint else Color.White),
                border = if (contact.isFavorite) BorderStroke(1.dp, AnclaColors.TealTint) else null,
            ) {
                Column(Modifier.fillMaxWidth().padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(contact.name, fontWeight = FontWeight.Bold)
                            Text(contact.phone, color = AnclaColors.MutedInk)
                        }
                        TextButton(onClick = {
                            onSaveContact(contacts.map { current -> if (current.id == contact.id) current.copy(isFavorite = !current.isFavorite) else current })
                        }) {
                            Text(if (contact.isFavorite) "★ Favorito" else "☆ Favorito", color = if (contact.isFavorite) AnclaColors.Teal else AnclaColors.MutedInk, maxLines = 1, softWrap = false)
                        }
                    }
                    OutlinedButton(onClick = { dial(context, contact.phone) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Text("Llamar", maxLines = 1) }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { name = contact.name; phone = contact.phone; markFavorite = contact.isFavorite; editingContactId = contact.id }, modifier = Modifier.weight(1f)) { Text("Editar", maxLines = 1) }
                        TextButton(onClick = { onSaveContact(contacts.filterNot { it.id == contact.id }) }, modifier = Modifier.weight(1f)) { Text("Quitar", color = AnclaColors.Rose, maxLines = 1, softWrap = false) }
                    }
                }
            }
        }
        HorizontalDivider()
        Text("Herramientas", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        ActionCard("Mi plan de seguridad", "Tus pasos y apoyos, disponibles sin conexión.", "♡", AnclaColors.LilacTint, AnclaColors.Lilac, onPlan)
        ActionCard("Buscar ayuda cercana", "Centros y profesionales en tu zona.", "⌖", AnclaColors.GoldTint, Color(0xFF9B772F), onMap)
        ActionCard("Ejercicios de regulación", "Respiración, sentidos y otras pausas.", "◌", AnclaColors.TealTint, AnclaColors.Teal, onBreathing)
        ActionCard("Grounding 5–4–3–2–1", "Volver a tus sentidos con calma.", "✿", AnclaColors.PeachTint, Color(0xFF9B5C3D), onGrounding)
        ActionCard("Relajación muscular", "Soltar algunas zonas suavemente.", "☁", AnclaColors.TealTint, AnclaColors.Teal, onRelax)
        ActionCard("Pausa de atención plena", "Un ejercicio breve y flexible.", "◉", AnclaColors.LilacTint, AnclaColors.Lilac, onMindful)
        ActionCard("Pequeños pasos", "Ideas voluntarias, sin rachas.", "☀", AnclaColors.GoldTint, Color(0xFF9B772F), onGoals)
        ActionCard("Biblioteca de recursos", "Información y enlaces de fuentes públicas.", "▤", AnclaColors.PeachTint, Color(0xFF9B5C3D), onResources)
    }
}

@Composable
private fun GuidedPracticeScreen(title: String, detail: String, steps: List<String>, modifier: Modifier, onFinish: () -> Unit) {
    var step by remember(title) { mutableStateOf(0) }
    Column(modifier.fillMaxSize().background(AnclaColors.Canvas).verticalScroll(rememberScrollState()).padding(22.dp), verticalArrangement = Arrangement.spacedBy(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        WelcomeBanner(title, detail)
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(30.dp), colors = CardDefaults.cardColors(containerColor = listOf(AnclaColors.TealTint, AnclaColors.PeachTint, AnclaColors.LilacTint, AnclaColors.GoldTint)[step % 4])) {
            Box(Modifier.fillMaxWidth().heightIn(min = 230.dp).padding(horizontal = 24.dp, vertical = 30.dp), contentAlignment = Alignment.Center) {
                Text(steps[step], modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, color = AnclaColors.Ink, fontWeight = FontWeight.SemiBold)
            }
        }
        Text("${step + 1} de ${steps.size}", color = AnclaColors.MutedInk)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { if (step > 0) step-- }, enabled = step > 0) { Text("Atrás") }
            Button(onClick = { if (step < steps.lastIndex) step++ else step = 0 }) { Text(if (step < steps.lastIndex) "Siguiente" else "Empezar de nuevo") }
        }
        TextButton(onClick = onFinish) { Text("Terminar aquí", color = AnclaColors.Teal) }
    }
}

@Composable
private fun GoalsScreen(done: List<String>, modifier: Modifier, onChange: (List<String>) -> Unit) {
    val options = listOf("Tomar un vaso de agua", "Salir un momento o abrir la ventana", "Escribir o hablar con alguien", "Descansar sin exigirme", "Hacer algo que me resulte agradable")
    val today = LocalDate.now().toString()
    val completed = done.filter { it.startsWith("$today|") }.map { it.substringAfter('|') }.toSet()
    Column(modifier.fillMaxSize().background(AnclaColors.Canvas).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        WelcomeBanner("Un paso pequeño", "Son ideas opcionales. Puedes elegir ninguna, una o varias.")
        options.forEach { option ->
            val checked = option in completed
            Card(onClick = { val next = if (checked) completed - option else completed + option; onChange(done.filterNot { it.startsWith("$today|") } + next.map { "$today|$it" }) }, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = if (checked) AnclaColors.TealTint else Color.White)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Checkbox(checked, onCheckedChange = null); Text(option, Modifier.padding(start = 10.dp)) }
            }
        }
        Text("No hay rachas ni penalizaciones. Si hoy no puedes, está bien.", color = AnclaColors.MutedInk)
    }
}

@Composable
private fun ResourcesScreen(modifier: Modifier) {
    val context = LocalContext.current
    val resources = listOf(
        Triple("En tiempos de estrés", "Guía de la OMS con técnicas prácticas de anclaje y acciones basadas en valores.", "https://www.who.int/es/publications/b/53604"),
        Triple("Depresión", "Información de la OMS sobre síntomas, apoyo y opciones de atención.", "https://www.who.int/es/news-room/fact-sheets/detail/depression/"),
        Triple("Respiración para el estrés", "Ejercicio de respiración calmada del NHS. No fuerces el ritmo.", "https://www.nhs.uk/mental-health/self-help/guides-tools-and-activities/breathing-exercises-for-stress/"),
        Triple("Línea 024", "Atención en España para personas con pensamientos o riesgo suicida y sus allegados. Si hay una emergencia inmediata, llama al 112.", "https://www.sanidad.gob.es/linea024/home.htm"),
    )
    Column(modifier.fillMaxSize().background(AnclaColors.Canvas).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        WelcomeBanner("Recursos para comprender", "Información educativa de fuentes públicas. No sustituye atención profesional y no ha sido revisada clínicamente para esta app.")
        resources.forEach { (title, description, url) ->
            Card(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } }, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = AnclaColors.Teal); Text(description); Text("Abrir fuente oficial ↗", style = MaterialTheme.typography.labelLarge, color = AnclaColors.Teal) }
            }
        }
    }
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
    val context = LocalContext.current
    var showShareDialog by remember { mutableStateOf(false) }
    var showShareError by remember { mutableStateOf(false) }
    val shareableSections = answers.mapIndexedNotNull { index, answer ->
        answer.trim().takeIf { it.isNotBlank() }?.let { ShareablePlanSection(index + 1, planPrompts[index].title, it) }
    }
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
        OutlinedButton(
            onClick = { showShareDialog = true },
            enabled = shareableSections.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(18.dp),
        ) { Text("Compartir una copia") }
        Text(
            if (shareableSections.isEmpty()) "Añade un paso para preparar una copia que puedas compartir."
            else "El plan guardado se cifra en este dispositivo. Compartir abrirá la app que elijas, sin crear un enlace web.",
            style = MaterialTheme.typography.bodySmall,
            color = AnclaColors.MutedInk,
        )
        if (showShareDialog) {
            SafetyPlanShareDialog(
                sections = shareableSections,
                onDismiss = { showShareDialog = false },
                onContinue = { selectedSections ->
                    showShareDialog = false
                    val message = buildString {
                        appendLine("Mi plan de seguridad")
                        appendLine()
                        selectedSections.forEach { section ->
                            appendLine("${section.number}. ${section.title}")
                            appendLine(section.content)
                            appendLine()
                        }
                    }.trim()
                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "Mi plan personal")
                        putExtra(Intent.EXTRA_TEXT, message)
                    }
                    try {
                        context.startActivity(Intent.createChooser(sendIntent, "Compartir una copia"))
                    } catch (_: ActivityNotFoundException) {
                        showShareError = true
                    }
                },
            )
        }
        if (showShareError) {
            AlertDialog(
                onDismissRequest = { showShareError = false },
                title = { Text("No se pudo abrir el menú para compartir") },
                text = { Text("Puedes intentarlo de nuevo más tarde. Tu plan sigue guardado únicamente en este dispositivo.") },
                confirmButton = { TextButton(onClick = { showShareError = false }) { Text("Entendido") } },
            )
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun SafetyPlanShareDialog(
    sections: List<ShareablePlanSection>,
    onDismiss: () -> Unit,
    onContinue: (List<ShareablePlanSection>) -> Unit,
) {
    val selected = remember(sections) { mutableStateListOf<Boolean>().apply { sections.forEach { add(true) } } }
    val hasSelection = selected.any { it }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Revisa qué quieres compartir") },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Elige los apartados que quieres incluir. Al continuar, Android abrirá el menú para elegir una app y destinatario.")
                sections.forEachIndexed { index, section ->
                    Column(
                        Modifier.fillMaxWidth()
                            .toggleable(
                                value = selected[index],
                                role = Role.Checkbox,
                                onValueChange = { selected[index] = it },
                            )
                            .padding(vertical = 4.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = selected[index], onCheckedChange = null)
                            Text(section.title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        }
                        Text(
                            section.content,
                            modifier = Modifier.padding(start = 48.dp, end = 8.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = AnclaColors.MutedInk,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Text(
                    "Ancla no crea un enlace ni guarda una copia en la web. La app que elijas recibirá el texto marcado y podría conservarlo o reenviarlo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = AnclaColors.MutedInk,
                )
            }
        },
        confirmButton = {
            Button(
                enabled = hasSelection,
                onClick = { onContinue(sections.filterIndexed { index, _ -> selected[index] }) },
            ) { Text("Elegir app") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun PlanStepCard(index: Int, prompt: SafetyPlanPrompt, value: String, onValueChange: (String) -> Unit) {
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
                Text(prompt.title, style = MaterialTheme.typography.titleMedium, color = AnclaColors.Ink, fontWeight = FontWeight.SemiBold)
            }
            Text(prompt.question, style = MaterialTheme.typography.bodyMedium, color = AnclaColors.MutedInk)
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text("Anota una idea, contacto o paso") },
                placeholder = { Text(prompt.hint, style = MaterialTheme.typography.bodySmall) },
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
private fun MascotMessageCard(title: String, detail: String, motion: MascotMood) {
    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, AnclaColors.TealTint),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = AnclaColors.Ink)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = AnclaColors.MutedInk)
            }
            AnclaMascot(motion, Modifier.size(116.dp))
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
