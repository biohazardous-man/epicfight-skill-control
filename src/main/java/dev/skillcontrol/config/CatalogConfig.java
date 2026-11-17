package dev.skillcontrol.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonIOException;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringReader;
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
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().serializeNulls().create();
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
        Map<String, SkillInfo> sortedCatalog = validateCatalog(target, catalog);
        JsonObject root;
        boolean changed = false;
        String text;
        try {
            text = Files.readString(target, StandardCharsets.UTF_8);
        } catch (NoSuchFileException missing) {
            text = null;
        } catch (IOException failure) {
            throw new IOException("Cannot read skill configuration '" + target + "': " + failure.getMessage(), failure);
        }
        if (text == null) {
            root = new JsonObject();
            root.addProperty("schemaVersion", 1);
            root.addProperty("instructions", INSTRUCTIONS);
            root.add("skills", new JsonObject());
            changed = true;
        } else {
            root = parse(target, text);
        }

        JsonObject skills = validateRoot(target, root);
        Set<String> disabled = new TreeSet<>();
        for (Map.Entry<String, JsonElement> entry : skills.entrySet()) {
            String id = entry.getKey();
            String location = "$.skills[" + id + "]";
            validateId(target, id, location);
            if (!entry.getValue().isJsonObject()) {
                throw invalid(target, location, "expected a skill object");
            }
            JsonElement enabled = entry.getValue().getAsJsonObject().get("enabled");
            if (enabled == null || !enabled.isJsonPrimitive() || !enabled.getAsJsonPrimitive().isBoolean()) {
                throw invalid(target, location + ".enabled", "expected an explicit boolean (true or false)");
            }
            if (!enabled.getAsBoolean()) {
                disabled.add(id);
            }
        }
        for (SkillInfo skill : sortedCatalog.values()) {
            if (!skills.has(skill.id())) {
                skills.add(skill.id(), entry(skill));
                changed = true;
            }
        }
        if (changed) {
            replaceAtomically(target, root);
        }
        return Collections.unmodifiableSet(disabled);
    }

    private static Map<String, SkillInfo> validateCatalog(Path path, Collection<SkillInfo> catalog) throws IOException {
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

    private static JsonObject entry(SkillInfo skill) {
        JsonObject entry = new JsonObject();
        entry.addProperty("enabled", true);
        entry.addProperty("name", skill.name());
        entry.addProperty("description", skill.description());
        entry.addProperty("category", skill.category());
        entry.addProperty("learnable", skill.learnable());
        entry.addProperty("translationKey", skill.translationKey());
        return entry;
    }

    private static JsonObject parse(Path path, String text) throws IOException {
        validateStringCharacters(path, text);
        try (JsonReader reader = new JsonReader(new StringReader(text))) {
            reader.setLenient(false);
            try {
                JsonElement root = readValue(path, reader, "$");
                if (reader.peek() != JsonToken.END_DOCUMENT) {
                    throw invalid(path, "$", "unexpected data after the root object");
                }
                if (!root.isJsonObject()) {
                    throw invalid(path, "$", "expected a root object");
                }
                return root.getAsJsonObject();
            } catch (IOException | IllegalStateException failure) {
                throw new IOException("Cannot parse skill configuration '" + path + "' at "
                        + reader.getPath() + ": " + failure.getMessage(), failure);
            }
        }
    }

    private static JsonElement readValue(Path path, JsonReader reader, String location) throws IOException {
        return switch (reader.peek()) {
            case BEGIN_OBJECT -> {
                reader.beginObject();
                JsonObject object = new JsonObject();
                while (reader.hasNext()) {
                    String name = reader.nextName();
                    String member = location + "[" + name + "]";
                    if (object.has(name)) {
                        throw invalid(path, member, "duplicate object key");
                    }
                    object.add(name, readValue(path, reader, member));
                }
                reader.endObject();
                yield object;
            }
            case BEGIN_ARRAY -> {
                reader.beginArray();
                JsonArray array = new JsonArray();
                while (reader.hasNext()) {
                    array.add(readValue(path, reader, location + "[" + array.size() + "]"));
                }
                reader.endArray();
                yield array;
            }
            case STRING -> new JsonPrimitive(reader.nextString());
            case NUMBER -> JsonParser.parseString(reader.nextString());
            case BOOLEAN -> new JsonPrimitive(reader.nextBoolean());
            case NULL -> {
                reader.nextNull();
                yield JsonNull.INSTANCE;
            }
            default -> throw invalid(path, location, "expected a JSON value");
        };
    }

    private static void validateStringCharacters(Path path, String text) throws IOException {
        boolean inString = false;
        boolean escaped = false;
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (inString && character < 0x20) {
                throw invalid(path, "$", "unescaped control character at character " + index);
            }
            if (escaped) {
                escaped = false;
            } else if (inString && character == '\\') {
                escaped = true;
            } else if (character == '"') {
                inString = !inString;
            }
        }
    }

    private static void replaceAtomically(Path path, JsonObject root) throws IOException {
        Path parent = path.getParent();
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, "." + path.getFileName() + ".", ".tmp");
        try {
            try (BufferedWriter writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
                writer.write('\n');
            }
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | JsonIOException failure) {
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
