package wb.yostradepost;

import java.io.Serializable;

public class PlayerInventory implements Serializable {
    private final ImplementedInventory tradePostInventory = ImplementedInventory.ofSize(27); // 9x3 inventory

    public ImplementedInventory getTradePostInventory() {
        return tradePostInventory;
    }
}