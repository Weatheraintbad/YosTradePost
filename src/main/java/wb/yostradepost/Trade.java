package wb.yostradepost;

import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

public class Trade {
    private String inputItem;
    private String outputItem;
    private int inputAmount;
    private int outputAmount;
    private String color;
    private static final String DEFAULT_COLOR = "FF555555";

    public Trade() {
        this.color = Integer.toHexString(Formatting.DARK_GRAY.getColorIndex()).toUpperCase();
    }

    public Trade(String inputItem, int inputAmount, String outputItem, int outputAmount) {
        this.inputItem = inputItem;
        this.inputAmount = inputAmount;
        this.outputItem = outputItem;
        this.outputAmount = outputAmount;
        this.color = Integer.toHexString(Formatting.DARK_GRAY.getColorIndex()).toUpperCase();
    }

    // Getters and Setters
    public String getInputItem() { return inputItem; }
    public void setInputItem(String inputItem) { this.inputItem = inputItem; }

    public String getOutputItem() { return outputItem; }
    public void setOutputItem(String outputItem) { this.outputItem = outputItem; }

    public int getInputAmount() { return inputAmount; }
    public void setInputAmount(int inputAmount) { this.inputAmount = inputAmount; }

    public int getOutputAmount() { return outputAmount; }
    public void setOutputAmount(int outputAmount) { this.outputAmount = outputAmount; }

    public int getColor() {
        if (this.color == null || this.color.equals("8")) {
            this.color = DEFAULT_COLOR;
        }
        return Integer.parseUnsignedInt(this.color, 16);
    }

    public void setColor(int color) {
        this.color = Integer.toHexString(color).toUpperCase();
    }

    // 向后兼容的方法
    public String getName() { return inputItem; }
    public void setName(String name) { this.inputItem = name; }

    public String getCurrency() { return outputItem; }
    public void setCurrency(String currency) { this.outputItem = currency; }

    public int getSellAmount() { return inputAmount; }
    public void setSellAmount(int sellAmount) { this.inputAmount = sellAmount; }

    public int getSellPrice() { return outputAmount; }
    public void setSellPrice(int sellPrice) { this.outputAmount = sellPrice; }

    // 匹配物品堆栈
    public boolean matches(ItemStack stack) {
        return Registries.ITEM.getId(stack.getItem()).equals(new Identifier(this.inputItem));
    }
}