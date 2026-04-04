package space.kararasenok.mapget.commands.args.map;

import org.bukkit.command.CommandSender;
import space.kararasenok.mapget.Mapget;
import space.kararasenok.mapget.commands.Argument;
import space.kararasenok.mapget.utils.MiniMessage;

public class Help implements Argument {
    public static final String NAME = "help";

    private final Mapget plugin;
    public Help(Mapget plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void handler(CommandSender sender, String[] args) {
        sender.sendMessage(MiniMessage.deserialize("===== <b><aqua><hover:show_text:'haii :3'>MapGet help</hover></aqua></b> =====\n<dark_gray><i>You can click on commands btw</i></dark_gray>\n\n<click:suggest_command:'/map help'><gold>/map help</gold></click> - Show that message\n<click:suggest_command:'/map get '><gold>/map get <url> <crop (true/<i>false</i>)></gold></click> - Get map with your image. I recommend to use <click:open_url:'https://catbox.moe/'><aqua><hover:show_text:'Also don\\'t forget to register an account there. Just in case :3'>catbox.moe</hover></click>.\n<click:suggest_command:'/map reload'><gold>/map reload</gold> - Reload config file</click>"));
    }
}
