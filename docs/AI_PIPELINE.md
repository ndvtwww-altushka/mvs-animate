# AI edit pipeline

```text
User topic + selected assets
        |
        +--> images (up to 4, <=5 MiB each) --------------------+
        |                                                       |
Music URI                                                     OpenCode
        |                                                       |
MediaExtractor + MediaCodec                                    |
        |                                                       |
PCM samples -> 100 ms RMS -> onset envelope -> BPM heuristic  |
        |                                                       |
selected / suggested segment + beat markers ------------------+
                                                                |
                                   POST /session                |
                                   POST /session/{id}/message   |
                                                                |
                                               edit plan text + JSON cuts
                                                                |
                                       PlanParser -> editable timeline
```

The model receives images as data-URI file parts if a vision-capable model is selected. The app never uploads the audio stream; it sends only text descriptors derived locally. The source browser uses Wikimedia Commons' MediaWiki API; it does not scrape arbitrary websites.

The UI exposes operational events (pipeline stages, connection health, request time, OpenCode session ID and errors). Model-private reasoning is not exposed. If a provider streams internal tool events through OpenCode, this build does not yet render all such events individually; it reports the request lifecycle and response.

## OpenCode endpoints used

- `GET /global/health`
- `POST /session`
- `POST /session/{id}/message`

OpenCode's server publishes its OpenAPI document at `/doc`. The client keeps the endpoint base URL configurable because a user may run OpenCode locally in Termux or on a separate trusted host.


## AI motion authoring (motion DSL)

The model can now return an `animation` object for every cut. The app validates it and stores the accepted curves on the target layer. `AI Studio → Create motion for layer` requests a single recipe for the currently selected layer; `Generate edit` asks for a full edit with unique recipes per cut.

Allowed tracks are `scale`, `rotation`, `opacity`, `position_x` and `position_y`. A track contains normalized clip-relative keyframes (`t` 0..1, numeric `v`, easing). Values are bounded before being accepted. Curves can be combined for velocity zoom, whip-pan, handheld shake, bounce, tilt reveal, spin-in, pulse and parallax drift. The project manifest stores the animation graph and a project-level AI Motion Library, so recipes survive save/reopen and can be applied to other layers. Captions can have an independent `textAnimation` recipe.

The AI does not execute arbitrary code or create binary effect plug-ins. It authors a declarative animation graph. The XML bridge maps transform tracks to scene keyframes; visual filters without a verified Alight Motion mapping stay as recipe labels and appear in export warnings rather than being encoded with fabricated effect identifiers.


## Generated animation and effect recipes

The editing-director prompt asks OpenCode for per-cut visual motion, separate caption motion, and up to three native Alight Motion effects when appropriate. Recipes include transform keyframes plus an allow-listed effect stack. The app validates effect IDs, parameter names/types, numeric ranges, colour strings and animatable properties before saving or exporting. The current native registry is deliberately limited to Shake 2, Gaussian Blur, RGB Split, Dark Glow, Glow, Motion Blur, Text Progress, Pulse Opacity and Swing 2. All generated recipes are stored in the `.mvsanim` project's `motionLibrary` so they can be reused.
