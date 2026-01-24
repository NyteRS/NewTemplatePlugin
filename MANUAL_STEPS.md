# Manual Steps Required for PartyPro Integration

## Overview
The PartyPro Java source code has been fully integrated, but UI assets and language files require manual copying from the source repository.

## Required Manual Steps

### Step 1: Copy UI Asset Files
Copy the following UI files from `NyteRS/partypluginunpacked` to your plugin resources:

```bash
# From: NyteRS/partypluginunpacked
# To: Your plugin resources directory

Source: Common/UI/Custom/Hud/PartyPro/PartyHud.ui
Destination: <plugin-resources>/Common/UI/Custom/Hud/PartyPro/PartyHud.ui

Source: Common/UI/Custom/Hud/PartyPro/PartyHudCompact.ui
Destination: <plugin-resources>/Common/UI/Custom/Hud/PartyPro/PartyHudCompact.ui

Source: Common/UI/Custom/Pages/PartyPro/*.ui (10 files)
Destination: <plugin-resources>/Common/UI/Custom/Pages/PartyPro/
```

**Files to copy from Common/UI/Custom/Pages/PartyPro/:**
- PlayerListEntry.ui
- InviteEntry.ui
- ChatMessage.ui
- MemberEntry.ui
- LeaderboardEntry.ui
- PartyInvite.ui
- PartyBrowser.ui
- PartyListEntry.ui

### Step 2: Copy Language Files
Copy language files for localization support:

```bash
# JSON language files (client-side)
Source: language/en.json
Source: language/de.json
Source: language/hu.json
Source: language/br.json
Source: language/es.json
Destination: <plugin-resources>/language/

# Server language file
Source: Server/Languages/en-US/commands.lang
Destination: <plugin-resources>/Server/Languages/en-US/commands.lang
```

### Step 3: Verify Directory Structure
After copying, your plugin resources should have this structure:

```
<plugin-resources>/
├── Common/
│   └── UI/
│       └── Custom/
│           ├── Hud/
│           │   └── PartyPro/
│           │       ├── PartyHud.ui
│           │       └── PartyHudCompact.ui
│           └── Pages/
│               └── PartyPro/
│                   ├── PartyBrowser.ui
│                   ├── PartyInvite.ui
│                   ├── PartyListEntry.ui
│                   ├── PlayerListEntry.ui
│                   ├── InviteEntry.ui
│                   ├── ChatMessage.ui
│                   ├── MemberEntry.ui
│                   └── LeaderboardEntry.ui
├── Server/
│   └── Languages/
│       └── en-US/
│           └── commands.lang
└── language/
    ├── en.json
    ├── de.json
    ├── hu.json
    ├── br.json
    └── es.json
```

### Step 4: Configure Build Dependencies
Ensure your build.gradle or build.gradle.kts has access to:
- Hytale modding plugin
- Hytale Server API libraries
- Maven repository: https://maven.hytale-modding.info/releases

### Step 5: Build and Test
Once resources are in place:
1. Run `./gradlew build` (or your build command)
2. Deploy the plugin to your Hytale server
3. Start the server
4. Verify configuration files are created in `<server-plugins>/PartyPro/`
5. Test party commands: `/party create`, `/party invite`, etc.
6. Verify HUD displays for party members
7. Test ping system: `/ping`

## Quick Copy Script (Example)
If you have both repositories cloned locally:

```bash
#!/bin/bash
# Example script to copy resources
SOURCE_REPO="/path/to/partypluginunpacked"
TARGET_REPO="/path/to/NewTemplatePlugin"
RESOURCE_DIR="$TARGET_REPO/src/main/resources"  # Adjust as needed

# Create directories
mkdir -p "$RESOURCE_DIR/Common/UI/Custom/Hud/PartyPro"
mkdir -p "$RESOURCE_DIR/Common/UI/Custom/Pages/PartyPro"
mkdir -p "$RESOURCE_DIR/Server/Languages/en-US"
mkdir -p "$RESOURCE_DIR/language"

# Copy HUD UI files
cp "$SOURCE_REPO/Common/UI/Custom/Hud/PartyPro/"*.ui "$RESOURCE_DIR/Common/UI/Custom/Hud/PartyPro/"

# Copy Page UI files
cp "$SOURCE_REPO/Common/UI/Custom/Pages/PartyPro/"*.ui "$RESOURCE_DIR/Common/UI/Custom/Pages/PartyPro/"

# Copy language files
cp "$SOURCE_REPO/language/"*.json "$RESOURCE_DIR/language/"
cp "$SOURCE_REPO/Server/Languages/en-US/commands.lang" "$RESOURCE_DIR/Server/Languages/en-US/"

echo "Resource files copied successfully!"
```

## Troubleshooting

### Issue: UI elements not displaying
**Solution:** Verify all .ui files are in the correct location relative to the plugin resources directory.

### Issue: Missing translations
**Solution:** Ensure language JSON files are copied and the LanguageManager can access them at runtime.

### Issue: Build fails
**Solution:** Check that Hytale API dependencies are properly configured in your build system.

### Issue: Config files not created
**Solution:** Verify the plugin has write permissions to the plugin directory.

## Notes
- The Java code is fully integrated and functional
- Only resource files (UI, language) require manual copying
- No META-INF or manifest.json should be copied
- Original code behavior is preserved exactly
