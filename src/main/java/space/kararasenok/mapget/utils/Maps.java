package space.kararasenok.mapget.utils;

import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;
import org.bukkit.plugin.java.JavaPlugin;
import space.kararasenok.mapget.technical.MapEntry;
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
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
        int TIMEOUT = plugin.getConfig().getInt("connection.timeout", 5000);
        int READ_TIMEOUT = plugin.getConfig().getInt("connection.readTimeout", 10000);
        int MAX_SIZE_BYTES = plugin.getConfig().getInt("connection.maxSize", 8) * 1024 * 1024;
        UUID playerId = player.getUniqueId();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                BufferedImage image = downloadImage(url, TIMEOUT, READ_TIMEOUT, MAX_SIZE_BYTES);
                if (image == null) {
                    sendPlayerMessage(playerId, "<red>Failed to create map: Invalid image.");
                    return;
                }

                BufferedImage mapImg = processImage(image, crop);

                Bukkit.getScheduler().runTask(plugin, () -> {
                    Player currentPlayer = Bukkit.getPlayer(playerId);
                    if (currentPlayer == null || !currentPlayer.isOnline()) {
                        return;
                    }

                    MapView view = Bukkit.createMap(currentPlayer.getWorld());
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
                                pstmt.setString(1, playerId.toString());
                                pstmt.setInt(2, mapId);
                                pstmt.setString(3, "images/" + mapLocalImgFileName);
                                pstmt.setString(4, url);
                                pstmt.executeUpdate();
                            }
                        }

                        applyRenderer(view, mapImg);

                        ItemStack mapItem = getMapItem(view, currentPlayer, mapId, url);

                        if (mapItem == null) {
                            currentPlayer.sendMessage(MiniMessage.deserialize("<red>Failed to create map (maybe MapMeta is null)!"));
                            return;
                        }

                        currentPlayer.getInventory().addItem(mapItem);
                        currentPlayer.sendMessage(MiniMessage.deserialize("<green>Map successfully created!"));
                    } catch (IOException e) {
                        currentPlayer.sendMessage(MiniMessage.deserialize("<red>Failed to create map: IOException: " + e.getMessage()));
                    } catch (SQLException e) {
                        currentPlayer.sendMessage(MiniMessage.deserialize("<red>Failed to create map: SQLException: " + e.getMessage()));
                    }
                });

            } catch (MalformedURLException | IllegalArgumentException e) {
                logger.error("Malformed URL: {}. Error: {}", url, e.getMessage());
                sendPlayerMessage(playerId, "<red>Failed to create map: Invalid URL.");
            } catch (ImageTooLargeException e) {
                sendPlayerMessage(playerId, e.getMessage());
            } catch (IOException e) {
                logger.error("Failed to load URL: {}. Error: {}", url, e.getMessage());
                sendPlayerMessage(playerId, "<red>Failed to load map: IOException: " + e.getMessage());
            }
        });
    }

    private BufferedImage processImage(BufferedImage image, boolean crop) {
        int mapSize = 128;
        BufferedImage mapImage = new BufferedImage(mapSize, mapSize, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = mapImage.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        int x = 0;
        int y = 0;
        int width = image.getWidth();
        int height = image.getHeight();
        if (crop) {
            int minSide = Math.min(width, height);
            x = (width - minSide) / 2;
            y = (height - minSide) / 2;
            width = height = minSide;
        }

        graphics.drawImage(image, 0, 0, mapSize, mapSize, x, y, x + width, y + height, null);
        graphics.dispose();
        return mapImage;
    }

    private BufferedImage downloadImage(String url, int timeout, int readTimeout, int maxSizeBytes)
            throws IOException {
        URL imageUrl = URI.create(url).toURL();
        HttpURLConnection connection = (HttpURLConnection) imageUrl.openConnection();
        connection.setRequestProperty("User-Agent", "Mozilla/5.0");
        connection.setConnectTimeout(timeout);
        connection.setReadTimeout(readTimeout);

        try {
            int contentLength = connection.getContentLength();
            if (contentLength > maxSizeBytes) {
                throw new ImageTooLargeException(
                        "<red>Image is too big (" + Math.round((double) contentLength / 1024 / 1024 * 100.0) / 100.0
                                + " MB > " + Math.round((double) maxSizeBytes / 1024 / 1024 * 100.0) / 100.0 + " MB).");
            }

            try (InputStream inputStream = connection.getInputStream()) {
                return ImageIO.read(inputStream);
            }
        } finally {
            connection.disconnect();
        }
    }

    private static class ImageTooLargeException extends IOException {
        private ImageTooLargeException(String message) {
            super(message);
        }
    }

    private void sendPlayerMessage(UUID playerId, String message) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                player.sendMessage(MiniMessage.deserialize(message));
            }
        });
    }

    public void deleteMap(int mapId) {
        MapView mapView = Bukkit.getMap(mapId);
        if (mapView != null) {
            mapView.getRenderers().forEach(mapView::removeRenderer);
        }

        try (PreparedStatement pstmt = conn.prepareStatement("DELETE FROM maps WHERE map_id = ?")) {
            pstmt.setInt(1, mapId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Failed to delete map: SQLException: {}", e.getMessage());
        }
    }

    public ItemStack getMapItem(MapView view, Player player, int mapId, String url) {
        ItemStack mapItem = new ItemStack(Material.FILLED_MAP);
        MapMeta mapMeta = (MapMeta) mapItem.getItemMeta();
        if (mapMeta == null) {
            return null;
        };
        mapMeta.setMapView(view);

        String mapName = plugin.getConfig().getString("map.name", "§6Image #%id%");
        String mapLore = plugin.getConfig().getString("map.lore", "§7Created by §b%player% §7at §b%timestamp%");
        String utcOffset = plugin.getConfig().getString("timestamp.utcOffset", "+00:00");
        String timestampFormat =  plugin.getConfig().getString("timestamp.format", "yyyy-MM-dd HH:mm:ssXXX");
        String timestamp = OffsetDateTime.now(ZoneOffset.of(utcOffset)).format(DateTimeFormatter.ofPattern(timestampFormat));

        Map<String, String> mapLoreMap = Map.of(
                "%id%", String.valueOf(mapId),
                "%player%", player.getName(),
                "%url%", url,
                "%timestamp%", timestamp
        );

        for (Map.Entry<String, String> entry : mapLoreMap.entrySet()) {
            mapLore =  mapLore.replace(entry.getKey(), entry.getValue());
        }

        mapMeta.setDisplayName(mapName.replace("%id%", String.valueOf(mapId)));
        mapMeta.setLore(List.of(mapLore.split("\n")));
        mapItem.setItemMeta(mapMeta);

        return mapItem;
    }

    public MapEntry getMapData(int mapId) {
        try (PreparedStatement pstmt = conn.prepareStatement("SELECT * FROM maps WHERE map_id = ?")) {
            pstmt.setInt(1, mapId);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (!rs.next()) { return null; }
                return new MapEntry(rs.getString("creator"), rs.getInt("map_id"), rs.getString("url"), rs.getString("local_path"));
            }
        } catch (SQLException e) {
            logger.error("Database error in getMapData: {}", e.getMessage());
        }
        return null;
    }

    public List<MapEntry> getMapsList(Player player) {
        String uuid = player.getUniqueId().toString();
        List<MapEntry> result = new ArrayList<>();

        try (PreparedStatement pstmt = conn.prepareStatement("SELECT * FROM maps WHERE creator = ?")) {
            pstmt.setString(1, uuid);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    result.add(new MapEntry(rs.getString("creator"), rs.getInt("map_id"), rs.getString("url"), rs.getString("local_path")));
                }
            }
        } catch (SQLException e) {
            logger.error("Database error in getMapsList: {}", e.getMessage());
        }

        return result;
    }

    public boolean checkIfPlayerOwnsMap(Player player, int mapId) {
        String uuid = player.getUniqueId().toString();

        try (PreparedStatement pstmt = conn.prepareStatement("SELECT map_id FROM maps WHERE map_id = ? AND creator = ?")) {
            pstmt.setInt(1, mapId);
            pstmt.setString(2, uuid);

            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            logger.error("Database error in giveMap: {}", e.getMessage());
            return false;
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
