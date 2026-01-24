# Hytale Plugin Template

A template for Hytale java plugins. Created by Up, and slightly modified by Kaupenjoe. 

## PartyPro Plugin Integration

This repository now includes the fully integrated PartyPro plugin code, ported from NyteRS/partypluginunpacked.

### Quick Links
- **[PartyPro Integration Guide](PARTYPRO_INTEGRATION.md)** - Comprehensive documentation of the integration
- **[Manual Setup Steps](MANUAL_STEPS.md)** - Step-by-step guide for copying required UI and language resources

### What's Included
- 61 Java source files ported to `com.example.exampleplugin.party.*`
- Full party management system (create, invite, join, leave, kick, disband, transfer)
- Party HUD with standard and compact modes
- Location ping system with compass markers
- Party chat system
- Party statistics and leaderboard
- Event systems for PvP, blocks, damage, and crafting
- Integration with ExamplePlugin.java

### Next Steps
1. Copy UI asset files (.ui files) - see MANUAL_STEPS.md
2. Copy language files (.json and .lang) - see MANUAL_STEPS.md
3. Configure Hytale API build dependencies
4. Build and deploy to test

For complete details, see [PARTYPRO_INTEGRATION.md](PARTYPRO_INTEGRATION.md).

