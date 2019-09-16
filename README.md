README
======

# Escapepod - Podcast Player for Android
<img src="https://raw.githubusercontent.com/y20k/escapepod/master/app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.png" width="192" />

**Version 0.7.x**

Escapepod is a simple and lightweight app for listening to podcasts. It's bare bone approach may not be to everyone's taste: Escapepod is an app of type BYOP ("bring your own podcast"). It does not feature any kind of built-in search option. You will have to manually add podcasts.

Escapepod is free and open source. It is published under the [MIT open source license](https://opensource.org/licenses/MIT). Want to help? Please check out the notes in [CONTRIBUTING.md](https://github.com/y20k/escapepod/blob/master/CONTRIBUTE.md) first.

## Install Escapepod
You can install Escapepod via <!--F-Froid and -->Google Play - or you can go and grab the latest APK on [GitHub](https://github.com/y20k/escapepod/releases).

<!-- [<img src="https://cloud.githubusercontent.com/assets/9103935/14702535/45f6326a-07ab-11e6-9256-469c1dd51c22.png" width="192">](https://f-droid.org/packages/org.y20k.escapepod) -->

[<img src="https://play.google.com/intl/de_de/badges/images/generic/en_badge_web_generic.png" width="192">](https://play.google.com/store/apps/details?id=org.y20k.escapepod)


## Frequent Questions

### Where can I download Escapepod?
Escapepod will be released on F-Droid and Google Play. You can also get an APK installer on [GitHub](https://github.com/y20k/escapepod/releases).

### I can not add podcast X.
The podcast import feature needs some testing. It works with most of my favorite podcasts, but problems are to be expected. Please head over to the [Wiki](https://github.com/y20k/escapepod/wiki/Podcasts-feeds-that-are-not-working-yet) and add the podcast that failed to import.

### Where are the settings?
Escapepod has no settings screen. It tries to rely on sensible defaults.

### What are the default settings?
- Auto-update does not download files over cellular network
- Dark mode is handled by the Android system
- Escapepod by default only keeps two episodes

### Escapepod keeps more than two episodes.
Escapepod tries to reduce the number of episodes it keeps. Here are the rules for that:

- Escapepod keeps the latest two episodes
- Episodes, that have been started, or that have been downloaded manually, are kept, too
- the maximum number of episodes available (and kept) is five

### Why make simplicity a high priority?
Escapepod is a one-person fun project. Simplicity helps maintaining the app.

### Does Escapepod have playback queue?
Escapepod has a simple up-next feature. Tap play on an episode while listening to another one. You will be be given the opportunity add it to the up-next slot.

### I really miss feature X.
For the foreseeable future the development of Escapepod will focus on stability and user interface polish. Do not expect new features anytime soon.

### Does Escapepod support OMPL?
You can import an [OPML](https://en.wikipedia.org/wiki/OPML) formatted podcast list. Open the list in a file browser and choose Escapepod as a handler. Escapepod does not have an explicit export feature, but it keeps an up-to-date OPML file in the following folder on your device `/Android/data/org.y20k.escapepod/files/collection/`.

## Screenshots (v0.7)
[<img src="https://raw.githubusercontent.com/y20k/escapepod/master/metadata/en-US/phoneScreenshots/01-lockscreen-active-v0.7-oneplus5.png" width="240">](https://raw.githubusercontent.com/y20k/escapepod/master/metadata/en-US/phoneScreenshots/01-lockscreen-active-v0.7-oneplus5.png)
[<img src="https://raw.githubusercontent.com/y20k/escapepod/master/metadata/en-US/phoneScreenshots/02-playback-v0.7-oneplus5.png" width="240">](https://raw.githubusercontent.com/y20k/escapepod/master/metadata/en-US/phoneScreenshots/02-playback-v0.7-oneplus5.png)
[<img src="https://raw.githubusercontent.com/y20k/escapepod/master/metadata/en-US/phoneScreenshots/03-large-player-sheet-v0.7-oneplus5.png" width="240">](https://raw.githubusercontent.com/y20k/escapepod/master/metadata/en-US/phoneScreenshots/03-large-player-sheet-v0.7-oneplus5.png)

[<img src="https://raw.githubusercontent.com/y20k/escapepod/master/metadata/en-US/phoneScreenshots/04-show_notes-v0.7-oneplus5.png" width="240">](https://raw.githubusercontent.com/y20k/escapepod/master/metadata/en-US/phoneScreenshots/04-show_notes-v0.7-oneplus5.png)
[<img src="https://raw.githubusercontent.com/y20k/escapepod/master/metadata/en-US/phoneScreenshots/05-podcast-details-v0.7-oneplus5.png" width="240">](https://raw.githubusercontent.com/y20k/escapepod/master/metadata/en-US/phoneScreenshots/05-podcast-details-v0.7-oneplus5.png)
[<img src="https://raw.githubusercontent.com/y20k/escapepod/master/metadata/en-US/phoneScreenshots/06-add-podcast-v0.7-oneplus5.png" width="240">](https://raw.githubusercontent.com/y20k/escapepod/master/metadata/en-US/phoneScreenshots/06-add-podcast-v0.7-oneplus5.png)

