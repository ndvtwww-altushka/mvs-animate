# MVS Animate

**Android AI edit studio · OpenCode client · Alight Motion XML bridge**

**Source version: 0.2.0** — AI-authored motion curves and validated native effect stacks for both visual and caption layers.

MVS Animate is an Android Studio project for building music-synchronised edits from photos, videos, text and motion presets. The app connects to an OpenCode server instead of hard-coding one model provider. It uses local audio DSP to turn music into numeric timing features and can attach up to four selected images to a vision-capable OpenCode model.

> This ZIP is a source project, not a prebuilt APK. The project has not been compiled in this environment because Android SDK/Gradle are not installed here. GitHub Actions is included to build and upload the debug APK after you push the project.

## Features in this source drop

### Editor
- 9:16 / custom canvas model (default 1080×1920, 30 fps).
- Timeline layer list with selection, reordering, renaming, timing, duration, scale and rotation controls.
- Add image, video, audio and text layers through Android's document picker.
- Attach a music file separately from visual layers.
- Apply 20+ camera, transition and motion starter presets; presets can include multi-track keyframe recipes.
- Import an Alight Motion XML template and bind newly selected images into available media placeholders in layer order.
- Save/import an `.mvsanim` project manifest or a ZIP package with the manifest and Alight Motion XML template/draft.

### Music analysis — on device
- Decode an audio stream using Android `MediaExtractor` + `MediaCodec`.
- Calculate a 100 ms RMS envelope, average energy, dynamic range and positive onset envelope.
- Estimate a tempo range using onset autocorrelation.
- Suggest an energetic segment and produce approximate beat markers.
- The raw music file is **not** sent to OpenCode. The AI prompt receives only the computed descriptors and timestamp list.

The BPM estimate is heuristic, not a studio-grade beat tracker. It can be wrong on half-time/double-time music, ambient tracks and syncopated arrangements. The suggested segment is a starting point for the user to adjust.

### OpenCode integration
- Connects to an existing `opencode serve` HTTP server.
- Health check, session creation and message submission are implemented.
- Optional HTTP Basic authentication fields are available.
- The model prompt includes edit brief, timeline metadata and local audio descriptors. It can return source-search queries that open in the Commons browser.
- Up to four selected still images (max 5 MiB each) are sent as image file parts. The configured model must support vision; otherwise the server/provider may reject or ignore image parts.
- The model is asked for an actionable timeline plan plus JSON cuts. Parsed cuts can be applied to the timeline as editable clip/caption placeholders.
- The AI screen shows operational pipeline stages, request latency, session ID, HTTP errors and a local log. It does **not** expose or claim to expose hidden chain-of-thought.
- The model can author a custom motion recipe per cut, or generate a motion-only recipe for the currently selected timeline layer. It creates compound transform tracks (scale, rotation, opacity and X/Y position) with easing and keyframes, not just text suggestions.
- AI can also design a small native Alight Motion effect stack per visual/caption recipe, independently for the image/video layer and its text caption. Validated native mappings in this build include Shake 2, Gaussian Blur, RGB Split, Dark Glow, Glow, Motion Blur, Text Progress, Pulse Opacity and Swing 2. Where the inspected schema supports it, numeric effect parameters can have their own keyframes. The recipes are data, never executable model-generated scripts.
- AI-created visual and caption recipes, including their native effects and animated parameters, are saved in the project's reusable AI Motion Library and can be applied to other layers.

The app does not bundle OpenCode, provider API keys, OAuth sessions or model weights. Configure providers and authenticate with OpenCode itself first.

### Source browser
- Wikimedia Commons search via the public MediaWiki API.
- Search results display thumbnail, author and license label when available, plus a source-page link.
- Verify the license and attribution requirements on the source page before publishing. A Commons result is not automatically public domain.

### Imports and exports

| Format / type | Behaviour in this build |
|---|---|
| Alight Motion `.xml` scene | Imports common scene layers and exports a conservative scene XML draft |
| `.alight` / `.zip` | Reads an archive looking for a scene XML or MVS manifest; an official Project Package link/QR is not the same as a local ZIP file |
| `.mvsanim` | Imports/exports the native MVS Animate JSON manifest |
| JSON manifest | Imports only when `format` is `mvs-animate-project` |
| Photos | JPEG, PNG, WebP, GIF, HEIC where Android has a decoder; SVG is passed through as an image URI where supported |
| Video | MP4, MOV, MKV, WebM and 3GP containers where the device has a matching codec |
| Audio | MP3, WAV, M4A, AAC, OGG and FLAC where the device has a matching decoder |
| SRT / VTT | Imports subtitle cues as timed text layers |
| `.wj` | Detected and diagnosed, but not decoded: no verified public schema or sample is bundled |

Container/extension detection does not mean every codec profile inside that container is supported by every Android phone.

## Alight Motion compatibility — read this before importing

Alight Motion has an XML scene interchange format. This project targets the common `scene` structure: canvas properties, text/shape layers, layer start/end times, transforms, opacity and selected effect data. It imports common layer attributes and a limited subset of nested properties.

**This is not a lossless clone of Alight Motion's internal project engine.**

