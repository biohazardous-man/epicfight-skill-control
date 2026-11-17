package dev.skillcontrol.network;

import dev.skillcontrol.SkillControl;
import dev.skillcontrol.SkillPolicy;
import io.netty.handler.codec.DecoderException;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.network.ConfigurationTask;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.configuration.ICustomConfigurationTask;
import net.neoforged.neoforge.network.event.RegisterConfigurationTasksEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class PolicyNetwork {
    private static final ConfigurationTask.Type TASK = new ConfigurationTask.Type(SkillControl.MOD_ID + ":skills");

    private PolicyNetwork() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(PolicyNetwork::registerPayloads);
        modBus.addListener(PolicyNetwork::registerTasks);
    }

    private static void registerTasks(RegisterConfigurationTasksEvent event) {
        event.register(new SyncTask());
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.configurationToClient(PolicyPayload.TYPE, PolicyPayload.CODEC, (payload, context) -> {
            SkillPolicy.setServerPolicy(payload.disabled());
            context.reply(Acknowledged.INSTANCE);
        });
        registrar.configurationToServer(Acknowledged.TYPE, Acknowledged.CODEC,
                (payload, context) -> context.finishCurrentTask(TASK));
    }

    private record SyncTask() implements ICustomConfigurationTask {
        @Override
        public void run(Consumer<CustomPacketPayload> sender) {
            sender.accept(new PolicyPayload(SkillPolicy.localDisabled()));
        }

        @Override
        public ConfigurationTask.Type type() {
            return TASK;
        }
    }

    public record PolicyPayload(Set<ResourceLocation> disabled) implements CustomPacketPayload {
        public static final Type<PolicyPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkillControl.MOD_ID, "policy"));
        public static final StreamCodec<FriendlyByteBuf, PolicyPayload> CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    buffer.writeVarInt(payload.disabled.size());
                    for (ResourceLocation id : payload.disabled) buffer.writeResourceLocation(id);
                },
                buffer -> {
                    int count = buffer.readVarInt();
                    if (count < 0 || count > 16384) throw new DecoderException("Invalid disabled skill count: " + count);
                    Set<ResourceLocation> ids = new HashSet<>();
                    for (int index = 0; index < count; index++) ids.add(buffer.readResourceLocation());
                    return new PolicyPayload(Set.copyOf(ids));
                });

        @Override
        public Type<PolicyPayload> type() {
            return TYPE;
        }
    }

    private record Acknowledged() implements CustomPacketPayload {
        private static final Acknowledged INSTANCE = new Acknowledged();
        private static final Type<Acknowledged> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkillControl.MOD_ID, "acknowledged"));
        private static final StreamCodec<FriendlyByteBuf, Acknowledged> CODEC = StreamCodec.unit(INSTANCE);

        @Override
        public Type<Acknowledged> type() {
            return TYPE;
        }
    }
}
