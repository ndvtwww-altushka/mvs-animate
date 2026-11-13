# Changelog

## 0.2.0

- Added AI-authored visual and caption motion recipes with independent transform tracks and normalized keyframes.
- Added a validated native Alight Motion effect registry for nine inspected effect IDs: Shake 2, Gaussian Blur, RGB Split, Dark Glow, Glow, Motion Blur, Text Progress, Pulse Opacity and Swing 2.
- Added typed effect parameters, value clamping, allow-listed effect IDs/properties and optional animated numeric parameters.
- Persisted native effect stacks and keyframes in `.mvsanim` projects and the reusable AI Motion Library.
- Updated the OpenCode edit-director prompt and sample AI plan to request concrete effect stacks and animated parameters, not just effect names.
- Added tests for native-effect project round-trip and structured AI plan parsing.
- Clarified that unsupported Alight Motion effects and `.wj` binary decoding are not claimed as supported.


## 0.2.1 — OpenCode API compatibility fix

- Detect current OpenCode servers with `GET /api/info`; no longer treat `/global/health` as the only health endpoint.
- Added OpenCode v2 session creation (`POST /api/session`), optional agent assignment at session creation, and prompt submission (`POST /api/session/{id}/prompt`).
- Added polling of `/api/session/{id}/message` to return the completed assistant text after asynchronous prompt execution.
- Added v2 image attachments as inline `data:` URIs, compatible with OpenCode's documented attachment handling.
- Kept the legacy `/global/health`, `/session`, and `/message` path as an automatic fallback.
- Uses Java Base64 so the HTTP client can be exercised in local JVM tests.
- Default Basic-auth username is `opencode`; the user enters the server password printed by `opencode serve`.
- Added OpenCode HTTP client tests for v2 health, authenticated requests, image prompt submission, finished-message retrieval, and legacy fallback.


## 0.2.2 — OpenCode Android Termux compatibility

- Corrected OpenCode 2.x API health detection and documented the exact tested server version family (`/api/info`).
- Hardened response polling for direct projected messages and `{ info, parts }` compatible wrappers.
- Handles successful, failed and interrupted idle states explicitly; avoids waiting for five minutes after an empty completed run.
- Bumped Android versionCode/versionName to 4 / 0.2.2.


## 0.2.3 — Model selection and quota diagnostics

- Added OpenCode 2.x model catalogue loading from `/api/model` and default-model detection from `/api/model/default`.
- Added in-app model selector with provider ID, context/output limits and image/Vision capability indicators.
- Applies the chosen provider/model reference to every newly created session using `POST /api/session/{id}/model` before sending the prompt.
- Automatically selects the server default model when no prior in-app selection exists.
- Blocks image-based generation when the selected model does not advertise image input, with a clear model-selection hint.
- Adds specific diagnostics for provider quota / max_tokens errors and explains the `limit.output` config override path.
- Adds JVM tests for model catalogue parsing, default selection and per-session model assignment.
