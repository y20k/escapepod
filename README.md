README
======

# Escapepods - Podcast Player for Android
<img src="https://raw.githubusercontent.com/y20k/escapepods/master/app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.png" width="192" />

**Version 0.5.x (Beta)**

Escapepods is a simple and lightweight app for listening to podcasts. It's bare bone approach may not be to everyone's taste: Escapepods is an app of type BYOP ("bring your own podcast"). It does not feature any kind of built-in search option. You will have to manually add podcasts.

Escapepods is free and open source. It is published under the [MIT open source license](https://opensource.org/licenses/MIT). Want to help? Please check out the notes in [CONTRIBUTING.md](https://github.com/y20k/escapepods/blob/master/CONTRIBUTE.md) first.


## Frequent Questions

### Where can I download Escapepods?
As long as Escapepods is in beta you can only get it on [GitHub](https://github.com/y20k/escapepods/releases). A future release on F-Droid is planned.

### I can not add podcast X.
The podcast import feature needs some testing. It works with most of my favorite podcasts, but problems are to be expected. Please head over to the [wiki](https://github.com/y20k/escapepods/wiki/Podcasts-feeds-that-are-not-working-yet) and add the podcast that failed to import.

### Where are the settings?
Escapepods has no settings screen. It tries to rely on sensible defaults.

### What are the default settings?
- Auto-update does not download files over cellular network
- Dark mode is handled by the Android system
- Escapepods by default only keeps two episodes

### Escapepods keeps more than two episodes.
Escapepods tries to reduce the number of episodes it keeps. Here are the rules for that:

- Escapepods keeps the latest two episodes
- Episodes, that have been started, or that have been downloaded manually, are kept, too
- the maximum number of episodes available (and kept) is five

### Why make simplicity a high priority?
Escapepods is a one-person fun project. Simplicity helps maintaining the app.

### Does Escapepod have playback queue?
Escapepods has a simple up-next feature. Tap play on an episode while listening to another one. You will be be given the opportunity add it to the up-next slot.

### I really miss feature X.
For the foreseeable future the development of Escapepods will focus on stability and user interface polish. Do not expect new features anytime soon. The only feature planned, but not yet implemented is a show notes viewer.

### Does Escapepods support OMPL?
You can import an [OPML](https://en.wikipedia.org/wiki/OPML) formatted podcast list. Open the list in a file browser and choose Escapepods as a handler. Escapepods does not have an explicit export feature, but it keeps an up-to-date OPML file in the following folder on your device `/Android/data/org.y20k.escapepods/files/collection/`.

## Screenshots (v0.5)
[<img src="https://raw.githubusercontent.com/y20k/escapepods/master/metadata/en-US/phoneScreenshots/01-lockscreen-active-v0.5-oneplus5.png" width="240">](https://raw.githubusercontent.com/y20k/escapepods/master/metadata/en-US/phoneScreenshots/01-lockscreen-active-v0.5-oneplus5.png)
[<img src="https://raw.githubusercontent.com/y20k/escapepods/master/metadata/en-US/phoneScreenshots/02-playback-v0.5-oneplus5.png" width="240">](https://raw.githubusercontent.com/y20k/escapepods/master/metadata/en-US/phoneScreenshots/02-playback-v0.5-oneplus5.png)
[<img src="https://raw.githubusercontent.com/y20k/escapepods/master/metadata/en-US/phoneScreenshots/03-large-player-sheet-v0.5-oneplus5.png" width="240">](https://raw.githubusercontent.com/y20k/escapepods/master/metadata/en-US/phoneScreenshots/03-large-player-sheet-v0.5-oneplus5.png)

[<img src="https://raw.githubusercontent.com/y20k/escapepods/master/metadata/en-US/phoneScreenshots/04-add-podcast-v0.5-oneplus5.png" width="240">](https://raw.githubusercontent.com/y20k/escapepods/master/metadata/en-US/phoneScreenshots/04-add-podcast-v0.5-oneplus5.png)
[<img src="https://raw.githubusercontent.com/y20k/escapepods/master/metadata/en-US/phoneScreenshots/05-podcast-details-v0.5-oneplus5.png" width="240">](https://raw.githubusercontent.com/y20k/escapepods/master/metadata/en-US/phoneScreenshots/05-podcast-details-v0.5-oneplus5.png)
[<img src="https://raw.githubusercontent.com/y20k/escapepods/master/metadata/en-US/phoneScreenshots/06-notification-v0.5-oneplus5.png" width="240">](https://raw.githubusercontent.com/y20k/escapepods/master/metadata/en-US/phoneScreenshots/06-notification-v0.5-oneplus5.png)

