package wb.yostradepost;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;

public class YosTradePostClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // 如果需要客户端渲染，可以在这里添加
        ConfigSynchronizer.client(null, null);
    }
}