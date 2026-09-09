# Literal Film

Shoot, share, done. No sliders in the way until you want them.

A minimalist analog-film look photo editor for Android, built entirely around the share sheet.

<p>
  <a href="https://github.com/256x/film/releases/latest"><img src="https://img.shields.io/github/v/release/256x/film?label=GitHub%20Release"></a>&nbsp;<img src="https://img.shields.io/badge/Android-8%2B-blue">&nbsp;<img src="https://img.shields.io/badge/license-MIT-lightgrey">
</p>

[User Guide](./docs/USER_GUIDE.md)

## Features

- Share a photo into the app from anywhere (Gallery, camera roll, any app) — it opens straight into the editor, no import step
- Tap a preset thumbnail (rendered live against your actual photo) for an instant film-emulation look
- Long-press the preview to compare against the original
- Build your own looks in the manual editor — brightness, contrast, shadows, highlights, structure, sharpness, grain, vignette, temperature, monochrome — then save them as new named presets
- Save to the gallery or export straight to the share sheet (e.g. KDE Connect to a PC), without cluttering the device with extra files
- Everything — including your saved presets — stays on-device; no accounts, no network calls

## Notes

**Presets, not sliders, by default.** The main screen only ever shows a wrench icon and a row of your saved preset thumbnails — the full parameter editor is one tap away, but never in the way of "share → pick a look → done."

## Development

- Kotlin / Jetpack Compose / OpenGL ES (a single generic GLSL fragment shader drives every preset — no per-look shader) / DataStore + kotlinx.serialization for saved presets
- Target: Android 8.0+

This app was built with substantial assistance from [Claude](https://claude.ai) (Anthropic).

## License

MIT
