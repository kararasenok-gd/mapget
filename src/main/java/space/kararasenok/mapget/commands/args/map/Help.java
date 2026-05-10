package space.kararasenok.mapget.commands.args.map;

import org.bukkit.command.CommandSender;
import space.kararasenok.mapget.Mapget;
import space.kararasenok.mapget.commands.Argument;
import space.kararasenok.mapget.commands.handlers.MapHandler;
import space.kararasenok.mapget.utils.MiniMessage;

import java.util.Map;

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
        Map<String, Argument> argmap = mapHandler.getArgumentMap();
        StringBuilder sb = new StringBuilder();

        if (args.length == 1) {
            sb.append("===== <b><aqua><hover:show_text:'haii :3'>MapGet help</hover></aqua></b> =====\n");
            sb.append("<gray><i>Click on command to view detailed info</i></gray>\n\n");

            for (Map.Entry<String, Argument> entry : argmap.entrySet()) {
                String name = entry.getKey().toLowerCase();
                Argument arg = entry.getValue();

                sb.append(String.format("<click:run_command:'/map help %s'><gold>/map %s</gold></click> - %s\n", name, name, arg.description(false)));
            }
        } else {
            String name = args[1];
            Argument cmdArg = argmap.get(name);

            sb.append(String.format("===== <b><aqua><hover:show_text:'haii :3'>MapGet help - /map %s</hover></aqua></b> =====\n", name));
            sb.append(cmdArg.description(true));

            if (!sb.toString().endsWith("\n")) { sb.append("\n"); }
        }

        sender.sendMessage(MiniMessage.deserialize(sb.toString()));
    }
}
