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

## Current task: from text.txt additions

1. [x] Evergreen theme replacing purple (#0E7A55 / mint dark), bubble matched
2. [x] Dark mode: Appearance card (System/Light/Dark) on setup screen,
       process-global ThemePreference, overlays follow it (verified: dark menu
       over light LinkedIn)
3. [x] LinkedIn support end to end (prompts, platform on all routes/stores,
       menu toggle w/ auto-detect, chips, Ideas filter) — verified on the real
       LinkedIn app: detection auto-selected LinkedIn, captured a CoinDCX post,
       comment-appropriate drafts (human/appreciate/ask)
4. [x] General tab (third platform "general"): backend prompts
       general_reply.md / general_post.md, routes accept it, app enum + menu
       segment + chips/filter generalized. Backend pushed (ab7c976). App code
       built + installed. NOT device-tested (skipped to save tokens; pipeline
       identical to LinkedIn's, which is proven).
5. [ ] Device theme is currently pinned Dark from testing — tap
       Appearance > System on the setup screen (one tap, cosmetic).
6. [ ] App repo: commit + push the General changes  <- doing now

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
