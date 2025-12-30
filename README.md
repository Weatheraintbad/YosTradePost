<div align="center">
  
  <img width="2238" height="500" alt="screenshot (11)" src="https://github.com/user-attachments/assets/b1c22991-aefa-41b6-8e65-1ca9b3ccf51a" />

### 为我的世界添加类似星露谷物语出货箱的新交易方块。
  
Adds a new trading block similar to Stardew Valley shipping box for Minecraft.

<img width="300" height="300" alt="已移除背景的0d416163-8852-4958-9319-92494f266302" src="https://github.com/user-attachments/assets/a8c1c53a-dd3e-4468-b63c-ae3c4a6da288" />

</div>

### 贸易站(Trade Post) yostradepost:tradepost 在创造模式下位于原版实用方块栏，可使用红色羊毛、白色羊毛、橡木木板和木桶合成。
Trade Post (yostradepost:tradepost) is located in the original crafting bar in creative mode and can be synthesized using red wool, white wool, oak planks, and wooden barrels.

<div align="center">

<img width="500" height="278" alt="ebaf7a7ed38c38ac55c1a92c1d38350d" src="https://github.com/user-attachments/assets/c5a6b070-7910-419d-a9a3-e4d3be464fde" />

</div>


### 玩家将需要交易的物品放入后，贸易站会在新的一天处理交易，将指定的物品转换成另一种指定的物品（可在config文件夹内yostradepost.json中配置）。
After the player puts in the item to be traded, the trading post will process the transaction on a new day, converting the specified item into another specified item (Configurable in the yostradepost.json in the config folder).


### 提供高度可自定义且简洁的配置方式。
Provides a highly customizable and clean way to configure.

- 贸易站在新的一天会瞬间执行3次处理（视为1次交易），单次交易支持货币三级转换（物品-货币1-货币2-货币3）。<br>
  The trading post will perform 3 processing times on a new day (considered as 1 transaction), and a single transaction supports three-level currency conversion (item - currency 1 - currency 2 - currency 3).

- 配置格式 Configuration format：

  ["输入物品ID", "输入数量", "输出物品ID", "输出数量"]（数字可不用双引号）
  
  ["Input Item ID", "Input Quantity", "Output Item ID", "Output Quantity"] (Numbers can be used without double quotation marks)

- 示例 Example：

  8 铁锭（minecraft:iron）-> 1 绿宝石（minecraft:emerald）

  ["minecraft:iron_ingot", 8, "minecraft:emerald", 1]

- 完整配置示意 Full text：

<pre>
{
  "trades": [
    ["输入物品ID", "输入数量", "输出物品ID", "输出数量"],
    ["minecraft:iron_ingot", 8, "minecraft:emerald", 1],
    ["minecraft:gold_ingot", 2, "minecraft:emerald", 1],
    ["minecraft:emerald", 8, "minecraft:diamond", 1]
  ]
}
</pre>

- 以上配置中玩家输入的铁锭和金锭会被转换成绿宝石（第一次处理），若输入铁锭/金锭的数量能兑换超过8个绿宝石，则自动转换为1个钻石（第二次处理），无第三次处理。<br>
  In this configuration, the iron and gold ingots entered by the player will be converted into emeralds (first processing), and if the number of iron/gold ingots entered can be exchanged for more than 8 emeralds, it will be automatically converted to 1 diamond (second processing) without a third processing.


### 目前支持的版本：

Fabric 1.20.1（需要 Fabric Loader 0.16.14 以及上版本，Fabric API任意版本）。

(requires Fabric Loader 0.16.14 or higher, any version of Fabric API).
