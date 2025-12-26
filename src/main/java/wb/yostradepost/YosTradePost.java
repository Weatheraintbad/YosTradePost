package wb.yostradepost;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

import static net.minecraft.server.command.CommandManager.literal;

public class YosTradePost implements ModInitializer {

    // 更新默认配置为表格格式
    public static final String defaultConfig = "{\n" +
            "  \"trades\": [\n" +
            "    [\"输入物品ID\", \"输入数量\", \"输出物品ID\", \"输出数量\"],\n" +
            "    [\"minecraft:iron_ingot\", 64, \"minecraft:emerald\", 1],\n" +
            "    [\"minecraft:gold_ingot\", 32, \"minecraft:emerald\", 1],\n" +
            "    [\"minecraft:diamond\", 1, \"minecraft:emerald\", 4],\n" +
            "    [\"minecraft:cobblestone\", 64, \"minecraft:iron_nugget\", 4],\n" +
            "    [\"minecraft:rotten_flesh\", 32, \"minecraft:emerald\", 1]\n" +
            "  ]\n" +
            "}";

    public static final File configFile = new File("config/yostradepost.json");
    public static final Logger LOGGER = LoggerFactory.getLogger("yostradepost");
    public static final ArrayList<Trade> trades = new ArrayList<>();
    public static final Gson gson = new Gson();

    public static final Block TRADE_POST_BLOCK;
    public static final BlockItem TRADE_POST_BLOCK_ITEM;
    public static final BlockEntityType<TradePostBlockEntity> TRADE_POST_BLOCK_ENTITY;
    public static final Identifier TRADE_POST = new Identifier("yostradepost", "trade_post");

    public static final PlayerInventoryManager inventoryManager = new PlayerInventoryManager();
    public static final File inventoryFile = new File("config/yostradepost.dat");
    private static HashMap<Identifier, Item> wrongIdItemsCheck;

    static {
        TRADE_POST_BLOCK = Registry.register(Registries.BLOCK, TRADE_POST,
                new TradePostBlock(FabricBlockSettings.copyOf(Blocks.CHEST).requiresTool()));
        TRADE_POST_BLOCK_ITEM = Registry.register(Registries.ITEM, TRADE_POST,
                new BlockItem(TRADE_POST_BLOCK, new Item.Settings()));
        TRADE_POST_BLOCK_ENTITY = Registry.register(Registries.BLOCK_ENTITY_TYPE, TRADE_POST,
                FabricBlockEntityTypeBuilder.create(TradePostBlockEntity::new, TRADE_POST_BLOCK).build(null));

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(entries -> {
            entries.addBefore(net.minecraft.item.Items.CRAFTING_TABLE, TRADE_POST_BLOCK_ITEM);
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(literal("reloadtradeconfig")
                    .requires(source -> source.hasPermissionLevel(4))
                    .executes(context -> {
                        reload();
                        context.getSource().sendMessage(Text.literal("Trade config reloaded."));
                        return 1;
                    }));

            // 添加调试命令
            dispatcher.register(literal("debugtrades")
                    .requires(source -> source.hasPermissionLevel(4))
                    .executes(context -> {
                        StringBuilder sb = new StringBuilder();
                        sb.append("=== 贸易站调试信息 ===\n");
                        sb.append("已加载交易规则: ").append(trades.size()).append("\n");

                        for (int i = 0; i < trades.size(); i++) {
                            Trade trade = trades.get(i);
                            sb.append(String.format("%d. %dx %s -> %dx %s\n",
                                    i + 1, trade.getInputAmount(), trade.getInputItem(),
                                    trade.getOutputAmount(), trade.getOutputItem()));
                        }

                        // 检查配置文件
                        sb.append("\n配置文件路径: ").append(configFile.getAbsolutePath()).append("\n");
                        sb.append("配置文件存在: ").append(configFile.exists() ? "是" : "否").append("\n");

                        if (configFile.exists()) {
                            try {
                                String content = new String(java.nio.file.Files.readAllBytes(configFile.toPath()));
                                sb.append("配置文件大小: ").append(content.length()).append(" 字符\n");
                            } catch (IOException e) {
                                sb.append("读取配置文件失败: ").append(e.getMessage()).append("\n");
                            }
                        }

                        // 检查库存文件
                        sb.append("\n库存文件路径: ").append(inventoryFile.getAbsolutePath()).append("\n");
                        sb.append("库存文件存在: ").append(inventoryFile.exists() ? "是" : "否").append("\n");

                        context.getSource().sendMessage(Text.literal(sb.toString())
                                .setStyle(net.minecraft.text.Style.EMPTY.withColor(net.minecraft.util.Formatting.YELLOW)));
                        return 1;
                    }));
        });
    }

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing Yos Trade Post mod...");

        // 先加载配置
        reload();

        // 然后初始化库存管理
        if (!inventoryFile.exists()) {
            try {
                inventoryFile.getParentFile().mkdirs();
                inventoryFile.createNewFile();
                inventoryManager.save(inventoryFile);
                LOGGER.info("Created new inventory file.");
            } catch (IOException e) {
                LOGGER.error("Failed to create inventory file: {}", e.getMessage());
            }
        } else {
            inventoryManager.load(inventoryFile);
            LOGGER.info("Loaded existing inventory file.");
        }

