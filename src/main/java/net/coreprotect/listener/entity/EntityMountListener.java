package net.coreprotect.listener.entity;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;

import net.coreprotect.config.Config;
import net.coreprotect.consumer.Queue;

/**
 * Listener for entity mount events using VehicleEnterEvent and PlayerInteractEntityEvent
 * Note: EntityMountEvent was added in Bukkit 1.21.4, using VehicleEnterEvent for broader compatibility
 * PlayerInteractEntityEvent catches additional mount scenarios
 */
public final class EntityMountListener extends Queue implements Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    private void onVehicleEnter(VehicleEnterEvent event) {
        Entity entered = event.getEntered();
        Entity vehicle = event.getVehicle();
        
        // Get the world
        if (!Config.getConfig(entered.getWorld()).ENTITY_CHANGE) {
            return;
        }
        
        // Log the mount event
        logMount(entered, vehicle, vehicle.getLocation());
    }

    /**
     * Handle player right-clicking an entity - may indicate mounting
     * This is a backup for VehicleEnterEvent which may not fire for all mount scenarios
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Entity entity = event.getRightClicked();

        // Get the world
        if (!Config.getConfig(entity.getWorld()).ENTITY_CHANGE) {
            return;
        }
        
        // Check if this entity can be mounted (is a vehicle)
        // In Bukkit, vehicles include: Minecart, Boat, Horse (and variants), Pig (with saddle), etc.
        if (!entity.isEmpty() && entity.getVehicle() != null) {
            // Entity is already mounted to something, so this click was likely a mount
            // The actual vehicle enter should handle this, but we can log here as backup
            Entity vehicle = entity.getVehicle();
            logMount(entity, vehicle, vehicle.getLocation());
        }
    }

    private void logMount(Entity entity, Entity target, org.bukkit.Location location) {
        String user;
        String entityUuid = null;
        String targetUuid = null;
        
        // Build metadata with entity name
        String metadata = "{\"name\":\"" + entity.getName() + "\"}";
        
        if (entity instanceof Player) {
            // Player is mounting
            user = ((Player) entity).getName();
            entityUuid = entity.getUniqueId().toString();
            targetUuid = target.getUniqueId().toString();

            
            // Queue the event - use target (vehicle) type, not entity (rider) type
            Queue.queueEntityInteract(
                user,
                location,
                net.coreprotect.consumer.process.Process.ENTITY_MOUNT,
                target.getType(),
                entityUuid,
                targetUuid,
                metadata
            );
        }
        else {
            // Non-player entity mounting (e.g., minecart with hoppers)
            // Log as #entity with the entity type as identifier
            // Use target (vehicle) type, not entity (rider) type
            user = "#" + entity.getType().name().toLowerCase();
            
            Queue.queueEntityInteract(
                user,
                location,
                net.coreprotect.consumer.process.Process.ENTITY_MOUNT,
                target != null ? target.getType() : entity.getType(),
                entity.getUniqueId().toString(),
                target != null ? target.getUniqueId().toString() : null,
                metadata
            );
        }
    }
}
