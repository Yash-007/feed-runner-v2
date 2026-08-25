# Session context — Feed Runner

Living state file. Update after every meaningful step. Hand this to any fresh
session (or another account) to continue: it assumes nothing from chat history.

## Project

- `feed-runner-v2` (this repo): Android app. Bubble overlays X/LinkedIn, captures
  posts, drafts replies/posts via the backend. Kotlin + Compose, minSdk 30.
- `feed-runner-backend` (sibling dir, own repo): Go + Mongo. All Claude calls
  live here; the app has no Anthropic key.
- Deployed: https://feed-runner-backend.onrender.com (Render free tier,
  render.yaml, auto-deploys on push to main). Mongo = Atlas (URI in Render env +
  backend/.env). Better Stack pings /healthz + /ping to keep it warm.
- Auth: bearer token on all routes except /healthz, /ping. Token lives in
  Render env API_TOKEN and app's local.properties as ideaBank.token
  (currently ipvIJqB3KmRQZ0c1TESPpvmI4T7iQE1AhYI2buumrQNor7d).
- Device: OnePlus via adb. Accessibility service must stay enabled; NEVER
  `am force-stop com.yash.feedrunner` (drops the grant, manual re-enable).
  uiautomator can't see overlay windows; use screenshots or dumpsys for those.

## Working practices (agreed with Yash)

- Keep THIS file current while working; it is the resume point.
- Token frugality: no screenshots unless the outcome is genuinely uncertain
  (they stay in context forever); batch shell into few calls; verify by
  code-read when code is the truth; after a milestone, start a fresh session
  from this file instead of continuing a long one.
- Commits: small, human, lowercase-ish messages, no AI trailer. Push with the
  token remotes already configured in each repo.
- Checklists live in repo files (task.txt, design.txt, text.txt) and get
  ticked as work lands.

## Current task: UI/UX polish pass (design.txt "Polish pass" section)

All ten tasks (G1–G10) done and verified on device 2026-08-25:

- Edge-to-edge everywhere (wash behind status bar; nav-bar padding on bottom
  bars/composers). enableEdgeToEdge in MainActivity + IdeasActivity.
- DesignSystem gained: Motion (spring tokens), Modifier.pressClickable (press
  scale on every button/card/chip), SegmentedControl (sliding thumb; used by
  theme picker, menu platform toggle, post/quote), Modifier.shimmer.
- Action menu redesigned: scrim + ONE connected card (platform segmented row +
  4 hairline-split rows, soft tinted icon circles from Accent hues), spring
  scale-in from the bubble side, haptic on open. Menu height now includes the
  platform row.
- Panels: spring slide-up + scrim fade-in, drag handle, slimmer header;
  loading = shimmer skeleton drafts + cycling stage line; error = PrimaryButton
  retry. Repost panel same treatment; GeneratingRow also shows skeletons.
- Draft cards: "✓ copied — paste it in" in green w/ scale-in; refine is a
  tinted chip. Chips everywhere got press feedback.
- Ideas: seed cards decluttered (source+age fold into one meta line, max
  platform/status/shelf-life chips), streak bars animate in staggered.
- Setup: permission checklist folds to "Set up and ready ✓" once granted
  (tap = details), bubble card moved above voice rules, voice rules card folds
  to a one-line preview with edit.
- Wash palettes re-anchored on evergreen (mint/sage/cream/sky; dark variants
  match). Meta text 10sp -> 11sp. Bubble dips on tap (scale animation).

Verified by screenshots: setup, menu over X, reply panel via Last result,
Ideas. X flow regression OK (Last result opens, saved strip works).

## Second round (same day): text.txt feature batch — all done

- Ideas is the LAUNCHER now. Header: "Ideas" + bubble pill (dot=state, tap
  toggles service, routes to Setup + toast if perms missing) + server pill +
  ⚙ opens MainActivity (label "Setup", launcher filter removed, still
  exported for adb). Header one row; sync line only when pending>0.
- Streak: compact single row (count · streak chip · mini bars), tap expands.
  Counts ALL used picks now — backend store.UsedPickTimes (reply+post+quote,
  was ReplyPickTimes), app StreakStore.recordUse/removeUse unconditional in
  IdeaBankRepository. Copy says "sent today".
