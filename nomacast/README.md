# Lawnchair N (Nomacast)

Personal build of Lawnchair, based on the upstream `16-dev` branch.

## What's different from upstream

- **Separate app**: package `app.lawnchair.nomacast`, named "Lawnchair N", so it installs
  next to the stock Lawnchair.
- **Folders open centred on screen** with a **frosted (blurred + dimmed) backdrop**, on the
  home screen and in the app drawer (`FolderBackdrop.kt`, `Folder.centerAboutIcon`).
- **Cleaner drawer defaults**: no suggested-apps row, no top shadow, no scrollbar, lighter
  folder preview background, slightly taller rows, slightly smaller labels, thinner search bar.

## Builds

Every push to `16-dev` triggers `.github/workflows/nomacast.yml`, which builds the release APK
and publishes it under **Releases** (`LawnchairN.apk`).

Signing uses `nomacast/nomacast.jks` (committed on purpose, so every build shares one
signature and updates install in place).
