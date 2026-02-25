package net.coreprotect.listener.entity;

import java.sql.Connection;
import java.sql.Statement;
import java.util.Locale;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.LeashHitch;
import org.bukkit.entity.Painting;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.inventory.ItemStack;

import net.coreprotect.bukkit.BukkitAdapter;
import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.consumer.Queue;
import net.coreprotect.database.Database;
import net.coreprotect.database.lookup.BlockLookup;
import net.coreprotect.language.Phrase;
import net.coreprotect.listener.player.PlayerInteractEntityListener;
import net.coreprotect.utility.Chat;
import net.coreprotect.utility.Color;
import net.coreprotect.utility.MaterialUtils;

public final class HangingBreakByEntityListener extends Queue implements Listener {

    static void inspectItemFrame(final BlockState block, final Player player) {
        // block check
        class BasicThread implements Runnable {
            @Override
            public void run() {
                if (!player.hasPermission("coreprotect.inspect")) {
                    Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.NO_PERMISSION));
                    ConfigHandler.inspecting.put(player.getName(), false);
                    return;
                }
                if (ConfigHandler.converterRunning) {
                    Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.UPGRADE_IN_PROGRESS));
                    return;
                }
                if (ConfigHandler.purgeRunning) {
                    Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.PURGE_IN_PROGRESS));
                    return;
                }
                if (ConfigHandler.lookupThrottle.get(player.getName()) != null) {
                    Object[] lookupThrottle = ConfigHandler.lookupThrottle.get(player.getName());
                    if ((boolean) lookupThrottle[0] || ((System.currentTimeMillis() - (long) lookupThrottle[1])) < 100) {
                        Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.DATABASE_BUSY));
                        return;
                    }
                }
                ConfigHandler.lookupThrottle.put(player.getName(), new Object[] { true, System.currentTimeMillis() });

                try (Connection connection = Database.getConnection(true)) {
                    if (connection != null) {
                        Statement statement = connection.createStatement();
                        String blockData = BlockLookup.performLookup(null, statement, block, player, 0, 1, 7);

                        if (blockData.contains("\n")) {
                            for (String b : blockData.split("\n")) {
                                Chat.sendComponent(player, b);
                            }
                        }
                        else if (blockData.length() > 0) {
                            Chat.sendComponent(player, blockData);
                        }

                        statement.close();
                    }
                    else {
                        Chat.sendMessage(player, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.DATABASE_BUSY));
                    }
                }
                catch (Exception e) {
                    e.printStackTrace();
                }

                ConfigHandler.lookupThrottle.put(player.getName(), new Object[] { false, System.currentTimeMillis() });
            }
        }
        Runnable runnable = new BasicThread();
        Thread thread = new Thread(runnable);
        thread.start();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onHangingBreakByEntity(HangingBreakByEntityEvent event) {

        Entity entity = event.getEntity();
        Entity remover = event.getRemover();
        BlockState blockEvent = event.getEntity().getLocation().getBlock().getState();
        
        boolean[] inspectionResult = handleInspection(event, blockEvent);
        boolean inspecting = inspectionResult[0];
        boolean cancelled = inspectionResult[1];
        
        if (cancelled) {
            return;
        }

        if (!(entity instanceof ItemFrame || entity instanceof Painting || entity instanceof LeashHitch)) {
            return;
        }

        String culprit = determineCulprit(remover);
        boolean logDrops = shouldLogDrops(remover);

        if (entity instanceof LeashHitch) {
            handleLeashHitchBreak(event, culprit, blockEvent, inspecting);
            return;
        }

        handleItemFrameOrPaintingBreak(event, entity, culprit, blockEvent, inspecting, logDrops);
    }



    private boolean[] handleInspection(HangingBreakByEntityEvent event, BlockState blockEvent) {
        boolean inspecting = false;
        boolean cancelled = false;

        if (event.getRemover() instanceof Player) {
            Player player = (Player) event.getRemover();
            if (isPlayerInspecting(player)) {
                inspectItemFrame(blockEvent, player);
                event.setCancelled(true);
                inspecting = true;
                cancelled = true;
            }
        }

        return new boolean[] { inspecting, cancelled };
    }

    private boolean isPlayerInspecting(Player player) {
        return ConfigHandler.inspecting.get(player.getName()) != null && 
               ConfigHandler.inspecting.get(player.getName());
    }

    private String determineCulprit(Entity remover) {
        if (remover == null) {
            return "#entity";
        }

        if (remover instanceof Player) {
            return ((Player) remover).getName();
        } else if (remover.getType() != null) {
            return "#" + remover.getType().name().toLowerCase(Locale.ROOT);
        }

        return "#entity";
    }

    private boolean shouldLogDrops(Entity remover) {
        if (remover instanceof Player) {
            Player player = (Player) remover;
            return player.getGameMode() != GameMode.CREATIVE;
        }
        return true;
    }

    private void handleLeashHitchBreak(HangingBreakByEntityEvent event, String culprit, BlockState blockEvent, boolean inspecting) {
        if (!event.isCancelled() && Config.getConfig(blockEvent.getWorld()).BLOCK_BREAK && !inspecting) {
            Queue.queueBlockBreak(culprit, blockEvent, Material.LEAD, null, 0);
        }
    }

    private void handleItemFrameOrPaintingBreak(HangingBreakByEntityEvent event, Entity entity, String culprit, BlockState blockEvent, boolean inspecting, boolean logDrops) {
        if (entity instanceof ItemFrame) {
            handleItemFrameBreak(event, (ItemFrame) entity, culprit, blockEvent, inspecting, logDrops);
        } else {
            handlePaintingBreak(event, (Painting) entity, culprit, blockEvent, inspecting);
        }
    }

    private void handleItemFrameBreak(HangingBreakByEntityEvent event, ItemFrame itemframe, String culprit, BlockState blockEvent, boolean inspecting, boolean logDrops) {
        Material material = BukkitAdapter.ADAPTER.getFrameType(itemframe);
        String blockData = "FACING=" + itemframe.getFacing().name();

        if (!event.isCancelled() && Config.getConfig(itemframe.getWorld()).ITEM_TRANSACTIONS && !inspecting) {
            if (itemframe.getItem().getType() != Material.AIR) {
                ItemStack[] oldState = new ItemStack[] { itemframe.getItem().clone() };
                ItemStack[] newState = new ItemStack[] { new ItemStack(Material.AIR) };
                PlayerInteractEntityListener.queueContainerSpecifiedItems(culprit, Material.ITEM_FRAME, new Object[] { oldState, newState, itemframe.getFacing() }, itemframe.getLocation(), logDrops);
            }
        }

        if (!event.isCancelled() && Config.getConfig(blockEvent.getWorld()).BLOCK_BREAK && !inspecting) {
            Queue.queueBlockBreak(culprit, blockEvent, material, blockData, 0);
        }
    }

    private void handlePaintingBreak(HangingBreakByEntityEvent event, Painting painting, String culprit, BlockState blockEvent, boolean inspecting) {
        Material material = Material.PAINTING;
        String blockData = "FACING=" + painting.getFacing().name();
        int itemData = 0;

        try {
            itemData = MaterialUtils.getArtId(painting.getArt().toString(), true);
        } catch (IncompatibleClassChangeError e) {
            // 1.21.2+
        }

        if (!event.isCancelled() && Config.getConfig(blockEvent.getWorld()).BLOCK_BREAK && !inspecting) {
            Queue.queueBlockBreak(culprit, blockEvent, material, blockData, itemData);
        }
    }

}
