package net.coreprotect.patch.script;

import java.sql.Statement;

import net.coreprotect.config.Config;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.language.Phrase;
import net.coreprotect.language.Selector;
import net.coreprotect.patch.Patch;
import net.coreprotect.utility.Chat;

public class __2_23_2 {

    protected static boolean patch(Statement statement) {
        try {
            if (Config.getGlobal().MYSQL) {
                try {
                    statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + ConfigHandler.prefix + "entity_interact(rowid int NOT NULL AUTO_INCREMENT,PRIMARY KEY(rowid), time int, user int, wid int, x int, y int, z int, action tinyint, entity_type int, entity_uuid varchar(36), target_uuid varchar(36), metadata blob,INDEX(wid,x,z,time),INDEX(user,time),INDEX(action,time)) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4");
                }
                catch (Exception e) {
                    Chat.console(Phrase.build(Phrase.PATCH_SKIP_UPDATE, ConfigHandler.prefix + "entity_interact", Selector.FIRST, Selector.FIRST));
                }
            }
            else {
                try {
                    statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + ConfigHandler.prefix + "entity_interact (time INTEGER, user INTEGER, wid INTEGER, x INTEGER, y INTEGER, z INTEGER, action INTEGER, entity_type INTEGER, entity_uuid TEXT, target_uuid TEXT, metadata BLOB);");
                }
                catch (Exception e) {
                    Chat.console(Phrase.build(Phrase.PATCH_SKIP_UPDATE, ConfigHandler.prefix + "entity_interact", Selector.FIRST, Selector.FIRST));
                }

                if (!Patch.continuePatch()) {
                    return false;
                }

                try {
                    statement.executeUpdate("CREATE INDEX IF NOT EXISTS entity_interact_index ON " + ConfigHandler.prefix + "entity_interact(wid,x,z,time);");
                    statement.executeUpdate("CREATE INDEX IF NOT EXISTS entity_interact_user_index ON " + ConfigHandler.prefix + "entity_interact(user,time);");
                    statement.executeUpdate("CREATE INDEX IF NOT EXISTS entity_interact_action_index ON " + ConfigHandler.prefix + "entity_interact(action,time);");
                }
                catch (Exception e) {
                    Chat.console(Phrase.build(Phrase.PATCH_SKIP_UPDATE, ConfigHandler.prefix + "entity_interact", Selector.SECOND, Selector.SECOND));
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        return true;
    }

}
