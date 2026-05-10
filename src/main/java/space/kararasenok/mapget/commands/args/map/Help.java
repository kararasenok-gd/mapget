package space.kararasenok.mapget.commands.args.map;

import org.bukkit.command.CommandSender;
import space.kararasenok.mapget.Mapget;
import space.kararasenok.mapget.commands.Argument;
import space.kararasenok.mapget.utils.MiniMessage;

public class Help implements Argument {
    public static final String NAME = "help";
    public static final String DESC = "Show detailed information about the commands";

    private final Mapget plugin;
    public Help(Mapget plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() { return DESC; }

    @Override
    public void handler(CommandSender sender, String[] args) {
        sender.sendMessage(MiniMessage.deserialize("""
        Theres nothing for now
        Maybe later i move it to wiki or idk"""));
    }
}
