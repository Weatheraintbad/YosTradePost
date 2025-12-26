package wb.yostradepost;

public class ShutdownThread extends Thread {
    @Override
    public void run() {
        YosTradePost.inventoryManager.save(YosTradePost.inventoryFile);
    }
}