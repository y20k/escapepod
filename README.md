README
======

# Escapepods - Podcast Player for Android
<img src="https://raw.githubusercontent.com/y20k/escapepods/master/app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.png" width="192" />

**Version 0.4.x (Pre-Release)**

Escapepods is a bare bones app for listening to podcasts. The Android platform has several high quality podcast players. Escapepods probably cannot compete with those apps. Escapepods is a one-person fun project. But still it may set itself somewhat apart, because Escapepods is very simple and lightweight. The app consists of only a single screen. Also it is free and open source.

Important note: This is an app of type BYOP ("bring your own podcast"). It does not feature any kind of built-in search option. You will have to manually add podcasts.

Escapepods is published under the [MIT open source license](https://opensource.org/licenses/MIT). Want to help? Please check out the notes in [CONTRIBUTING.md](https://github.com/y20k/escapepods/blob/master/CONTRIBUTE.md) first.


## Frequent Questions

### Where can I download Escapepods?
As long as Escapepods is still in pre-release state you can get it only here on GitHub (head over to [releases](https://github.com/y20k/escapepods/releases)). A future release on F-Droid is planned.

### Where are the settings?
Escapepods has no settings. The app is designed to be very simple. It tries to rely on sensible defaults.

### Okay. And what are the default settings?
- Auto-update does not download files over cellular network
- Dark mode is handled by the Android system
- Escapepods by default only keeps the recent two episodes of an podcast

### The behavior of the app in regard to which episode it keeps is confusing.
Okay. That may seem so. Escapepods has a policy that tries to reduce the number of episodes it keeps to a minimum. Here are the rules for that:
- Escapepods keeps the latest two episodes
- Escapepods also keeps episodes that have been started, or that have been downloaded manually
- The maximum number of episodes available (end kept) is five.

### Why on earth make simplicity such a high priority?
Escapepods is a one-person fun project. Simplicity helps maintaining the app. And as it happens I personally like minimalistic apps, too.

### I really miss a playback queue.
Escapepods has a simple up-next queue. You can determine the episode you would like to listen to next. Just press play on that episode while listening to the current one.

### I really miss feature X.
For the foreseeable future the development of Escapepods will focus on stability and user interface polish. Do not expect new features anytime soon.

### Does Escapepods support OMPL?
You can import an OPML formatted podcast list by choosing Escapepods when trying opening the list. Instead of an explicit export feature Escapepods always keeps an up-to-date OPML file in the following folder on your device `/Android/data/org.y20k.escapepods/files/collection/`.

## User Interface (May 2019)
<img src="https://raw.githubusercontent.com/y20k/escapepods/master/assets/ui-screenshot-001-2019-05.png" width="280" /><img src="https://raw.githubusercontent.com/y20k/escapepods/master/assets/ui-screenshot-002-2019-05.png" width="280" />
