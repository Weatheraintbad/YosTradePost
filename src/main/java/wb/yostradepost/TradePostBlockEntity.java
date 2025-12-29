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
    // 存储上次处理的游戏日
    private long lastTradeDay = -1;

    // 时间常量
    private static final long TICKS_PER_DAY = 24000L;

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

        // 🔧 修复：使用正确的游戏日计算方法
        // 参考提供的代码：world.getTimeOfDay() / 24000L
        long currentDay = getCurrentGameDay();

        YosTradePost.LOGGER.info("贸易站时间检查 - 当前游戏日: {}, 最后交易日: {}", currentDay, lastTradeDay);

        // 数据验证
        if (shouldResetData(currentDay)) {
            YosTradePost.LOGGER.warn("检测到异常数据，重置最后交易日！当前日={}，最后交易日={}",
                    currentDay, lastTradeDay);
            lastTradeDay = -1;
            markDirty();
        }

        // 检查是否应该处理交易
        boolean shouldProcess = false;

        if (lastTradeDay < 0) {
            // 第一次使用
            shouldProcess = true;
            YosTradePost.LOGGER.info("第一次使用贸易站");
        } else if (lastTradeDay < currentDay) {
            // 新的一天
            shouldProcess = true;
            YosTradePost.LOGGER.info("检测到新的一天！从第 {} 天到第 {} 天",
                    lastTradeDay, currentDay);
        } else if (lastTradeDay > currentDay) {
            // 时间倒流（比如使用/time set命令）
            shouldProcess = true;
            YosTradePost.LOGGER.warn("时间倒流检测！最后交易日={} > 当前日={}，强制处理",
                    lastTradeDay, currentDay);
        }

        if (shouldProcess) {
            YosTradePost.LOGGER.info("开始处理第 {} 天的交易...", currentDay);
            processDailyTrades(player);
            lastTradeDay = currentDay;
            markDirty();
            YosTradePost.LOGGER.info("第 {} 天交易处理完成", currentDay);
        } else {
            YosTradePost.LOGGER.info("第 {} 天已经处理过交易", lastTradeDay);
        }
    }

    /**
     * 🔧 修复：使用正确的游戏日计算方法
     * 根据参考代码：world.getTimeOfDay() / 24000L
     */
    private long getCurrentGameDay() {
        if (world == null) return 0;

        // 获取游戏时间（当天时间刻，0-23999）
        long timeOfDay = world.getTimeOfDay();

        // 计算当前游戏日
        long currentDay = timeOfDay / TICKS_PER_DAY;

        return currentDay;
    }

    /**
     * 🔧 添加调试方法，显示详细时间信息
     */
    public void debugTimeInfo() {
        if (world == null) return;

        long timeOfDay = world.getTimeOfDay();
        long totalTime = world.getTime();
        long calculatedDay = timeOfDay / TICKS_PER_DAY;

        YosTradePost.LOGGER.info("=== 时间调试信息 ===");
        YosTradePost.LOGGER.info("world.getTimeOfDay(): {}", timeOfDay);
        YosTradePost.LOGGER.info("world.getTime(): {}", totalTime);
        YosTradePost.LOGGER.info("计算出的游戏日: {}", calculatedDay);
        YosTradePost.LOGGER.info("最后交易日: {}", lastTradeDay);
        YosTradePost.LOGGER.info("当天时间刻: {}", timeOfDay % TICKS_PER_DAY);
    }

    /**
     * 🔧 判断是否需要重置数据
     */
    private boolean shouldResetData(long currentDay) {
        // 如果 lastTradeDay 是未来很多天（明显错误）
        if (lastTradeDay > currentDay + 100) {
            return true;
        }

        // 如果 lastTradeDay 是极端负值
        if (lastTradeDay < -100) {
            return true;
        }

        return false;
    }

    /**
     * 🔧 强制重置贸易站数据
     */
    public void resetTradeData() {
        YosTradePost.LOGGER.warn("强制重置贸易站数据");
        lastTradeDay = -1;
        markDirty();
    }

    /**
     * 🔧 强制执行交易（无视时间限制）
     */
    public void forceProcessTrades(PlayerEntity player) {
        YosTradePost.LOGGER.info("强制执行交易处理");
        processDailyTrades(player);
        lastTradeDay = getCurrentGameDay();
        markDirty();
    }

    /**
     * 🔧 手动设置最后交易日（用于修复）
     */
    public void setLastTradeDay(long day) {
        YosTradePost.LOGGER.info("手动设置最后交易日为: {}", day);
        lastTradeDay = day;
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

                YosTradePost.LOGGER.info("最终槽位{}: {}x {}", i, finalStack.getCount(),
                        Registries.ITEM.getId(finalStack.getItem()));
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

        YosTradePost.LOGGER.info("交易处理完成，填充了{}个槽位", filledSlots);
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

        // 读取数据
        if (nbt.contains("LastTradeDay")) {
            lastTradeDay = nbt.getLong("LastTradeDay");
        } else {
            lastTradeDay = -1;
        }

        // 验证数据
        if (lastTradeDay < -1 || lastTradeDay > 1000000) {
            YosTradePost.LOGGER.warn("读取到无效的LastTradeDay: {}，重置为-1", lastTradeDay);
            lastTradeDay = -1;
        }
    }

    @Override
    protected void writeNbt(net.minecraft.nbt.NbtCompound nbt) {
        super.writeNbt(nbt);

        // 保存数据
        if (lastTradeDay >= -1) {
            nbt.putLong("LastTradeDay", lastTradeDay);
        } else {
            nbt.putLong("LastTradeDay", -1);
        }
    }
}