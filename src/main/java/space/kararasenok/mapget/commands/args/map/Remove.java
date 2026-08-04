package space.kararasenok.mapget.commands.args.map;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import space.kararasenok.mapget.Mapget;
import space.kararasenok.mapget.technical.Argument;
import space.kararasenok.mapget.utils.Maps;
import space.kararasenok.mapget.utils.MiniMessage;

import java.sql.Connection;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class Remove implements Argument {
    public static final String NAME = "remove";
    public static final String DESC = "Remove one of your maps";
    public static final String LONGDESC = "Removes a map created by you.\n\n<gold><b>Usage:</b></gold>\n<aqua>/map remove <id></aqua>";

    private static final long CONFIRM_WINDOW_MS = 3000;

    private final Maps mapsClass;
    private final ConcurrentHashMap<UUID, Integer> pendingDeletes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> pendingDeleteTimestamps = new ConcurrentHashMap<>();

    public Remove(Mapget plugin, Connection conn) {
        this.mapsClass = new Maps(plugin, conn, plugin.logger, plugin.getDataFolder());
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description(boolean showLong) {
        return showLong ? LONGDESC : DESC;
    }

    @Override
    public void handler(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MiniMessage.deserialize("<red>Only players can execute this command!"));
            return;
        }

        if (args.length != 2) {
            player.sendMessage(MiniMessage.deserialize("<red>Incorrect number of arguments! Usage: <aqua>/map remove <id></aqua>"));
            return;
        }

        int mapId;
        try {
            mapId = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            player.sendMessage(MiniMessage.deserialize("<red>Map ID must be a number!"));
            return;
        }

        UUID uuid = player.getUniqueId();
        Integer pendingMapId = pendingDeletes.get(uuid);
        Long pendingTimestamp = pendingDeleteTimestamps.get(uuid);
        boolean confirmed = pendingMapId != null && pendingMapId == mapId
                && pendingTimestamp != null
                && System.currentTimeMillis() - pendingTimestamp <= CONFIRM_WINDOW_MS;

        if (!confirmed) {
            pendingDeletes.put(uuid, mapId);
            pendingDeleteTimestamps.put(uuid, System.currentTimeMillis());
            player.sendMessage(MiniMessage.deserialize(String.format(
                    "<yellow>Run <red><click:run_command:'/map remove %d'>/map remove %d</click></red> again to confirm removing map #%d.",
                    mapId, mapId, mapId
            )));
            return;
        }

        pendingDeletes.remove(uuid);
        pendingDeleteTimestamps.remove(uuid);

        if (!mapsClass.checkIfPlayerOwnsMap(player, mapId)) {
            player.sendMessage(MiniMessage.deserialize("<red>Failed to check map ownership! Maybe you don't own this map or something went wrong (maybe map doesn't exist or database error)."));
            return;
        }

        mapsClass.deleteMap(mapId);
        player.sendMessage(MiniMessage.deserialize("<green>Successfully deleted the map!"));
    }
}
