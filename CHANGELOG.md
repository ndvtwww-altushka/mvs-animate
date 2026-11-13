# Changelog

## 0.2.0

- Added AI-authored visual and caption motion recipes with independent transform tracks and normalized keyframes.
- Added a validated native Alight Motion effect registry for nine inspected effect IDs: Shake 2, Gaussian Blur, RGB Split, Dark Glow, Glow, Motion Blur, Text Progress, Pulse Opacity and Swing 2.
- Added typed effect parameters, value clamping, allow-listed effect IDs/properties and optional animated numeric parameters.
- Persisted native effect stacks and keyframes in `.mvsanim` projects and the reusable AI Motion Library.
- Updated the OpenCode edit-director prompt and sample AI plan to request concrete effect stacks and animated parameters, not just effect names.
- Added tests for native-effect project round-trip and structured AI plan parsing.
- Clarified that unsupported Alight Motion effects and `.wj` binary decoding are not claimed as supported.
