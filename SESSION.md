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

## Fifth round (2026-08-26): accounts — done

- Backend (d8af755): users collection (bcrypt hash; unique username + token
  indexes). Open routes POST /auth/signup {username,password,name} and
  /auth/login {username,password} → {token,username,name}. Session token =
  "fr_"+64 hex, never expires, same token returned on every login (second
  device just works). requireToken accepts session tokens alongside the
  legacy shared API_TOKEN (kept for the Better Stack pinger + old installs).
  Validation: username [a-z0-9_]{3,30}, password ≥6, name 1-60.
- App (8257af1): BackendConfig gains authToken/accountName/accountUsername +
  signOut; IdeaBankApi sends session token when present, legacy token as
  fallback, and its 401 message now prefers the server's body text. New
  AuthActivity: full wash+halftone background, serif wordmark, one card —
  Sign in/Create account segmented, name field animates in for signup,
  show/hide password, spinner-in-button, inline errors. IdeasActivity
  bounces to it until logged in. Setup gets an account card with Sign out
  (clears session → front door).
- Verified: curl (signup, duplicate 409, wrong-password 401, login returns
  same token, session token passes /streak, garbage rejected) AND on-device
  (login as probe_test → Ideas loads; sign out → auth screen). Device left
  at the sign-in screen — Yash creates his real account there.
- DB litter: one test user "probe_test" (password probe123) in users
  collection; delete whenever.

## Copy tweaks (2026-08-26, 1897dfd)

- Auth footnote: session-mechanics line replaced with "Your ideas, drafts and
  streak live in your account."
- Both loading sheets (reply + repost skeletons) now say: "You can close this
  and keep scrolling — you'll get a nudge when the drafts are ready." Verified
  truthful in code: both jobs survive dismissal and toast on completion.
- Installed and eyeballed on device once replugged; footnote also centred
  (0d4e8c7). Loading hint verified by code-read (both jobs survive dismissal
  and toast), skipped the paid call.

## Sixth round (2026-08-26): per-user data scoping — done (ee5691b)

- Owner rides the request context: auth middleware resolves the bearer
  (session token -> that user; shared API_TOKEN -> OWNER_USERNAME env,
  default "yashx_404") and store.WithOwner stamps it; every store query
  folds it in via scoped(). Handlers untouched. Empty owner (laptop, no
  token) = unscoped, old behaviour.
- Owner field (bson only, json:"-") on IdeaSeed, DraftPick, IdeaBatch.
  Owner+created_at / owner+picked_at indexes added.
- Boot migration ClaimUnowned: ownerless rows -> OWNER_USERNAME, idempotent.
- Verified live: probe_test sees 0 seeds / 0 streak; legacy token sees all
  153 seeds and the 89-total 10-day streak. Yash picked username yashx_404 —
  MUST sign up as exactly that to own the data (username is first-come;
  account does not exist yet, only the mapping).
- Note: client_seed_id/client_pick_id unique indexes still global (ids carry
  ms timestamps, collision practically nil). Device-local data (results.json,
  streak cache, prefs) still single-namespace per phone.
- Shortened close-me hint APK (d86a86d) installed on device this round.

## Seventh round (2026-08-26): perf pass — done

- Root cause of "app feels slow": debug builds. Release variant now = R8 +
  shrinkResources + debug signing (2.7MB vs 49MB); THE PHONE RUNS RELEASE NOW
  — install app/build/outputs/apk/release/app-release.apk from here on.
  proguard-rules.pro keeps enum valueOf names (server strings -> enums).
- Main-thread disk I/O removed: bubble-menu open (MenuController age line),
  PanelController showLastResult/selectResult/showFinished, and the unread
  badge count all parsed results.json on main; all on workers now, UI shows
  first and data fills in. Menu "Last result" row stays tappable while age
  loads ("…" placeholder; empty store answers with the toast).
- Verified R8 build on device: auth renders, probe_test login works, Ideas
  shows probe's EMPTY bank (scoping visible on device), cold start 1.6s /
  warm 0. Signed out after; device back at the sign-in screen.