- linkedin_post.md gained STRUCTURAL VARIETY section (anti AI-pattern:
  varied openings/lengths/rhythm/endings, contrast-scaffold banned).
- Capture viewer: translucent ✕ top-right (tap-anywhere kept).
- New bubble icon ic_send_bubble (speech bubble + paper-plane knockout);
  adaptive launcher icon (evergreen gradient bg + same glyph + monochrome),
  manifest icon/roundIcon set.
- Add-idea dialog: 5-line field, IME Default for prose.
- Verified on device: launcher resolves to IdeasActivity, bubble pill starts
  service, new bubble icon seen over X, dialog sized right. ic_spark now
  unused (kept in repo).

## Third round (same day): word-limit slider — done

- WordLimitSlider in DesignSystem: hand-drawn (hairline track, evergreen
  gradient fill, white thumb, haptic notch ticks); far-right notch = "auto"
  (no cap, the default); readout chip shows "auto" / "≤ N words". Gesture
  handlers read state via rememberUpdatedState — keying pointerInput on the
  value kills a drag at its first notch (bug found on device, fixed).
- Reply panel: slider above the composer, 10–60 step 5. Repost composer:
  20–150 step 10. Caps persist in WordLimitStore (prefs; reply + post keys,
  0 = auto) and apply to analyze/angle/refine/chat and posts/chat calls.
- Wire: word_limit int on all six /copilot bodies; backend appends a hard-cap
  line via the voice-rules block (arrives last, overrides — no llm-layer
  change). Absent/0 = no cap, old servers ignore the field.
- Verified on device: render, drag-snap, tap-to-set, auto reset. Committed +
  pushed: app 0e1632c, backend e8d3d5a (Render auto-deploy).
- Live E2E after deploy: /copilot/replies/chat with word_limit=8 answered in
  6 words; same request uncapped answered in 15. Cap reaches the model.
- Side find: local.properties had a mangled line gluing the dead
  anthropic.apiKey to "ideaBank.token=localdevtoken", so `grep ideaBank.token`
  matched two lines and curl sent a broken auth header (HTTP 000 / empty
  reply). Line deleted; grep with `^ideaBank.token=` from now on.

## Fourth round (same day): menu icons + shared slider + regenerate — done

- Menu platform toggle shows brand icons (ic_brand_x / _linkedin / _general,
  simplified vectors) via a new iconRes slot on SegmentedControl; labels stay
  as content descriptions. Verified on device.
- Reply sheet: second WordLimitSlider beside the drafts + "↻ regenerate
  drafts" chip (shown only when the capture file still exists). Both sliders
  bind the same replyLimit state/store — drag one, the other follows
  (verified). Regenerate = PanelController.regenerate(): decodes stored
  capturePath off-main, resubmits to AnalysisManager at the current cap;
  panel flips to the skeleton loading state in place (window not reopened).
  Saves as a new result. Verified E2E on device against the deployed backend.
- Cap obedience finding: wiring was perfect, the model soft-overshoots small
  caps on rich captures (28-33 words against 15; probes with empty rules
  obeyed). Backend cap wording hardened (6cf5d8b): highest priority, count
  and recount — re-probed after deploy, all six drafts 9-10 words on a 15
  cap with real voice rules. App commit be13a56.

Nothing in flight. Next work starts fresh from this file.

## Known open items (older, not urgent)

- Rotate: the GitHub PAT in both repos' remotes, and ideally the Atlas
  password / scoped user (URI grants access to 15 unrelated DBs).
- Render free tier: 2/8 test calls hung >150s once; server-side timeout on
  Anthropic calls still unimplemented; paid instance would fix cold starts.
- Angle EXTEND / style OBSERVATION chips still purple (categorical hues,
  deliberately kept; recolour if Yash wants).
- Latency logging: app logs Copilot calls as tag "Copilot" (path, ms, kb).

## How to test cheaply

- Backend: `curl -s -H "Authorization: Bearer <token>" https://feed-runner-backend.onrender.com/healthz`
- Free handler probe: POST /copilot/replies/chat with {"platform":"tiktok"} → 422.
- Reply drafting test payloads exist in scratchpad from prior sessions; or
  build a synthetic capture with PIL (see git history of this session's tests).
