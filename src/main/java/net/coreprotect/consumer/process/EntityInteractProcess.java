package net.coreprotect.consumer.process;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Locale;

import org.bukkit.Location;

import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.statement.EntityInteractStatement;
import net.coreprotect.utility.WorldUtils;

/**
 * Process handler for entity interact events (mount/dismount/leash/unleash)
 */
public class EntityInteractProcess {

    @SuppressWarnings("unused")
    public static void process(PreparedStatement preparedStmt, Connection connection, int i, String user, Location location, int entityTypeId, int action, String entityUuid, String targetUuid, String metadata) {
        try {
            int time = (int) (System.currentTimeMillis() / 1000L);
            // User ID should already be cached from Process.processConsumer
            int userId = ConfigHandler.playerIdCache.get(user.toLowerCase(Locale.ROOT));
            int wid = WorldUtils.getWorldId(location.getWorld().getName());
            int x = location.getBlockX();
            int y = location.getBlockY();
            int z = location.getBlockZ();

            EntityInteractStatement.insert(preparedStmt, i, time, userId, wid, x, y, z, action, entityTypeId, entityUuid, targetUuid, metadata);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}
