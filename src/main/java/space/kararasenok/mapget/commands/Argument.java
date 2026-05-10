package space.kararasenok.mapget.commands;

import org.bukkit.command.CommandSender;

public interface Argument {
    String name();
    String description();
    void handler(CommandSender sender, String[] args);
}
