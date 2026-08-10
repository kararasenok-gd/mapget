package space.kararasenok.mapget.utils;

public class MiniMessage {
    public static net.kyori.adventure.text.Component deserialize(String msg) {
        return net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                .deserialize(legacyToMiniMessage(msg));
    }

    public static String legacyToMiniMessage(String msg) {
        StringBuilder result = new StringBuilder(msg.length());

        for (int i = 0; i < msg.length(); i++) {
            if (msg.charAt(i) != '\u00a7' || i + 1 >= msg.length()) {
                result.append(msg.charAt(i));
                continue;
            }

            char code = Character.toLowerCase(msg.charAt(i + 1));
            String tag = switch (code) {
                case '0' -> "<black>";
                case '1' -> "<dark_blue>";
                case '2' -> "<dark_green>";
                case '3' -> "<dark_aqua>";
                case '4' -> "<dark_red>";
                case '5' -> "<dark_purple>";
                case '6' -> "<gold>";
                case '7' -> "<gray>";
                case '8' -> "<dark_gray>";
                case '9' -> "<blue>";
                case 'a' -> "<green>";
                case 'b' -> "<aqua>";
                case 'c' -> "<red>";
                case 'd' -> "<light_purple>";
                case 'e' -> "<yellow>";
                case 'f' -> "<white>";
                case 'k' -> "<obfuscated>";
                case 'l' -> "<bold>";
                case 'm' -> "<strikethrough>";
                case 'n' -> "<underlined>";
                case 'o' -> "<italic>";
                case 'r' -> "<reset>";
                case 'x' -> {
                    if (i + 13 >= msg.length()) {
                        yield null;
                    }

                    StringBuilder hex = new StringBuilder(6);
                    boolean valid = true;
                    for (int offset = 0; offset < 6; offset++) {
                        if (msg.charAt(i + 2 + offset * 2) != '\u00a7') {
                            valid = false;
                            break;
                        }
                        char hexDigit = msg.charAt(i + 3 + offset * 2);
                        if (Character.digit(hexDigit, 16) == -1) {
                            valid = false;
                            break;
                        }
                        hex.append(hexDigit);
                    }

                    if (!valid) {
                        yield null;
                    }
                    i += 13;
                    yield "<#" + hex + ">";
                }
                default -> null;
            };

            if (tag == null) {
                result.append(msg.charAt(i));
            } else {
                result.append(tag);
                i++;
            }
        }

        return result.toString();
    }
}
