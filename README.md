# Nexro TV

Separate deliverables in this repository:

- `website/`: static Nexro Remote AI website, deployed using GitHub Pages.
- `android/`: Android TV launcher source. GitHub Actions builds a debug APK artifact.

The launcher shows only apps actually installed on the TV; it does not fabricate movie, TV-show, library or recommendation rows. Real control depends on Android TV capabilities and permissions. Standard Android apps cannot universally power off a TV.

## Website
Enable GitHub Pages with the GitHub Actions source in Settings → Pages. The `website/` folder is deployed by `.github/workflows/pages.yml`.

## Android APK
Open Actions → Build Nexro TV Launcher APK → Run workflow. Download the APK artifact from the completed run. This is a debug APK, not a release-signed Play Store package.

## Pairing
The website and launcher use a PIN-paired local bridge on port 8765. Both devices should be on the same Wi-Fi. HTTPS GitHub Pages to an HTTP LAN bridge can be blocked by browser security; use a browser that permits local-network access or a secure local bridge.