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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.*;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;

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

                    if (plugin.getConfig().getBoolean("map.tryToRecover", true)) {
                        logger.info("Trying to recover map {}...", id);
                        String imageUrl = rs.getString("url");
                        String savedParams = rs.getString("params");
                        ArrayList<String> params = savedParams == null || savedParams.isBlank()
                                ? new ArrayList<>()
                                : new ArrayList<>(Arrays.asList(savedParams.split(" ")));
                        int TIMEOUT = plugin.getConfig().getInt("connection.timeout", 5000);
                        int READ_TIMEOUT = plugin.getConfig().getInt("connection.readTimeout", 10000);
                        int MAX_SIZE_BYTES = plugin.getConfig().getInt("connection.maxSize", 8) * 1024 * 1024;

                        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                            BufferedImage img;
                            try {
                                img = downloadImage(
                                        imageUrl,
                                        TIMEOUT,
                                        READ_TIMEOUT,
                                        MAX_SIZE_BYTES
                                );
                            } catch (IOException e) {
                                logger.error("Failed to download image for map {}", id, e);
                                return;
                            }

                            if (img == null) {
                                logger.error("Failed to recover map {}: URL does not contain a valid image", id);
                                return;
                            }

                            boolean crop = params.stream().anyMatch("crop:true"::equals);
                            int[] size = parseGridSize(params);
                            int tile = parseTileIndex(params);
                            List<BufferedImage> tiles = processImage(img, crop, size[0], size[1]);
                            if (tile < 0 || tile >= tiles.size()) {
                                logger.error("Failed to recover map {}: invalid tile index", id);
                                return;
                            }
                            BufferedImage mapImg = tiles.get(tile);

                            try {
                                if (!ImageIO.write(mapImg, "PNG", imgFile)) {
                                    logger.error("Failed to recover map {}: PNG writer is unavailable", id);
                                    return;
                                }

                                Bukkit.getScheduler().runTask(plugin, () -> applyRenderer(view, mapImg));
                            } catch (IOException e) {
                                logger.error("Failed to write image for map {}", id, e);
                            }
                        });
                    }

                    loaded++;
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

    public void createMap(String url, boolean crop, boolean temp, int sizeX, int sizeY, Player player) {
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

                List<BufferedImage> mapImages = processImage(image, crop, sizeX, sizeY);

                Bukkit.getScheduler().runTask(plugin, () -> {
                    Player currentPlayer = Bukkit.getPlayer(playerId);
                    if (currentPlayer == null || !currentPlayer.isOnline()) {
                        return;
                    }

                    try {
                        for (int tile = 0; tile < mapImages.size(); tile++) {
                            BufferedImage mapImg = mapImages.get(tile);
                            String imgHash = this.getImgHash(mapImg);
                            int hashMapId = checkHash(imgHash);
                            MapView view = Bukkit.createMap(currentPlayer.getWorld());
                            view.setTrackingPosition(false);
                            view.setUnlimitedTracking(false);
                            view.getRenderers().forEach(view::removeRenderer);
                            int mapId = view.getId();
                            String mapLocalImgFileName = "map_" + (hashMapId == -1 ? mapId : hashMapId) + ".png";
                            File outImg = new File(folder, "images/" + mapLocalImgFileName);

                            if (!outImg.exists() && !ImageIO.write(mapImg, "PNG", outImg)) {
                                throw new IOException("PNG writer is unavailable");
                            }

                            if (useDatabase && !temp) {
                                try (PreparedStatement pstmt = conn.prepareStatement("INSERT OR REPLACE INTO maps (creator, map_id, local_path, url, hash, params) VALUES (?, ?, ?, ?, ?, ?)")) {
                                    pstmt.setString(1, playerId.toString());
                                    pstmt.setInt(2, mapId);
                                    pstmt.setString(3, "images/" + mapLocalImgFileName);
                                    pstmt.setString(4, url);
                                    pstmt.setString(5, imgHash);
                                    ArrayList<String> params = new ArrayList<>();
                                    if (crop) params.add("crop:true");
                                    params.add("size:" + sizeX + "x" + sizeY);
                                    params.add("tile:" + tile);
                                    pstmt.setString(6, String.join(" ", params));
                                    pstmt.executeUpdate();
                                }
                            }

                            applyRenderer(view, mapImg);
                            ItemStack mapItem = getMapItem(view, currentPlayer, mapId, url);
                            if (mapItem == null) {
                                throw new IOException("MapMeta is null");
                            }
                            currentPlayer.getInventory().addItem(mapItem);
                        }
                        currentPlayer.sendMessage(MiniMessage.deserialize("<green>" + mapImages.size() + " map(s) successfully created!"));
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

    private List<BufferedImage> processImage(BufferedImage image, boolean crop, int sizeX, int sizeY) {
        int mapSize = 128;
        int canvasWidth = mapSize * sizeX;
        int canvasHeight = mapSize * sizeY;
        BufferedImage canvas = new BufferedImage(canvasWidth, canvasHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = canvas.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        int x = 0;
        int y = 0;
        int width = image.getWidth();
        int height = image.getHeight();
        if (crop) {
            double targetRatio = (double) canvasWidth / canvasHeight;
            if ((double) width / height > targetRatio) {
                int croppedWidth = (int) (height * targetRatio);
                x = (width - croppedWidth) / 2;
                width = croppedWidth;
            } else {
                int croppedHeight = (int) (width / targetRatio);
                y = (height - croppedHeight) / 2;
                height = croppedHeight;
            }
        }

        graphics.drawImage(image, 0, 0, canvasWidth, canvasHeight, x, y, x + width, y + height, null);
        graphics.dispose();
        List<BufferedImage> tiles = new ArrayList<>(sizeX * sizeY);
        for (int tileY = 0; tileY < sizeY; tileY++) {
            for (int tileX = 0; tileX < sizeX; tileX++) {
                tiles.add(canvas.getSubimage(tileX * mapSize, tileY * mapSize, mapSize, mapSize));
            }
        }
        return tiles;
    }

    private int[] parseGridSize(List<String> params) {
        for (String param : params) {
            if (!param.startsWith("size:")) continue;
            String[] dimensions = param.substring(5).split("x", -1);
            if (dimensions.length == 2) {
                try {
                    return new int[]{Integer.parseInt(dimensions[0]), Integer.parseInt(dimensions[1])};
                } catch (NumberFormatException ignored) {
                    break;
                }
            }
        }
        return new int[]{1, 1};
    }

    private int parseTileIndex(List<String> params) {
        for (String param : params) {
            if (!param.startsWith("tile:")) continue;
            try {
                return Integer.parseInt(param.substring(5));
            } catch (NumberFormatException ignored) {
                return -1;
            }
        }
        return 0;
    }

    private BufferedImage downloadImage(String url, int timeout, int readTimeout, int maxSizeBytes)
            throws IOException {
        URI imageUri = URI.create(url);
        String[] allowedDomains = plugin.getConfig().getStringList("connection.whitelist.domains").toArray(new String[0]);
        boolean actAsBlacklist = plugin.getConfig().getBoolean("connection.whitelist.actAsBlacklist");

        if (Arrays.stream(allowedDomains).noneMatch("*"::equals)) {
            String domain = imageUri.getAuthority();
            if (!actAsBlacklist) {
                if (Arrays.stream(allowedDomains).noneMatch(domain::equals)) {
                    throw new IOException("Domain not allowed: " + domain);
                }
            } else {
                if (Arrays.asList(allowedDomains).contains(domain)) {
                    throw new IOException("Domain not allowed: " + domain);
                }
            }
        }

        URL imageUrl = imageUri.toURL();
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

        MapEntry mapData = this.getMapData(mapId);
        if (mapData == null) {
            return;
        }

        try (PreparedStatement pstmt = conn.prepareStatement("DELETE FROM maps WHERE map_id = ?")) {
            pstmt.setInt(1, mapId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Failed to delete map: SQLException: {}", e.getMessage());
        }

        File mapFile = new File(this.plugin.getDataFolder(), mapData.localPath());
        boolean fileExsistDB = false;

        try (PreparedStatement pstmt = conn.prepareStatement("SELECT 1 FROM maps WHERE local_path = ?")) {
            pstmt.setString(1, mapData.localPath());
            if (pstmt.executeQuery().next()) fileExsistDB = true;
        } catch (SQLException e) {
            logger.error("Failed to check local path existence: SQLException: {}", e.getMessage());
        }

        if (mapFile.exists() && !fileExsistDB) {
            if (!mapFile.delete()) {
                logger.warn("Failed to delete map file: {}", mapData.localPath());
            }
        } else {
            logger.warn("Map file {} does not exist or it is still in database. Skipping deletion...", mapData.localPath());
        }
    }

    public ItemStack getMapItem(MapView view, Player player, int mapId, String url) {
        ItemStack mapItem = new ItemStack(Material.FILLED_MAP);
        MapMeta mapMeta = (MapMeta) mapItem.getItemMeta();
        if (mapMeta == null) {
            return null;
        }
        mapMeta.setMapView(view);

        String mapName = plugin.getConfig().getString("map.name", "<gold>Image #%id%");
        String mapLore = plugin.getConfig().getString("map.lore", "<gray>Created by <aqua>%player%</aqua> at <aqua>%timestamp%</aqua>");
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

        mapMeta.displayName(
                MiniMessage.deserialize(
                        mapName.replace(
                                "%id%",
                                String.valueOf(mapId)
                        )
                )
        );
        mapMeta.lore(
                Arrays.stream(mapLore.split("\n"))
                        .map(MiniMessage::deserialize)
                        .toList()
        );
        mapItem.setItemMeta(mapMeta);

        return mapItem;
    }

    public MapEntry getMapData(int mapId) {
        try (PreparedStatement pstmt = conn.prepareStatement("SELECT * FROM maps WHERE map_id = ?")) {
            pstmt.setInt(1, mapId);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (!rs.next()) { return null; }
                return new MapEntry(rs.getString("creator"), rs.getInt("map_id"), rs.getString("url"), rs.getString("local_path"), rs.getString("hash"), rs.getString("params"));
            }
        } catch (SQLException e) {
            logger.error("Database error in getMapData: {}", e.getMessage());
        }
        return null;
    }

    private String getImgHash(BufferedImage image) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            int[] pixels = image.getRGB(
                    0, 0,
                    image.getWidth(), image.getHeight(),
                    null, 0, image.getWidth()
            );

            for (int argb : pixels) {
                digest.update((byte) (argb >>> 24));
                digest.update((byte) (argb >>> 16));
                digest.update((byte) (argb >>> 8));
                digest.update((byte) argb);
            }

            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private int checkHash(String hash) {
        try (PreparedStatement pstmt = conn.prepareStatement("SELECT map_id FROM maps WHERE hash = ?")) {
            pstmt.setString(1, hash);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("map_id");
                }
            }
        } catch (SQLException e) {
            logger.error("Database error in checkHash: {}", e.getMessage());
        }
        return -1;
    }

    // why...

    public record MapListResult(
            List<MapEntry> entries,
            int pages
    ) {}

    public MapListResult getMapsList(Player player) {
        return getMapsList(player, 10, 0);
    }

    public MapListResult getMapsList(Player player, int limit) {
        return getMapsList(player, limit, 0);
    }

    public MapListResult getMapsList(Player player, int limit, int offset) {
        String uuid = player.getUniqueId().toString();
        List<MapEntry> result = new ArrayList<>();

        int allPages = 0;

        try (PreparedStatement pstmt = conn.prepareStatement("SELECT COUNT(*) FROM maps WHERE creator = ?")) {
            pstmt.setString(1, uuid);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    allPages = (rs.getInt(1) + limit - 1) / limit;
                }
            }
        } catch (SQLException e) {
            logger.error("Database error while counting maps in getMapsList: {}", e.getMessage());
        }

        try (PreparedStatement pstmt = conn.prepareStatement("SELECT * FROM maps WHERE creator = ? LIMIT ? OFFSET ?")) {
            pstmt.setString(1, uuid);
            pstmt.setInt(2, limit);
            pstmt.setInt(3, offset);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    result.add(new MapEntry(rs.getString("creator"), rs.getInt("map_id"), rs.getString("url"), rs.getString("local_path"), rs.getString("hash"), rs.getString("params")));
                }
            }
        } catch (SQLException e) {
            logger.error("Database error in getMapsList: {}", e.getMessage());
        }

        return new MapListResult(
                result,
                allPages
        );
    }



    public boolean checkIfPlayerOwnsMap(Player player, int mapId) {
        if (player.hasPermission("mapget.bypass.ownership")) return true;
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
        if (Arrays.toString(view.getClass().getDeclaredMethods()).contains("setLocked")) {
            view.setLocked(true);
        }

        view.getRenderers().forEach(view::removeRenderer);
        view.addRenderer(new StaticImageRenderer(img));
    }
}
