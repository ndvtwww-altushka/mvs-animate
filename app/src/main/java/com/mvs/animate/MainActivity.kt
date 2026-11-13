package com.mvs.animate

import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.mvs.animate.data.OpenCodeClient
import com.mvs.animate.data.MotionRecipeFactory
import com.mvs.animate.data.PlanParser
import com.mvs.animate.data.ProjectCodec
import com.mvs.animate.data.WikimediaClient
import com.mvs.animate.format.AlightMotionXml
import com.mvs.animate.format.ImportInspector
import com.mvs.animate.media.AudioAnalyzer
import com.mvs.animate.model.AudioAnalysis
import com.mvs.animate.model.ClipAnimation
import com.mvs.animate.model.ClipKind
import com.mvs.animate.model.EditClip
import com.mvs.animate.model.EditProject
import com.mvs.animate.model.EffectPreset
import com.mvs.animate.model.PipelineStage
import com.mvs.animate.model.SourceAsset
import com.mvs.animate.model.StageStatus
import com.mvs.animate.model.builtInEffects
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

private val Bg = Color(0xFF090B12)
private val Panel = Color(0xFF141927)
private val Panel2 = Color(0xFF1C2435)
private val Accent = Color(0xFFB9A1FF)
private val Mint = Color(0xFF78E2C0)
private val Muted = Color(0xFF9BA6BD)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MvsTheme { MvsAnimateApp() } }
    }
}

@Composable
private fun MvsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent,
            onPrimary = Color(0xFF160E2B),
            secondary = Mint,
            background = Bg,
            surface = Panel,
            surfaceVariant = Panel2,
            onBackground = Color(0xFFF3F4FA),
            onSurface = Color(0xFFF3F4FA),
            onSurfaceVariant = Muted,
            error = Color(0xFFFF748A),
        ),
        content = content,
    )
}

private enum class AppTab(val label: String) { EDIT("Edit"), AI("AI Studio"), ASSETS("Assets"), SETTINGS("Settings") }

