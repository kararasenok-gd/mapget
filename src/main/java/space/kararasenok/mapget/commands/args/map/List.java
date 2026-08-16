package space.kararasenok.mapget.commands.args.map;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.map.MapView;
import space.kararasenok.mapget.Mapget;
import space.kararasenok.mapget.technical.Argument;
import space.kararasenok.mapget.technical.MapEntry;
import space.kararasenok.mapget.utils.Maps;
import space.kararasenok.mapget.utils.MiniMessage;

import java.sql.Connection;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class List implements Argument {
    public static final String NAME = "list";
    public static final String DESC = "Shows your maps";
    public static final String LONGDESC = "Show list of maps created by you. Also can help you get the map back if you lost it";

    private static final long CONFIRM_WINDOW_MS = 3000;
    private static final int MAPS_PER_PAGE = 10;

    private final Mapget plugin;
    private final Connection conn;
    private final Maps mapsClass;

    private final ConcurrentHashMap<UUID, Integer> pendingDeletes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> pendingDeleteTimestamps = new ConcurrentHashMap<>();

    public List(Mapget plugin, Connection conn) {
        this.plugin = plugin;
        this.conn = conn;
        this.mapsClass = new Maps(plugin, conn, plugin.logger, plugin.getDataFolder());
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description(boolean showLong) { return showLong ?  LONGDESC : DESC; }


    private boolean giveMap(Player player, int mapId) {
        if (!mapsClass.checkIfPlayerOwnsMap(player, mapId)) {
            player.sendMessage(MiniMessage.deserialize("<red>Failed to check map ownership! Maybe you don't own this map or something went wrong (maybe map doesn't exist or database error)."));
            return false;
        }

        MapView mapView = Bukkit.getMap(mapId);
        MapEntry data = mapsClass.getMapData(mapId);

        ItemStack mapItem = mapsClass.getMapItem(mapView, player, mapId, data.url());
        if (mapItem == null) return false;
        player.getInventory().addItem(mapItem);

        return true;
    }

    private boolean deleteMap (Player player, int mapId) {
        if (!mapsClass.checkIfPlayerOwnsMap(player, mapId)) {
            player.sendMessage(MiniMessage.deserialize("<red>Failed to check map ownership! Maybe you don't own this map or something went wrong (maybe map doesn't exist or database error)."));
            return false;
        }

        mapsClass.deleteMap(mapId);
        return true;
    }

    @Override
    public void handler(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MiniMessage.deserialize("<red>Only players can execute this command!"));
            return;
        }
        String act = "";
        int mapId = -1;
        if (args.length >= 2 && args[1].matches("(get|rmv)_\\d+")) {
            act = args[1].toLowerCase();
            if (act.split("_").length > 1) {
                mapId = Integer.parseInt(act.split("_")[1]);
                act = act.split("_")[0];
            }
        }

        if (args.length < 2 || !java.util.List.of("get", "rmv").contains(act)) {
            int currentPage = 1;
            if (args.length >= 2) {
                try {
                    currentPage = Integer.parseInt(args[1]);
                } catch (NumberFormatException e) {
                    player.sendMessage(MiniMessage.deserialize("<red>Invalid page number!"));
                    return;
                }
            }

            Maps.MapListResult firstPage = mapsClass.getMapsList(player, MAPS_PER_PAGE, 0);
            if (firstPage.pages() == 0) {
                player.sendMessage(MiniMessage.deserialize("<yellow>You have no maps."));
                return;
            }

            int pages = firstPage.pages();
            if (currentPage > pages || currentPage < 1) {
                player.sendMessage(MiniMessage.deserialize("<red>Invalid page number!"));
                return;
            }

            java.util.List<MapEntry> maps = currentPage == 1
                    ? firstPage.entries()
                    : mapsClass.getMapsList(player, MAPS_PER_PAGE, (currentPage - 1) * MAPS_PER_PAGE).entries();

            String listText = maps.stream()
                    .map(m -> String.format(
                            "<dark_gray>[<aqua>#%d</aqua>]</dark_gray> <click:open_url:'%s'><aqua>View</aqua></click> <dark_gray>•</dark_gray> <click:run_command:'/map list get_%d'><green>Give</green></click> <dark_gray>•</dark_gray> <click:run_command:'/map list rmv_%d'><red>Delete</red></click>",
                            m.mapId(),
                            m.url(),
                            m.mapId(),
                            m.mapId()
                    ))
                    .collect(Collectors.joining("\n"));

            String navigation = "";
            if (pages > 1) {
                String previous = currentPage > 1
                        ? String.format("<click:run_command:'/map list %d'><aqua><< Previous</aqua></click>", currentPage - 1)
                        : "<dark_gray><< Previous</dark_gray>";
                String next = currentPage < pages
                        ? String.format("<click:run_command:'/map list %d'><aqua>Next >></aqua></click>", currentPage + 1)
                        : "<dark_gray>Next >></dark_gray>";
                navigation = String.format("\n%s <dark_gray>|</dark_gray> <gold>Page %d/%d</gold> <dark_gray>|</dark_gray> %s", previous, currentPage, pages, next);
            }

            player.sendMessage(MiniMessage.deserialize("<gold>Your maps:</gold>\n" + listText + navigation));
        } else {
            if (Objects.equals(act, "get")) {
                boolean status = giveMap(player, mapId);

                if (status) {
                    player.sendMessage(MiniMessage.deserialize("<green>Successfully gave you the map!"));
                } else {
                    player.sendMessage(MiniMessage.deserialize("<red>Failed to give you the map!"));
                }
            } else if (Objects.equals(act, "rmv")) {
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
                            "<yellow>Click <red><click:run_command:'/map list rmv_%d'>Delete</click></red> again to confirm removing map #%d.",
                            mapId, mapId
                    )));
                    return;
                }

                pendingDeletes.remove(uuid);
                pendingDeleteTimestamps.remove(uuid);

                boolean status = deleteMap(player, mapId);

                if (status) {
                    player.sendMessage(MiniMessage.deserialize("<green>Successfully deleted the map!"));
                } else {
                    player.sendMessage(MiniMessage.deserialize("<red>Failed to delete the map!"));
                }
            }
        }
    }
}