- Server pill removed from the Ideas header (060bc45): deployed backend is
  the default, failures still show via the message line + sync counter; the
  backend-address dialog stays reachable from the empty state. Release APK
  installed.

## Eighth round (2026-08-26): pre-publish backend review — done

Reviewed the whole backend. Fixed (backend 6ceb1ce, app 393c8bf, deployed):
- Signup gated by SIGNUP_CODE env (403 on mismatch; empty = open + boot
  warning). App signup form gained an "Invite code" field (ignored when the
  server has no code). *** ACTION FOR YASH: set SIGNUP_CODE in Render env
  BEFORE publishing the APK, else anyone can create accounts and spend the
  Anthropic key. ***
- /auth/* rate limited: 20/min per IP (fixed window, in-memory,
  X-Forwarded-For aware). Verified live: hammering returns 429s, normal
  login works after the window.
- client_seed_id / client_pick_id uniqueness now per owner (partial index for
  seeds since hand-typed ones lack the id); old global indexes dropped at
  boot. Fixes the cross-account duplicate-key 500 that would have stuck the
  app outbox in a retry loop.
- Login timing flattened: unknown usernames burn a dummy bcrypt.
- Anthropic calls: option.WithRequestTimeout(150s) — closes the old "server
  side timeout unimplemented" item.
Accepted as-is (noted, low risk): session tokens stored plaintext in mongo,
never-expiring sessions, global deleted_ideas counter, chat replies returned
but unsaved if mongo write fails after generation.
Verified after deploy: probe_test login OK (same token), signup validation
answers (not 403, code unset), 153 seeds + 89/10 streak intact through the
index migration. Release APK with invite field installed on device.

- Late catch (a9cc5fb): release builds ship IDEA_BANK_TOKEN as "" — the
  shared bearer no longer travels in the published APK (verified absent from
  classes.dex). Release relies wholly on the session token behind the auth
  gate. Debug builds keep the token for adb work.

## Ninth round (2026-08-26): split reply caps — done

- Reply sheet sliders independent now: draftLimit (capture/regenerate/refine;
  key reply_drafts) vs chatLimit (chat + angle batches; key reply_chat), old
  "reply" key read as default for both. Phone-local by choice. Repost cap
  unchanged. Release built + installed.
- Yash signed up on device as yashx_404 (his session showed owned seeds, dark
  theme active). NOTE: device is now actively HIS — no more scripted
  input taps without checking mCurrentFocus first; a blind login script this
  round typed into his open seed thread (harmlessly — verified no stray chat
  turns server-side, nothing destructive).
- run-as no longer works for prefs inspection (release build, not debuggable).

- Defaults (45eb3f9): drafts + composer caps default to 20 words on fresh
  installs; post/quote stays auto. Explicit auto (0) is respected. Yash's
  phone has the old "reply"=15 key, so his sliders read 15 until he moves
  them. Installed.

## Tenth round (2026-08-26): panel platform tabs + rail marks — done (2da0f93)

- Reply sheet header: static platform chip replaced by the icon SegmentedControl
  (X | in | spark, 136dp, thumb in the platform's hue; wordmark trimmed to 19sp
  so Close never clips). Tapping another tab calls switchPlatform -> redrafts
  the SAME capture under that platform's prompts (regenerateAs path; no-op on
  same tab; toast if the capture file is gone). Hidden while loading.
- HistoryEntry carries platform; each saved-rail card shows the brand mark
  tinted in its hue before the author.
- Verified on device (dark theme): tabs render w/ LinkedIn thumb, rail shows
  "in" marks. Tab-switch redraft not exercised on device (same code path as
  regenerate, already proven; saves a vision call).
- Device driving now guarded: locate bubble by pixel scan, check
  mCurrentFocus/launcher idle before any scripted taps (bubble pill moved
  after the server pill was removed — coords (845,225) in current layout).

- Correction (0b5cb52): panel platform tabs now NAVIGATE — newest saved
  result for the tapped network, no API call (Yash's expectation; the
  redraft-on-switch behaviour surprised him with a paid call). Empty network
  -> toast, thumb stays. Tested on device: X tab switched instantly to the
  saved X result (rail selection followed), General tap no-op'd with toast,
  logcat -s Copilot empty throughout = zero drafting calls. Cross-platform
  redraft has no dedicated button now (regenerate stays same-platform);
  workable via menu platform toggle + fresh capture.

- Follow-up (this commit): investigated Yash's "tabs swapped" report on his
  actual data — NOT a bug: his two newest results are the same @DrishtiPandita
  post captured twice, once labeled in, once X (platform = menu toggle at
  capture time), so both tabs show the "same" post in different voices.
  General had zero results, so its toast-only tap read as broken. Fix: tabs
  for networks with nothing saved render dimmed (optionDimmed slot on
  SegmentedControl; availability from Ready.history). Verified on device.

## APK sharing (2026-08-26)

- Shareable build at ~/Desktop/FeedRunner-0.1.apk (debug-signed release; future
  shared updates must be built on this Mac or friends reinstall).
- Invite-code flow REMOVED at Yash's call (backend 437ecb1, app 85cf2b6):
  signup is open again; auth rate limit (20/min/IP) stays as the only guard.
  SIGNUP_CODE env no longer read — nothing to set on Render. Accepted risk:
  anyone with the URL can create an account and spend the Anthropic key.
- Bubble left running on device; phone has the same build as the Desktop APK.

## Eleventh round (2026-08-27): web component library patterns — done (838b225)

- Surveyed via 5 parallel agents: beautifului.dev, beui.dev, rareui.com,
  transitions.dev, ui.shadcn.com (full catalogs in their reports; convergent
  picks implemented, web code NOT portable — patterns re-built in Compose).
- Shipped: DigitTicker (per-digit reel roll, streak count; first composition
  static, changes animate), ShimmerText (drafting stage line), DrawnCheck
  (stroke-draw check on copied, reply + repost), SendButton morph (arrow ->
  spinner via AnimatedContent, ChatUi), StaggerIn (draft cards settle 45ms
  apart, keyed on resultKey so refinements never replay), Ideas empty-state
  anatomy (tinted spark circle + serif title + line + CTA) — all in
  DesignSystem + surface wiring.
- Verified on device: empty state (filtered view) renders; ticker/stagger/
  morph compile-verified only (need value changes / generation to see).
  Desktop APK refreshed. Yash actively using app (5 sent today, 11-day
  streak) — keep guarding scripted taps.
- Parked ideas from the survey (good, not now): sonner-style action toasts w/
  undo, long-press context menu on cards, GitHub-heatmap streak, grid-reveal
  loader, emoji/leaf burst on pick, command palette for ideas search.

## Twelfth round (2026-08-30): harvest engine prompt alignment — done

- Yash is building an X-scrolling harvesting engine that seeds the bank
  directly. Rewrote his filter prompt as backend prompts/harvest_filter.md
  (e75c043), aligned to POST /seeds wire format: each output object is a
  ready-to-store body. Key alignments: client_seed_id "harvest-<post id>"
  (idempotent rescrapes), source "harvest" (NEW enum value, backend model +
  app SeedSource d66ab22, label "harvested"), post_text preserved (<=300
  chars verbatim — app thread header + ideation prompt need it), category
  rides as FIRST theme tag (filterable in Ideas) plus its own key (server
  ignores extra keys), shelf_life/tension/angle_hint 1:1 with schema and
  reply.md's idea-seed vocabulary. Added: never seed @yashx_404's own posts;
  engine contract section (captured_at_millis, vision ids, per-run caps).
  Purpose preserved: category caps 3-4/batch, dedup, high bar, vision variant.
- Live contract test on deployed backend: harvest seed stored (source=
  harvest, tags ordered), duplicate:true on repost, deleted after. Green.
- Device was unplugged: release APK with the "harvested" label NOT installed
  yet; Desktop APK NOT refreshed. Do both on next adb connect.

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