        // 定时保存
        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                inventoryManager.save(inventoryFile);
            }
        }, 0, 10 * 60 * 1000);

        // 关闭钩子
        Runtime.getRuntime().addShutdownHook(new ShutdownThread());

        // 服务器启动时重新加载配置
        ServerLifecycleEvents.SERVER_STARTING.register(s -> {
            LOGGER.info("Server starting, reloading trade config...");
            reload();
        });

        // 检查错误ID的命令
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, registrationEnvironment) -> dispatcher.register(
                literal("tradepost_log_wrong_ids").executes(context -> {
                    if (wrongIdItemsCheck != null) {
                        int wrongCount = 0;
                        for (var entry : wrongIdItemsCheck.entrySet()) {
                            if (entry.getValue().equals(net.minecraft.item.Items.AIR)) {
                                LOGGER.error("WRONG ITEM IDENTIFIER: {}", entry.getKey());
                                wrongCount++;
                                if (context.getSource().isExecutedByPlayer()) {
                                    context.getSource().getPlayer().sendMessage(
                                            Text.literal("错误物品ID: " + entry.getKey())
                                                    .setStyle(net.minecraft.text.Style.EMPTY.withColor(net.minecraft.util.Formatting.RED)),
                                            false);
                                }
                            }
                        }
                        if (wrongCount == 0) {
                            context.getSource().sendMessage(Text.literal("✓ 所有物品ID都有效")
                                    .setStyle(net.minecraft.text.Style.EMPTY.withColor(net.minecraft.util.Formatting.GREEN)));
                        }
                    }
                    return 1;
                })
        ));

        // 配置同步
        ServerPlayConnectionEvents.INIT.register(ConfigSynchronizer::server);

        LOGGER.info("Yos Trade Post mod initialized successfully! Loaded {} trade rules.", trades.size());
    }

    public static void reload() {
        trades.clear();
        wrongIdItemsCheck = new HashMap<>();

        LOGGER.info("Loading trade configuration from {}", configFile.getAbsolutePath());

        if (!configFile.exists()) {
            try {
                configFile.getParentFile().mkdirs();
                configFile.createNewFile();
                try (BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(configFile))) {
                    bufferedWriter.write(defaultConfig);
                }
                LOGGER.info("Default config has been written to the file.");
            } catch (IOException e) {
                LOGGER.error("Failed to create config file: {}", e.getMessage());
                return;
            }
        }

        try {
            // 读取文件内容
            String content = new String(java.nio.file.Files.readAllBytes(configFile.toPath()));
            LOGGER.debug("Config file content:\n{}", content);

            JsonObject json = JsonParser.parseString(content).getAsJsonObject();

            if (!json.has("trades")) {
                LOGGER.error("Config file missing 'trades' array");
                return;
            }

            JsonArray tradesArray = json.getAsJsonArray("trades");

            int lineNumber = 0;
            int loadedCount = 0;

            for (JsonElement element : tradesArray) {
                lineNumber++;

                if (!element.isJsonArray()) {
                    LOGGER.warn("Line {} is not a JSON array", lineNumber);
                    continue;
                }

                JsonArray tradeRow = element.getAsJsonArray();

                // 跳过表头（第一行）
                if (lineNumber == 1) {
                    LOGGER.debug("Skipping header row: {}", tradeRow);
                    continue;
                }

                // 检查行格式
                if (tradeRow.size() != 4) {
                    LOGGER.warn("Skipping invalid trade row at line {}: expected 4 columns, got {}",
                            lineNumber, tradeRow.size());
                    continue;
                }

                try {
                    String inputItem = tradeRow.get(0).getAsString();
                    int inputAmount = tradeRow.get(1).getAsInt();
                    String outputItem = tradeRow.get(2).getAsString();
                    int outputAmount = tradeRow.get(3).getAsInt();

                    LOGGER.debug("Parsing trade: {}x {} -> {}x {}",
                            inputAmount, inputItem, outputAmount, outputItem);

                    // 验证物品ID
                    Identifier inputId = new Identifier(inputItem);
                    Identifier outputId = new Identifier(outputItem);
                    Item inputItemObj = Registries.ITEM.get(inputId);
                    Item outputItemObj = Registries.ITEM.get(outputId);

                    wrongIdItemsCheck.put(inputId, inputItemObj);
                    wrongIdItemsCheck.put(outputId, outputItemObj);

                    // 检查物品是否存在
                    if (inputItemObj == net.minecraft.item.Items.AIR) {
                        LOGGER.warn("Input item not found: {}", inputItem);
                    }
                    if (outputItemObj == net.minecraft.item.Items.AIR) {
                        LOGGER.warn("Output item not found: {}", outputItem);
                    }

                    // 创建交易对象
                    Trade trade = new Trade(inputItem, inputAmount, outputItem, outputAmount);
                    trades.add(trade);
                    loadedCount++;

                    LOGGER.info("Loaded trade: {}x {} -> {}x {}",
                            inputAmount, inputItem, outputAmount, outputItem);

                } catch (Exception e) {
                    LOGGER.error("Error parsing trade at line {}: {}", lineNumber, e.getMessage());
                    e.printStackTrace();
                }
            }

            LOGGER.info("Successfully loaded {} trade rules from config", loadedCount);

            // 打印摘要
            if (!trades.isEmpty()) {
                LOGGER.info("=== 加载的交易规则 ===");
                for (int i = 0; i < trades.size(); i++) {
                    Trade trade = trades.get(i);
                    LOGGER.info("{}. {}x {} -> {}x {}",
                            i + 1, trade.getInputAmount(), trade.getInputItem(),
                            trade.getOutputAmount(), trade.getOutputItem());
                }
            }

        } catch (FileNotFoundException e) {
            LOGGER.error("Config file not found: {}", e.getMessage());
        } catch (Exception e) {
            LOGGER.error("Failed to parse config file: {}", e.getMessage());
            e.printStackTrace();
        }
    }
}