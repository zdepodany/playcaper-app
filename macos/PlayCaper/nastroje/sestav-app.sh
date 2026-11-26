#!/bin/zsh
# Sestaví macos/PlayCaper/dist/PlayCaper.app a podepíše ho certifikátem Apple Development
# (stabilní podpis = macOS si pamatuje oprávnění k nahrávání obrazovky i po novém buildu).
set -euo pipefail
cd "${0:A:h}/.."

VERZE=0.1.0
BUILD=1

swift build -c release
APP=dist/PlayCaper.app
rm -rf "$APP"
mkdir -p "$APP/Contents/MacOS" "$APP/Contents/Resources"
cp .build/release/PlayCaper "$APP/Contents/MacOS/PlayCaper"

cat > "$APP/Contents/Info.plist" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>CFBundleIdentifier</key><string>cz.playcaper.mac</string>
    <key>CFBundleName</key><string>PlayCaper</string>
    <key>CFBundleDisplayName</key><string>PlayCaper</string>
    <key>CFBundleExecutable</key><string>PlayCaper</string>
    <key>CFBundlePackageType</key><string>APPL</string>
    <key>CFBundleShortVersionString</key><string>$VERZE</string>
    <key>CFBundleVersion</key><string>$BUILD</string>
    <key>LSMinimumSystemVersion</key><string>15.0</string>
    <key>NSLocalNetworkUsageDescription</key>
    <string>PlayCaper hledá přijímač titulků na televizi a posílá mu přeložené titulky.</string>
</dict>
</plist>
EOF

# Otisk (SHA-1) certifikátu: jméno s diakritikou security vypisuje zkomoleně.
IDENTITA=$(security find-identity -v -p codesigning | awk '/Apple Development:/ { print $2; exit }')
if [[ -n "$IDENTITA" ]]; then
    codesign --force --sign "$IDENTITA" "$APP"
    echo "Podepsáno: $IDENTITA"
else
    codesign --force --sign - "$APP"
    echo "Pozor: ad-hoc podpis, macOS se bude po každém buildu znovu ptát na oprávnění."
fi
echo "Hotovo: $(pwd)/$APP"
