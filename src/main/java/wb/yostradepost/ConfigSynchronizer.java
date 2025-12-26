package wb.yostradepost;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.util.Identifier;

import java.util.LinkedList;
import java.util.List;

public class ConfigSynchronizer {
    public static final Identifier CHANNEL = new Identifier("yostradepost", "init");

    public static void server(ServerPlayNetworkHandler serverPlayNetworkHandler, MinecraftServer minecraftServer) {
        // 修正：SyncPacket需要List<Trade>，YosTradePost.trades是ArrayList<Trade>，可以直接传递
        ServerPlayNetworking.send(serverPlayNetworkHandler.player, new SyncPacket(YosTradePost.trades));
    }

    public static void client(ClientPlayNetworkHandler networkHandler, MinecraftClient client) {
        ClientPlayNetworking.registerGlobalReceiver(SyncPacket.TYPE, ConfigSynchronizer::sync);
    }

    private static void sync(SyncPacket syncPacket, ClientPlayerEntity clientPlayerEntity, PacketSender packetSender) {
        // 清空并重新添加交易列表
        YosTradePost.trades.clear();
        YosTradePost.trades.addAll(syncPacket.trades);
    }

    public static class SyncPacket implements FabricPacket {
        public final List<Trade> trades;
        public static final PacketType<SyncPacket> TYPE = PacketType.create(CHANNEL, SyncPacket::new);

        public SyncPacket(PacketByteBuf buf) {
            List<Trade> l = new LinkedList<>();
            int len = buf.readVarInt();
            for (int i = 0; i < len; i++) {
                try {
                    // 按新字段顺序读取
                    String inputItem = buf.readString();
                    int inputAmount = buf.readVarInt();
                    String outputItem = buf.readString();
                    int outputAmount = buf.readVarInt();
                    int color = buf.readInt();

                    Trade t = new Trade(inputItem, inputAmount, outputItem, outputAmount);
                    t.setColor(color);
                    l.add(t);
                } catch (Exception e) {
                    YosTradePost.LOGGER.error("Error reading trade from packet: {}", e.getMessage());
                }
            }
            trades = l;
        }

        public SyncPacket(List<Trade> trades) {
            this.trades = trades;
        }

        @Override
        public void write(PacketByteBuf buf) {
            buf.writeVarInt(trades.size());
            for (Trade t : trades) {
                // 按新字段顺序写入
                buf.writeString(t.getInputItem());
                buf.writeVarInt(t.getInputAmount());
                buf.writeString(t.getOutputItem());
                buf.writeVarInt(t.getOutputAmount());
                buf.writeInt(t.getColor());
            }
        }

        @Override
        public PacketType<?> getType() {
            return TYPE;
        }
    }
}