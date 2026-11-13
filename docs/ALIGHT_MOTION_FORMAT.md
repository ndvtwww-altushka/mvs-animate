# Alight Motion XML bridge

MVS Animate reads/writes the XML `scene` interchange structure. The current adapter intentionally targets common layer types and retains an imported template document as a source document.

## Template-first workflow

1. In Alight Motion, export a project as XML.
2. In MVS Animate, choose **Import project** and select the XML.
3. The adapter reads canvas settings and common layer metadata. Common media-filled `shape` layers become image placeholders.
4. Add images. They are assigned, in order, to available imported image slots whose media URI has not yet been replaced. Other files are appended as new timeline layers.
5. Export **AM XML**. For imported templates, the DOM patcher updates layer labels/timing and media URI references, keeps original effect/keyframe/unknown XML subtrees intact, and appends newly created layers.
6. Import the result in Alight Motion and verify each media link, effect, and font. Some Android `content://` URIs are private to the granting app and cannot be opened by Alight Motion; relinking is then required.

## Supported mappings

| Scene item | Import | Export |
|---|---|---|
| `scene` canvas attributes | Read | Write / update |
| `text` with `content` | Text layer | Basic text layer |
| `shape` | Shape layer | Rectangle/colour placeholder |
| `shape fillType=media` + `fillImage` | Image slot | URI replacement in imported template; basic fillImage in new scene |
| `group`, `drawing`, `color`, `media` | Partial metadata | Original preserved in template; generic new layer not fully recreated |
| `transform` location/scale/rotation/opacity | Read simple scalar values and common normalized keyframes | Write static transforms and AI-authored normalized keyframe tracks for location, scale, rotation and opacity |
| `effect` IDs | Read effect IDs | Preserve template effects; new export emits only the small validated native-effect allow-list |
| `property` / `kf` | Common transform keyframes are read into the MVS motion model; unknown XML subtree retained in template | AI-authored scale/rotation/opacity/location tracks and starter motion curves |
| bookmarks, masks, speed/time remap, custom fonts | Not exposed to editor | Preserved only when part of imported template XML |

For an imported template, unrecognised subtrees are retained in the original XML document. That is different from supporting their semantics in the editor: the app cannot yet inspect/edit arbitrary effect parameters or guarantee that an effect exists on the target Alight Motion version.

## `.wj` and project packages

No verified `.wj` interchange specification or real sample was available for this build. Files with that extension are identified and diagnosed without rewriting the bytes. A real `.wj` sample and its origin/version are needed before a decoder can be implemented safely.

The official Alight Motion Project Package workflow creates an app-managed share link and QR code. MVS Animate does not generate those links. It can inspect a local ZIP archive if it contains a scene XML or an MVS project manifest, but that does not imply that every official package payload is a normal ZIP.

## References

- Alight Motion Help Center, project backup / Project Package: https://support.alightmotion.com/hc/en-us/articles/13724965664785-How-do-I-back-up-my-projects
- Community reverse-engineered scene XML schema (not an official Alight Motion specification): https://github.com/boiniArun2006/alight-motion-xml-schema/blob/main/docs/03-scene-project-xml.md
- Alight Motion Help Center, Elements and Project Package import: https://support.alightmotion.com/hc/en-us/articles/10536791122449-Elements-The-Complete-Guide


## AI-generated motion curves and native effect stacks

The AI returns a declarative, validated recipe, not executable scripts. The bridge exports transform properties `scale`, `rotation`, `opacity`, `position_x` and `position_y`. X/Y are normalized offsets from canvas center in MVS Animate and converted to pixel coordinates for scene keyframes. Easing is mapped only to the supported cubic-bezier forms used by this bridge.

A deliberately small allow-list can emit native Alight Motion effect nodes with validated parameter names/types and animated numeric properties: Shake 2 (`com.alightcreative.effects.shake2`), Gaussian Blur (`com.alightcreative.effects.gaussianblur`), RGB Split (`com.alightcreative.effects.rgbsep`), Dark Glow (`com.alightcreative.effects.darkglow`), Glow (`com.alightcreative.glow`), Motion Blur (`com.alightcreative.effects.motionblur4`), Text Progress/typewriter (`com.alightcreative.effects.textprogress`), Pulse Opacity (`com.alightcreative.effects.pulseopacity`) and Swing 2 (`com.alightcreative.effects.swing2`). The registry includes only the properties inspected for this build. Unknown effect IDs, unknown properties, invalid colours and non-finite numeric values are dropped; they are never guessed. The AI may combine a few native effects with transform keyframes to create custom shake, glitch, glow, impact and motion-blur stacks. Unsupported requests remain human-readable MVS labels and appear in export warnings.

This is not support for every effect in Alight Motion. The public effect guide includes a much larger catalogue, while this build maps only these nine effects with inspected fields. Existing template effect XML is preserved rather than overwritten silently. Verify generated effect stacks in the target Alight Motion version before publishing.
