package space.kararasenok.mapget.commands.args.map;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import space.kararasenok.mapget.Mapget;
import space.kararasenok.mapget.commands.Argument;
import space.kararasenok.mapget.utils.Maps;
import space.kararasenok.mapget.utils.MiniMessage;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class Get implements Argument {
    public static final String NAME = "get";
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
    public void handler(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MiniMessage.deserialize("<red>Only players can execute this command!"));
            return;
        }

        if (!sender.hasPermission(PERMS)) {
            sender.sendMessage(MiniMessage.deserialize("<red>You do not have permission to use this command!"));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(MiniMessage.deserialize("<red>Incorrect number of arguments! Check <aqua><click:suggest_command:'/map help'>help</click></aqua> for right usage."));
            return;
        }

        String link = args[1];
        boolean crop = plugin.getConfig().getBoolean("map.cropDefault", false);

        if (args.length >= 3) {
            crop = args[2].equalsIgnoreCase("true");
        }

        player.sendMessage(MiniMessage.deserialize("<gray>Creating map. Please wait..."));

        plugin.logger.info("Creating map...");
        plugin.logger.info("Data:");
        plugin.logger.info("    Player: {}", player.getName());
        plugin.logger.info("    URL: {}", link);
        plugin.logger.info("    Must be cropped?: {}", crop);

        new Maps(plugin, this.conn, plugin.logger, plugin.getDataFolder()).createMap(link, crop, player);
    }
}
