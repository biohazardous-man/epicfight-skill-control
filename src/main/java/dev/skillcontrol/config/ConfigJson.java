package dev.skillcontrol.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonIOException;
import com.google.gson.JsonObject;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.IOException;
import java.io.StringReader;
import java.io.Writer;
import java.nio.file.Path;

final class ConfigJson {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().serializeNulls().create();
    private static final TypeAdapter<JsonElement> JSON_VALUE = GSON.getAdapter(JsonElement.class);

    private ConfigJson() {
    }

    static JsonObject parse(Path path, String text) throws IOException {
        try (JsonReader reader = new JsonReader(new StringReader(text))) {
            reader.setLenient(false);
            try {
                rejectUnescapedControlCharacters(text);
                JsonElement root = readValue(reader);
                if (reader.peek() != JsonToken.END_DOCUMENT) {
                    throw new IOException("Unexpected data after the root object");
                }
                if (!root.isJsonObject()) {
                    throw new IOException("Expected a root object");
                }
                return root.getAsJsonObject();
            } catch (IOException | IllegalStateException failure) {
                throw new IOException("Cannot parse skill configuration '" + path + "' at "
                        + reader.getPath() + ": " + failure.getMessage(), failure);
            }
        }
    }

    static void write(Writer writer, JsonObject root) throws IOException {
        try {
            GSON.toJson(root, writer);
        } catch (JsonIOException failure) {
            throw new IOException(failure.getMessage(), failure);
        }
        writer.write('\n');
    }

    private static JsonElement readValue(JsonReader reader) throws IOException {
        return switch (reader.peek()) {
            case BEGIN_OBJECT -> readObject(reader);
            case BEGIN_ARRAY -> readArray(reader);
            case STRING, NUMBER, BOOLEAN, NULL -> JSON_VALUE.read(reader);
            default -> throw new IOException("Expected a JSON value");
        };
    }

    private static JsonObject readObject(JsonReader reader) throws IOException {
        reader.beginObject();
        JsonObject object = new JsonObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            if (object.has(name)) {
                throw new IOException("Duplicate object key '" + name + "'");
            }
            object.add(name, readValue(reader));
        }
        reader.endObject();
        return object;
    }

    private static JsonArray readArray(JsonReader reader) throws IOException {
        reader.beginArray();
        JsonArray array = new JsonArray();
        while (reader.hasNext()) {
            array.add(readValue(reader));
        }
        reader.endArray();
        return array;
    }

    private static void rejectUnescapedControlCharacters(String text) throws IOException {
        // Gson 2.10 accepts raw control characters even in non-lenient mode.
        boolean inString = false;
        boolean escaped = false;
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (inString && character < 0x20) {
                throw new IOException("Unescaped control character at character " + index);
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
}