@Composable
private fun MvsAnimateApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(AppTab.EDIT) }
    var project by remember { mutableStateOf(EditProject()) }
    var selectedClipId by remember { mutableStateOf<String?>(null) }
    var topic by remember { mutableStateOf("anime velocity edit, high impact cuts, clean typography") }
    var openCodeUrl by remember { mutableStateOf("http://127.0.0.1:4096") }
    var openCodeUser by remember { mutableStateOf("opencode") }
    var openCodePassword by remember { mutableStateOf("") }
    var openCodeAgent by remember { mutableStateOf("") }
    var connectionStatus by remember { mutableStateOf("Not connected") }
    var isConnecting by remember { mutableStateOf(false) }
    var isGenerating by remember { mutableStateOf(false) }
    var planText by remember { mutableStateOf("") }
    var generationMode by remember { mutableStateOf("plan") }
    var planCuts by remember { mutableStateOf<List<PlanParser.Cut>>(emptyList()) }
    var planQueries by remember { mutableStateOf<List<String>>(emptyList()) }
    var audioAnalysis by remember { mutableStateOf<AudioAnalysis?>(null) }
    var selectedEffect by remember { mutableStateOf<EffectPreset?>(null) }
    var assetQuery by remember { mutableStateOf("anime city night") }
    var assetResults by remember { mutableStateOf<List<SourceAsset>>(emptyList()) }
    var assetLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val stages = remember { mutableStateListOf<PipelineStage>() }
    val logs = remember { mutableStateListOf<String>() }

    fun log(message: String) {
        logs.add(0, "${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}  $message")
        while (logs.size > 160) logs.removeAt(logs.lastIndex)
    }
    fun updateStage(id: String, status: StageStatus, detail: String, duration: Long? = null) {
        val index = stages.indexOfFirst { it.id == id }
        val stage = PipelineStage(id, id, detail, status, duration)
        if (index >= 0) stages[index] = stage else stages.add(stage)
    }

    val mediaPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach { uri ->
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            scope.launch {
                val name = displayName(context, uri)
                val inspection = withContext(Dispatchers.IO) { ImportInspector.inspect(context.contentResolver, uri, name) }
                if (inspection.isProject && inspection.project != null) {
                    project = inspection.project
                    selectedClipId = project.clips.firstOrNull()?.id
                    log("Imported project descriptor: $name")
                    if (inspection.warnings.isNotEmpty()) errorMessage = inspection.warnings.joinToString("\n")
                } else if (inspection.kind != null && inspection.kind != ClipKind.UNKNOWN) {
                    if (inspection.kind == ClipKind.AUDIO) {
                        val audioDuration = readDuration(context, uri).coerceAtLeast(1_000L)
                        val audioClip = EditClip(name = name, kind = ClipKind.AUDIO, uri = uri.toString(), startMs = 0L, durationMs = audioDuration)
                        project = project.copy(
                            musicUri = uri.toString(), musicName = name,
                            musicStartMs = 0L, musicEndMs = audioDuration,
                            clips = project.clips.filterNot { it.kind == ClipKind.AUDIO } + audioClip,
                        )
                        selectedClipId = audioClip.id
                        log("Music attached: $name")
                        audioAnalysis = null
                        updateStage("Audio", StageStatus.WAITING, "Audio selected; local analysis is pending")
                        runCatching {
                            val result = AudioAnalyzer.analyze(context, uri)
                            audioAnalysis = result
                            project = project.copy(musicStartMs = result.suggestedStartMs, musicEndMs = result.suggestedEndMs)
                            updateStage("Audio", StageStatus.DONE, result.description)
                            log("Audio analysis: ${result.description}; suggested ${formatMs(result.suggestedStartMs)}–${formatMs(result.suggestedEndMs)}")
                        }.onFailure {
                            updateStage("Audio", StageStatus.FAILED, it.message ?: "Decode failed")
                            errorMessage = "Audio analysis failed: ${it.message}"
                            log("Audio analysis failed: ${it.message}")
                        }
                    } else {
                        val duration = if (inspection.kind == ClipKind.VIDEO) readDuration(context, uri) else 4_000L
                        val templateSlot = if (project.sourceSceneXml != null && inspection.kind == ClipKind.IMAGE) {
                            project.clips.firstOrNull { it.templateLayer && it.kind == ClipKind.IMAGE && (it.uri.isNullOrBlank() || it.uri == it.templateUri) }
                        } else null
                        if (templateSlot != null) {
                            val replacement = templateSlot.copy(name = name, uri = uri.toString(), durationMs = templateSlot.durationMs.coerceAtLeast(1L))
                            project = project.copy(clips = project.clips.map { if (it.id == templateSlot.id) replacement else it })
                            selectedClipId = replacement.id
                            log("Bound image '$name' to Alight Motion template layer ${templateSlot.templateLayerId}")
                        } else {
                            val clip = EditClip(name = name, kind = inspection.kind, uri = uri.toString(),
                                startMs = project.clips.maxOfOrNull { it.startMs + it.durationMs } ?: 0L,
                                durationMs = duration.coerceAtLeast(1_000L))
                            project = project.copy(clips = project.clips + clip)
                            selectedClipId = clip.id
                            log("Added ${inspection.kind.name.lowercase()} asset: $name")
                        }
                    }
                } else {
                    errorMessage = inspection.error ?: "Unsupported file"
                    log("Import rejected: ${inspection.error}")
                }
            }
        }
    }

    val projectPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            scope.launch {
                val name = displayName(context, uri)
                val result = withContext(Dispatchers.IO) { ImportInspector.inspect(context.contentResolver, uri, name) }
                if (result.isProject && result.project != null) {
                    project = result.project
                    selectedClipId = project.clips.firstOrNull()?.id
                    log("Project imported: $name")
                    if (result.warnings.isNotEmpty()) errorMessage = result.warnings.joinToString("\n")
                } else errorMessage = result.error ?: "The selected file was not recognised as a project"
            }
        }
    }

    val xmlExporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/xml")) { uri ->
        if (uri != null) scope.launch {
            val result = AlightMotionXml.exportScene(project)
            withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(result.xml) } }
            log("Alight Motion XML exported")
            if (result.warnings.isNotEmpty()) errorMessage = result.warnings.joinToString("\n")
        }
    }
    val packageExporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val scene = AlightMotionXml.exportScene(project)
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        ZipOutputStream(output).use { zip ->
                            fun put(name: String, text: String) {
                                zip.putNextEntry(ZipEntry(name)); zip.write(text.toByteArray(Charsets.UTF_8)); zip.closeEntry()
                            }
                            put("project.mvsanim", ProjectCodec.encode(project))
                            put("alight-motion/scene.xml", scene.xml)
                            put("README.txt", "MVS Animate project package.\nThe manifest stores Android content URIs for linked media; these URIs may only resolve on the original device. Add/copy media again when moving to another device.\nAlight Motion XML is an interchange draft; review media relinking and unsupported feature warnings.\n")
                        }
                    } ?: error("Could not open output stream")
                    scene.warnings
                }
            }.onSuccess { warnings ->
                log("MVS project package exported")
                if (warnings.isNotEmpty()) errorMessage = warnings.joinToString("\n")
            }.onFailure { errorMessage = "Package export failed: ${it.message}" }
        }
    }

    val selectedClip = project.clips.firstOrNull { it.id == selectedClipId }

    Scaffold(
        containerColor = Bg,
        bottomBar = {
            Surface(color = Color(0xFF0E121D), tonalElevation = 12.dp) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 9.dp), horizontalArrangement = Arrangement.SpaceAround) {
                    AppTab.entries.forEach { item ->
                        val icon = when (item) {
                            AppTab.EDIT -> Icons.Default.Timeline
                            AppTab.AI -> Icons.Default.AutoAwesome
                            AppTab.ASSETS -> Icons.Default.ImageSearch
                            AppTab.SETTINGS -> Icons.Default.Settings
                        }
                        TextButton(onClick = { tab = item }) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(icon, null, tint = if (tab == item) Accent else Muted)
                                Text(item.label, color = if (tab == item) Accent else Muted, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Accent), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Bolt, null, tint = Color(0xFF160E2B))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("MVS ANIMATE", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, letterSpacing = 1.6.sp)
                    Text("AI edit studio · Alight Motion bridge", style = MaterialTheme.typography.labelSmall, color = Muted)
                }
                AssistChip(onClick = { tab = AppTab.SETTINGS }, label = { Text(if (connectionStatus.startsWith("OpenCode")) "AI connected" else "Connect AI") }, leadingIcon = {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(if (connectionStatus.startsWith("OpenCode")) Mint else Muted))
                })
            }
            when (tab) {
                AppTab.EDIT -> EditorScreen(
                    project = project,
                    selectedClipId = selectedClipId,
                    onSelectClip = { selectedClipId = it },
                    onAddMedia = { mediaPicker.launch(arrayOf("image/*", "video/*", "audio/*", "text/plain", "text/vtt", "application/x-subrip")) },
                    onImportProject = { projectPicker.launch(arrayOf("application/xml", "text/xml", "application/zip", "application/json", "application/octet-stream")) },
                    onExportXml = { xmlExporter.launch("${safeName(project.title)}.xml") },
                    onExportPackage = { packageExporter.launch("${safeName(project.title)}.mvsanim.zip") },
                    onAddText = {
                        val clip = EditClip(name = "Title text", kind = ClipKind.TEXT, text = "YOUR TEXT", startMs = project.clips.maxOfOrNull { it.startMs + it.durationMs } ?: 0L, durationMs = 2_000L)
                        project = project.copy(clips = project.clips + clip); selectedClipId = clip.id
                    },
                    onUpdateProject = { project = it },
                    onMoveClip = { id, delta ->
                        val list = project.clips.toMutableList(); val index = list.indexOfFirst { it.id == id }; val next = index + delta
                        if (index >= 0 && next in list.indices) { val item = list.removeAt(index); list.add(next, item); project = project.copy(clips = list) }
                    },
                    onDeleteClip = { id -> project = project.copy(clips = project.clips.filterNot { it.id == id }); if (selectedClipId == id) selectedClipId = null },
                    onUpdateClip = { updated -> project = project.copy(clips = project.clips.map { if (it.id == updated.id) updated else it }) },
                    onOpenEffects = { tab = AppTab.ASSETS },
                    onUpdateMusicRange = { start, end -> project = project.copy(musicStartMs = start, musicEndMs = end) },
                )
                AppTab.AI -> AiStudioScreen(
                    topic = topic, onTopicChange = { topic = it },
                    connectionStatus = connectionStatus,
                    isGenerating = isGenerating,
                    stages = stages,
                    logs = logs,
                    planText = planText,
                    planCuts = planCuts,
                    planQueries = planQueries,
                    audioAnalysis = audioAnalysis,
                    selectedClipName = selectedClipId?.let { id -> project.clips.firstOrNull { it.id == id }?.name },
                    onGenerate = { mode ->
                        if (isGenerating) return@AiStudioScreen
                        generationMode = mode
                        scope.launch {
                            isGenerating = true
                            planText = ""
                            planCuts = emptyList()
                            planQueries = emptyList()
                            stages.clear()
                            updateStage("Inputs", StageStatus.RUNNING, "Checking project and gathering visual assets")
                            log("Pipeline started for topic: $topic")
                            try {
                                val imageAttachments = project.clips.filter { it.kind == ClipKind.IMAGE && !it.uri.isNullOrBlank() }.take(4).mapNotNull { clip ->
                                    runCatching {
                                        val bytes = withContext(Dispatchers.IO) {
                                            context.contentResolver.openInputStream(Uri.parse(clip.uri!!))?.use { input ->
                                                val out = ByteArrayOutputStream(); val buffer = ByteArray(16 * 1024); var total = 0
                                                while (true) { val n = input.read(buffer); if (n < 0) break; total += n; if (total > 5 * 1024 * 1024) error("Image exceeds 5 MB") ; out.write(buffer, 0, n) }
                                                out.toByteArray()
                                            } ?: error("Could not read image")
                                        }
                                        val mime = context.contentResolver.getType(Uri.parse(clip.uri!!)) ?: "image/jpeg"
                                        OpenCodeClient.ImageAttachment(clip.name, mime, bytes)
                                    }.onFailure { log("Image omitted (${clip.name}): ${it.message}") }.getOrNull()
                                }
                                updateStage("Inputs", StageStatus.DONE, "${project.clips.size} timeline layers · ${imageAttachments.size} image(s) attached")

                                if (project.musicUri != null && audioAnalysis == null) {
                                    updateStage("Audio DSP", StageStatus.RUNNING, "Decoding locally; no audio is sent to the model")
                                    val analysis = AudioAnalyzer.analyze(context, Uri.parse(project.musicUri))
                                    audioAnalysis = analysis
                                    project = project.copy(musicStartMs = analysis.suggestedStartMs, musicEndMs = analysis.suggestedEndMs)
                                    updateStage("Audio DSP", StageStatus.DONE, analysis.description)
                                } else if (audioAnalysis != null) updateStage("Audio DSP", StageStatus.DONE, audioAnalysis!!.description)
                                else updateStage("Audio DSP", StageStatus.DONE, "No music loaded; timing plan will use visual pacing")

                                updateStage("OpenCode", StageStatus.RUNNING, "Connecting to $openCodeUrl")
                                val client = OpenCodeClient(openCodeUrl, openCodeUser, openCodePassword)
                                val health = client.health()
                                connectionStatus = health
                                updateStage("OpenCode", StageStatus.DONE, health)
                                val prompt = buildString {
                                    appendLine("You are the editing director AND motion designer for MVS Animate, an Android edit studio with Alight Motion XML interchange.")
                                    appendLine("Create an actionable edit plan and author custom motion recipes, not just effect names. You may inspect attached images if the model supports vision. Do not claim to hear audio: music was processed locally and only numeric/text descriptors are provided.")
                                    appendLine("Task mode: $generationMode. Topic and style: $topic")
                                    appendLine("Project: ${project.title}; canvas ${project.width}x${project.height}; ${project.fps} fps.")
                                    appendLine("Timeline assets:")
                                    project.clips.forEach { appendLine("- ${it.name}; kind=${it.kind}; start=${it.startMs}ms; duration=${it.durationMs}ms; effects=${it.effects.joinToString()}; currentMotion=${it.animation?.name ?: "none"}") }
                                    if (generationMode == "motion") {
                                        val target = selectedClipId?.let { id -> project.clips.firstOrNull { it.id == id } }
                                        appendLine("MOTION-ONLY MODE: design one original, reusable, detailed animation for the selected layer '${target?.name ?: "no layer selected"}'. Do not change the edit structure. Return one cut covering the selected layer's time range (${target?.startMs ?: 0L}ms, ${target?.durationMs ?: 3000L}ms).")
                                    }
                                    appendLine("Music file: ${project.musicName.ifBlank { "none" }}")
                                    audioAnalysis?.let { appendLine("Audio features (DSP only): duration=${it.durationMs}ms; bpm=${it.bpm ?: "uncertain"}; averageRms=${it.averageEnergy}; dynamics=${it.dynamicRange}; energetic segment=${it.suggestedStartMs}-${it.suggestedEndMs}ms; ${it.description}; beatMarkers=${it.beatMarkersMs.take(80)}") }
                                    appendLine("For EVERY visual cut, design a unique animation recipe with 2-5 transform tracks and 2-8 keyframes per track. If text is non-empty, also create a separate textAnimation recipe so captions can animate independently. Supported properties: scale (0.05..8), rotation (-720..720 degrees), opacity (0..1), position_x and position_y (-1..1 of half-canvas offset). Times t are normalized 0..1 within the clip. Easing must be linear, ease_in, ease_out, ease_in_out or hold. Use multiple tracks together for compound effects like velocity zoom, whip-pan, handheld shake, bounce, parallax drift, spin-in, glitch-like flicker (using opacity/position only), text reveal and impact pulse. Avoid random noise with dozens of frames; keep smooth, intentional keyframes.")
                                    appendLine("Use built-in preset IDs when helpful: [shake_soft, shake_hard, zoom_punch, flash_white, rgb_echo, motion_blur, fade_in, fade_out, glow, beat_cut, velocity_ramp, whip_pan, handheld, bounce, tilt_reveal, pulse, drift, snap_zoom, flicker, spin_in, float].")
                                    appendLine("AI MUST CREATE EFFECTS, not only name them: for a strong edit, compose 0-3 native Alight Motion effects per recipe when visually useful, and animate their numeric parameters with 2-6 keyframes where supported. Use ONLY these verified effect IDs and exact parameter names/types; do not invent IDs or properties: com.alightcreative.effects.shake2 [mag:float,freq:float,evolution:float,seed:float,angle:float,slack:float,zshake:float]; com.alightcreative.effects.gaussianblur [strength:float]; com.alightcreative.effects.rgbsep [strength:float,angle:float,centerChannel:int,mode:int]; com.alightcreative.effects.darkglow [strength:float,threshold:float,color:color,blend:float,alpha:float]; com.alightcreative.glow [radius:float,alpha:float,hardness:float,color:color,blendMode:int]; com.alightcreative.effects.motionblur4 [tune:float,usePos:bool,useScale:bool,useAngle:bool]; com.alightcreative.effects.textprogress [start:float,end:float,cursor:int,blink:bool]; com.alightcreative.effects.pulseopacity [freq:float,strength:float,phase:float,type:int]; com.alightcreative.effects.swing2 [freq:float,a1:float,a2:float,phase:float,type:int]. Use type=bool as true/false, color as #AARRGGBB. Only numeric float parameters may receive keyframes. The app validates all values against its local registry and drops unknown IDs/parameters. Combine native filter stacks with the transform tracks; e.g. impact = shake2 + motion blur + zoom, dreamy = glow + gentle drift, glitch = RGB split + rotational/position pulse. Avoid stacking every effect on every cut.")
                                    appendLine("Return overview and a short source-search query list. Include a JSON block with schema {\"title\":string,\"cuts\":[{\"atMs\":number,\"durationMs\":number,\"visual\":string,\"text\":string,\"effect\":string,\"animation\":{\"name\":string,\"description\":string,\"effects\":[string],\"tracks\":[{\"property\":string,\"keyframes\":[{\"t\":number,\"v\":number,\"easing\":string}]}],\"nativeEffects\":[{\"id\":string,\"locallyApplied\":true,\"parameters\":[{\"name\":string,\"value\":string,\"keyframes\":[{\"t\":number,\"v\":number,\"easing\":string}]}]}]},\"textAnimation\":{same animation schema, only when text is non-empty}}],\"searchQueries\":[string]}. Do not output hidden chain-of-thought.")
                                }
                                updateStage("Model request", StageStatus.RUNNING, "Sending prompt and ${imageAttachments.size} image attachment(s); waiting for model response")
                                log("POST /session → create edit-plan session")
                                val started = System.currentTimeMillis()
                                val response = client.generateEditPlan(prompt, openCodeAgent, imageAttachments)
                                val elapsed = System.currentTimeMillis() - started
                                planText = response.text
                                planCuts = PlanParser.parseCuts(response.text)
                                planQueries = PlanParser.parseSearchQueries(response.text)
                                val motionTracks = planCuts.sumOf { it.animation?.tracks?.size ?: 0 } + planCuts.sumOf { it.textAnimation?.tracks?.size ?: 0 }
                                val motionKeys = planCuts.sumOf { cut -> (cut.animation?.tracks.orEmpty() + cut.textAnimation?.tracks.orEmpty()).sumOf { it.keyframes.size } }
                                val nativeEffectCount = planCuts.sumOf { (it.animation?.nativeEffects?.size ?: 0) + (it.textAnimation?.nativeEffects?.size ?: 0) }
                                updateStage("Motion authoring", StageStatus.DONE, "$motionTracks transform track(s) · $motionKeys keyframe(s) · $nativeEffectCount native effect(s) validated")
                                updateStage("Model request", StageStatus.DONE, "Response received in ${elapsed} ms · session ${response.sessionId.take(10)}", elapsed)
                                updateStage("Plan", StageStatus.DONE, "Text plan received; review before applying to timeline")
                                log("OpenCode responded (${elapsed} ms, HTTP ${response.httpCode})")
                            } catch (e: Exception) {
                                updateStage("Pipeline", StageStatus.FAILED, e.message ?: "Generation failed")
                                errorMessage = "AI pipeline failed: ${e.message}"
                                log("Pipeline failed: ${e.message}")
                            } finally { isGenerating = false }
                        }
                    },
                    onApplyPlan = {
                        if (planCuts.isNotEmpty()) {
                            val selectedTarget = selectedClipId?.let { id -> project.clips.firstOrNull { it.id == id } }
                            val originals = if (generationMode == "motion") listOfNotNull(selectedTarget) else
                                project.clips.filter { it.kind == ClipKind.IMAGE || it.kind == ClipKind.VIDEO }.sortedBy { it.startMs }
                            val additions = mutableListOf<EditClip>()
                            val updates = mutableMapOf<String, EditClip>()
                            planCuts.forEachIndexed { index, cut ->
                                val source = originals.getOrNull(index)
                                val effect = cut.effect.takeIf { it in builtInEffects.map(EffectPreset::id) }
                                val visualClip = if (source != null) source.copy(
                                    startMs = cut.atMs, durationMs = cut.durationMs,
                                    effects = source.effects + listOfNotNull(effect),
                                    animation = cut.animation ?: MotionRecipeFactory.fromPreset(effect ?: "") ?: source.animation,
                                ) else EditClip(
                                    name = "AI visual: ${cut.visual.take(48)}", kind = ClipKind.SHAPE,
                                    startMs = cut.atMs, durationMs = cut.durationMs, effects = listOfNotNull(effect),
                                    animation = cut.animation ?: MotionRecipeFactory.fromPreset(effect ?: "")
                                )
                                if (source != null) updates[source.id] = visualClip else additions += visualClip
                                if (cut.text.isNotBlank()) additions += EditClip(
                                    name = "AI caption ${index + 1}", kind = ClipKind.TEXT, text = cut.text,
                                    startMs = cut.atMs, durationMs = cut.durationMs, animation = cut.textAnimation
                                )
                            }
                            val retained = project.clips.map { updates[it.id] ?: it }
                            val newRecipes = planCuts.flatMap { listOfNotNull(it.animation, it.textAnimation) }
                            val updatedLibrary = (project.motionLibrary + newRecipes)
                                .distinctBy { it.name.trim().lowercase() }
                                .takeLast(100)
                            project = project.copy(clips = retained + additions, motionLibrary = updatedLibrary)
                            val generatedTrackCount = planCuts.sumOf { (it.animation?.tracks?.size ?: 0) + (it.textAnimation?.tracks?.size ?: 0) }
                            val generatedEffectCount = planCuts.sumOf { (it.animation?.nativeEffects?.size ?: 0) + (it.textAnimation?.nativeEffects?.size ?: 0) }
                            log("Applied ${planCuts.size} AI cut(s), ${generatedTrackCount} motion track(s), ${generatedEffectCount} native effect(s) to the timeline")
                            tab = AppTab.EDIT
                        } else errorMessage = "No structured cuts were found. Ask OpenCode to return the requested JSON block."
                    },
                    onSearchSources = { query ->
                        scope.launch {
                            assetQuery = query
                            tab = AppTab.ASSETS
                            assetLoading = true
                            runCatching { WikimediaClient.search(query) }
                                .onSuccess { assetResults = it; log("Found ${it.size} Commons results for '$query'") }
                                .onFailure { errorMessage = it.message }
                            assetLoading = false
                        }
                    },
                )
                AppTab.ASSETS -> AssetsScreen(
                    query = assetQuery, onQueryChange = { assetQuery = it }, results = assetResults,
                    motionLibrary = project.motionLibrary,
                    onApplyMotion = { recipe ->
                        val target = selectedClipId?.let { id -> project.clips.firstOrNull { it.id == id } }
                        if (target != null) project = project.copy(clips = project.clips.map { if (it.id == target.id) it.copy(animation = recipe) else it })
                        else {
                            val clip = EditClip(name = recipe.name, kind = ClipKind.SHAPE, durationMs = 2_500L, animation = recipe)
                            project = project.copy(clips = project.clips + clip)
                            selectedClipId = clip.id
                        }
                        log("Applied motion recipe '${recipe.name}'")
                        tab = AppTab.EDIT
                    },
                    loading = assetLoading, onSearch = {
                        scope.launch {
                            assetLoading = true
                            runCatching { WikimediaClient.search(assetQuery) }
                                .onSuccess { assetResults = it; log("Asset search returned ${it.size} results") }
                                .onFailure { errorMessage = it.message }
                            assetLoading = false
                        }
                    },
                    onEffect = { effect ->
                        selectedEffect = effect
                        val target = selectedClipId?.let { id -> project.clips.firstOrNull { it.id == id } }
                        if (target != null) project = project.copy(clips = project.clips.map {
                            if (it.id == target.id) it.copy(
                                effects = (it.effects + effect.id).distinct(),
                                animation = MotionRecipeFactory.fromPreset(effect.id) ?: it.animation,
                            ) else it
                        })
                        else {
                            val clip = EditClip(name = effect.name, kind = ClipKind.SHAPE, durationMs = 2_000L, effects = listOf(effect.id), animation = MotionRecipeFactory.fromPreset(effect.id))
                            project = project.copy(clips = project.clips + clip); selectedClipId = clip.id
                        }
                        log("Preset applied: ${effect.id}")
                    },
                    onOpenSource = { url -> runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } },
                )
                AppTab.SETTINGS -> SettingsScreen(
                    url = openCodeUrl, onUrlChange = { openCodeUrl = it },
                    username = openCodeUser, onUsernameChange = { openCodeUser = it },
                    password = openCodePassword, onPasswordChange = { openCodePassword = it },
                    agent = openCodeAgent, onAgentChange = { openCodeAgent = it },
                    status = connectionStatus, connecting = isConnecting,
                    onTest = {
                        scope.launch {
                            isConnecting = true
                            runCatching { OpenCodeClient(openCodeUrl, openCodeUser, openCodePassword).health() }
                                .onSuccess { connectionStatus = it; log("OpenCode connection test succeeded: $it") }
                                .onFailure { connectionStatus = "Connection failed"; errorMessage = it.message; log("OpenCode connection failed: ${it.message}") }
                            isConnecting = false
                        }
                    },
                )
            }
        }
    }

    if (errorMessage != null) {
        AlertDialog(
            onDismissRequest = { errorMessage = null },
            title = { Text("Notice") },
            text = { Text(errorMessage ?: "") },
            confirmButton = { TextButton(onClick = { errorMessage = null }) { Text("OK") } },
            containerColor = Panel,
        )
    }
}

