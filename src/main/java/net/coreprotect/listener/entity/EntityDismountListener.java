package net.coreprotect.listener.entity;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.vehicle.VehicleExitEvent;

import net.coreprotect.config.Config;
import net.coreprotect.consumer.Queue;

public final class EntityDismountListener extends Queue implements Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    private void onVehicleExit(VehicleExitEvent event) {
        Entity exited = event.getExited();
        Entity vehicle = event.getVehicle();

        // Get the world
        if (!Config.getConfig(exited.getWorld()).ENTITY_CHANGE) {
            return;
        }
        
        // Log the dismount event
        logDismount(exited, vehicle, vehicle.getLocation());
    }

    /**
     * Log a dismount event
     * 
     * @param entity
     *            The entity that dismounted
     * @param previousVehicle
     *            The previous vehicle
     * @param location
     *            The location
     */
    private void logDismount(Entity entity, Entity previousVehicle, org.bukkit.Location location) {
        String user;
        String entityUuid = null;
        String targetUuid = null;
        
        // Build metadata with entity name
        String metadata = "{\"name\":\"" + entity.getName() + "\"}";
        
        if (entity instanceof Player) {
            // Player is dismounting
            user = ((Player) entity).getName();
            entityUuid = entity.getUniqueId().toString();
            targetUuid = previousVehicle.getUniqueId().toString();

            
            // Queue the event - use previousVehicle (vehicle) type, not entity (rider) type
            Queue.queueEntityInteract(
                user,
                location,
                net.coreprotect.consumer.process.Process.ENTITY_DISMOUNT,
                previousVehicle.getType(),
                entityUuid,
                targetUuid,
                metadata
            );
        }
        else {
            // Non-player entity dismounting (e.g., minecart with hoppers)
            // Log as #entity with the entity type as identifier
            // Use previousVehicle (vehicle) type, not entity (rider) type
            user = "#" + entity.getType().name().toLowerCase();
            
            Queue.queueEntityInteract(
                user,
                location,
                net.coreprotect.consumer.process.Process.ENTITY_DISMOUNT,
                previousVehicle != null ? previousVehicle.getType() : entity.getType(),
                entity.getUniqueId().toString(),
                previousVehicle != null ? previousVehicle.getUniqueId().toString() : null,
                metadata
            );
        }
    }
}
