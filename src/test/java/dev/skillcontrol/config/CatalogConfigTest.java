package dev.skillcontrol.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CatalogConfigTest {
    private static final CatalogConfig.SkillInfo ALPHA = new CatalogConfig.SkillInfo(
            "epicfight:alpha", "Alpha", "Café: block <target> for %1$s seconds.", "guard", true,
            "skill.epicfight.alpha");
    private static final CatalogConfig.SkillInfo ZETA = new CatalogConfig.SkillInfo(
            "other:zeta", "Zeta", "", "passive", false, "skill.other.zeta");

    @TempDir
    Path directory;

    @Test
    void generatesSortedPrettyUtf8CatalogWithAllSkillsEnabled() throws IOException {
        Path path = directory.resolve("config/skills.json");

        Set<String> disabled = CatalogConfig.load(path, List.of(ZETA, ALPHA));

        assertTrue(disabled.isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> disabled.add(ALPHA.id()));
        String text = Files.readString(path, StandardCharsets.UTF_8);
        JsonObject root = JsonParser.parseString(text).getAsJsonObject();
        assertEquals(1, root.get("schemaVersion").getAsInt());
        assertTrue(root.get("instructions").getAsString().contains("enabled to false"));
        assertTrue(text.contains("\n  \"schemaVersion\": 1,"));
        assertTrue(text.indexOf('"' + ALPHA.id() + '"') < text.indexOf('"' + ZETA.id() + '"'));
        assertTrue(text.contains("Café: block <target> for %1$s seconds."));
        JsonObject skills = root.getAsJsonObject("skills");
        assertEquals(List.of(ALPHA.id(), ZETA.id()), List.copyOf(skills.keySet()));
        for (CatalogConfig.SkillInfo skill : List.of(ALPHA, ZETA)) {
            JsonObject entry = skills.getAsJsonObject(skill.id());
            assertTrue(entry.get("enabled").getAsBoolean());
            assertEquals(skill.name(), entry.get("name").getAsString());
            assertEquals(skill.description(), entry.get("description").getAsString());
            assertEquals(skill.category(), entry.get("category").getAsString());
            assertEquals(skill.learnable(), entry.get("learnable").getAsBoolean());
            assertEquals(skill.translationKey(), entry.get("translationKey").getAsString());
        }
        assertOnlyConfigFile(path);
    }

    @Test
    void generatesValidEmptyCatalog() throws IOException {
        Path path = directory.resolve("empty.json");

        assertEquals(Set.of(), CatalogConfig.load(path, List.of()));
        assertTrue(readRoot(path).getAsJsonObject("skills").isEmpty());
        assertEquals(Set.of(), CatalogConfig.load(path, List.of()));
    }

    @Test
    void preservesDesignerDataAndUnknownEntriesWhenAddingSortedSkills() throws IOException {
        Path path = directory.resolve("skills.json");
        Files.writeString(path, """
                {
                  "schemaVersion": 1,
                  "instructions": "Designer instructions",
                  "designerRoot": {"notes": ["keep", 7, null], "empty": null},
                  "nullableRoot": null,
                  "skills": {
                    "epicfight:alpha": {
                      "enabled": false,
                      "name": "Designer name",
                      "description": "Designer description %s",
                      "category": "custom",
                      "learnable": false,
                      "translationKey": "custom.key",
                      "notes": {"owner": "designer", "empty": null}
                    },
                    "removed:old_skill": {"enabled": false, "custom": "retain"},
                    "removed:enabled_skill": {"enabled": true}
                  }
                }
                """, StandardCharsets.UTF_8);
        JsonObject before = readRoot(path);
        CatalogConfig.SkillInfo beta = new CatalogConfig.SkillInfo(
                "epicfight:beta", "Beta", "", "dodge", true, "skill.epicfight.beta");

        Set<String> disabled = CatalogConfig.load(path, List.of(ZETA, beta, ALPHA));

        assertEquals(Set.of(ALPHA.id(), "removed:old_skill"), disabled);
        assertThrows(UnsupportedOperationException.class, () -> disabled.remove(ALPHA.id()));
        JsonObject after = readRoot(path);
        assertEquals(before.get("instructions"), after.get("instructions"));
        assertEquals(before.get("designerRoot"), after.get("designerRoot"));
        assertTrue(after.has("nullableRoot"));
        assertTrue(after.get("nullableRoot").isJsonNull());
        JsonObject beforeSkills = before.getAsJsonObject("skills");
        JsonObject afterSkills = after.getAsJsonObject("skills");
        for (String id : beforeSkills.keySet()) {
            assertEquals(beforeSkills.get(id), afterSkills.get(id));
        }
        assertEquals(List.of(ALPHA.id(), "removed:old_skill", "removed:enabled_skill", beta.id(), ZETA.id()),
                List.copyOf(afterSkills.keySet()));
        assertTrue(afterSkills.getAsJsonObject(beta.id()).get("enabled").getAsBoolean());
        assertTrue(afterSkills.getAsJsonObject(ZETA.id()).get("enabled").getAsBoolean());
        assertOnlyConfigFile(path);
    }

    @Test
    void returnsExplicitFalseIncludingUnknownIdsWithoutRewriting() throws IOException {
        Path path = directory.resolve("skills.json");
        byte[] original = ("{\r\n\t\"schemaVersion\":1,\"custom\":null,\"skills\":{"
                + "\"epicfight:alpha\":{\"enabled\":false},"
                + "\"removed:disabled\":{\"enabled\":false},"
                + "\"removed:enabled\":{\"enabled\":true}}}\r\n").getBytes(StandardCharsets.UTF_8);
        Files.write(path, original);
        Files.setLastModifiedTime(path, FileTime.fromMillis(946684800000L));
        FileTime timestamp = Files.getLastModifiedTime(path);

        assertEquals(Set.of(ALPHA.id(), "removed:disabled"), CatalogConfig.load(path, List.of(ALPHA)));
        assertEquals(Set.of(ALPHA.id(), "removed:disabled"), CatalogConfig.load(path, List.of()));

        assertArrayEquals(original, Files.readAllBytes(path));
        assertEquals(timestamp, Files.getLastModifiedTime(path));
        assertOnlyConfigFile(path);
    }

    @Test
    void rejectsEveryNonBooleanAndMissingEnabledWithoutClobbering() throws IOException {
        for (String value : List.of("\"false\"", "\"true\"", "0", "1", "null", "[]", "{}")) {
            assertInvalidUnchanged("{\"schemaVersion\":1,\"skills\":{\"epicfight:alpha\":{\"enabled\":"
                    + value + "}}}", ALPHA.id());
        }
        assertInvalidUnchanged("{\"schemaVersion\":1,\"skills\":{\"epicfight:alpha\":{\"name\":\"keep\"}}}",
                ALPHA.id());
        assertInvalidUnchanged("{\"schemaVersion\":1,\"skills\":{\"removed:skill\":{\"enabled\":\"false\"}}}",
                "removed:skill");
    }

    @Test
    void rejectsInvalidRootsVersionsAndSkillObjectsWithoutClobbering() throws IOException {
        for (String malformed : List.of(
                "", "null", "[]", "true", "1", "\"text\"", "{", "{\"schemaVersion\":1,\"skills\":{}} garbage",
                "{\"schemaVersion\":1,\"skills\":{},}", "{/* comment */\"schemaVersion\":1,\"skills\":{}}",
                "{schemaVersion:1,skills:{}}", "{\"schemaVersion\":1,\"skills\":{},\"text\":\"raw\nline\"}")) {
            assertInvalidUnchanged(malformed, null);
        }
        for (String version : List.of("\"1\"", "true", "null", "0", "2", "1.0", "1e0", "[]", "{}")) {
            assertInvalidUnchanged("{\"schemaVersion\":" + version + ",\"skills\":{}}", "schemaVersion");
        }
        assertInvalidUnchanged("{\"skills\":{}}", "schemaVersion");
        assertInvalidUnchanged("{\"schemaVersion\":1}", "skills");
        for (String skills : List.of("null", "[]", "\"skills\"", "false", "1")) {
            assertInvalidUnchanged("{\"schemaVersion\":1,\"skills\":" + skills + "}", "skills");
        }
        for (String skill : List.of("null", "[]", "true", "1", "\"skill\"")) {
            assertInvalidUnchanged("{\"schemaVersion\":1,\"skills\":{\"epicfight:alpha\":" + skill + "}}", ALPHA.id());
        }
        assertInvalidUnchanged("{\"schemaVersion\":1,\"skills\":{\"alpha\":{\"enabled\":false}}}", "alpha");
    }

    @Test
    void rejectsDuplicateKeysAtEveryLevelWithoutClobbering() throws IOException {
        assertInvalidUnchanged("{\"schemaVersion\":1,\"schemaVersion\":1,\"skills\":{}}", "schemaVersion");
        assertInvalidUnchanged("{\"schemaVersion\":1,\"skills\":{},\"skills\":{}}", "skills");
        assertInvalidUnchanged("""
                {"schemaVersion":1,"skills":{
                  "epicfight:alpha":{"enabled":false},
                  "epicfight:alpha":{"enabled":true}
                }}
                """, ALPHA.id());
        assertInvalidUnchanged("""
                {"schemaVersion":1,"skills":{
                  "epicfight:alpha":{"enabled":false,"enabled":true}
                }}
                """, ALPHA.id());
        assertInvalidUnchanged("""
                {"schemaVersion":1,"skills":{},"notes":[{"value":1,"value":2}]}
                """, "value");
        assertInvalidUnchanged("""
                {"schemaVersion":1,"skills":{
                  "epicfight:alpha":{"enabled":false},
                  "epicfight:\\u0061lpha":{"enabled":true}
                }}
                """, ALPHA.id());
    }

    @Test
    void rejectsInvalidUtf8WithoutClobbering() throws IOException {
        Path path = directory.resolve("skills.json");
        byte[] original = {(byte) 0xc3, (byte) 0x28};
        Files.write(path, original);

        IOException failure = assertThrows(IOException.class, () -> CatalogConfig.load(path, List.of(ALPHA)));

        assertTrue(failure.getMessage().contains(path.toString()));
        assertArrayEquals(original, Files.readAllBytes(path));
        assertOnlyConfigFile(path);
    }

    @Test
    void rejectsDuplicateAndInvalidCatalogIdsBeforeCreatingAFile() throws IOException {
        Path path = directory.resolve("skills.json");
        IOException duplicate = assertThrows(IOException.class,
                () -> CatalogConfig.load(path, List.of(ALPHA, ALPHA)));
        assertTrue(duplicate.getMessage().contains(ALPHA.id()));
        assertFalse(Files.exists(path));
        CatalogConfig.SkillInfo invalid = new CatalogConfig.SkillInfo("alpha", "Name", "", "guard", true, "key");
        IOException invalidId = assertThrows(IOException.class,
                () -> CatalogConfig.load(path, List.of(invalid)));
        assertTrue(invalidId.getMessage().contains("alpha"));
        assertFalse(Files.exists(path));
    }

    private void assertInvalidUnchanged(String content, String expectedLocation) throws IOException {
        Path path = directory.resolve("skills.json");
        byte[] original = content.getBytes(StandardCharsets.UTF_8);
        Files.write(path, original);
        IOException failure = assertThrows(IOException.class, () -> CatalogConfig.load(path, List.of(ALPHA, ZETA)));
        assertTrue(failure.getMessage().contains(path.toString()), failure.getMessage());
        if (expectedLocation != null) {
            assertTrue(failure.getMessage().contains(expectedLocation), failure.getMessage());
        }
        assertArrayEquals(original, Files.readAllBytes(path));
        assertOnlyConfigFile(path);
    }

    private static JsonObject readRoot(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static void assertOnlyConfigFile(Path path) throws IOException {
        try (var files = Files.list(path.getParent())) {
            assertEquals(List.of(path), files.toList());
        }
    }
}
