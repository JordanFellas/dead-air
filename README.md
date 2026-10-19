# Dead Air

A post-apocalyptic radio mod for **NeoForge 1.21.1**. Build radio towers, carry walkie-talkies, and talk to your crew across the wasteland, by text or by voice with [Simple Voice Chat](https://modrinth.com/plugin/simple-voice-chat), through static that gets worse the farther you are from a tower.

Made for a Firewatch-inspired zombie apocalypse modpack.

## Features

### Walkie-talkies
- Firewatch-style 3D handheld radio. Hold right-click to raise it to your face; sneak + right-click (or **R**) opens the radio.
- LCD screen with signal bars, battery, frequency, channel presets (CH1–CH4), text messages, and battery charging.
- Runs on battery cells. Charge it from the fullest cell in your inventory without wasting energy.

### Voice over radio (Simple Voice Chat)
- Hold a powered radio (or wear one on your belt with Curios) and talk with your push-to-talk key.
- Everyone with a radio on the same frequency, inside the same tower network, hears you at any distance.
- Walkie-talkie filtering, a squelch click and roger beep, and static and dropouts that grow as the signal weakens.
- Adds a "Radio" volume slider to the Simple Voice Chat menu.

### Radio towers (free-form)
- Build any tower design from tower parts on top of (or next to) a **Radio Tower Controller**. Height and size add range; antennas add more.
- Parts include lattice frames (white and red/white), three sizes of tapering mast sections, sloped lattice panels with proper corners, gallery platforms with railings, ladders, a 4-block antenna, parabolic dishes, drum dishes, Yagi and panel antennas, and aviation lights.
- Towers must stand on the ground: some parts have to sit level with the controller.
- **Broadcast Controller**: for huge towers. Needs a 5×5 Tower Foundation pad and holds 10× the energy.
- Towers link into networks when their ranges overlap. Right-click any tower part with an empty hand to open its control panel.
- Bigger towers use more power (from 20 FE/s). They accept FE from any energy mod or run on battery cells.

### Radio stations
Run your own station, like a wasteland KIIS FM, and broadcast it over your tower network.
- **Station Console**, in three tabs:
  - **STUDIO**: a getting-started checklist, START/STOP BROADCAST, NEXT SONG, PLAY ID NOW, and PLAY FROM playlist or live stream. It tells you exactly why nothing is playing.
  - **MUSIC**: add songs and see the playlist. Click a song to play it now.
  - **SETUP**: station name, frequency, how often the station ID plays, and the live stream link.
  - It only goes on the air when a powered tower covers it.
- **Adding songs**: in the MUSIC tab, paste a song file from your computer (`C:\...\song.mp3`), a web link to an MP3 (Google Drive and Dropbox share links work), or press **BROWSE PC**. The server gets the file, checks it plays, saves it and puts it on the air. Nobody has to touch the server's folders. Pasting a live radio link switches the station to it.
- **Server songs**: you can also drop `.mp3` or `.wav` files in `config/dead_air/music/` on the server; every station plays those. Songs added at a console go in a folder named after its call sign (`music/KIIS/`). Songs stream through Simple Voice Chat. Only use music you have the rights to play.
- **Live stream**: put an MP3 internet radio link (Icecast/Shoutcast) in SETUP and press Enter; the station relays it live with the stream's song titles. Only stream content you're allowed to rebroadcast.
- **Server owner settings** in `config/dead_air-common.toml`: turn song adding or stream links off, limit them to operators, set the largest song size (25 MB) and a total music folder cap. Links to the local network are blocked by default.
- **Studio Mic**: place it within 8 blocks of a console, stand next to it and talk to go live as the DJ. The music ducks under your voice. Right-click the mic to record a station ID, and right-click again to save it. It plays every N tracks.
- **Listen on**: a walkie tuned to the station (the LCD shows what's playing), a **Home Radio** block that plays out loud, or a **Boombox** you carry around (right-click: on/off, sneak + right-click: tune, SCAN finds stations).
- Weak signal adds hiss and crackle. Without Simple Voice Chat, stations still play music discs.

### Signal and power
- `dead_air:signal_blocking_blocks` block tag: obsidian, deepslate, iron and similar blocks between you and your tower weaken the signal.
- Three battery tiers: Scrap Battery Cell (50k FE), Lithium Battery Pack (250k FE), Fusion Power Core (1M FE).
- **Radio Backpack**: wear it (Curios back slot or chest slot) to carry a 200-block mobile relay.

## Requirements
| Mod | Required? |
|---|---|
| NeoForge 21.1.x (Minecraft 1.21.1) | Yes |
| [Kotlin for Forge](https://modrinth.com/mod/kotlin-for-forge) 5.5+ | Yes |
| [Simple Voice Chat](https://modrinth.com/plugin/simple-voice-chat) | Optional, for voice over radio |
| [Curios API](https://modrinth.com/mod/curios) | Optional, for belt and back slots |

Install the same mods on the server and on every player's client.

## Building
```
./gradlew build
```
The jar is written to `build/libs/`. Useful dev runs:
- `./gradlew runClient`: test client
- `./gradlew runServer`: local test server (port 25566, voice chat on 24464)
- `./gradlew runClientJoin` / `runClient2`: two players that auto-join the test server

## License
MIT. See [LICENSE](LICENSE).
