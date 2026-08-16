package space.kararasenok.mapget.commands.args.map;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import space.kararasenok.mapget.Mapget;
import space.kararasenok.mapget.technical.Argument;
import space.kararasenok.mapget.utils.Maps;
import space.kararasenok.mapget.utils.MiniMessage;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class Remove implements Argument {
    public static final String NAME = "remove";
    public static final String DESC = "Remove one of your maps";
    public static final String LONGDESC = "Removes a map created by you.\n\n<gold><b>Usage:</b></gold>\n<aqua>/map remove <id></aqua> - Remove a map by ID\n<aqua>/map remove <id1>,<id2>,...</aqua> - Remove multiple maps by ID\n<aqua>/map remove <id1>-<id2></aqua> - Remove a range of maps by ID\n\n<blue><b>Note:</b></blue> You can combine multiple IDs with ranges. Example: <aqua>/map remove 1,2,3-5</aqua>";

    private static final long CONFIRM_WINDOW_MS = 3000;

    private final Maps mapsClass;
    private final ConcurrentHashMap<UUID, String> pendingDeletes = new ConcurrentHashMap<>();
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

        ArrayList<Integer> mapIds = new ArrayList<>();

        for (String part : args[1].split(",")) {
            part = part.trim();

            if (part.matches("\\d+")) {
                mapIds.add(Integer.parseInt(part));
            } else if (part.matches("\\d+-\\d+")) {
                String[] range = part.split("-");
                int from = Integer.parseInt(range[0]);
                int to = Integer.parseInt(range[1]);

                if (from > to) {
                    player.sendMessage(MiniMessage.deserialize("<red>Invalid range!"));
                    return;
                }

                for (int i = from; i <= to; i++) {
                    mapIds.add(i);
                }
            } else {
                player.sendMessage(MiniMessage.deserialize("<red>Map ID must be a number (123), list of numbers (123,456) or range (123-456)!"));
                return;
            }
        }

        ArrayList<Integer> ownedMapIds = new ArrayList<>(new LinkedHashSet<>(mapIds));
        ownedMapIds.removeIf(mapId -> !mapsClass.checkIfPlayerOwnsMap(player, mapId));
        if (ownedMapIds.isEmpty()) {
            player.sendMessage(MiniMessage.deserialize("<red>You do not own any of the specified maps."));
            return;
        }

        UUID uuid = player.getUniqueId();
        String removeArgs = args[1];
        String pendingArguments = pendingDeletes.get(uuid);
        Long pendingTimestamp = pendingDeleteTimestamps.get(uuid);
        boolean confirmed = removeArgs.equals(pendingArguments)
                && pendingTimestamp != null
                && System.currentTimeMillis() - pendingTimestamp <= CONFIRM_WINDOW_MS;

        if (!confirmed) {
            pendingDeletes.put(uuid, removeArgs);
            pendingDeleteTimestamps.put(uuid, System.currentTimeMillis());
            player.sendMessage(MiniMessage.deserialize(String.format(
                    "<yellow>Run <red><click:run_command:'/map remove %s'>/map remove %s</click></red> again to confirm removing %d maps.",
                    removeArgs, removeArgs, ownedMapIds.size()
            )));
            return;
        }

        pendingDeletes.remove(uuid);
        pendingDeleteTimestamps.remove(uuid);

        int succ = 0;
        int failed = 0;

        for (int mapId : ownedMapIds) {
            if (!mapsClass.checkIfPlayerOwnsMap(player, mapId)) {
                failed++;
                continue;
            }
            mapsClass.deleteMap(mapId);
            succ++;
        }

        player.sendMessage(MiniMessage.deserialize(String.format(
                "<green>Successfully removed %d maps. Failed to remove %d maps.",
                succ, failed
        )));
    }
}
