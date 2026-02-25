package net.coreprotect.listener.entity;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.LeashHitch;
import org.bukkit.entity.Painting;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.inventory.ItemStack;

import net.coreprotect.bukkit.BukkitAdapter;
import net.coreprotect.config.Config;
import net.coreprotect.consumer.Queue;
import net.coreprotect.database.Lookup;
import net.coreprotect.listener.player.PlayerInteractEntityListener;
import net.coreprotect.utility.MaterialUtils;

public final class HangingBreakListener extends Queue implements Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    private void onHangingBreak(HangingBreakEvent event) {

        HangingBreakEvent.RemoveCause cause = event.getCause();
        Entity entity = event.getEntity();
        Block blockEvent = event.getEntity().getLocation().getBlock();

        if (entity instanceof LeashHitch) {
            handleLeashHitchBreak(event, cause, blockEvent);
            return;
        }

        if (entity instanceof ItemFrame || entity instanceof Painting) {
            handleItemFrameOrPaintingBreak(event, cause, entity, blockEvent);
        }
    }

    private void handleLeashHitchBreak(HangingBreakEvent event, HangingBreakEvent.RemoveCause cause, Block blockEvent) {
        if (!isRelevantCause(cause)) {
            return;
        }

        String causeName = determineCauseName(cause, blockEvent);
        Block attachedBlock = blockEvent;

        if (!event.isCancelled() && Config.getConfig(blockEvent.getWorld()).NATURAL_BREAK) {
            Queue.queueNaturalBlockBreak(causeName, blockEvent.getState(), attachedBlock, Material.LEAD, null, 0);
        }
    }

    private void handleItemFrameOrPaintingBreak(HangingBreakEvent event, HangingBreakEvent.RemoveCause cause, Entity entity, Block blockEvent) {
        if (!isRelevantCause(cause)) {
            return;
        }

        String causeName = "#explosion";
        Block attachedBlock = null;
        boolean logDrops = false;

        if (cause.equals(HangingBreakEvent.RemoveCause.PHYSICS)) {
            causeName = "#physics";
        } else if (cause.equals(HangingBreakEvent.RemoveCause.OBSTRUCTION)) {
            causeName = "#obstruction";
        }

        if (!cause.equals(HangingBreakEvent.RemoveCause.EXPLOSION)) {
            causeName = determineNonExplosionCause((Hanging) entity);
            attachedBlock = determineAttachedBlock((Hanging) entity);
            logDrops = !causeName.startsWith("#");
        }

        if (entity instanceof ItemFrame) {
            handleItemFrameBreak(event, causeName, (ItemFrame) entity, blockEvent, logDrops);
        } else {
            handlePaintingBreak(event, causeName, (Painting) entity, blockEvent, attachedBlock);
        }
    }

    private boolean isRelevantCause(HangingBreakEvent.RemoveCause cause) {
        return cause.equals(HangingBreakEvent.RemoveCause.EXPLOSION) || 
               cause.equals(HangingBreakEvent.RemoveCause.PHYSICS) || 
               cause.equals(HangingBreakEvent.RemoveCause.OBSTRUCTION);
    }

    private String determineCauseName(HangingBreakEvent.RemoveCause cause, Block blockEvent) {
        String causeName = "#explosion";

        if (cause.equals(HangingBreakEvent.RemoveCause.PHYSICS)) {
            causeName = "#physics";
        } else if (cause.equals(HangingBreakEvent.RemoveCause.OBSTRUCTION)) {
            causeName = "#obstruction";
        }

        if (!cause.equals(HangingBreakEvent.RemoveCause.EXPLOSION)) {
            String removed = Lookup.whoRemovedCache(blockEvent.getState());
            if (!removed.isEmpty()) {
                causeName = removed;
            }
        }

        return causeName;
    }

    private String determineNonExplosionCause(Hanging hangingEntity) {
        BlockFace attached = hangingEntity.getAttachedFace();
        Block attachedBlock = hangingEntity.getLocation().getBlock().getRelative(attached);
        String removed = Lookup.whoRemovedCache(attachedBlock.getState());
        return removed.isEmpty() ? "#unknown" : removed;
    }

    private Block determineAttachedBlock(Hanging hangingEntity) {
        BlockFace attached = hangingEntity.getAttachedFace();
        return hangingEntity.getLocation().getBlock().getRelative(attached);
    }

    private void handleItemFrameBreak(HangingBreakEvent event, String causeName, ItemFrame itemframe, Block blockEvent, boolean logDrops) {
        Material material = BukkitAdapter.ADAPTER.getFrameType(itemframe);
        String blockData = "FACING=" + itemframe.getFacing().name();

        if (!event.isCancelled() && Config.getConfig(itemframe.getWorld()).ITEM_TRANSACTIONS) {
            if (itemframe.getItem().getType() != Material.AIR) {
                ItemStack[] oldState = new ItemStack[] { itemframe.getItem().clone() };
                ItemStack[] newState = new ItemStack[] { new ItemStack(Material.AIR) };
                PlayerInteractEntityListener.queueContainerSpecifiedItems(causeName, Material.ITEM_FRAME, new Object[] { oldState, newState, itemframe.getFacing() }, itemframe.getLocation(), logDrops);
            }
        }

        if (!event.isCancelled() && Config.getConfig(blockEvent.getWorld()).NATURAL_BREAK) {
            Queue.queueNaturalBlockBreak(causeName, blockEvent.getState(), null, material, blockData, 0);
        }
    }

    private void handlePaintingBreak(HangingBreakEvent event, String causeName, Painting painting, Block blockEvent, Block attachedBlock) {
        Material material = Material.PAINTING;
        String blockData = "FACING=" + painting.getFacing().name();
        int itemData = 0;

        try {
            itemData = MaterialUtils.getArtId(painting.getArt().toString(), true);
        } catch (IncompatibleClassChangeError e) {
            // 1.21.2+
        }

        if (!event.isCancelled() && Config.getConfig(blockEvent.getWorld()).NATURAL_BREAK) {
            Queue.queueNaturalBlockBreak(causeName, blockEvent.getState(), attachedBlock, material, blockData, itemData);
        }
    }
}
