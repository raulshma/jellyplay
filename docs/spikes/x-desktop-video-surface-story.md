# Spike: Desktop video surface story (wave 12B) — mpv render-API software renderer

**Status:** implemented and gated on Windows only. Honest framing: every runtime
observation below comes from the Windows dev machine (`tools/mpv/libmpv-2.dll`,
mpv dev build shipped with this checkout). Nothing in this document has touched
real macOS/Linux hardware yet; the whole point of shipping path B is that it is
plausible there, not that it is proven there.

## The problem (plan risk R1, fallback path B)

Since wave 9A, desktop video only worked on Windows: an mpv child window
(HWND) embedded behind a SwingPanel
(`DesktopVideoSurfaceBridge.isWindowsVideoSurfaceSupported`), with
`Route.VideoPlayer` dead-ended on every other OS. This wave adds the
platform-independent embedding alternative:

```
libmpv (vo=libmpv, render API "sw" backend)
   └─ mpv_render_context_render(SW_SIZE/SW_FORMAT/SW_STRIDE/SW_POINTER)
        writes a composited, scaled, letterboxed BGRA frame into our Memory
        └─ JNA bulk read → byte[] → skia Image.makeRaster(BGRA_8888, OPAQUE)
             └─ toComposeImageBitmap → Compose Canvas drawImage
                  (shared/feature/player-video/.../DesktopSoftwareVideoPane.jvm.kt)
```

No GL context, no child window, no display dependency — decode → swizzle →
raster happens entirely on CPU. Header says plainly: "This method of rendering
is very slow" (render.h L139) relative to GPU VOs; for 1080p-class content on
modern CPUs it is fine, and correctness beats absence of video.

## Chosen mechanism

- `vo=libmpv` set before `mpv_initialize`; `mpv_render_context_create` with
  `MPV_RENDER_PARAM_API_TYPE="sw"` at engine construction, i.e. before any
  loadfile can start VO init (render.h L113-115 warns of window fallback).
- One pull model instead of push callbacks (see below); frames land in a
  caller-owned JNA `Memory` sized `stride × h`, stride rounded up to 64 B
  (render.h L395-398 recommends 64-multiples for the SIMD path).
- Pixel format pinned to `"bgr0"` (render.h L367-374 list), which is byte-exact
  Skia `BGRA_8888` raster memory when alpha type is OPAQUE (mpv's 4th channel
  is documented uninitialized garbage; skia ignores it). Zero per-pixel
  conversion end-to-end.
- mpv does its own scaling + letterboxing into whatever SW_SIZE it receives,
  so aspect/panscan semantics match the HWND path by construction, and it
  composites subtitles+OSD INTO the surface — the sw Canvas gets that for free.

## Header-verified constant table (tools/mpv/include/mpv/render.h)

Every value re-checked against THIS checkout's header before mapping:

