package dev.skillcontrol.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;

public final class CatalogConfig {
    private static final Pattern SKILL_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9/._-]+");
    private static final String INSTRUCTIONS = "Set enabled to false to disable a skill. "
            + "Set enabled to true to enable a skill. "
            + "Use the full namespaced skill ID as the key. "
            + "Names and descriptions are reference text only. "
            + "Keep format placeholders in reference text. "
            + "New skills are added with enabled set to true. "
            + "Restart the server after each change.";

    private CatalogConfig() {
    }

    public record SkillInfo(String id, String name, String description, String category,
                            boolean learnable, String translationKey) {
    }

    public static Set<String> load(Path path, Collection<SkillInfo> catalog) throws IOException {
        Path target = path.toAbsolutePath().normalize();
        Map<String, SkillInfo> sortedCatalog = validateAndSortCatalog(target, catalog);
        JsonObject root = readExistingConfig(target);
        boolean created = root == null;
        if (created) {
            root = createDefaultConfig();
        }

        JsonObject skills = validateRoot(target, root);
        Set<String> disabled = readDisabledSkills(target, skills);
        boolean updated = appendMissingSkills(skills, sortedCatalog.values());
        if (created || updated) {
            replaceAtomically(target, root);
        }
        return Collections.unmodifiableSet(disabled);
    }

    private static JsonObject readExistingConfig(Path path) throws IOException {
        String text;
        try {
            text = Files.readString(path, StandardCharsets.UTF_8);
        } catch (NoSuchFileException missing) {
            return null;
        } catch (IOException failure) {
            throw new IOException("Cannot read skill configuration '" + path + "': " + failure.getMessage(), failure);
        }
        return ConfigJson.parse(path, text);
    }

    private static JsonObject createDefaultConfig() {
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 1);
        root.addProperty("instructions", INSTRUCTIONS);
        root.add("skills", new JsonObject());
        return root;
    }

    private static Set<String> readDisabledSkills(Path path, JsonObject skills) throws IOException {
        Set<String> disabled = new TreeSet<>();
        for (Map.Entry<String, JsonElement> entry : skills.entrySet()) {
            String id = entry.getKey();
            String location = "$.skills[" + id + "]";
            validateId(path, id, location);
            if (!entry.getValue().isJsonObject()) {
                throw invalid(path, location, "expected a skill object");
            }
            JsonElement enabled = entry.getValue().getAsJsonObject().get("enabled");
            if (enabled == null || !enabled.isJsonPrimitive() || !enabled.getAsJsonPrimitive().isBoolean()) {
                throw invalid(path, location + ".enabled", "expected an explicit boolean (true or false)");
            }
            if (!enabled.getAsBoolean()) {
                disabled.add(id);
            }
        }
        return disabled;
    }

    private static boolean appendMissingSkills(JsonObject skills, Collection<SkillInfo> catalog) {
        boolean updated = false;
        for (SkillInfo skill : catalog) {
            if (!skills.has(skill.id())) {
                skills.add(skill.id(), createSkillEntry(skill));
                updated = true;
            }
        }
        return updated;
    }

    private static Map<String, SkillInfo> validateAndSortCatalog(Path path, Collection<SkillInfo> catalog) throws IOException {
        if (catalog == null) {
            throw invalid(path, "catalog", "catalog must not be null");
        }
        Map<String, SkillInfo> sorted = new TreeMap<>();
        for (SkillInfo skill : catalog) {
            if (skill == null) {
                throw invalid(path, "catalog", "skill must not be null");
            }
            String location = "catalog[" + skill.id() + "]";
            validateId(path, skill.id(), location);
            if (skill.name() == null || skill.description() == null || skill.category() == null
                    || skill.translationKey() == null) {
                throw invalid(path, location, "metadata strings must not be null; use an empty string for missing text");
            }
            if (sorted.putIfAbsent(skill.id(), skill) != null) {
                throw invalid(path, location, "duplicate catalog skill ID");
            }
        }
        return sorted;
    }

    private static void validateId(Path path, String id, String location) throws IOException {
        if (id == null || !SKILL_ID.matcher(id).matches()) {
            throw invalid(path, location, "expected a full namespaced skill ID");
        }
    }

    private static JsonObject validateRoot(Path path, JsonObject root) throws IOException {
        JsonElement version = root.get("schemaVersion");
        if (version == null || !version.isJsonPrimitive() || !version.getAsJsonPrimitive().isNumber()
                || !"1".equals(version.getAsString())) {
            throw invalid(path, "$.schemaVersion", "expected integer 1");
        }
        JsonElement skills = root.get("skills");
        if (skills == null || !skills.isJsonObject()) {
            throw invalid(path, "$.skills", "expected an object");
        }
        return skills.getAsJsonObject();
    }

    private static JsonObject createSkillEntry(SkillInfo skill) {
        JsonObject entry = new JsonObject();
        entry.addProperty("enabled", true);
        entry.addProperty("name", skill.name());
        entry.addProperty("description", skill.description());
        entry.addProperty("category", skill.category());
        entry.addProperty("learnable", skill.learnable());
        entry.addProperty("translationKey", skill.translationKey());
        return entry;
    }

    private static void replaceAtomically(Path path, JsonObject root) throws IOException {
        Path parent = path.getParent();
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, "." + path.getFileName() + ".", ".tmp");
        try {
            try (BufferedWriter writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                ConfigJson.write(writer, root);
            }
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException failure) {
            throw new IOException("Cannot update skill configuration '" + path + "' atomically: "
                    + failure.getMessage(), failure);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static IOException invalid(Path path, String location, String reason) {
        return new IOException("Invalid skill configuration '" + path + "' at " + location + ": " + reason);
    }
}
