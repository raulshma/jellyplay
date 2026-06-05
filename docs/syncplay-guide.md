# SyncPlay watch parties

[SyncPlay](https://jellyfin.org/docs/general/server/sync-play) is Jellyfin's
built-in feature for watching media in perfect sync with friends and
family, no matter where they are. JellyPlay has first-class SyncPlay
support, including in-player group chat and speed/skip correction to
keep everyone's playback frame-aligned.

## What is SyncPlay?

SyncPlay synchronises the position and play state of multiple Jellyfin
clients so that everyone watches the same thing at the same time. Even
with network jitter, JellyPlay uses **server-time synchronization** and
intelligently adjusts playback speed or performs tiny skip-to-sync
corrections to keep the group aligned.

Think "Discord watch party" but built into your media server — no
accounts, no extra service, no cloud.

## Requirements

- Jellyfin server **10.7.0 or later** (10.10+ recommended)
- All participants must be connected to the same Jellyfin server
- Each participant has their own JellyPlay install (or any other
  SyncPlay-compatible client)
- All users need access to the same media item

## Starting a watch party

### As the host

1. Open any movie, episode, or video in JellyPlay
2. Start playback
3. Tap the **SyncPlay** icon in the player controls (or the kebab menu
   → **SyncPlay → Start group**)
4. Choose a **group name** and optional **password**
5. Tap **Create**

JellyPlay automatically:
- Pauses your local playback
- Registers the group with the Jellyfin server
- Generates a 6-character **invite code**

### Inviting friends

Share the invite code via Discord, Telegram, WhatsApp, or any chat app.

Friends join with: **SyncPlay → Join group → enter code**.

You can also share a **deep link** that pre-fills the join dialog:
`jellyplay://syncplay/join/<code>`

## Joining a watch party

### As a guest

1. Open **SyncPlay** from the player controls (or from
   **Settings → SyncPlay**)
2. Tap **Join group**
3. Enter the **invite code** shared by the host
4. If the group has a password, enter it
5. JellyPlay buffers the media and starts playback in sync

### Auto-join (optional)

If your friends start parties often, enable
**Settings → SyncPlay → Auto-join groups I'm invited to** to be
automatically added when a host lists you as a participant.

## In-player group chat

While watching together, tap the **Chat** icon in the player overlay
(or press **OK / Enter** on the D-pad when a notification is on
screen) to send a message to the group. Chat history is preserved for
the session.

## Sync correction

If someone's network drops a packet, JellyPlay uses two correction
strategies:

| Strategy | When used | Visible to the viewer? |
| -------- | --------- | ---------------------- |
| **Speed-to-sync** | Drift under 2 seconds | Briefly plays at 1.05x or 0.95x |
| **Skip-to-sync** | Drift over 2 seconds | A small seek to the corrected timestamp |

The thresholds are configurable in **Settings → SyncPlay**:

- `max_delay_speed` (default 50 ms) — max drift before speed-correction
- `max_delay_skip` (default 300 ms) — max drift before skip-correction
- `sync_attempts` (default 5) — number of retries before disabling sync

For most home networks on WiFi, the default values are perfect.

## Group settings

The group host can toggle from the SyncPlay overlay:

- **Repeat mode** — loop the current item
- **Shuffle** — randomize the order of queued items
- **Pause for everyone** — synchronised pause (only the host can
  unpause for the whole group; guests can pause locally only)

## What works and what doesn't

| Feature | SyncPlay support |
| ------- | ---------------- |
| Movies, TV episodes, music videos | ✅ |
| Live TV | ❌ (one stream only) |
| Recorded DVR content | ✅ |
| Music (audio) | ❌ (audio SyncPlay not yet supported by Jellyfin server) |
| Subtitles | ✅ (each viewer can override their own) |
| Audio tracks | ✅ (independent per viewer) |
| Trickplay thumbnails | ✅ |
| Hardware decoding | ✅ |

## Troubleshooting

| Problem | Fix |
| ------- | --- |
| "Group not found" | The host may have left or the server was restarted. Ask for a new code. |
| Audio is desynced | Lower `sync_attempts` or increase `max_delay_speed` in **Settings → SyncPlay**. |
| Chat messages not sending | Check that all participants are connected to the same Jellyfin server URL. |
| Cannot start SyncPlay | Verify the server is Jellyfin 10.7.0+ — older versions don't support SyncPlay. |
| Frame drops during sync | Switch to **libmpv** engine in **Settings → Player** for smoother buffering. |

## Privacy

SyncPlay runs entirely over your Jellyfin server. JellyPlay does **not**
route any media or chat data through third-party services. If your
Jellyfin server is exposed via a reverse proxy, the same rules apply —
your traffic stays on your infrastructure.

## Next steps

- ⬇️ [Set up offline downloads for travel →](./offline-downloads.md)
- 📺 [Install JellyPlay on your TV →](./android-tv-setup.md)
- 🎵 [Explore the music player and synced lyrics →](./lyrics-music-player.md)