| Constant                          | Value | Header line |
|-----------------------------------|-------|-------------|
| MPV_RENDER_PARAM_INVALID          | 0     | L176        |
| MPV_RENDER_PARAM_API_TYPE         | 1     | L192        |
| MPV_RENDER_PARAM_OPENGL_INIT_PARAMS | 2   | L198        |
| MPV_RENDER_PARAM_BLOCK_FOR_TARGET_TIME | **12** | L317   |
| MPV_RENDER_PARAM_SW_SIZE          | **17** | L360       |
| MPV_RENDER_PARAM_SW_FORMAT        | **18** | L385       |
| MPV_RENDER_PARAM_SW_STRIDE        | **19** | L406       |
| MPV_RENDER_PARAM_SW_POINTER       | **20** | L424       |
| MPV_RENDER_API_TYPE_SW            | "sw"  | L470 (#define) |

(The task brief floated SW_SIZE=10/BLOCK=9 etc. — wrong; trust the header.)
Signatures mapped (`MpvLibRender.RenderC`): create L578, set_parameter L591,
update L661, render L709, report_swap L722, free L733. `mpv_render_param` is
`{ enum type; void *data; }` (L458-461).

### The indirection matrix — where this spike nearly died

`param.data` carries DIFFERENT levels of indirection per param, and getting
`SW_POINTER` wrong produces the worst failure mode imaginable: instant silent
native heap smash whose symptoms surface minutes later as random JVM crashes.

Verified against mpv's own consumer code (`video/out/libmpv_sw.c`, master):

| Param                | `param.data` holds                         |
|----------------------|--------------------------------------------|
| SIZE / STRIDE / BLOCK_FOR_TARGET_TIME | address OF the value (`int*`, `size_t*`) |
| FORMAT / API_TYPE    | the NUL-terminated string itself           |
| SW_POINTER           | **the pixel buffer pointer itself** — libmpv_sw.c does `void *ptr = get_mpv_render_param(params, MPV_RENDER_PARAM_SW_POINTER, NULL); wrap_img.planes[0] = ptr;` |

The first implementation passed `&target` for SW_POINTER. mpv then rendered a
full frame (~300 KB at 320×240×4) into the 8-byte pointer slot. Observed:
EXCEPTION_ACCESS_VIOLATIONs in jvm.dll during unrelated work — JUnit class-name
filtering, `ClassLoader.defineClass0`, tiered compilation — always far from the
actual call, plus spurious "needs a giant thread stack" folklore born while
chasing those secondary crashes. Fixed to single indirection
(`params[3].data = target`), after which all real-engine sw tests pass on
DEFAULT thread stacks. The trap is now documented at [MpvLibRender.swRenderParams]
and must be preserved through any future refactor.

## Polling vs update-callback decision: POLLING, deliberately

- `mpv_render_context_set_update_callback` (L634) fires on an mpv-owned native
  thread. Marshalling that into a JVM callback through JNA adds a
  native-thread ↔ JVM attach hazard we do not need.
- `mpv_render_context_update` (L661) is only obligatory alongside
  MPV_RENDER_PARAM_ADVANCED_CONTROL; the header marks it "optional if
  MPV_RENDER_PARAM_ADVANCED_CONTROL was not set (default)" (~L641-642). We
  never set ADVANCED_CONTROL and never subscribe to callbacks, so there is
  nothing update() would be asked about — render requests are issued by our own
  cadence instead.
- `mpv_render_context_report_swap` (L722): header warns "if you use it
  inconsistently, expect bad playback"; a fixed-cadence poller cannot promise
  per-swap calls, so we never start using it at all.

The pane ticks at ~30 fps cap (33 ms) **while playing** (`playingFlow`), and
runs a 250 ms watchdog tick while the session is LOADED but NOT playing
(paused / keep-open ENDED) so seek-while-paused repaints within a quarter
second — a full-payload compare dedups unchanged frames, so an untouched pause
costs ~4 memcmps/sec, not raster rebuilds. When the session is fully IDLE and
not playing, zero polling: the loop suspends until load or play resumes.
(Decision table extracted as `SwPaneTicker`, unit-tested in
`SwPaneTickerTest`.) Both cadences do ALL heavy work — native render AND the
native→JVM copy — on Dispatchers.Default, never the compose dispatcher: the sw
backend scales/composites on the calling thread and self-throttles toward
frame-display time (BLOCK_FOR_TARGET_TIME default enabled = the natural
pacer), so an inline call would jank the UI at HD sizes. render.h L62-64
allows renders from any thread given the one-call-at-a-time rule, which the
engine enforces with a tryLock. Single-flight also means a tick arriving while
a render is still in flight is DROPPED (never queued behind itself).

## Other quirks observed on the Windows dev build

1. **`file:/…` URIs stall forever**: `loadfile "file:/C:/..."` never emits
   START_FILE or any error on this dll (documented in MpvSwRenderPipelineTest);
   absolute WIN32 paths (`C:/...`) load fine. Production sources are HTTP(s)
   URIs, unaffected. Kept as-is; if mac/linux shows the same, revisit later.
2. **Pre-VO renders are wasted work**: until "vo-configured" flips true the sw
   backend has no frame; `pullFrame` reads that flag on the CORE handle first
   (render contexts are NOT mpv_handles — querying on them access-violates)
   and drops those ticks.
3. **Stride padding is writable-by-mpv**: some scaler backends may overwrite
   the `(w,y)→(0,y+1)` gap AND the tail padding after the last line (header
   L414-422). Buffers must therefore be exactly `stride × h` and callers must
   not assume padding bytes are preserved (they aren't read back either).
4. `vo=null` cleanly turns the same class into an audio-only engine (tests use
   `ao=null` instead so the RENDER path stays exercised).

## Guard unification & policy

- `DesktopVideoSurfaceBridge` gains `isSoftwareVideoSurfaceSupported`: probed
  lazily by `MpvSoftwareSurfaceSupport` (injected from `DesktopAppRoot`
  composition): create core → `vo=libmpv`,`ao=null` init → sw
  render_context_create → free. Caches; never throws; failure restores pre-12B
  behavior. Real smoke test, NOT an os.name check — a stripped libmpv without
  the sw backend degrades honestly.
- Entry registration AND dead-end predicate both read
  `isWindowsVideoSurfaceSupported || isSoftwareVideoSurfaceSupported`.
- Factory policy: realized HWND wins (Windows-primary, unchanged byte-for-byte);
  no handle within the existing 4 s budget + sw smoke-passed → software engine
  (logged when that fallback fires ON Windows, since it means AWT failed);
  neither story → legacy no-wid audio-only degrade.

## What remains UNVERIFIED here (Windows-only evidence so far)

- macOS/Linux COMPOSITING quality: skia BGRA_8888 raster semantics are
  platform-independent in Compose Multiplatform theory, but endianness
  assumptions of "bgr0"-as-BGRA_8888 have literally never run off-Windows in
  this repo. First mac/linux bring-up should visually check colors, not just
  non-blackness (R<->B swapped video would pass every pixel-variance test).
- libmpv DISTRIBUTION risk: none of macOS/Linux bundles exist in-repo yet; on
  those machines users need libmpv installed where dlopen finds it. The probe
  covers "dll present but sw backend missing" (MPV_ERROR_NOT_IMPLEMENTED from
  create); it cannot cover "no dll at all" better than returning false.
- AudioQueue interplay: sw-path tests run `ao=null`. Whether the desktop
  AudioQueue manager (DesktopAudioQueueManager) behaves identically when the
  session engine is the sw variant is untested (its tests use plain
  MpvDesktopEngine). No known coupling, but unproven.
- Subtitle overlay interplay: NativePinnedSubtitleHost / zoomed subtitle
  overlays compose ABOVE the canvas like above SwingPanel, but mpv ALSO bakes
  subs into the sw surface (sub-text routed through COMPOSE_CUE). Double-draw
  risk when both paths are active is untested pixel-level; expect the commonMain
  screen's existing single-subtitle-source logic to govern, verify visually.
- Performance on real content (720p60/1080p24+) is unmeasured anywhere; zimg vs
  swscale builds differ measurably. Threading: the pull runs off-UI-thread
  (Dispatchers.Default) as of the review round, so any remaining jank would be
  CPU saturation, not dispatcher blocking.
- The EOF-keep-open replay path WAS exercised against the real sw engine; the
  multi-session reuse case (factory reusing engines across navigation) follows
  the HWND engine's release discipline and was not separately stress-tested.
- Wayland: wl_display param constant exists but untested; X11 likewise.

## Files (wave 12B)

- `apps/desktop/.../player/mpv/MpvLibRender.kt` — JNA binding + constant table.
- `apps/desktop/.../player/MpvSoftwareRenderEngine.kt` — engine subclassing the
  HWND engine via three hooks (`liveMpvHandle`, `onBeforeContextDestroy`,
  `hwdecFor` pinned to "no").
- `apps/desktop/.../player/MpvSoftwareSurfaceSupport.kt` — cached capability probe.
- `apps/desktop/.../player/DesktopMpvPlayerEngineFactory.kt` — HWND-primary selection.
- `apps/desktop/.../DesktopAppRoot.kt` — probe wiring + unified guards.
- `shared/feature/player-video/.../DesktopSoftwareVideoPane.jvm.kt` — seam +
  compose pane (`SoftwareFrameVideoSurface` interface lives here).
- `shared/feature/player-video/.../DesktopVideoSurface.jvm.kt` — dispatch, HWND path untouched.
- `shared/feature/player-video/.../DesktopVideoSurfaceBridge.kt` — second predicate.
- Tests: `MpvSoftwareRenderEngineTest` (contract), `MpvSwRenderPipelineTest`
  (pixels: distinct-frame pulls, seek-changes-pixels, probe smoke).