- For a new edit, the exporter writes a conservative scene XML using a baseline format version and known XML structures. For an imported XML template, it patches the original document, preserves unknown XML subtrees/effect/keyframe nodes, updates matching media references and appends new layers.
- `shake_soft` and `shake_hard` map to `com.alightcreative.effects.shake2`. AI transform tracks for scale, rotation, opacity and X/Y location export as editable keyframes. A conservative allow-list also emits native nodes for Gaussian Blur, RGB Split, Dark Glow, Glow, Motion Blur, Text Progress/typewriter, Pulse Opacity and Swing 2 with validated parameter names/types; numeric effect parameters can carry keyframes when supported by the inspected schema.
- This is not full support for every Alight Motion effect or arbitrary plug-in parameters. The native effect list is intentionally small; unsupported effects, masks, many blend modes, fonts, groups, drawings, time remapping/speed curves and future-version fields are not fully round-tripped. Existing unknown template XML is preserved, but it is not all exposed as editable controls.
- Image/video XML references may use Android `content://` URIs. Those are app-specific and may not resolve when Alight Motion imports the XML. The app shows a warning; relinking may be required.
- Audio tracks are preserved in the MVS project manifest, but this first XML exporter does not create a fully linked Alight Motion audio resource.
- Imported template nodes are preserved as XML, but the app does not understand or expose every proprietary property in the editor. Newly generated scenes do not contain arbitrary unknown fields.
- Alight Motion's Project Package feature is an app-managed sharing flow that produces a link/QR. The app does not generate official cloud Project Package links.

The schema details are based on public reverse-engineered community documentation, not an official Alight Motion SDK. The published catalogue lists 315 effect definitions, but this build intentionally maps only nine effects with inspected parameter fields. Always test generated XML with a disposable project first.

## Build on GitHub

1. Create a repository, for example `mvs-animate`.
2. Upload the **contents** of this folder to the repository root (so `settings.gradle.kts` is at the root).
3. Push to `main` or `master`.
4. Open **Actions → Android build**.
5. Download the `mvs-animate-debug` artifact and install the APK on your Android device.

The workflow installs JDK 17, Android API 35 and Gradle 8.9, runs unit tests and builds the debug APK.

## Build locally in Android Studio

- Android Studio with JDK 17.
- Android SDK Platform 35 and Build Tools 35.0.0.
- Gradle 8.9.
- Open the project root and sync Gradle.

This source drop intentionally has no Gradle Wrapper JAR. The included GitHub Actions workflow installs the pinned Gradle distribution itself. If building locally from a terminal, install Gradle 8.9 and run `gradle assembleDebug` from the project root.

## Connect OpenCode on Android / Termux

1. Install and authenticate OpenCode in Termux using its official installation instructions.
2. Copy `.opencode/agents/mvs-edit-director.md` into the OpenCode project configuration folder if you want the included editing-director agent.
3. In Termux, run:

```bash
bash scripts/start-opencode-termux.sh
```

Or run directly:

```bash
opencode serve --hostname 127.0.0.1 --port 4096
```

4. Keep the process alive. In MVS Animate → Settings, use `http://127.0.0.1:4096` and tap **Test**.
5. Select a vision-capable model through OpenCode for image understanding. If a model is text-only, the app can still pass file metadata and audio descriptors, but visual interpretation will be unavailable.

For a separate device, bind OpenCode only to a trusted network interface, protect it with authentication and prefer HTTPS. Do not expose an unauthenticated OpenCode server to the public internet.

## Sample files

- `samples/alight-motion-template-example.xml` — sample media-placeholder template with a text layer and shake effect.
- `samples/example-ai-plan.json` — example JSON plan format the AI parser accepts.

## Project structure

```text
app/src/main/java/com/mvs/animate/
  MainActivity.kt                 Compose UI, timeline, AI workflow and exports
  data/OpenCodeClient.kt          OpenCode HTTP API integration
  data/MotionRecipeFactory.kt     Built-in editable motion recipes
  data/PlanParser.kt              Structured AI plan extraction
  data/ProjectCodec.kt            Native MVS project manifest
  data/WikimediaClient.kt         Licensed-source search
  format/AlightMotionXml.kt       Alight Motion scene XML adapter
  format/ImportInspector.kt       Format detection and safe ZIP inspection
  format/SubtitleImporter.kt      SRT/VTT importer
  media/AudioAnalyzer.kt          Local MediaCodec + DSP pipeline
  model/ProjectModels.kt          Project, clip, audio, effect and keyframe models
.opencode/agents/                 Optional OpenCode editing-director agent
scripts/                          Termux OpenCode start script
.github/workflows/                GitHub Actions Android build
```

## Security notes

- The app requests Internet access only; source media is selected through Android's Storage Access Framework.
- OpenCode credentials are entered at runtime and are not written to a local config file by this version.
- HTTP cleartext is restricted by network security configuration to local development hosts; remote servers should use HTTPS.
- ZIP inspection has entry-count and expanded-size limits and does not extract arbitrary paths.
- Image attachments are limited to four files, each capped at 5 MiB.

## Current limitations / next milestones

This is a solid app foundation and interchange/AI workflow, not yet a full replacement for Alight Motion or a finished rendering engine. The following major systems remain future work: a real-time preview compositor, multi-track MP4 rendering with audio muxing, full Alight Motion effect/parameter registry, lossless unknown-node preservation, `.wj` reverse engineering from a lawful sample, reusable animation/shake pack import/export, downloaded-source asset ingestion and project autosave/version history.

No generated APK has been verified in this environment. The first real Android compilation must run in GitHub Actions or Android Studio.
