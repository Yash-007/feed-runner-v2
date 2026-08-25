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

Nothing in flight. Next work starts fresh from this file.

## Known open items (older, not urgent)

- local.properties anthropic.apiKey is INVALID (on-device drafting was dead
  before the server move; harmless now, key unused, can be deleted).
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
