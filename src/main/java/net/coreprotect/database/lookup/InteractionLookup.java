package net.coreprotect.database.lookup;

import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Locale;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;

import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.statement.UserStatement;
import net.coreprotect.language.Phrase;
import net.coreprotect.language.Selector;
import net.coreprotect.listener.channel.PluginChannelListener;
import net.coreprotect.utility.ChatUtils;
import net.coreprotect.utility.Color;
import net.coreprotect.utility.EntityUtils;
import net.coreprotect.utility.MaterialUtils;
import net.coreprotect.utility.StringUtils;
import net.coreprotect.utility.WorldUtils;

public class InteractionLookup {

    private static final int LOOKUP_BLOCK_INTERACTION = 2;
    private static final int LOOKUP_ENTITY_INTERACTION = 34;

    public static String performLookup(String command, Statement statement, Block block, CommandSender commandSender, int offset, int page, int limit) {
        String result = "";

        try {
            if (block == null) {
                return result;
            }

            if (command == null) {
                if (commandSender.hasPermission("coreprotect.co")) {
                    command = "co";
                }
                else if (commandSender.hasPermission("coreprotect.core")) {
                    command = "core";
                }
                else if (commandSender.hasPermission("coreprotect.coreprotect")) {
                    command = "coreprotect";
                }
                else {
                    command = "co";
                }
            }

            boolean found = false;
            int x = block.getX();
            int y = block.getY();
            int z = block.getZ();
            int time = (int) (System.currentTimeMillis() / 1000L);
            int worldId = WorldUtils.getWorldId(block.getWorld().getName());
            int checkTime = 0;
            int count = 0;
            int rowMax = page * limit;
            int pageStart = rowMax - limit;
            int lookupAction = LOOKUP_BLOCK_INTERACTION;
            String lookupEntityUuid = null;
            if (offset > 0) {
                checkTime = time - offset;
            }

            String lookupCommand = ConfigHandler.lookupCommand.get(commandSender.getName());
            if (lookupCommand != null) {
                String[] data = lookupCommand.split("\\.");
                if (data.length > 4) {
                    try {
                        int action = Integer.parseInt(data[4]);
                        if (action == LOOKUP_ENTITY_INTERACTION) {
                            lookupAction = action;
                        }

                        if (data.length > 6 && !data[6].isEmpty()) {
                            lookupEntityUuid = data[6];
                        }
                    }
                    catch (NumberFormatException e) {
                        // ignore malformed state, keep block interaction lookup
                    }
                }
            }

            String query;
            if (lookupAction == LOOKUP_ENTITY_INTERACTION) {
                String queryEntity = "action IN('30','31','32','33') AND time >= '" + checkTime + "'";
                if (lookupEntityUuid != null) {
                    queryEntity = "(entity_uuid = '" + lookupEntityUuid + "' OR target_uuid = '" + lookupEntityUuid + "') AND " + queryEntity;
                }
                else {
                    queryEntity = "wid = '" + worldId + "' AND x = '" + x + "' AND z = '" + z + "' AND y = '" + y + "' AND " + queryEntity;
                }

                query = "SELECT COUNT(*) as count from " + ConfigHandler.prefix + "entity_interact "
                        + WorldUtils.getWidIndex("entity_interact")
                        + "WHERE " + queryEntity + " LIMIT 0, 1";
            }
            else {
                query = "SELECT COUNT(*) as count from " + ConfigHandler.prefix + "block " + WorldUtils.getWidIndex("block") + "WHERE wid = '" + worldId + "' AND x = '" + x + "' AND z = '" + z + "' AND y = '" + y + "' AND action='2' AND time >= '" + checkTime + "' LIMIT 0, 1";
            }
            ResultSet results = statement.executeQuery(query);

            while (results.next()) {
                count = results.getInt("count");
            }
            results.close();
            int totalPages = (int) Math.ceil(count / (limit + 0.0));

            if (lookupAction == LOOKUP_ENTITY_INTERACTION) {
                String queryEntity = "action IN('30','31','32','33') AND time >= '" + checkTime + "'";
                if (lookupEntityUuid != null) {
                    queryEntity = "(entity_uuid = '" + lookupEntityUuid + "' OR target_uuid = '" + lookupEntityUuid + "') AND " + queryEntity;
                }
                else {
                    queryEntity = "wid = '" + worldId + "' AND x = '" + x + "' AND z = '" + z + "' AND y = '" + y + "' AND " + queryEntity;
                }

                query = "SELECT time,user,action,entity_type as type,metadata,wid,x,y,z FROM " + ConfigHandler.prefix + "entity_interact "
                        + WorldUtils.getWidIndex("entity_interact")
                        + "WHERE " + queryEntity + " ORDER BY rowid DESC LIMIT " + pageStart + ", " + limit;
            }
            else {
                query = "SELECT time,user,action,type,data,rolled_back FROM " + ConfigHandler.prefix + "block " + WorldUtils.getWidIndex("block") + "WHERE wid = '" + worldId + "' AND x = '" + x + "' AND z = '" + z + "' AND y = '" + y + "' AND action='2' AND time >= '" + checkTime + "' ORDER BY rowid DESC LIMIT " + pageStart + ", " + limit + "";
            }
            results = statement.executeQuery(query);

            StringBuilder resultBuilder = new StringBuilder();
            while (results.next()) {
                int resultUserId = results.getInt("user");
                int resultAction = results.getInt("action");
                int resultType = results.getInt("type");
                long resultTime = results.getLong("time");

                if (ConfigHandler.playerIdCacheReversed.get(resultUserId) == null) {
                    UserStatement.loadName(statement.getConnection(), resultUserId);
                }

                String resultUser = ConfigHandler.playerIdCacheReversed.get(resultUserId);
                String timeAgo = ChatUtils.getTimeSince(resultTime, time, true);

                if (!found) {
                    resultBuilder = new StringBuilder(Color.WHITE + "----- " + Color.DARK_AQUA + Phrase.build(Phrase.INTERACTIONS_HEADER) + Color.WHITE + " ----- " + ChatUtils.getCoordinates(command, worldId, x, y, z, false, false) + "\n");
                }
                found = true;

                String rbFormat = "";
                String target;
                Phrase phrase;
                int resultWorldId = worldId;
                int resultX = x;
                int resultY = y;
                int resultZ = z;

                if (lookupAction == LOOKUP_ENTITY_INTERACTION) {
                    resultWorldId = results.getInt("wid");
                    resultX = results.getInt("x");
                    resultY = results.getInt("y");
                    resultZ = results.getInt("z");

                    String entityTypeName = EntityUtils.getEntityType(resultType).name().toLowerCase(Locale.ROOT);
                    phrase = mapEntityPhrase(resultAction);
                    String metadata = results.getString("metadata");
                    String leashBlockType = parseLeashBlockType(metadata);

                    if (resultAction == 32 && leashBlockType != null && !leashBlockType.isEmpty()) {
                        target = entityTypeName + " to " + leashBlockType;
                        resultBuilder.append(timeAgo).append(" ").append(Color.WHITE).append("- ")
                                .append(Color.DARK_AQUA).append(resultUser).append(Color.WHITE).append(" leashed ")
                                .append(Color.DARK_AQUA).append(entityTypeName).append(Color.WHITE).append(" to ")
                                .append(Color.DARK_AQUA).append(leashBlockType).append(Color.WHITE).append(".")
                                .append("\n");
                    }
                    else {
                        target = entityTypeName;
                        resultBuilder.append(timeAgo + " " + Color.WHITE + "- ")
                                .append(Phrase.build(phrase, Color.DARK_AQUA + resultUser + Color.WHITE, Color.DARK_AQUA + entityTypeName + Color.WHITE, Selector.FIRST))
                                .append("\n");
                    }
                }
                else {
                    int resultData = results.getInt("data");
                    int resultRolledBack = results.getInt("rolled_back");
                    if (resultRolledBack == 1 || resultRolledBack == 3) {
                        rbFormat = Color.STRIKETHROUGH;
                    }

                    Material resultMaterial = MaterialUtils.getType(resultType);
                    if (resultMaterial == null) {
                        resultMaterial = Material.AIR;
                    }
                    target = resultMaterial.name().toLowerCase(Locale.ROOT);
                    target = StringUtils.nameFilter(target, resultData);
                    if (target.length() > 0) {
                        target = "minecraft:" + target.toLowerCase(Locale.ROOT) + "";
                    }

                    // Hide "minecraft:" for now.
                    if (target.startsWith("minecraft:")) {
                        target = target.split(":")[1];
                    }

                    phrase = Phrase.LOOKUP_INTERACTION;
                    resultBuilder.append(timeAgo + " " + Color.WHITE + "- ")
                            .append(Phrase.build(phrase, Color.DARK_AQUA + rbFormat + resultUser + Color.WHITE + rbFormat, Color.DARK_AQUA + rbFormat + target + Color.WHITE, Selector.FIRST))
                            .append("\n");
                }

                PluginChannelListener.getInstance().sendData(commandSender, resultTime, phraseForLookupAction(lookupAction, resultAction), Selector.FIRST, resultUser, target, -1, resultX, resultY, resultZ, resultWorldId, rbFormat, false, false);
            }
            result = resultBuilder.toString();
            results.close();

            if (found) {
                if (count > limit) {
                    String pageInfo = Color.WHITE + "-----\n";
                    pageInfo = pageInfo + ChatUtils.getPageNavigation(command, page, totalPages) + "\n";
                    result = result + pageInfo;
                }
            }
            else {
                if (rowMax > count && count > 0) {
                    result = Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.NO_RESULTS_PAGE, Selector.SECOND);
                }
                else {
                    result = Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- " + Phrase.build(Phrase.NO_DATA_LOCATION, Selector.THIRD);
                }
            }

            ConfigHandler.lookupPage.put(commandSender.getName(), page);
            ConfigHandler.lookupType.put(commandSender.getName(), 7);
            String lookupState = x + "." + y + "." + z + "." + worldId + "." + lookupAction + "." + limit;
            if (lookupAction == LOOKUP_ENTITY_INTERACTION && lookupEntityUuid != null) {
                lookupState += "." + lookupEntityUuid;
            }
            ConfigHandler.lookupCommand.put(commandSender.getName(), lookupState);
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        return result;
    }

