# Google Play: what to submit

Everything for the Play Console listing is in this repository:

- **Text, 33 languages:** `fastlane/metadata/android/<locale>/` (`title.txt` ≤ 30, `short_description.txt` ≤ 80,
  `full_description.txt` ≤ 4000, `changelogs/<version code>.txt` ≤ 500). This is the layout
  [fastlane supply](https://docs.fastlane.tools/actions/supply/) uploads; by hand, copy each language into
  *Grow → Store presence → Main store listing → Manage translations*.
- **Images:** `fastlane/metadata/android/en-US/images/` (Play shows the default language's images in every
  language that has none of its own):
  - `icon.png` 512×512, the hi-res icon
  - `featureGraphic.png` 1024×500
  - `tvBanner.png` 1280×720, the TV banner
  - `tvScreenshots/*.png` 1920×1080
- **The app:** the `.aab` from the GitHub Release (`vi-translator-android-tv-X.Y.Z.aab`).

## First-time setup

1. Create the app: name *VI Translator*, default language English (US), *App*, *Free*.
2. *Setup → App signing:* keep **Play App Signing** on. The key in `C:\dev\vitranslator.app\android-tv\release.p12`
   is the **upload key**.
3. *Test and release → Advanced settings → Form factors:* add **Android TV**, and accept the TV
   requirements. TV releases get their own review (TV quality guidelines).
4. Personal developer accounts: run a **closed test with at least 12 testers for 14 days** before
   production is unlocked.

## App content answers

| Form | Answer |
| --- | --- |
| Privacy policy | https://vitranslator.app/privacy/ |
| Ads | No ads |
| App access | All functionality needs the free PC app; see *Reviewer notes* below |
| Content rating (IARC) | Category *Utility, Productivity, Communication or Other*; no violence, sexuality, language, gambling, user interaction or sharing of location; the app shows text sent from the user's own PC. Expected: Everyone / PEGI 3 |
| Target audience | 13 and over (it's a tool for console players; not aimed at children) |
| Data safety | **No data collected, no data shared.** The app only talks to the user's own PC on the local network (pairing code, translated text); nothing is sent to the developer or third parties. Data isn't encrypted in transit on the LAN (plain HTTP to the user's own PC); users can't request deletion because nothing is stored off the device |
| Government app / financial / health / news | No |
| Foreground service permissions | `FOREGROUND_SERVICE_SPECIAL_USE`, see below |

### Foreground service: special use

Both services declare `specialUse`. Text for the declaration:

> The app shows live translated subtitles over the TV picture while the user plays a console game on
> an HDMI input. The overlay service must keep running while another input or app is on screen,
> because that is when the subtitles are needed; it draws a non-interactive overlay window and
> receives the subtitle text from the user's own PC on the local network. A second service waits up
> to 10 minutes for the user to pair the TV from the PC after leaving the app (the PC is often on
> another input of the same TV). Both start only after the user opens the app, and show a
> notification.

Attach a short video: open the app, pair from the PC, switch to the console's input, subtitles show.

### Display over other apps

The app asks for *Display over other apps* (`SYSTEM_ALERT_WINDOW`): showing subtitles over the game is
the app's core feature. Say so in the description (it does) and in the reviewer notes.

## Reviewer notes (App access → instructions)

> VI Translator for Android TV shows translated subtitles over the TV picture. The subtitles come
> from the free VI Translator app on a Windows, macOS or Linux PC on the same network
> (https://github.com/vi-translator/vi-translator-desktop/releases). To test:
> 1. Open the app on the TV: it shows a 6-digit code.
> 2. On the PC, start VI Translator, open *Source → Show on a TV → Connect a TV*, type the code, press *Connect*.
> 3. In the PC app pick any source (a capture card, or *Screen* to read the PC's own screen) with English
>    subtitles on screen; the translations appear over the TV picture.
> 4. If the TV asks, allow *Display over other apps*.
> A video of the whole flow: <add a link to an unlisted video>.
> No account or login is needed.
