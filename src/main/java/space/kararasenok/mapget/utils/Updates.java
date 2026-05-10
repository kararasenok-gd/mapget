package space.kararasenok.mapget.utils;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import space.kararasenok.mapget.Mapget;
import space.kararasenok.mapget.technical.UpdateResponse;

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Updates {
    // I stole this code from here: https://github.com/kararasenok-gd/NewDayNotifier/blob/master/src/main/java/space/kararasenok/newDayNotifier/Updates.java
    // Yeah, I'm too lazy to do something new. And why if I did it before?

    private static final String repository = "kararasenok-gd/mapget";

    private static boolean isOutdated(String current, String latest, boolean useBeta) {
        Pattern p = Pattern.compile("^v?(\\d+(?:\\.\\d+)*)(?:-beta\\.?(\\d+))?$");

        Matcher mCurrent = p.matcher(current);
        Matcher mLatest = p.matcher(latest);

        if (!mCurrent.find() || !mLatest.find()) return false;

        String baseCurrent = mCurrent.group(1);
        String baseLatest = mLatest.group(1);

        String betaCurrentStr = mCurrent.group(2);
        String betaLatestStr = mLatest.group(2);

        if (!useBeta && betaLatestStr != null) {
            return false;
        }

        String[] cParts = baseCurrent.split("\\.");
        String[] lParts = baseLatest.split("\\.");
        int length = Math.max(cParts.length, lParts.length);

        for (int i = 0; i < length; i++) {
            int cv = i < cParts.length ? Integer.parseInt(cParts[i]) : 0;
            int lv = i < lParts.length ? Integer.parseInt(lParts[i]) : 0;

            if (lv > cv) return true;
            if (lv < cv) return false;
        }
        
        int cb = (betaCurrentStr == null) ? Integer.MAX_VALUE : Integer.parseInt(betaCurrentStr);
        int lb = (betaLatestStr == null) ? Integer.MAX_VALUE : Integer.parseInt(betaLatestStr);

        return lb > cb;
    }

    public static CompletableFuture<UpdateResponse> checkUpdates(Mapget plugin, boolean useBeta) {
        String version = plugin.getDescription().getVersion();

        return CompletableFuture.supplyAsync(() -> {
            try {
                HttpClient client = HttpClient.newHttpClient();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("https://api.github.com/repos/" + repository + "/releases" + (useBeta ? "?per_page=1" : "/latest")))
                        .header("Accept", "application/vnd.github.v3+json")
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    String latestVersion = "";

                    if (!useBeta) {
                        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                        latestVersion = json.get("tag_name").getAsString().replace("v", "");
                    } else {
                        JsonArray jsonArr = JsonParser.parseString(response.body()).getAsJsonArray();
                        JsonObject json = jsonArr.get(0).getAsJsonObject();
                        latestVersion = json.get("tag_name").getAsString().replace("v", "");
                    }

                    return new UpdateResponse(isOutdated(version, latestVersion, useBeta), latestVersion);
                }
            } catch (Exception e) {
                plugin.getComponentLogger().error("Failed to check updates.");
                plugin.getComponentLogger().error(e.getMessage());
            }

            return new UpdateResponse(false, "");
        });
    };

    public static void validateAndFixConfig(Mapget plugin) {
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
            File fldr = new File(plugin.getDataFolder(), "backup");
            if (!fldr.exists()) {
                if (!fldr.mkdirs()) {
                    plugin.logger.warn("Failed to create folder for backups!");
                    fldr = plugin.getDataFolder();
                }
            }
            plugin.getComponentLogger().info("Updating config...");
            String ts = new SimpleDateFormat("dd-MM-yyyy-HH-mm-ss").format(new Date());
            File backup = new File(plugin.getDataFolder(), fldr.getName() + "/" + ts + ".yml");

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
