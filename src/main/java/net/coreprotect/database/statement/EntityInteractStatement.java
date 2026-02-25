package net.coreprotect.database.statement;

import java.sql.PreparedStatement;
import java.sql.SQLException;


/**
 * Statement handler for entity_interact table (mount/dismount/leash/unleash)
 */
public class EntityInteractStatement {


    public static void insert(PreparedStatement preparedStmt, int batchCount, int time, int userId, int wid, int x, int y, int z, int action, int entityType, String entityUuid, String targetUuid, String metadata) {
        try {
            preparedStmt.setInt(1, time);
            preparedStmt.setInt(2, userId);
            preparedStmt.setInt(3, wid);
            preparedStmt.setInt(4, x);
            preparedStmt.setInt(5, y);
            preparedStmt.setInt(6, z);
            preparedStmt.setInt(7, action);
            preparedStmt.setInt(8, entityType);
            preparedStmt.setString(9, entityUuid);
            preparedStmt.setString(10, targetUuid);

            byte[] byteData = null;
            if (metadata != null) {
                byteData = metadata.getBytes();
            }
            preparedStmt.setObject(11, byteData);
            
            preparedStmt.addBatch();

            if (batchCount > 0 && batchCount % 1000 == 0) {
                preparedStmt.executeBatch();
            }
        }
        catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
