package space.kararasenok.mapget;

import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import space.kararasenok.mapget.commands.handlers.MapHandler;
import space.kararasenok.mapget.utils.Maps;
import space.kararasenok.mapget.utils.Updates;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;

public final class Mapget extends JavaPlugin {
    private Connection conn;
    public final ComponentLogger logger = getComponentLogger();

    @Override
    public void onEnable() {
//        logger.info("Loading plugin...");
        long startTime = Instant.now().toEpochMilli();
        List<Integer> supportedDataVersions = List.of(4671, 4556, 4790);

        saveDefaultConfig();

        try {
            File db = new File(getDataFolder(), "maps.db");
            this.conn = DriverManager.getConnection("jdbc:sqlite:" + db.getAbsolutePath());
            try (Statement s = conn.createStatement()) {
                s.execute("CREATE TABLE IF NOT EXISTS maps(" +
                        "creator TEXT NOT NULL," +
                        "map_id INTEGER PRIMARY KEY," +
                        "local_path TEXT NOT NULL," +
                        "url TEXT NOT NULL)"
                );
            }
        } catch (SQLException e) {
            logger.error("Failed to connect to database! Error: {}", e.getMessage());
            return;
        }

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            final Commands commands = event.registrar();

            commands.register(
                    "map",
                    "MapGet command. That all i can say.",
                    new MapHandler(this, conn)
            );
        });

        File fld = new File(getDataFolder(), "images");
        if (!fld.exists()) {
            logger.info("'images' folder does not exist, creating it...");
            if (fld.mkdirs()) {
                logger.info("Folder created!");
            } else {
                logger.warn("Failed to create folder!");
            }
        }

        if (!getConfig().getBoolean("map.temp", false)) {
            Bukkit.getScheduler().runTaskLater(this, () -> {
                logger.info("Restoring images...");
                long restoreStart = Instant.now().toEpochMilli();
                int loaded = new Maps(this, conn, logger, getDataFolder()).loadSaved();
                logger.info("Successfully loaded {} images in {} ms!", loaded, Instant.now().toEpochMilli() - restoreStart);
            }, 20L);
        }

        logger.info("Validating config.yml...");
        Updates.validateAndFixConfig(this);

        if (getConfig().getBoolean("updates.check", true)) {
            logger.info("Checking for updates...");
            Updates.checkUpdates(this, getConfig().getBoolean("updates.beta", false)).thenAccept(out -> {
                if (out.hasUpdates()) {
                    logger.info("New update is available!");
                    logger.info("Download it here: https://github.com/kararasenok-gd/mapget/releases/tag/v{}", out.tag());
                } else {
                    logger.info("Everything is fine. MapGet is running on latest version. Yippee :3");
                };
            });
        }

        int serverVersion = Bukkit.getUnsafe().getDataVersion();
        if (!supportedDataVersions.contains(serverVersion)) {
            String serverVersionToDisplay = getServer().getMinecraftVersion();

            logger.warn("Version {} has not been tested. You can continue using the plugin, but be prepared for it to have bugs or not work at all.",  serverVersionToDisplay);
            logger.warn("If you encounter a problem, please report it here: https://github.com/kararasenok-gd/mapget/issues/");
        }

        long enableTime = Instant.now().toEpochMilli() - startTime;
        logger.info("Plugin enabled successfully in {} ms!", enableTime);
    }

    @Override
    public void onDisable() {
//        logger.info("Disabling...");
        long start =  Instant.now().toEpochMilli();
        try {
            if (conn != null && !conn.isClosed()) {
                conn.close();
                logger.info("Connection to database closed");
            }
        } catch (SQLException e) {
            logger.error("Failed to close connection to database! Error: {}", e.getMessage());
        }

        logger.info("Plugin disabled in {} ms!", Instant.now().toEpochMilli() - start);
    }
}
