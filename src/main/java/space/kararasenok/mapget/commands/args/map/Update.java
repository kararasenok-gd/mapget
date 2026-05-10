package space.kararasenok.mapget.commands.args.map;

import org.bukkit.command.CommandSender;
import space.kararasenok.mapget.Mapget;
import space.kararasenok.mapget.commands.Argument;
import space.kararasenok.mapget.utils.MiniMessage;
import space.kararasenok.mapget.utils.Updates;

public class Update implements Argument {
    public static final String NAME = "update";
    public static final String DESC = "Check for updates";
    public static final String LONGDESC = "Check for updates\n\n<gold><b>Permissions:</b></gold>\nmapget.update - Restrict access to command. By default available only for ops";
    public static final String PERMS = "mapget.update";

    private final Mapget plugin;
    public Update(Mapget plugin) {
        this.plugin = plugin;
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
        if (!sender.hasPermission(PERMS)) {
            sender.sendMessage(MiniMessage.deserialize("<red>You do not have permission to use this command!"));
            return;
        }

        sender.sendMessage(MiniMessage.deserialize("<yellow>Checking for updates...</yellow>"));

        Updates.checkUpdates(plugin, plugin.getConfig().getBoolean("updates.beta", false)).thenAccept(out -> {
           if (out.hasUpdates()) {
               sender.sendMessage(MiniMessage.deserialize(String.format("<green>Updates available!</green>\nYou can download it here: <aqua><click:open_url:'%s'>[click!]</click></aqua>", String.format("https://github.com/kararasenok-gd/mapget/releases/tag/v%s",  out.tag()))));
           } else {
               sender.sendMessage(MiniMessage.deserialize("<blue>You already use latest version!</blue>"));
           }
        });
    }
}
