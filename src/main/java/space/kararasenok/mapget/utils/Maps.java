package space.kararasenok.mapget.utils;

import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import org.bukkit.Bukkit;
import org.bukkit.Input;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;
import org.bukkit.plugin.java.JavaPlugin;
import space.kararasenok.mapget.technical.StaticImageRenderer;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.sql.*;

public class Maps {
    private final JavaPlugin plugin;
    private final Connection conn;
    private final ComponentLogger logger;
    private final File folder;

    public Maps(JavaPlugin plugin, Connection conn, ComponentLogger logger, File folder) {
        this.plugin = plugin;
        this.conn = conn;
        this.logger = logger;
        this.folder = folder;
    }

    public int loadSaved() {
        int loaded = 0;
        logger.info("Starting to load maps from database...");

        try (Statement s = conn.createStatement(); ResultSet rs = s.executeQuery("SELECT * FROM maps")) {
            while (rs.next()) {
                int id = rs.getInt("map_id");
                String path = rs.getString("local_path");

                MapView view = Bukkit.getServer().getMap(id);
                if (view == null) {
                    logger.warn("Map ID {} not found by Bukkit! (Maybe world not loaded?)", id);
                    continue;
                }

                File imgFile = new File(folder, path);
                if (!imgFile.exists()) {
                    logger.warn("Image file not found for map {}: {}", id, imgFile.getAbsolutePath());
                    continue;
                }

                try {
                    BufferedImage image = ImageIO.read(imgFile);
                    if (image == null) {
                        logger.error("Failed to read image for map {}: File is corrupted or not an image.", id);
                        continue;
                    }
                    applyRenderer(view, image);
                    loaded++;
                } catch (IOException e) {
                    logger.error("IOException while loading map {}: {}", id, e.getMessage());
                }
            }
        } catch (Exception e) {
            logger.error("Database error in loadSaved: {}", e.getMessage());
            e.printStackTrace();
        }

        return loaded;
    }

    public void createMap(String url, boolean crop, boolean temp, Player player) {
        boolean useDatabase = !plugin.getConfig().getBoolean("map.temp", false);
        try {
            int TIMEOUT = plugin.getConfig().getInt("connection.timeout", 5000);
            int READ_TIMEOUT = plugin.getConfig().getInt("connection.readTimeout", 10000);
            int MAX_SIZE_BYTES = plugin.getConfig().getInt("connection.maxSize", 8) * 1024 * 1024;

            URL u = URI.create(url).toURL();
            HttpURLConnection connect = (HttpURLConnection) u.openConnection();
            connect.setRequestProperty("User-Agent", "Mozilla/5.0");
            connect.setConnectTimeout(TIMEOUT);
            connect.setReadTimeout(READ_TIMEOUT);

            int contentLength = connect.getContentLength();
            if (contentLength > MAX_SIZE_BYTES) {
                player.sendMessage(MiniMessage.deserialize("<red>Image is too big (" + Math.round((double) contentLength / 1024 / 1024 * 100.0) / 100.0 + " MB > " + Math.round((double) MAX_SIZE_BYTES / 1024 / 1024 * 100.0) / 100.0 + "MB)."));
            }

            InputStream inputStream = connect.getInputStream();
            BufferedImage image = ImageIO.read(inputStream);
            if (image == null) {
                player.sendMessage(MiniMessage.deserialize("<red>Failed to create map: Invalid message"));
                return;
            }

            int MAP_SIZE = 128;
            BufferedImage mapImg = new BufferedImage(MAP_SIZE, MAP_SIZE, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = mapImg.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

            int x = 0, y = 0, w = image.getWidth(), h = image.getHeight();
            if (crop) {
                int minSide = Math.min(w, h);
                x = (w - minSide) / 2;
                y = (h - minSide) / 2;
                w = h = minSide;
            }

            g.drawImage(image, 0, 0, MAP_SIZE, MAP_SIZE, x, y, x + w, y + h, null);
            g.dispose();

            Bukkit.getScheduler().runTask(plugin, () -> {
                MapView view = Bukkit.createMap(player.getWorld());
                view.setTrackingPosition(false);
                view.setUnlimitedTracking(false);
                view.getRenderers().forEach(view::removeRenderer);

                int mapId = view.getId();

                String mapLocalImgFileName = "map_" + mapId + ".png";
                File outImg = new File(folder, "images/" + mapLocalImgFileName);
                try {
                    ImageIO.write(mapImg, "PNG", outImg);

                    if (useDatabase && !temp) {
                        try (PreparedStatement pstmt = conn.prepareStatement("INSERT OR REPLACE INTO maps (creator, map_id, local_path, url) VALUES (?, ?, ?, ?)")) {
                            pstmt.setString(1, player.getUniqueId().toString());
                            pstmt.setInt(2, mapId);
                            pstmt.setString(3, "images/" + mapLocalImgFileName);
                            pstmt.setString(4, url);
                            pstmt.executeUpdate();
                        }
                    }

                    applyRenderer(view, mapImg);

                    ItemStack mapItem = new ItemStack(Material.FILLED_MAP);
                    MapMeta mapMeta = (MapMeta) mapItem.getItemMeta();
                    if (mapMeta == null) {
                        player.sendMessage(MiniMessage.deserialize("<red>Failed to create map: Metadata is empty"));
                        return;
                    };
                    mapMeta.setMapView(view);

                    String mapName = plugin.getConfig().getString("map.name", "§6Image #%id%");
                    mapMeta.setDisplayName(mapName.replace("%id%", String.valueOf(mapId)));
                    mapItem.setItemMeta(mapMeta);

                    player.getInventory().addItem(mapItem);
                    player.sendMessage(MiniMessage.deserialize("<green>Map successfully created!"));
                } catch (IOException e) {
                    player.sendMessage(MiniMessage.deserialize("<red>Failed to create map: IOException: " + e.getMessage()));
                } catch (SQLException e) {
                    player.sendMessage(MiniMessage.deserialize("<red>Failed to create map: SQLException: " + e.getMessage()));
                }
            });

        } catch (MalformedURLException e) {
            logger.error("Malformed URL: {}. Error: {}", url, e.getMessage());
            player.sendMessage(MiniMessage.deserialize("<red>Failed to create map: MalformedURLException: " + e.getMessage()));
        } catch (IOException e) {
            logger.error("Failed to load URL: {}. Error: {}", url, e.getMessage());
            player.sendMessage(MiniMessage.deserialize("<red>Failed to load map: IOException: " + e.getMessage()));
        }
    }

    private static void applyRenderer(MapView view, BufferedImage img) {
        view.setTrackingPosition(false);
        if (view.getClass().getDeclaredMethods().toString().contains("setLocked")) {
            view.setLocked(true);
        }

        view.getRenderers().forEach(view::removeRenderer);
        view.addRenderer(new StaticImageRenderer(img));
    }
}