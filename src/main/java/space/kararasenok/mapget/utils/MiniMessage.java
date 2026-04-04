package space.kararasenok.mapget.utils;

public class MiniMessage {
    public static net.kyori.adventure.text.Component deserialize(String msg) {
        return net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(msg);
    }
}
