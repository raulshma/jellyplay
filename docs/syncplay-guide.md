# SyncPlay watch parties

[SyncPlay](https://jellyfin.org/docs/general/server/sync-play) is Jellyfin's
built-in feature for watching media in perfect sync with friends and
family, no matter where they are. JellyPlay has first-class SyncPlay
support, including speed/skip correction to keep everyone's playback
frame-aligned.

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
3. Tap the **SyncPlay** icon in the player controls — or open the
   detail page's kebab menu and tap **Start watch party**
4. Choose a **group name** and tap **Create**

JellyPlay automatically:
- Pauses your local playback
- Registers the group with the Jellyfin server

### Inviting friends

The group appears in every participant's SyncPlay group list, so
friends can join by tapping **Join** on the group's card.

You can also share a **deep link** that opens the group directly:
`jellyplay://syncplay/<groupId>`

## Joining a watch party

### As a guest

1. Open **SyncPlay** from the player controls
2. Tap **Join** on the host's group card
3. JellyPlay buffers the media and starts playback in sync

Depending on your **Join behavior** setting (see below), invites can
also be accepted automatically.

### Auto-accept invites (optional)

If your friends start parties often, enable
**Settings → Playback → SyncPlay → Auto-accept invites** to be
automatically added when you receive an invite.

## Sync correction

If someone's network drops a packet, JellyPlay uses two correction
strategies:

| Strategy | When used | Visible to the viewer? |
| -------- | --------- | ---------------------- |
| **Speed-to-sync** | Drift between 60 ms and 400 ms | Briefly plays faster or slower to close the gap |
| **Skip-to-sync** | Drift over 400 ms | A small seek to the corrected timestamp |

Drift under 60 ms is ignored. The thresholds are fixed in the client —
no configuration needed.

## SyncPlay settings

SyncPlay options live in **Settings → Playback → SyncPlay**:

- **Join behavior** — always join automatically, ask every time, or
  never join
- **Sync tolerance** — how far drift may wander before correction:
  tight (50 ms), balanced (100 ms), loose (500 ms), or a custom value
- **Auto-accept invites** — join invited groups without prompting

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
| "Group not found" | The host may have left or the server was restarted. Ask them to start a new group. |
| Cannot start SyncPlay | Verify the server is Jellyfin 10.7.0+ — older versions don't support SyncPlay. |
| Frame drops during sync | Switch to the **mpv** engine in **Settings → Playback → Player Engine** for smoother buffering. |

## Privacy

SyncPlay runs entirely over your Jellyfin server. JellyPlay does **not**
route any media through third-party services. If your
Jellyfin server is exposed via a reverse proxy, the same rules apply —
your traffic stays on your infrastructure.

## Next steps

- ⬇️ [Set up offline downloads for travel →](./offline-downloads.md)
- 📺 [Install JellyPlay on your TV →](./android-tv-setup.md)
- 🎵 [Explore the music player and synced lyrics →](./lyrics-music-player.md)
