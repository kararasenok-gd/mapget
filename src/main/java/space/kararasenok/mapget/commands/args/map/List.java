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
        String act = null;
        int mapId = -1;
        if (args.length >= 2) {
            act = args[1].toLowerCase();
            if (act.split("_").length > 1) {
                mapId = Integer.parseInt(act.split("_")[1]);
                act = act.split("_")[0];
            }
        }

        if (args.length < 2 || !java.util.List.of("get", "rmv").contains(act)) {
            java.util.List<MapEntry> maps = mapsClass.getMapsList(player);

            if (maps.isEmpty()) {
                player.sendMessage(MiniMessage.deserialize("<yellow>You have no maps."));
                return;
            }

            String listText = maps.stream()
                    .map(m -> String.format(
                            "<aqua>Image #%d</aqua> <gray>-</gray> <white><click:open_url:'%s'>%s</click></white> <green><click:run_command:'/map list get_%d'>[Give]</click></green> <red><click:run_command:'/map list rmv_%d'>[Delete]</click></red>",
                            m.mapId(),
                            m.url(),
                            m.url(),
                            m.mapId(),
                            m.mapId()
                    ))
                    .collect(Collectors.joining("\n"));

            player.sendMessage(MiniMessage.deserialize("<gold>Your maps:</gold>\n" + listText));
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
                            "<yellow>Click <red><click:run_command:'/map list rmv_%d'>[Delete]</click></red> again to confirm removing map #%d.",
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
