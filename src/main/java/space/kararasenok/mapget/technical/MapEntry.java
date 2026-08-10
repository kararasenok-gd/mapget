package space.kararasenok.mapget.technical;

public record MapEntry(
        String creator,
        int mapId,
        String url,
        String localPath,
        String hash
) {}
