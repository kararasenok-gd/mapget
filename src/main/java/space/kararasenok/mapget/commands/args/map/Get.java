package space.kararasenok.mapget.commands.args.map;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import space.kararasenok.mapget.Mapget;
import space.kararasenok.mapget.technical.Argument;
import space.kararasenok.mapget.utils.Maps;
import space.kararasenok.mapget.utils.MiniMessage;

import java.sql.Connection;

public class Get implements Argument {
    public static final String NAME = "get";
    public static final String DESC = "Convert image into map";
    public static final String LONGDESC = "Download and convert image into map\n\n<gold><b>Permissions:</b></gold>\nmapget.get - Limit map creation. By default available for everyone\nmapget.temp - Bypass restriction for creating temp maps. By default available for ops\n\n<gold><b>Usage:</b></gold>\n<aqua>/map get <url> <crop> <temp></aqua> - Get the map. Crop and temp arguments are boolean (true/false)\n\n<blue><b>Note:</b></blue> You can define arguments like this: <aqua>/map get <arg>:<value></aqua>\n<blue>Examples:</blue>\n<aqua>/map get https://...</aqua> - Get the map with image https://...\n<aqua>/map get https://... temp:true</aqua> - Get temporary map with image https://...\n<aqua>/map get c:true t:true u:https://...</aqua> - Get temporary and cropped map with image https://...\n<blue>Yep, there's alt names for args</blue>\nurl = link = href = u\ncrop = trim = cut = c\ntemp = temporary = t";
    public static final String PERMS = "mapget.get";

    private final Mapget plugin;
    private final Connection conn;

    public Get(Mapget plugin, Connection conn) {
        this.plugin = plugin;
        this.conn = conn;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description(boolean showLong) { return showLong ?  LONGDESC : DESC; }

    @Override
    public void handler(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(MiniMessage.deserialize("<red>Only players can execute this command!"));
            return;
        }

        if (!sender.hasPermission(PERMS)) {
            sender.sendMessage(MiniMessage.deserialize("<red>You do not have permission to use this command!"));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(MiniMessage.deserialize("<red>Incorrect number of arguments! Check <aqua><click:run_command:'/map help get'>help</click></aqua> for right usage."));
            return;
        }

        String link = null;
        boolean crop = plugin.getConfig().getBoolean("map.cropDefault", false);
        boolean temp = false;
        boolean allowTemp = plugin.getConfig().getBoolean("map.allowTemporaryMaps", false) || sender.hasPermission("mapget.temp");
        int index = 0;

        for (int i = 1; i < args.length; i++) {
            String arg = args[i];

            if (arg.contains(":") && !arg.startsWith("https://") && !arg.startsWith("http://")) {
                String[] split = arg.split(":");
                String key = split[0];
                String value = split[1];

                switch (key) {
                    case "url", "link", "href", "u" -> link = value;
                    case "crop", "trim", "cut", "c" -> crop = value.equalsIgnoreCase("true");
                    case "temp", "temporary", "t" ->  temp = value.equalsIgnoreCase("true") &&  allowTemp;
                }
            } else {
                index++;

                switch (index) {
                    case 1: link = arg;
                    case 2: crop = arg.equalsIgnoreCase("true");
                    case 3: temp = arg.equalsIgnoreCase("true") && allowTemp;
                }
            }
        }

        if (link == null) {
            sender.sendMessage(MiniMessage.deserialize("<red>Failed to create map because link is empty!"));
            return;
        }

        sender.sendMessage(MiniMessage.deserialize("<gray>Creating map. Please wait..."));

        plugin.logger.info("Creating map...");
        plugin.logger.info("Data:");
        plugin.logger.info("    Player: {}", sender.getName());
        plugin.logger.info("    URL: {}", link);
        plugin.logger.info("    Must be cropped?: {}", crop);
        plugin.logger.info("    Temporary map?: {}", temp);

        new Maps(plugin, this.conn, plugin.logger, plugin.getDataFolder()).createMap(link, crop, temp, (Player) sender);
    }
}
