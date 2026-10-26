# Nexro Remote AI website

This is a static GitHub Pages site. It does not need a separate website ZIP or a hosted Node server. The page pairs directly to Nexro TV Launcher's local bridge using its IP address and PIN.

## Publish
In repository Settings → Pages, select **GitHub Actions** as the build/deployment source. The workflow deploys this directory after website changes land on `main`.

## Browser limitation
The website is HTTPS on GitHub Pages while the TV bridge currently speaks HTTP on the local network. Browsers may block such mixed-content/local-network requests. This is a browser security policy, not a pairing PIN issue. A production deployment may need a trusted HTTPS local bridge or a secure relay.