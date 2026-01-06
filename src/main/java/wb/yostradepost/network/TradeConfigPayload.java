package wb.yostradepost.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import wb.yostradepost.YosTradePost;

import java.util.ArrayList;
import java.util.List;

public final class TradeConfigPayload {

    public static final Identifier ID = new Identifier("yostradepost", "trade_config");

    /* 编码：List -> buf */
    public static void encode(PacketByteBuf buf, List<TradeConfigEntry> list) {
        buf.writeVarInt(list.size());
        for (TradeConfigEntry e : list) {
            buf.writeString(e.inputItem());
            buf.writeVarInt(e.inputAmount());
            buf.writeString(e.outputItem());
            buf.writeVarInt(e.outputAmount());
        }
    }

    /* 解码：buf -> List */
    public static List<TradeConfigEntry> decode(PacketByteBuf buf) {
        int size = buf.readVarInt();
        List<TradeConfigEntry> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(new TradeConfigEntry(buf.readString(), buf.readVarInt(),
                    buf.readString(), buf.readVarInt()));
        }
        return list;
    }

    public record TradeConfigEntry(String inputItem, int inputAmount,
                                   String outputItem, int outputAmount) {}
}