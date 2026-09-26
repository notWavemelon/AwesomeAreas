package org.wavemelon.awesomeareas.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server-to-Client packet that instructs the client to open the interactive area map GUI.
 */
public record OpenAreaMapPayload(String targetAreaName) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<OpenAreaMapPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("awesomeareas", "open_map"));

    public static final StreamCodec<ByteBuf, OpenAreaMapPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            OpenAreaMapPayload::targetAreaName,
            OpenAreaMapPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