@Composable
private fun EditorScreen(
    project: EditProject,
    selectedClipId: String?,
    onSelectClip: (String) -> Unit,
    onAddMedia: () -> Unit,
    onImportProject: () -> Unit,
    onExportXml: () -> Unit,
    onExportPackage: () -> Unit,
    onAddText: () -> Unit,
    onUpdateProject: (EditProject) -> Unit,
    onMoveClip: (String, Int) -> Unit,
    onDeleteClip: (String) -> Unit,
    onUpdateClip: (EditClip) -> Unit,
    onOpenEffects: () -> Unit,
    onUpdateMusicRange: (Long, Long) -> Unit,
) {
    val selected = project.clips.firstOrNull { it.id == selectedClipId }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Your edit", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("Build the timeline, then send it to the AI director.", color = Muted, style = MaterialTheme.typography.bodySmall)
                    }
                    Surface(color = Panel2, shape = RoundedCornerShape(12.dp)) {
                        Text("${project.clips.size} layers", Modifier.padding(horizontal = 12.dp, vertical = 8.dp), color = Mint, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(22.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        OutlinedTextField(value = project.title, onValueChange = { onUpdateProject(project.copy(title = it)) }, label = { Text("Project name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Metric("CANVAS", "${project.width} × ${project.height}", Modifier.weight(1f))
                            Metric("FPS", project.fps.toInt().toString(), Modifier.weight(1f))
                            Metric("DURATION", formatMs(project.clips.maxOfOrNull { it.startMs + it.durationMs } ?: 0L), Modifier.weight(1f))
                        }
                    }
                }
            }
            if (project.musicUri != null) item {
                Card(colors = CardDefaults.cardColors(containerColor = Panel2), shape = RoundedCornerShape(17.dp)) {
                    Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.MusicNote, null, tint = Mint)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Music segment", fontWeight = FontWeight.Bold)
                                Text(project.musicName, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            NumberField("Start ms", project.musicStartMs.toString(), Modifier.weight(1f)) { value ->
                                val start = value.toLongOrNull()?.coerceAtLeast(0L) ?: project.musicStartMs
                                onUpdateMusicRange(start, project.musicEndMs.coerceAtLeast(start + 1L))
                            }
                            NumberField("End ms", project.musicEndMs.toString(), Modifier.weight(1f)) { value ->
                                val end = value.toLongOrNull()?.coerceAtLeast(project.musicStartMs + 1L) ?: project.musicEndMs
                                onUpdateMusicRange(project.musicStartMs, end)
                            }
                        }
                        Text("The AI receives the selected time range plus local DSP descriptors, never the audio samples.", color = Muted, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onAddMedia, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color(0xFF1A1230))) {
                        Icon(Icons.Default.Add, null); Spacer(Modifier.width(5.dp)); Text("Add media")
                    }
                    OutlinedButton(onClick = onImportProject, modifier = Modifier.weight(1f)) { Icon(Icons.Default.FolderOpen, null); Spacer(Modifier.width(5.dp)); Text("Import project") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = onAddText, modifier = Modifier.weight(1f)) { Text("+ Text layer") }
                    FilledTonalButton(onClick = onOpenEffects, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Shuffle, null); Spacer(Modifier.width(4.dp)); Text("Effects") }
                }
            }
            item {
                SectionHeader("TIMELINE", "Select a layer to edit its settings")
                if (project.clips.isEmpty()) EmptyState("Your timeline is empty", "Add photos, videos, music or a text layer to start.")
            }
            itemsIndexed(project.clips) { index, clip ->
                val active = clip.id == selectedClipId
                Card(
                    onClick = { onSelectClip(clip.id) },
                    colors = CardDefaults.cardColors(containerColor = if (active) Color(0xFF27213C) else Panel),
                    border = if (active) BorderStroke(1.dp, Accent) else null,
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(Panel2), contentAlignment = Alignment.Center) {
                            Icon(when (clip.kind) { ClipKind.IMAGE -> Icons.Default.ImageSearch; ClipKind.VIDEO -> Icons.Default.PlayArrow; ClipKind.AUDIO -> Icons.Default.MusicNote; ClipKind.TEXT -> Icons.Default.Edit; else -> Icons.Default.Timeline }, null, tint = Accent)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(clip.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${clip.kind.name} · ${formatMs(clip.startMs)} · ${formatMs(clip.durationMs)}", color = Muted, style = MaterialTheme.typography.labelSmall)
                            if (clip.effects.isNotEmpty()) Text(clip.effects.joinToString(" · "), color = Mint, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            clip.animation?.let { motion -> Text("${motion.name} · ${motion.tracks.size} motion tracks · ${motion.tracks.sumOf { it.keyframes.size }} keyframes", color = Accent, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                        }
                        Column {
                            IconButton(onClick = { onMoveClip(clip.id, -1) }, modifier = Modifier.size(32.dp)) { Text("↑", color = Muted) }
                            IconButton(onClick = { onMoveClip(clip.id, 1) }, modifier = Modifier.size(32.dp)) { Text("↓", color = Muted) }
                        }
                    }
                }
            }
            if (selected != null) item {
                Card(colors = CardDefaults.cardColors(containerColor = Panel2), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Selected layer", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            IconButton(onClick = { onDeleteClip(selected.id) }) { Icon(Icons.Default.Delete, null, tint = Color(0xFFFF8297)) }
                        }
                        OutlinedTextField(value = selected.name, onValueChange = { onUpdateClip(selected.copy(name = it)) }, label = { Text("Layer name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        if (selected.kind == ClipKind.TEXT) OutlinedTextField(value = selected.text, onValueChange = { onUpdateClip(selected.copy(text = it)) }, label = { Text("Text content") }, modifier = Modifier.fillMaxWidth())
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            NumberField("Start ms", selected.startMs.toString(), Modifier.weight(1f)) { onUpdateClip(selected.copy(startMs = it.toLongOrNull()?.coerceAtLeast(0L) ?: selected.startMs)) }
                            NumberField("Duration ms", selected.durationMs.toString(), Modifier.weight(1f)) { onUpdateClip(selected.copy(durationMs = it.toLongOrNull()?.coerceAtLeast(1L) ?: selected.durationMs)) }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            NumberField("Scale", selected.scale.toString(), Modifier.weight(1f)) { onUpdateClip(selected.copy(scale = it.toFloatOrNull()?.coerceIn(0.05f, 10f) ?: selected.scale)) }
                            NumberField("Rotation °", selected.rotation.toString(), Modifier.weight(1f)) { onUpdateClip(selected.copy(rotation = it.toFloatOrNull() ?: selected.rotation)) }
                        }
                    }
                }
            }
            item {
                SectionHeader("EXPORT", "Alight Motion exchange + native project backup")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onExportXml, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Download, null); Spacer(Modifier.width(5.dp)); Text("AM XML") }
                    OutlinedButton(onClick = onExportPackage, modifier = Modifier.weight(1f)) { Icon(Icons.Default.FolderOpen, null); Spacer(Modifier.width(5.dp)); Text("MVS ZIP") }
                }
                Text("XML exports editable scene structure. Media may need relinking in Alight Motion; the MVS ZIP stores a project manifest but Android content URIs are device-specific.", color = Muted, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun AiStudioScreen(
    topic: String, onTopicChange: (String) -> Unit,
    connectionStatus: String, isGenerating: Boolean,
    stages: List<PipelineStage>, logs: List<String>, planText: String, planCuts: List<PlanParser.Cut>, planQueries: List<String>,
    audioAnalysis: AudioAnalysis?, selectedClipName: String?, onGenerate: (String) -> Unit, onApplyPlan: () -> Unit,
    onSearchSources: (String) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("AI director", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("The model receives images and text descriptors. Music is analysed locally as signal data; raw audio is never sent.", color = Muted, style = MaterialTheme.typography.bodySmall)
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("EDIT BRIEF", color = Accent, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    OutlinedTextField(value = topic, onValueChange = onTopicChange, modifier = Modifier.fillMaxWidth(), minLines = 3, label = { Text("Describe the edit / motion style") })
                    Text("Selected layer: ${selectedClipName ?: "none — choose a layer in Edit"}", color = Muted, style = MaterialTheme.typography.labelSmall)
                    Text("AI can author compound keyframe animations: scale, rotation, opacity and X/Y camera motion. The curves remain editable in the exported scene XML.", color = Muted, style = MaterialTheme.typography.labelSmall)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(connectionStatus, color = if (connectionStatus.startsWith("OpenCode")) Mint else Muted, style = MaterialTheme.typography.labelSmall)
                            Text("OpenCode server · image-capable model recommended", color = Muted, style = MaterialTheme.typography.labelSmall)
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Button(onClick = { onGenerate("plan") }, enabled = !isGenerating, colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color(0xFF1A1230))) {
                                if (isGenerating) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Icon(Icons.Default.AutoAwesome, null)
                                Spacer(Modifier.width(6.dp)); Text(if (isGenerating) "Working" else "Generate edit")
                            }
                            OutlinedButton(onClick = { onGenerate("motion") }, enabled = !isGenerating && selectedClipName != null) {
                                Icon(Icons.Default.Bolt, null); Spacer(Modifier.width(6.dp)); Text("Create motion for layer")
                            }
                        }
                    }
                }
            }
        }
        if (audioAnalysis != null) item {
            Card(colors = CardDefaults.cardColors(containerColor = Panel2), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(13.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.LibraryMusic, null, tint = Mint); Spacer(Modifier.width(8.dp)); Text("Local audio analysis", fontWeight = FontWeight.Bold) }
                    Spacer(Modifier.height(6.dp))
                    Text("${audioAnalysis.bpm?.let { "$it BPM" } ?: "Tempo uncertain"} · ${formatMs(audioAnalysis.suggestedStartMs)}–${formatMs(audioAnalysis.suggestedEndMs)}", color = Mint)
                    Text(audioAnalysis.description, color = Muted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item { SectionHeader("PIPELINE", "Visible operational stages; no hidden model reasoning") }
        if (stages.isEmpty()) item { EmptyState("Waiting for a task", "Generation will show connection, media preparation, DSP, model request and plan parsing here.") }
        itemsIndexed(stages) { _, stage ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(Panel).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                when (stage.status) {
                    StageStatus.DONE -> Icon(Icons.Default.CheckCircle, null, tint = Mint)
                    StageStatus.RUNNING -> CircularProgressIndicator(Modifier.size(19.dp), strokeWidth = 2.dp)
                    StageStatus.FAILED -> Icon(Icons.Default.Warning, null, tint = Color(0xFFFF8297))
                    StageStatus.WAITING -> Box(Modifier.size(19.dp).clip(CircleShape).background(Panel2))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(stage.title, fontWeight = FontWeight.SemiBold)
                    Text(stage.detail, color = Muted, style = MaterialTheme.typography.labelSmall)
                }
                stage.durationMs?.let { Text("${it}ms", color = Muted, style = MaterialTheme.typography.labelSmall) }
            }
        }
        if (planText.isNotBlank()) item {
            Card(colors = CardDefaults.cardColors(containerColor = Panel2), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("MODEL PROPOSAL", color = Accent, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                    Text(planText, style = MaterialTheme.typography.bodyMedium)
                    if (planCuts.isNotEmpty()) {
                        val tracks = planCuts.sumOf { (it.animation?.tracks?.size ?: 0) + (it.textAnimation?.tracks?.size ?: 0) }
                        val keyframes = planCuts.sumOf { cut ->
                            (cut.animation?.tracks.orEmpty() + cut.textAnimation?.tracks.orEmpty()).sumOf { it.keyframes.size }
                        }
                        Text("${planCuts.size} cuts · $tracks AI motion tracks · $keyframes keyframes", color = Mint, style = MaterialTheme.typography.labelSmall)
                        planCuts.forEachIndexed { index, cut ->
                            cut.animation?.let { recipe -> Text("${index + 1}. Visual: ${recipe.name} · ${recipe.effects.joinToString()} · ${recipe.tracks.joinToString { it.property }}", color = Accent, style = MaterialTheme.typography.labelSmall) }
                            cut.textAnimation?.let { recipe -> Text("${index + 1}. Caption: ${recipe.name} · ${recipe.tracks.joinToString { it.property }}", color = Mint, style = MaterialTheme.typography.labelSmall) }
                        }
                        Button(onClick = onApplyPlan, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.CheckCircle, null); Spacer(Modifier.width(6.dp)); Text("Apply plan to timeline") }
                    }
                }
            }
            if (planQueries.isNotEmpty()) {
                SectionHeader("AI SOURCE QUERIES", "Search suggestions generated from your brief")
                planQueries.forEach { query ->
                    OutlinedButton(onClick = { onSearchSources(query) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Search, null); Spacer(Modifier.width(6.dp)); Text(query, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Button(onClick = { onSearchSources(topic) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.ImageSearch, null); Spacer(Modifier.width(6.dp)); Text("Search the original topic") }
        }
        item {
            SectionHeader("PROCESS LOG", "HTTP events, stage transitions and errors")
            if (logs.isEmpty()) Text("No events yet", color = Muted, style = MaterialTheme.typography.bodySmall)
            logs.take(35).forEach { line -> Text(line, color = Muted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(vertical = 2.dp)) }
        }
    }
}

@Composable
private fun AssetsScreen(
    query: String, onQueryChange: (String) -> Unit,
    results: List<SourceAsset>, loading: Boolean,
    motionLibrary: List<ClipAnimation>, onApplyMotion: (ClipAnimation) -> Unit,
    onSearch: () -> Unit, onEffect: (EffectPreset) -> Unit, onOpenSource: (String) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Asset browser", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Search reusable images on Wikimedia Commons with author and license metadata.", color = Muted, style = MaterialTheme.typography.bodySmall)
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = query, onValueChange = onQueryChange, modifier = Modifier.weight(1f), singleLine = true, label = { Text("Search visuals") })
                Spacer(Modifier.width(8.dp))
                Button(onClick = onSearch, enabled = !loading) { if (loading) CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp) else Icon(Icons.Default.Search, null) }
            }
        }
        item { SectionHeader("EFFECT & MOTION LIBRARY", "Built-in starter presets; add more packs later") }
        itemsIndexed(builtInEffects) { _, effect ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(Panel).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(Panel2), contentAlignment = Alignment.Center) { Icon(Icons.Default.Bolt, null, tint = Accent) }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(effect.name, fontWeight = FontWeight.SemiBold)
                    Text("${effect.category} · ${effect.description}", color = Muted, style = MaterialTheme.typography.labelSmall)
                }
                TextButton(onClick = { onEffect(effect) }) { Text("Add") }
            }
        }
        item { SectionHeader("AI MOTION LIBRARY", "Custom recipes saved in this project · ${motionLibrary.size}/100") }
        if (motionLibrary.isEmpty()) item {
            EmptyState("No AI recipes saved", "Generate an edit or create motion for a selected layer in AI Studio. Its motion recipes will appear here for reuse.")
        }
        itemsIndexed(motionLibrary) { _, recipe ->
            Card(colors = CardDefaults.cardColors(containerColor = Panel2), shape = RoundedCornerShape(14.dp)) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(recipe.name, fontWeight = FontWeight.SemiBold)
                            Text(recipe.description, color = Muted, style = MaterialTheme.typography.labelSmall)
                        }
                        TextButton(onClick = { onApplyMotion(recipe) }) { Text("Apply") }
                    }
                    Text("${recipe.tracks.size} tracks · ${recipe.tracks.sumOf { it.keyframes.size }} keyframes · ${recipe.nativeEffects.size} native effects · ${recipe.effects.joinToString().ifBlank { "custom motion" }}", color = Mint, style = MaterialTheme.typography.labelSmall)
                    Text(recipe.tracks.joinToString("  ·  ") { it.property }, color = Muted, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        item { SectionHeader("SEARCH RESULTS", "Always verify the source license before publishing") }
        if (results.isEmpty() && !loading) item { EmptyState("No assets loaded", "Search Wikimedia Commons, or use your own source images and videos.") }
        itemsIndexed(results) { _, asset ->
            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(16.dp)) {
                Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (asset.thumbnailUrl != null) AsyncImage(model = asset.thumbnailUrl, contentDescription = asset.title, modifier = Modifier.size(82.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
                    else Box(Modifier.size(82.dp).clip(RoundedCornerShape(10.dp)).background(Panel2), contentAlignment = Alignment.Center) { Icon(Icons.Default.ImageSearch, null, tint = Muted) }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(asset.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("By ${asset.author}", color = Muted, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(asset.license, color = Mint, style = MaterialTheme.typography.labelSmall)
                        TextButton(onClick = { onOpenSource(asset.pageUrl) }, contentPadding = PaddingValues(0.dp)) { Text("Open source page") }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    url: String, onUrlChange: (String) -> Unit,
    username: String, onUsernameChange: (String) -> Unit,
    password: String, onPasswordChange: (String) -> Unit,
    agent: String, onAgentChange: (String) -> Unit,
    status: String, connecting: Boolean, onTest: () -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Connections", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Connect to an OpenCode server you control. The app does not bundle an OpenCode runtime or provider keys. OpenCode 2.x is detected through /api/info; older servers are supported as a fallback.", color = Muted, style = MaterialTheme.typography.bodySmall)
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Code, null, tint = Accent); Spacer(Modifier.width(8.dp)); Text("OpenCode server", fontWeight = FontWeight.Bold) }
                    OutlinedTextField(value = url, onValueChange = onUrlChange, modifier = Modifier.fillMaxWidth(), label = { Text("Base URL") }, singleLine = true)
                    OutlinedTextField(value = agent, onValueChange = onAgentChange, modifier = Modifier.fillMaxWidth(), label = { Text("Agent (optional, e.g. mvs-edit-director)") }, singleLine = true)
                    OutlinedTextField(value = username, onValueChange = onUsernameChange, modifier = Modifier.fillMaxWidth(), label = { Text("Basic-auth username (default: opencode)") }, singleLine = true)
                    OutlinedTextField(value = password, onValueChange = onPasswordChange, modifier = Modifier.fillMaxWidth(), label = { Text("Basic-auth password") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                    Text("For Termux: username is usually opencode; password is printed as “server password” when opencode serve starts. Keep it private.", color = Muted, style = MaterialTheme.typography.labelSmall)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(status, color = if (status.startsWith("OpenCode")) Mint else Muted, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
                        Button(onClick = onTest, enabled = !connecting) {
                            if (connecting) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Icon(Icons.Default.Link, null)
                            Spacer(Modifier.width(5.dp)); Text("Test")
                        }
                    }
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Panel2), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Start OpenCode", fontWeight = FontWeight.Bold)
                    Text("On the same Android phone (for example in Termux), start the server on loopback:", color = Muted, style = MaterialTheme.typography.bodySmall)
                    Text("opencode serve --hostname 127.0.0.1 --port 4096", color = Mint, style = MaterialTheme.typography.bodyMedium)
                    Text("Then keep the server process running, enter the printed server password in Basic-auth password, and tap Test. For another device, use a reachable HTTPS URL. Vision image inspection requires choosing a vision-capable model in OpenCode.", color = Muted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("Privacy and process visibility", fontWeight = FontWeight.Bold)
                    Text("Audio is decoded on-device into RMS energy, onset envelope, tempo estimate and suggested segment. The app sends these descriptors—not raw audio—to OpenCode. The AI screen shows pipeline stages, request latency, session ID and errors, not hidden chain-of-thought.", color = Muted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun NumberField(label: String, value: String, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = onChange, modifier = modifier, label = { Text(label) }, singleLine = true)
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(10.dp)).background(Panel2).padding(horizontal = 10.dp, vertical = 7.dp)) {
        Text(label, color = Muted, style = MaterialTheme.typography.labelSmall)
        Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, color = Accent, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
        Text(subtitle, color = Muted, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun EmptyState(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(Panel).padding(17.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.Timeline, null, tint = Muted, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(6.dp))
        Text(title, fontWeight = FontWeight.SemiBold)
        Text(subtitle, color = Muted, style = MaterialTheme.typography.bodySmall)
    }
}

private fun displayName(context: android.content.Context, uri: Uri): String {
    var name = "asset-${System.currentTimeMillis()}"
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) name = cursor.getString(0) ?: name
    }
    return name
}

private fun readDuration(context: android.content.Context, uri: Uri): Long {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, uri)
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 5_000L
    } catch (_: Exception) { 5_000L } finally { runCatching { retriever.release() } }
}

private fun formatMs(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0L) / 1000L)
    return "%d:%02d.%d".format(totalSeconds / 60, totalSeconds % 60, (ms.coerceAtLeast(0L) % 1000L) / 100L)
}

private fun safeName(name: String): String = name.replace(Regex("[^A-Za-z0-9._-]+"), "_").trim('_').ifBlank { "mvs-edit" }.take(60)
