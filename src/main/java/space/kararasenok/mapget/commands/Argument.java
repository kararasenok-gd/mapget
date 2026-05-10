package space.kararasenok.mapget.commands;

import org.bukkit.command.CommandSender;

public interface Argument {
    String name();
    String description(boolean showLong);
    void handler(CommandSender sender, String[] args);
}
