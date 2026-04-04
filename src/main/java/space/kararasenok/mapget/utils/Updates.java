package space.kararasenok.mapget.utils;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.CompletableFuture;

public class Updates {
    // I stole this code from here: https://github.com/kararasenok-gd/NewDayNotifier/blob/master/src/main/java/space/kararasenok/newDayNotifier/Updates.java
    // Yeah, I'm too lazy to do something new. And why if I did it before?

    private static final String repository = "kararasenok-gd/mapget";

    private static boolean isOutdated(String current, String latest) {
        String[] c = current.split("\\.");
        String[] l = latest.split("\\.");

        int length = Math.max(c.length, l.length);

        for  (int i = 0; i < length; i++) {
            int cv = i < c.length ? Integer.parseInt(c[i]) : 0;
            int lv =  i < l.length ? Integer.parseInt(l[i]) : 0;

            if (cv < lv) return true;
            if (cv > lv) return false;
        }

        return false;
    };

    public static CompletableFuture<Boolean> checkUpdates(JavaPlugin plugin) {
        String version = plugin.getDescription().getVersion();

        return CompletableFuture.supplyAsync(() -> {
            try {
                HttpClient client = HttpClient.newHttpClient();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("https://api.github.com/repos/" + repository + "/releases/latest"))
                        .header("Accept", "application/vnd.github.v3+json")
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    JsonObject json = (JsonObject) new JsonParser().parse(response.body()).getAsJsonObject();
                    String latestVersion = json.get("tag_name").getAsString().replace("v", "");

                    return isOutdated(version, latestVersion);
                }
            } catch (Exception e) {
                plugin.getComponentLogger().error("Failed to check updates.");
                plugin.getComponentLogger().error(e.getMessage());
            }

            return false;
        });
    };

    public static void validateAndFixConfig(JavaPlugin plugin) {
        File oldConfigFile = new File(plugin.getDataFolder(), "config.yml");
        if (!oldConfigFile.exists()) {
            plugin.saveResource("config.yml", false);
            return;
        }

        FileConfiguration oldConfig = YamlConfiguration.loadConfiguration(oldConfigFile);

        InputStream newConfigStream = plugin.getResource("config.yml");
        if (newConfigStream == null) return;
        FileConfiguration newConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(newConfigStream));

        boolean needUpdate = false;

        for (String key : newConfig.getKeys(true)) {
            if (oldConfig.get(key) == null) {
                needUpdate = true;
                break;
            }
        }

        if (needUpdate) {
            plugin.getComponentLogger().info("Updating config...");
            String ts = new SimpleDateFormat("dd-MM-yyyy-HH-mm-ss").format(new Date());
            File backup = new File(plugin.getDataFolder(), "config." + ts + ".yml");

            try {
                oldConfig.save(backup);
            } catch (IOException e) {
                plugin.getComponentLogger().warn("Could not save old config to {}. Update config.yml manually", backup.getAbsolutePath());
                return;
            }

            plugin.saveResource("config.yml", true);
            FileConfiguration newConfig2 = YamlConfiguration.loadConfiguration(oldConfigFile);

            for (String key : oldConfig.getKeys(true)) {
                if (oldConfig.get(key) instanceof ConfigurationSection) continue;
                newConfig2.set(key, oldConfig.get(key));
            }
            try {
                newConfig2.save(oldConfigFile);
            } catch (IOException e) { plugin.getComponentLogger().warn("Could not save new config to {}. Update config.yml manually", oldConfigFile.getAbsolutePath()); }

            plugin.getComponentLogger().info("config.yml has been updated.");
        } else {
            plugin.getComponentLogger().info("config.yml is up to date!");
        }
    }
}
