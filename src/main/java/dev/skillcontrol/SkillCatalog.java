package dev.skillcontrol;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.skillcontrol.config.CatalogConfig;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import yesman.epicfight.registry.EpicFightRegistries;
import yesman.epicfight.skill.Skill;

final class SkillCatalog {
    private SkillCatalog() {}

    static void load() throws IOException {
        Map<String, String> english = new HashMap<>();
        Set<String> namespaces = new HashSet<>();
        for (ResourceLocation id : EpicFightRegistries.SKILL.keySet()) namespaces.add(id.getNamespace());
        for (var file : ModList.get().getModFiles()) {
            for (String namespace : namespaces) {
                Path language = file.getFile().findResource("assets", namespace, "lang", "en_us.json");
                if (!Files.isRegularFile(language)) continue;
                try (Reader reader = Files.newBufferedReader(language, StandardCharsets.UTF_8)) {
                    JsonObject entries = JsonParser.parseReader(reader).getAsJsonObject();
                    for (Map.Entry<String, JsonElement> entry : entries.entrySet()) {
                        if (entry.getValue().isJsonPrimitive() && entry.getValue().getAsJsonPrimitive().isString()) {
                            english.put(entry.getKey(), entry.getValue().getAsString().replaceAll("§.", ""));
                        }
                    }
                }
            }
        }
        List<CatalogConfig.SkillInfo> catalog = new ArrayList<>();
        for (Skill skill : EpicFightRegistries.SKILL) {
            String key = skill.getTranslationKey();
            String id = skill.getRegistryName().toString();
            catalog.add(new CatalogConfig.SkillInfo(id, english.getOrDefault(key, id),
                    english.getOrDefault(key + ".tooltip", ""),
                    skill.getCategory().toString().toLowerCase(Locale.ROOT), skill.getCategory().learnable(), key));
        }
        Path config = FMLPaths.CONFIGDIR.get().resolve("epicfight-skill-control.json");
        Set<ResourceLocation> disabled = new HashSet<>();
        for (String id : CatalogConfig.load(config, catalog)) {
            ResourceLocation parsed = ResourceLocation.tryParse(id);
            if (parsed == null) throw new IOException("Invalid skill ID in " + config + ": " + id);
            disabled.add(parsed);
            if (!EpicFightRegistries.SKILL.containsKey(parsed)) {
                SkillControl.LOGGER.warn("Configured disabled skill {} is not registered; keeping it for future installs", id);
            }
        }
        SkillPolicy.setLocalPolicy(disabled);
        SkillControl.LOGGER.info("Skill catalog: {} registered skills, {} disabled; configuration: {}", catalog.size(), disabled.size(), config.toAbsolutePath());
    }
}
