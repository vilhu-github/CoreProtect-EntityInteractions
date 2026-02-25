package net.coreprotect.listener.player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Creature;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.consumer.Queue;
import net.coreprotect.database.logger.ItemLogger;
import net.coreprotect.listener.player.inspector.InteractionInspector;
import net.coreprotect.model.BlockGroup;
import net.coreprotect.utility.ItemUtils;

public final class PlayerInteractEntityListener extends Queue implements Listener {
    private final InteractionInspector interactionInspector = new InteractionInspector();

    @EventHandler(priority = EventPriority.MONITOR)
    private void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (!shouldProcessEvent(event)) {
            return;
        }

        Player player = event.getPlayer();
        Entity entity = event.getRightClicked();

        if (isPlayerInspecting(player)) {
            handleInspection(event, player, entity);
            return;
        }

        if (entity instanceof ItemFrame) {
            handleItemFrameInteraction(event, player, (ItemFrame) entity);
        } else if (entity instanceof Creature && isAllayEntity(entity)) {
            handleAllayInteraction(event, player, (Creature) entity);
        }
    }

    private boolean shouldProcessEvent(PlayerInteractEntityEvent event) {
        if (event instanceof PlayerArmorStandManipulateEvent) {
            return false;
        }

        return event.getHand() != EquipmentSlot.OFF_HAND;
    }

    private boolean isPlayerInspecting(Player player) {
        return Boolean.TRUE.equals(ConfigHandler.inspecting.get(player.getName()));
    }

    private void handleInspection(PlayerInteractEntityEvent event, Player player, Entity entity) {
        if (!(entity instanceof ItemFrame)) {
            interactionInspector.performEntityInteractionLookup(player, entity);
            event.setCancelled(true);
        } else {
            handleItemFrameInspection(event, player, (ItemFrame) entity);
        }
    }

    private void handleItemFrameInspection(PlayerInteractEntityEvent event, Player player, ItemFrame frame) {
        if (BlockGroup.CONTAINERS.contains(Material.ARMOR_STAND)) {
            ArmorStandManipulateListener.inspectHangingTransactions(frame.getLocation(), player);
        }
        event.setCancelled(true);
    }

    private void handleItemFrameInteraction(PlayerInteractEntityEvent event, Player player, ItemFrame frame) {
        if (event.isCancelled()) {
            return;
        }

        ItemStack handItem = determineHandItem(player, event.getHand());
        if (handItem.getType().equals(Material.AIR)) {
            return;
        }

        if (shouldLogPlayerInteraction(frame, event.getHand(), player)) {
            Queue.queuePlayerInteraction(player.getName(), frame.getLocation().getBlock().getState(), Material.ITEM_FRAME);
        }

        if (!Config.getConfig(player.getWorld()).ITEM_TRANSACTIONS) {
            return;
        }

        handleItemFrameItemTransaction(player, frame, handItem);
    }

    private ItemStack determineHandItem(Player player, EquipmentSlot hand) {
        ItemStack handItem = new ItemStack(Material.AIR);
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        ItemStack offHand = player.getInventory().getItemInOffHand();

        if (hand.equals(EquipmentSlot.HAND) && mainHand.getType() != Material.AIR) {
            handItem = mainHand;
        } else if (hand.equals(EquipmentSlot.OFF_HAND) && offHand.getType() != Material.AIR) {
            handItem = offHand;
        }

        return handItem;
    }

    private boolean shouldLogPlayerInteraction(ItemFrame frame, EquipmentSlot hand, Player player) {
        return frame.getItem().getType() != Material.AIR && 
               hand.equals(EquipmentSlot.HAND) && 
               Config.getConfig(player.getWorld()).PLAYER_INTERACTIONS;
    }

    private void handleItemFrameItemTransaction(Player player, ItemFrame frame, ItemStack handItem) {
        if (frame.getItem().getType().equals(Material.AIR) && !handItem.getType().equals(Material.AIR)) {
            ItemStack[] oldState = new ItemStack[] { new ItemStack(Material.AIR) };
            ItemStack[] newState = new ItemStack[] { handItem.clone() };
            
            if (newState[0].getAmount() > 1) {
                newState[0].setAmount(1); // never add more than 1 item to an item frame at once
            }
            
            queueContainerSpecifiedItems(player.getName(), Material.ITEM_FRAME, new Object[] { oldState, newState, frame.getFacing() }, frame.getLocation(), false);
        }
    }

    private boolean isAllayEntity(Entity entity) {
        return entity instanceof Creature && entity.getType().name().equals("ALLAY");
    }

    private void handleAllayInteraction(PlayerInteractEntityEvent event, Player player, Creature allay) {
        if (event.isCancelled()) {
            return;
        }

        ItemStack handItem = determineHandItem(player, event.getHand());
        if (handItem.getType().equals(Material.AIR)) {
            return;
        }

        ItemStack allayItem = allay.getEquipment().getItemInMainHand();
        if (handItem.getType().equals(allayItem.getType())) {
            return;
        }

        handleAllayItemExchange(player, allay, handItem, allayItem);
    }

    private void handleAllayItemExchange(Player player, Creature allay, ItemStack handItem, ItemStack allayItem) {
        if (allayItem.getType().equals(Material.AIR)) {
            // Player giving item to allay
            ItemStack removedItem = handItem.clone();
            removedItem.setAmount(1);
            CraftItemListener.logCraftedItem(player.getLocation(), player.getName(), removedItem, ItemLogger.ITEM_SELL);
        } else if (handItem.getType().equals(Material.AIR)) {
            // Allay giving item to player
            ItemStack addItem = allayItem.clone();
            addItem.setAmount(1);
            CraftItemListener.logCraftedItem(player.getLocation(), player.getName(), addItem, ItemLogger.ITEM_BUY);
        }
    }

    public static void queueContainerSpecifiedItems(String user, Material type, Object container, Location location, boolean logDrop) {
        ItemStack[] contents = (ItemStack[]) ((Object[]) container)[0];
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();

        String transactingChestId = location.getWorld().getUID().toString() + "." + x + "." + y + "." + z;
        String loggingChestId = user.toLowerCase(Locale.ROOT) + "." + x + "." + y + "." + z;
        int chestId = Queue.getChestId(loggingChestId);
        if (chestId > 0) {
            if (ConfigHandler.forceContainer.get(loggingChestId) != null) {
                int forceSize = ConfigHandler.forceContainer.get(loggingChestId).size();
                List<ItemStack[]> list = ConfigHandler.oldContainer.get(loggingChestId);

                if (list.size() <= forceSize) {
                    list.add(ItemUtils.getContainerState(contents));
                    ConfigHandler.oldContainer.put(loggingChestId, list);
                }
            }
        }
        else {
            List<ItemStack[]> list = new ArrayList<>();
            list.add(ItemUtils.getContainerState(contents));
            ConfigHandler.oldContainer.put(loggingChestId, list);
        }

        ConfigHandler.transactingChest.computeIfAbsent(transactingChestId, k -> Collections.synchronizedList(new ArrayList<>()));
        Queue.queueContainerTransaction(user, location, type, container, chestId);

        if (logDrop) {
            ItemStack dropItem = contents[0];
            if (dropItem.getType() == Material.AIR) {
                return;
            }

            PlayerDropItemListener.playerDropItem(location, user, dropItem);
        }
    }
}
