package space.kararasenok.mapget.commands.handlers;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import space.kararasenok.mapget.Mapget;
import space.kararasenok.mapget.commands.Argument;
import space.kararasenok.mapget.commands.args.map.Get;
import space.kararasenok.mapget.commands.args.map.Help;
import space.kararasenok.mapget.commands.args.map.Reload;

import java.sql.Connection;
import java.util.*;

public class MapHandler implements BasicCommand {
    private final Mapget plugin;
    private final Connection conn;

    private final Map<String, Argument> argumentMap = new HashMap<>();
    private final List<String> cachedSuggestions;
    private final String usageMessage;

    public final String commandName = "map";

    public MapHandler(Mapget plugin, Connection conn) {
        this.plugin = plugin;
        this.conn = conn;

        List.of(
                new Help(plugin, this),
                new Reload(plugin),
                new Get(plugin, conn)
        ).forEach(arg -> argumentMap.put(arg.name().toLowerCase(), arg));

        this.cachedSuggestions = argumentMap.values().stream()
                .map(Argument::name)
                .sorted()
                .toList();

        String combinedArgs = String.join(" | ", cachedSuggestions);
        this.usageMessage = "§eUsage: /map <" + combinedArgs + ">";
    }

    @Override
    public void execute(CommandSourceStack stack, String[] args) {
        CommandSender sender = stack.getSender();

        if (args.length == 0) {
            sender.sendMessage(usageMessage);
            return;
        }

        String subCommand = args[0].toLowerCase();
        Argument handler = argumentMap.get(subCommand);

        if (handler != null) {
            handler.handler(sender, args);
        } else {
            sender.sendMessage(usageMessage);
        }
    }

    @Override
    public Collection<String> suggest(CommandSourceStack stack, String[] args) {
        if (args.length <= 1) {
            String input = args.length == 1 ? args[0].toLowerCase() : "";
            return cachedSuggestions.stream()
                    .filter(s -> s.startsWith(input))
                    .toList();
        }
        return Collections.emptyList();
    }

    public String getCommandName() {
        return commandName;
    }
    public Map<String, Argument> getArgumentMap() { return argumentMap; }
}