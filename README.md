# Epic Fight Skill Control

Configurable skill control for **Minecraft 1.21.1 / NeoForge 21.1.233+ / Epic Fight 21.17.3.1**. One JAR provides server-side restrictions and client-side hiding. Install it alongside Epic Fight **on the server and every client**. For single-player, install it on the client only.

## Configuration

The first launch creates `config/epicfight-skill-control.json`. Its `skills` object lists **every skill registered in the installed modpack**, including skills from other addons, weapon skills, and system skills. All skills are enabled by default.

Find the skill ID and change its `enabled` value:

```json
"epicfight:phantom_ascent": {
  "enabled": false,
  "name": "Phantom Ascent",
  "description": "Allows a player to jump %d times more in the air.",
  "category": "mover",
  "learnable": true,
  "translationKey": "skill.epicfight.phantom_ascent"
}
```

This is an entry inside `skills`, not a complete configuration file. After editing, **restart the server**, or restart the client for single-player. Clients receive the server's restrictions before entering the world, regardless of their local configuration.

- English names and descriptions come from `assets/<namespace>/lang/en_us.json` in installed mods. If a translation is missing, the name falls back to the skill ID and the description is empty. Client resource packs are not used.
- Placeholders such as `%d` and `%s` are part of the original translation templates, not calculated gameplay values. Description fields are reference text and do not change skill behavior.
- Newly registered skills are added on the next launch with `enabled: true`. Existing settings and extra fields are preserved.
- Entries for temporarily removed mods are retained. Unknown disabled skill IDs produce a log warning.
- If there are no new entries, the file is not rewritten. Invalid JSON, duplicate keys, or values such as `"false"` instead of `false` stop loading with an error that identifies the file. Invalid configuration is never replaced with defaults.
- Deleting the file generates a new configuration with everything enabled. Deleting an individual entry causes it to be added again as enabled.

## Disabled skill behavior

A disabled skill cannot be equipped or cast, including held skills. Its passive handlers are not registered. Restrictions also apply when loading player saves and assigning weapon skills.

Disabled skills are hidden from skill selection in Survival and Creative modes. Unequipped skills have no HUD entries. For standard Epic Fight weapon capabilities, tooltips also omit references to disabled special attacks. Skill books disappear from creative tabs and search results. Optional JEI integration hides individual book variants and related information recipes.

**Existing books for disabled skills are deleted** from player inventories and opened containers, and when book item entities enter the world. Disabled books are also removed from loot. Back up an existing world before enabling restrictions. Items in unopened storage or nested items are processed when their contents become accessible through a regular menu or the world.

Previously learned skills remain in player data but are hidden and unavailable while disabled. After re-enabling a skill, players can select it again; unequipped skills are not automatically re-equipped. Registry entries are retained to preserve references in weapons and saves.

## Limitations

- Disabling `epicfight:combo_attacks` affects regular attacks, not just special attacks. Test the combat system before disabling system skills.
- The addon does not change regular combo animations, physics parameters, or mob behavior that does not use the player skill system.
- Custom interfaces, tooltips, and direct API bypasses in third-party addons require separate compatibility checks. Third-party JEI recipe categories other than information recipes are not automatically filtered. EMI is not integrated.
- Epic Fight support is intentionally restricted to `21.17.3.1` because Mixin hooks depend on its API. The original Epic Fight JAR is neither modified nor bundled.

## Build

Use JDK 21. Place the original `epic-fight-21.17.3.1-mc1.21.1-neoforge.jar` in the parent directory of this project.

```powershell
$env:JAVA_HOME = 'C:\path\to\jdk-21'
.\gradlew.bat build
```

Output: `build/libs/epicfight-skill-control-1.0.0.jar`. Automated tests cover configuration generation and preservation.

## Tests

```powershell
.\gradlew.bat test
.\gradlew.bat runGameTestServer
```

Unit tests cover catalog generation and updates, setting preservation, escaped text and exact numeric values, and rejection of invalid configuration without overwriting the file.

GameTest covers skill installation and casting restrictions, save loading, passive skills, weapon special attacks, books, containers, and loot.

The test mod in `src/gameTest` is used only in development runs and is not included in the release JAR.
