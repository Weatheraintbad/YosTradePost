package wb.yostradepost;

import java.io.*;
import java.util.HashMap;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PlayerInventoryManager {
    private static final Logger LOGGER = Logger.getLogger(PlayerInventoryManager.class.getName());
    private HashMap<UUID, PlayerInventory> playerInventories = new HashMap<>();

    public PlayerInventory getPlayerInventory(UUID playerId) {
        return playerInventories.computeIfAbsent(playerId, k -> new PlayerInventory());
    }

    public void save(File file) {
        try (ObjectOutputStream outputStream = new ObjectOutputStream(new FileOutputStream(file))) {
            outputStream.writeObject(playerInventories);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to save player inventories to file: " + file.getAbsolutePath(), e);
        }
    }

    @SuppressWarnings("unchecked")
    public void load(File file) {
        try (ObjectInputStream inputStream = new ObjectInputStream(new FileInputStream(file))) {
            playerInventories = (HashMap<UUID, PlayerInventory>) inputStream.readObject();
        } catch (IOException | ClassNotFoundException e) {
            LOGGER.log(Level.WARNING, "Failed to load player inventories, starting fresh: " + e.getMessage());
            playerInventories = new HashMap<>();
        }
    }
}