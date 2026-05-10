package space.kararasenok.mapget.commands.args.map;

import org.bukkit.command.CommandSender;
import space.kararasenok.mapget.Mapget;
import space.kararasenok.mapget.commands.Argument;
import space.kararasenok.mapget.commands.handlers.MapHandler;
import space.kararasenok.mapget.utils.MiniMessage;

public class Help implements Argument {
    public static final String NAME = "help";
    public static final String DESC = "Show information about the commands";
    public static final String LONGDESC = "Shows information about the commands.\n\n<gold><b>Usage:</b></gold>\n<aqua>/map help</aqua> - Show all available comamnds\n<aqua>/map help <command></aqua> - Show detailed information about command";

    private final Mapget plugin;
    private final MapHandler mapHandler;
    public Help(Mapget plugin, MapHandler mapHandler) {
        this.plugin = plugin;
        this.mapHandler = mapHandler;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description(boolean showLong) { return showLong ?  LONGDESC : DESC; }

    @Override
    public void handler(CommandSender sender, String[] args) {
        sender.sendMessage(MiniMessage.deserialize("Theres nothing for now. Maybe later I move it to wiki or idk"));
    }
}
