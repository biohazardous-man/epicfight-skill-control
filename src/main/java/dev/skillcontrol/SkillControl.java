package dev.skillcontrol;

import com.mojang.logging.LogUtils;
import dev.skillcontrol.client.ClientVisibility;
import dev.skillcontrol.network.PolicyNetwork;
import java.io.IOException;
import java.io.UncheckedIOException;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(SkillControl.MOD_ID)
public final class SkillControl {
    public static final String MOD_ID = "ef_skill_control";
    public static final Logger LOGGER = LogUtils.getLogger();

    public SkillControl(IEventBus modBus) {
        PolicyNetwork.register(modBus);
        NeoForge.EVENT_BUS.register(BookCleanup.class);
        modBus.addListener(this::loadComplete);
        if (FMLEnvironment.dist == Dist.CLIENT) ClientOnly.init(modBus);
    }

    private void loadComplete(FMLLoadCompleteEvent event) {
        event.enqueueWork(() -> {
            try {
                SkillCatalog.load();
            } catch (IOException exception) {
                throw new UncheckedIOException("Cannot load Epic Fight Skill Control configuration", exception);
            }
        });
    }

    private static final class ClientOnly {
        static void init(IEventBus modBus) {
            ClientVisibility.init(modBus);
            SkillPolicy.setRefreshListener(ClientVisibility::policyChanged);
        }
    }
}
