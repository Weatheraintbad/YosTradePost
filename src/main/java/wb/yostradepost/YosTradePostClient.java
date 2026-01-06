package wb.yostradepost;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import wb.yostradepost.network.TradeConfigPayload;

import java.util.List;

public class YosTradePostClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        /* 接收同步 */
        ClientPlayNetworking.registerGlobalReceiver(TradeConfigPayload.ID,
                (client, handler, buf, responseSender) -> {
                    List<TradeConfigPayload.TradeConfigEntry> list =
                            TradeConfigPayload.decode(buf);
                    client.execute(() -> {
                        YosTradePost.trades.clear();
                        YosTradePost.INPUT_TRADE_MAP.clear();
                        for (var e : list) {
                            Trade t = new Trade(e.inputItem(), e.inputAmount(),
                                    e.outputItem(), e.outputAmount());
                            YosTradePost.trades.add(t);
                            YosTradePost.INPUT_TRADE_MAP.put(
                                    new Identifier(e.inputItem()), t);
                        }
                        YosTradePost.LOGGER.info("[CLIENT] 同步完成，已加载 {} 条规则",
                                YosTradePost.trades.size());
                    });
                });

        /* Tooltip */
        ItemTooltipCallback.EVENT.register((stack, context, lines) -> {
            var id = Registries.ITEM.getId(stack.getItem());
            var trade = YosTradePost.INPUT_TRADE_MAP.get(id);
            if (trade == null) return;

            String outName = Registries.ITEM.get(new Identifier(trade.getOutputItem()))
                    .getName().getString();
            lines.add(Text.translatable(
                    "tooltip.yostradepost.exchange_info",
                    trade.getInputAmount(),
                    trade.getOutputAmount(),
                    outName
            ).formatted(Formatting.GOLD));
        });
    }
}