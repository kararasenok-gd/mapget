package space.kararasenok.mapget;

import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import space.kararasenok.mapget.commands.handlers.MapHandler;
import space.kararasenok.mapget.technical.DatabaseColumn;
import space.kararasenok.mapget.utils.Database;
import space.kararasenok.mapget.utils.Maps;
import space.kararasenok.mapget.utils.Updates;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

public final class Mapget extends JavaPlugin {
    private Connection conn;
    public final ComponentLogger logger = getComponentLogger();

    @Override
    public void onEnable() {
//        logger.info("Loading plugin...");
        long startTime = Instant.now().toEpochMilli();
        List<Integer> supportedDataVersions = IntStream.rangeClosed(3953, 4903).boxed().toList();

        saveDefaultConfig();

        try {
            File db = new File(getDataFolder(), "maps.db");
            boolean databaseExisted = db.isFile();
            this.conn = DriverManager.getConnection("jdbc:sqlite:" + db.getAbsolutePath());
            Database database = new Database(this, conn);
            Map<String, List<DatabaseColumn>> schema = Map.of(
                    "maps", List.of(
                            new DatabaseColumn("creator", "TEXT", "NOT NULL"),
                            new DatabaseColumn("map_id", "INTEGER", "PRIMARY KEY"),
                            new DatabaseColumn("local_path", "TEXT", "NOT NULL"),
                            new DatabaseColumn("url", "TEXT", "NOT NULL"),
                            new DatabaseColumn("hash", "TEXT")
                    )
            );

            if (databaseExisted && database.requiresSynchronization(schema) && !backupDatabase(db)) {
                logger.error("Database schema migration cancelled because the backup could not be created.");
                conn.close();
                return;
            }

            if (!database.synchronizeSchema(schema)) {
                logger.error("Database schema migration failed.");
                conn.close();
                return;
            }
        } catch (SQLException e) {
            logger.error("Failed to connect to database! Error: {}", e.getMessage());
            return;
        }

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            final Commands commands = event.registrar();

            commands.register(
                    "map",
                    "mapget is a plugin that allows you to convert images into maps. For more information, see /map help.",
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

    private boolean backupDatabase(File database) {
        File backupFolder = new File(getDataFolder(), "backup");
        if (!backupFolder.exists() && !backupFolder.mkdirs()) {
            logger.warn("Failed to create folder for backups. Saving the database backup in the plugin folder.");
            backupFolder = getDataFolder();
        }

        String timestamp = new SimpleDateFormat("dd-MM-yyyy-HH-mm-ss").format(new Date());
        File backup = new File(backupFolder, timestamp + "-maps.db");
        try {
            Files.copy(database.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
            logger.info("Created database backup at {}", backup.getAbsolutePath());
            return true;
        } catch (IOException e) {
            logger.warn("Could not back up database to {}: {}", backup.getAbsolutePath(), e.getMessage());
            return false;
        }
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
