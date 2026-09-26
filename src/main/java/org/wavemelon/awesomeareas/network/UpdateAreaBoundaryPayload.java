package org.wavemelon.awesomeareas.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

/**
 * Client-to-Server packet to update an area's geographic boundary.
 */
public record UpdateAreaBoundaryPayload(UUID areaId, String boundaryJson) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<UpdateAreaBoundaryPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("awesomeareas", "update_boundary"));

    public static final StreamCodec<ByteBuf, UpdateAreaBoundaryPayload> CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC,
            UpdateAreaBoundaryPayload::areaId,
            ByteBufCodecs.stringUtf8(1048576),
            UpdateAreaBoundaryPayload::boundaryJson,
            UpdateAreaBoundaryPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