    public static String performEntityLookup(String command, Statement statement, Block block, String entityUuid, CommandSender commandSender, int offset, int page, int limit) {
        int x = block.getX();
        int y = block.getY();
        int z = block.getZ();
        int worldId = WorldUtils.getWorldId(block.getWorld().getName());

        ConfigHandler.lookupCommand.put(commandSender.getName(), x + "." + y + "." + z + "." + worldId + "." + LOOKUP_ENTITY_INTERACTION + "." + limit + "." + entityUuid);
        return performLookup(command, statement, block, commandSender, offset, page, limit);
    }

    private static Phrase phraseForLookupAction(int lookupAction, int resultAction) {
        if (lookupAction == LOOKUP_ENTITY_INTERACTION) {
            return mapEntityPhrase(resultAction);
        }

        return Phrase.LOOKUP_INTERACTION;
    }

    private static Phrase mapEntityPhrase(int action) {
        if (action == 30) {
            return Phrase.LOOKUP_ENTITY_MOUNT;
        }
        else if (action == 31) {
            return Phrase.LOOKUP_ENTITY_DISMOUNT;
        }
        else if (action == 32) {
            return Phrase.LOOKUP_ENTITY_LEASH;
        }
        else if (action == 33) {
            return Phrase.LOOKUP_ENTITY_UNLEASH;
        }

        return Phrase.LOOKUP_ENTITY_MOUNT;
    }

    private static String parseLeashBlockType(String metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }

        String key = "\"block_type\":\"";
        int keyStart = metadata.indexOf(key);
        if (keyStart < 0) {
            return null;
        }

        int valueStart = keyStart + key.length();
        int valueEnd = metadata.indexOf('"', valueStart);
        if (valueEnd <= valueStart) {
            return null;
        }

        return metadata.substring(valueStart, valueEnd).toLowerCase(Locale.ROOT).replace('_', ' ');
    }

}
