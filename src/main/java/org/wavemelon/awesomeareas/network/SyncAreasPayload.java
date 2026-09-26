package org.wavemelon.awesomeareas.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server-to-Client packet that synchronizes all active areas and homes to clients.
 * Uses an expanded UTF-8 buffer limit (1MB) to prevent length overflow with large territories.
 */
public record SyncAreasPayload(String jsonData) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SyncAreasPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("awesomeareas", "sync_areas"));

    public static final StreamCodec<ByteBuf, SyncAreasPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(1048576),
            SyncAreasPayload::jsonData,
            SyncAreasPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
