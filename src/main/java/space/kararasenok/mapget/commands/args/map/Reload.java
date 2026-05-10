package space.kararasenok.mapget.commands.args.map;

import space.kararasenok.mapget.utils.MiniMessage;
import org.bukkit.command.CommandSender;
import space.kararasenok.mapget.Mapget;
import space.kararasenok.mapget.commands.Argument;

public class Reload implements Argument {
    public static final String NAME = "reload";
    public static final String DESC = "Reloads the plugin";
    public static final String LONGDESC = "Reloads the plugin";
    public static final String PERMS = "mapget.reload";

    private final Mapget plugin;
    public Reload(Mapget plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description(boolean showLong) { return showLong ?  LONGDESC : DESC; }

    @Override
    public void handler(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMS)) {
            sender.sendMessage(MiniMessage.deserialize("<red>You do not have permission to use this command!"));
            return;
        }

        sender.sendMessage(MiniMessage.deserialize("<yellow>Reloading config..."));
        plugin.reloadConfig();
        sender.sendMessage(MiniMessage.deserialize("<green>Successfully reloaded config!"));
    }
}
