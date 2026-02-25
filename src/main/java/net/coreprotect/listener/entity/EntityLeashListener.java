package net.coreprotect.listener.entity;

import java.util.Locale;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LeashHitch;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityUnleashEvent;
import org.bukkit.event.entity.PlayerLeashEntityEvent;
import org.bukkit.event.player.PlayerUnleashEntityEvent;

import io.papermc.paper.entity.Leashable;
import net.coreprotect.config.Config;
import net.coreprotect.consumer.Queue;

/**
 * Listener for entity leash/unleash events
 */
public final class EntityLeashListener extends Queue implements Listener {

    private static final String METADATA_NAME_PREFIX = "{\"name\":\"";

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerLeashEntity(PlayerLeashEntityEvent event) {
        Entity entity = event.getEntity();
        Player player = event.getPlayer();
        Entity leashHolder = event.getLeashHolder();

        if (!Config.getConfig(entity.getWorld()).ENTITY_CHANGE) {
            return;
        }

        // Only log entities that can actually be leashed
        if (!(entity instanceof Leashable)) {
            return;
        }

        if (leashHolder instanceof LeashHitch) {
            String blockType = leashHolder.getLocation().getBlock().getType().name().toLowerCase(Locale.ROOT);
            logLeashToBlock(entity, player, leashHolder.getLocation(), blockType);
        }
        else {
            logLeash(entity, player, entity.getLocation());
        }
    }

    /**
     * Log a leash event
     */
    public static void logLeash(Entity entity, Player player, org.bukkit.Location location) {
        if (entity == null || player == null || location == null) {
            return;
        }

        if (location.getWorld() == null || !Config.getConfig(location.getWorld()).ENTITY_CHANGE) {
            return;
        }

        String user = player.getName();
        String entityUuid = entity.getUniqueId().toString();
        String targetUuid = player.getUniqueId().toString();

        Queue.queueEntityInteract(
                user,
                location,
                net.coreprotect.consumer.process.Process.ENTITY_LEASH,
                entity.getType(),
                entityUuid,
                targetUuid,
                ""
        );
    }

    private static void logLeashToBlock(Entity entity, Player player, org.bukkit.Location location, String blockType) {
        if (entity == null || player == null || location == null) {
            return;
        }

        if (location.getWorld() == null || !Config.getConfig(location.getWorld()).ENTITY_CHANGE) {
            return;
        }

        String user = player.getName();
        String entityUuid = entity.getUniqueId().toString();
        String targetUuid = "";
        String metadata = METADATA_NAME_PREFIX + entity.getName() + "\",\"block_type\":\"" + blockType + "\"}";

        Queue.queueEntityInteract(
                user,
                location,
                net.coreprotect.consumer.process.Process.ENTITY_LEASH,
                entity.getType(),
                entityUuid,
                targetUuid,
                metadata
        );
    }

    /**
     * Log an unleash event
     */
    public static void logUnleash(Entity entity, Player player, org.bukkit.Location location) {
        if (entity == null || player == null || location == null) {
            return;
        }

        logUnleashWithUser(entity, player.getName(), player.getUniqueId().toString(), location);
    }

    private static void logUnleashWithUser(Entity entity, String user, String targetUuid, org.bukkit.Location location) {
        if (entity == null || user == null || location == null) {
            return;
        }

        if (location.getWorld() == null || !Config.getConfig(location.getWorld()).ENTITY_CHANGE) {
            return;
        }

        String entityUuid = entity.getUniqueId().toString();

        // Build metadata with entity name
        String metadata = METADATA_NAME_PREFIX + entity.getName() + "\"}";

        Queue.queueEntityInteract(
                user,
                location,
                net.coreprotect.consumer.process.Process.ENTITY_UNLEASH,
                entity.getType(),
                entityUuid,
                targetUuid,
                metadata
        );
    }

    /**
     * Handle entity unleashed
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerUnleashEntity(PlayerUnleashEntityEvent event) {
        Entity entity = event.getEntity();
        Player player = event.getPlayer();

        if (!Config.getConfig(entity.getWorld()).ENTITY_CHANGE) {
            return;
        }

        logUnleash(entity, player, entity.getLocation());

    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityUnleash(EntityUnleashEvent event) {
        Entity entity = event.getEntity();


        if (!Config.getConfig(entity.getWorld()).ENTITY_CHANGE) {
            return;
        }

        EntityUnleashEvent.UnleashReason reason = event.getReason();

        // Log with reason - use reason as user when player is not directly available
        if ((!reason.name().equals("PLAYER_UNLEASH"))) {
            logUnleashWithReason(entity, reason.name(), entity.getLocation());
        }
    }

    /**
     * Log an unleash event with reason
     */
    private static void logUnleashWithReason(Entity entity, String reason, org.bukkit.Location location) {
        String user = "#" + reason.toLowerCase(Locale.ROOT);
        String entityUuid = entity.getUniqueId().toString();
        String targetUuid = "";

        // Build metadata with entity name and reason
        String metadata = METADATA_NAME_PREFIX + entity.getName() + "\",\"reason\":\"" + reason + "\"}";

        Queue.queueEntityInteract(
                user,
                location,
                net.coreprotect.consumer.process.Process.ENTITY_UNLEASH,
                entity.getType(),
                entityUuid,
                targetUuid,
                metadata
        );
    }

}
