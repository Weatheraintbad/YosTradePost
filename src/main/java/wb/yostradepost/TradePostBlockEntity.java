package wb.yostradepost;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TradePostBlockEntity extends BlockEntity implements NamedScreenHandlerFactory {
    // 使用绝对时间刻系统，避免天数计算问题
    private long lastProcessTime = -1; // 上次处理的时间刻
    private long baseTime = -1; // 基准时间，用于校准
    private static final long TICKS_PER_DAY = 24000L;
    private static final long MAX_REASONABLE_TIME = TICKS_PER_DAY * 365 * 10; // 10年

    // 调试模式
    private boolean debugMode = true;

    public TradePostBlockEntity(BlockPos pos, BlockState state) {
        super(YosTradePost.TRADE_POST_BLOCK_ENTITY, pos, state);
    }

    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        return new GenericContainerScreenHandler(ScreenHandlerType.GENERIC_9X3, syncId, playerInventory,
                YosTradePost.inventoryManager.getPlayerInventory(player.getUuid()).getTradePostInventory(), 3);
    }

    @Override
    public Text getDisplayName() {
        return Text.translatable("block.yostradepost.trade_post");
    }

    public void processTrades(PlayerEntity player) {
        if (world == null || world.isClient) return;

        // 🔧 获取当前绝对时间刻（最可靠的方法）
        long currentTime = getCurrentWorldTime();

        // 🔧 初始化基准时间（如果是第一次）
        if (baseTime < 0) {
            baseTime = currentTime;
            markDirty();
        }

        // 🔧 计算相对于基准时间的天数（避免绝对天数计算的混乱）
        long daysSinceBase = (currentTime - baseTime) / TICKS_PER_DAY;
        long lastProcessDays = (lastProcessTime - baseTime) / TICKS_PER_DAY;

        if (debugMode) {
            YosTradePost.LOGGER.info("=== 贸易站时间系统 ===");
            YosTradePost.LOGGER.info("当前时间刻: {}", currentTime);
            YosTradePost.LOGGER.info("基准时间刻: {}", baseTime);
            YosTradePost.LOGGER.info("上次处理时间: {}", lastProcessTime);
            YosTradePost.LOGGER.info("相对于基准的天数: {}", daysSinceBase);
            YosTradePost.LOGGER.info("上次处理的天数: {}", lastProcessDays);

            // 同时显示传统的天数计算（用于调试）
            long traditionalDay = currentTime / TICKS_PER_DAY;
            YosTradePost.LOGGER.info("传统天数计算: {}", traditionalDay);
        }

        // 🔧 数据验证和修复
        if (shouldResetTimeData(currentTime)) {
            YosTradePost.LOGGER.warn("⚠️ 时间数据异常，执行重置！");
            resetTimeData(currentTime);
        }

        // 🔧 检查是否应该处理（使用相对天数系统）
        boolean shouldProcess = false;

        if (lastProcessTime < 0) {
            // 第一次使用
            shouldProcess = true;
            YosTradePost.LOGGER.info("第一次使用贸易站");
        } else if (daysSinceBase > lastProcessDays) {
            // 新的一天（相对于基准）
            shouldProcess = true;
            YosTradePost.LOGGER.info("检测到新的一天（相对天数 {} > {}）",
                    daysSinceBase, lastProcessDays);
        } else if (currentTime - lastProcessTime > TICKS_PER_DAY) {
            // 距离上次处理超过一天（绝对时间）
            shouldProcess = true;
            YosTradePost.LOGGER.info("距离上次处理超过一天（{}刻）",
                    currentTime - lastProcessTime);
        } else if (lastProcessTime > currentTime) {
            // 时间倒流（/time set命令）
            shouldProcess = true;
            YosTradePost.LOGGER.warn("⚠️ 时间倒流检测！上次={} > 当前={}",
                    lastProcessTime, currentTime);
        }

        if (debugMode) {
            YosTradePost.LOGGER.info("是否应该处理: {}", shouldProcess ? "✅ 是" : "❌ 否");
        }

        if (shouldProcess) {
            YosTradePost.LOGGER.info("🔄 开始处理交易...");
            processDailyTrades(player);
            lastProcessTime = currentTime;
            markDirty();
            YosTradePost.LOGGER.info("✅ 交易处理完成，更新时间戳为 {}", lastProcessTime);
        } else {
            YosTradePost.LOGGER.info("📅 今天已经处理过交易");
        }
    }

    /**
     * 🔧 获取当前世界时间（最可靠的方法）
     */
    private long getCurrentWorldTime() {
        if (world == null) return 0;

        // 方法1：尝试从世界属性获取（最准确）
        try {
            if (world.getLevelProperties() instanceof net.minecraft.world.WorldProperties) {
                long worldTime = world.getLevelProperties().getTime();
                if (worldTime >= 0 && worldTime < MAX_REASONABLE_TIME) {
                    return worldTime;
                }
            }
        } catch (Exception e) {
            YosTradePost.LOGGER.warn("获取世界属性时间失败: {}", e.getMessage());
        }

        // 方法2：使用 world.getTime()
        long worldTime = world.getTime();
        if (worldTime >= 0 && worldTime < MAX_REASONABLE_TIME) {
            return worldTime;
        }

        // 方法3：使用 world.getTimeOfDay()（可能返回绝对时间）
        long timeOfDay = world.getTimeOfDay();
        if (timeOfDay >= 0 && timeOfDay < MAX_REASONABLE_TIME) {
            return timeOfDay;
        }

        // 默认返回0
        return 0;
    }

    /**
     * 🔧 检查是否需要重置时间数据
     */
    private boolean shouldResetTimeData(long currentTime) {
        // 检查 lastProcessTime
        if (lastProcessTime < -1 || lastProcessTime > currentTime + TICKS_PER_DAY * 365) {
            return true;
        }

        // 检查 baseTime
        if (baseTime < -1 || baseTime > currentTime + TICKS_PER_DAY * 365) {
            return true;
        }

        // 如果 lastProcessTime 比基准时间还早（不应该发生）
        if (lastProcessTime >= 0 && baseTime >= 0 && lastProcessTime < baseTime) {
            return true;
        }

        return false;
    }

    /**
     * 🔧 重置时间数据
     */
    private void resetTimeData(long currentTime) {
        YosTradePost.LOGGER.warn("重置时间数据：当前={}, 上次={}, 基准={}",
                currentTime, lastProcessTime, baseTime);

        // 保留 lastProcessTime 但如果明显错误则重置
        if (lastProcessTime < 0 || lastProcessTime > currentTime + TICKS_PER_DAY * 100) {
            lastProcessTime = -1;
        }

        // 总是重置基准时间为当前时间或合理值
        if (baseTime < 0 || baseTime > currentTime || baseTime < currentTime - TICKS_PER_DAY * 365) {
            baseTime = Math.max(0, currentTime - (currentTime % TICKS_PER_DAY));
        }

        markDirty();
    }

    // 提取交易处理逻辑
    private void processDailyTrades(PlayerEntity player) {
        var playerInventory = YosTradePost.inventoryManager.getPlayerInventory(player.getUuid()).getTradePostInventory();

        // 复制当前库存内容
        List<ItemStack> beforeItems = new ArrayList<>();
        for (int i = 0; i < playerInventory.size(); i++) {
            ItemStack stack = playerInventory.getStack(i);
            if (!stack.isEmpty()) {
                beforeItems.add(stack.copy());
            }
        }

        YosTradePost.LOGGER.info("处理前库存: {}个物品堆栈", beforeItems.size());

        if (beforeItems.isEmpty()) {
            YosTradePost.LOGGER.info("库存为空，跳过处理");
            return;
        }

        // 第一步处理
        List<ItemStack> firstResult = processSinglePass(beforeItems);
        YosTradePost.LOGGER.info("第一次处理结果: {}个物品堆栈", firstResult.size());

        // 第二步处理
        List<ItemStack> secondResult = processSinglePass(firstResult);
        YosTradePost.LOGGER.info("第二次处理结果: {}个物品堆栈", secondResult.size());

        // 整理物品堆叠
        List<ItemStack> organizedResult = organizeItemsSimple(secondResult);
        YosTradePost.LOGGER.info("整理后结果: {}个物品堆栈", organizedResult.size());

        // 清空并填充整理后的结果
        playerInventory.clear();
        int filledSlots = 0;
        for (int i = 0; i < Math.min(organizedResult.size(), 27); i++) {
            ItemStack finalStack = organizedResult.get(i);
            if (!finalStack.isEmpty()) {
                playerInventory.setStack(i, finalStack);
                filledSlots++;

                if (debugMode) {
                    YosTradePost.LOGGER.info("最终槽位{}: {}x {}", i, finalStack.getCount(),
                            Registries.ITEM.getId(finalStack.getItem()));
                }
            }
        }

        // 如果还有剩余物品，生成实体
        if (organizedResult.size() > 27) {
            for (int i = 27; i < organizedResult.size(); i++) {
                ItemStack stack = organizedResult.get(i);
                if (!stack.isEmpty()) {
                    spawnItemEntity(stack.getItem(), stack.getCount());
                    YosTradePost.LOGGER.info("生成实体: {}x {}", stack.getCount(),
                            Registries.ITEM.getId(stack.getItem()));
                }
            }
        }

        YosTradePost.LOGGER.info("✅ 交易处理完成，填充了{}个槽位", filledSlots);
    }

    // 🔧 添加完整的时间调试命令
    public void debugTime() {
        if (world == null) return;

        long currentTime = getCurrentWorldTime();
        long traditionalDay = currentTime / TICKS_PER_DAY;

        YosTradePost.LOGGER.info("=== 时间调试 ===");
        YosTradePost.LOGGER.info("world.getTime(): {}", world.getTime());
        YosTradePost.LOGGER.info("world.getTimeOfDay(): {}", world.getTimeOfDay());

        try {
            if (world.getLevelProperties() instanceof net.minecraft.world.WorldProperties) {
                YosTradePost.LOGGER.info("world.getLevelProperties().getTime(): {}",
                        world.getLevelProperties().getTime());
            }
        } catch (Exception e) {
            YosTradePost.LOGGER.info("无法获取LevelProperties时间");
        }

        YosTradePost.LOGGER.info("getCurrentWorldTime(): {}", currentTime);
        YosTradePost.LOGGER.info("传统天数计算: {}", traditionalDay);
        YosTradePost.LOGGER.info("基准时间: {}", baseTime);
        YosTradePost.LOGGER.info("上次处理时间: {}", lastProcessTime);
        YosTradePost.LOGGER.info("相对天数: {}",
                baseTime >= 0 ? (currentTime - baseTime) / TICKS_PER_DAY : "N/A");
    }

    // 🔧 强制重新校准基准时间
    public void recalibrate() {
        if (world == null) return;

        long currentTime = getCurrentWorldTime();
        long traditionalDay = currentTime / TICKS_PER_DAY;

        YosTradePost.LOGGER.info("=== 重新校准 ===");
        YosTradePost.LOGGER.info("当前时间: {}", currentTime);
        YosTradePost.LOGGER.info("传统天数: {}", traditionalDay);

        // 设置基准时间为最近的一天开始
        baseTime = currentTime - (currentTime % TICKS_PER_DAY);
        lastProcessTime = -1; // 重置处理时间

        YosTradePost.LOGGER.info("新基准时间: {}", baseTime);
        YosTradePost.LOGGER.info("重置处理时间");

        markDirty();
    }

    // 🔧 强制立即处理（无视时间）
    public void forceProcess(PlayerEntity player) {
        YosTradePost.LOGGER.info("🔧 强制立即处理交易");
        processDailyTrades(player);
        lastProcessTime = getCurrentWorldTime();
        markDirty();
        YosTradePost.LOGGER.info("✅ 强制处理完成");
    }

    // 🔧 手动设置时间（用于修复）
    public void setManualTime(long manualLastProcessTime, long manualBaseTime) {
        YosTradePost.LOGGER.info("🔧 手动设置时间：lastProcessTime={}, baseTime={}",
                manualLastProcessTime, manualBaseTime);

        lastProcessTime = manualLastProcessTime;
        baseTime = manualBaseTime;
        markDirty();

        // 显示状态
        long currentTime = getCurrentWorldTime();
        long daysSinceBase = baseTime >= 0 ? (currentTime - baseTime) / TICKS_PER_DAY : -1;
        long lastDays = baseTime >= 0 && lastProcessTime >= 0 ?
                (lastProcessTime - baseTime) / TICKS_PER_DAY : -1;

        YosTradePost.LOGGER.info("设置后状态：当前={}, 相对天数={}, 上次处理相对天数={}",
                currentTime, daysSinceBase, lastDays);
    }

    // 执行一次完整的交易处理
    private List<ItemStack> processSinglePass(List<ItemStack> inputItems) {
        if (inputItems.isEmpty()) {
            return new ArrayList<>();
        }

        List<Trade> trades = YosTradePost.trades;
        List<ItemStack> result = new ArrayList<>();

        for (ItemStack originalStack : inputItems) {
            if (originalStack.isEmpty()) continue;

            ItemStack currentStack = originalStack.copy();
            Identifier currentItemId = Registries.ITEM.getId(currentStack.getItem());
            boolean isMatched = false;

            for (Trade trade : trades) {
                Identifier tradeItemId = new Identifier(trade.getInputItem());

                if (currentItemId.equals(tradeItemId)) {
                    int requiredAmount = trade.getInputAmount();
                    int stackAmount = currentStack.getCount();

                    if (stackAmount >= requiredAmount) {
                        isMatched = true;
                        int tradeCount = stackAmount / requiredAmount;
                        int remainder = stackAmount % requiredAmount;

                        if (remainder > 0) {
                            result.add(new ItemStack(currentStack.getItem(), remainder));
                        }

                        // 兑换为输出物品
                        Item outputItem = Registries.ITEM.get(new Identifier(trade.getOutputItem()));
                        int totalOutput = trade.getOutputAmount() * tradeCount;

                        result.add(new ItemStack(outputItem, totalOutput));

                        YosTradePost.LOGGER.debug("交易成功: {}x {} -> {}x {}",
                                tradeCount * requiredAmount, trade.getInputItem(),
                                totalOutput, trade.getOutputItem());
                        break;
                    }
                }
            }

            if (!isMatched && !currentStack.isEmpty()) {
                result.add(currentStack);
            }
        }

        return result;
    }

    // 简化整理：只合并完全相同类型的物品
    private List<ItemStack> organizeItemsSimple(List<ItemStack> items) {
        if (items.isEmpty()) {
            return new ArrayList<>();
        }

        // 分组统计
        Map<String, Integer> itemGroups = new HashMap<>();
        Map<String, ItemStack> sampleStacks = new HashMap<>();

        for (ItemStack stack : items) {
            if (stack.isEmpty()) continue;

            // 创建物品的唯一标识（物品ID + NBT哈希）
            String key = Registries.ITEM.getId(stack.getItem()).toString();
            if (stack.hasNbt()) {
                key += "#" + stack.getNbt().hashCode();
            }

            // 统计数量
            itemGroups.put(key, itemGroups.getOrDefault(key, 0) + stack.getCount());

            // 保存一个样本用于复制NBT
            if (!sampleStacks.containsKey(key)) {
                sampleStacks.put(key, stack.copy());
            }
        }

        // 重新创建堆叠
        List<ItemStack> result = new ArrayList<>();

        for (Map.Entry<String, Integer> entry : itemGroups.entrySet()) {
            String key = entry.getKey();
            int totalCount = entry.getValue();

            // 解析物品ID
            String itemIdStr = key.split("#")[0];
            Identifier itemId = new Identifier(itemIdStr);
            Item item = Registries.ITEM.get(itemId);

            if (item == null || item.equals(net.minecraft.item.Items.AIR)) {
                continue;
            }

            // 获取样本堆栈
            ItemStack sampleStack = sampleStacks.get(key);

            // 按最大堆叠数分割
            int maxStackSize = sampleStack.getMaxCount();

            while (totalCount > 0) {
                int stackSize = Math.min(totalCount, maxStackSize);
                ItemStack newStack = sampleStack.copy();
                newStack.setCount(stackSize);
                result.add(newStack);
                totalCount -= stackSize;
            }
        }

        return result;
    }

    // 辅助方法
    private void spawnItemEntity(Item item, int count) {
        if (world == null) return;
        ItemEntity itemEntity = new ItemEntity(world,
                pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
                new ItemStack(item, count));
        world.spawnEntity(itemEntity);
    }

    // NBT处理
    @Override
    public void readNbt(net.minecraft.nbt.NbtCompound nbt) {
        super.readNbt(nbt);

        // 读取新字段
        if (nbt.contains("LastProcessTime")) {
            lastProcessTime = nbt.getLong("LastProcessTime");
        } else {
            lastProcessTime = -1;
        }

        if (nbt.contains("BaseTime")) {
            baseTime = nbt.getLong("BaseTime");
        } else {
            baseTime = -1;
        }

        // 验证数据
        validateAndFixTimeData();
    }

    private void validateAndFixTimeData() {
        long currentTime = getCurrentWorldTime();

        // 如果基准时间无效，设置为当前时间
        if (baseTime < 0 || baseTime > currentTime + TICKS_PER_DAY * 365) {
            baseTime = Math.max(0, currentTime - (currentTime % TICKS_PER_DAY));
        }

        // 如果处理时间无效，重置
        if (lastProcessTime < -1 || lastProcessTime > currentTime + TICKS_PER_DAY * 365) {
            lastProcessTime = -1;
        }

        // 确保基准时间不晚于处理时间
        if (lastProcessTime >= 0 && baseTime > lastProcessTime) {
            baseTime = lastProcessTime - (lastProcessTime % TICKS_PER_DAY);
        }
    }

    @Override
    protected void writeNbt(net.minecraft.nbt.NbtCompound nbt) {
        super.writeNbt(nbt);

        // 保存新字段
        nbt.putLong("LastProcessTime", lastProcessTime);
        nbt.putLong("BaseTime", baseTime);

        // 删除旧字段
        nbt.remove("LastTradeDay");
        nbt.remove("LastProcessedDay");
        nbt.remove("lastTradeDay");
        nbt.remove("lastProcessedDay");

        if (debugMode) {
            YosTradePost.LOGGER.info("保存时间数据：LastProcessTime={}, BaseTime={}",
                    lastProcessTime, baseTime);
        }
    }
}