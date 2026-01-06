package wb.yostradepost.network;

import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import wb.yostradepost.YosTradePost;

import java.util.ArrayList;
import java.util.List;

public class TradeConfigSync {
    public static void sendToPlayer(ServerPlayerEntity player) {
        List<TradeConfigPayload.TradeConfigEntry> list = new ArrayList<>();
        YosTradePost.trades.forEach(t -> list.add(
                new TradeConfigPayload.TradeConfigEntry(
                        t.getInputItem(), t.getInputAmount(),
                        t.getOutputItem(), t.getOutputAmount())));
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        TradeConfigPayload.encode(buf, list);
        player.networkHandler.sendPacket(
                ServerPlayNetworking.createS2CPacket(TradeConfigPayload.ID, buf));
    }
}