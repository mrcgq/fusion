package ctbrec.image;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

import static java.nio.charset.StandardCharsets.UTF_8;

public interface PortraitStore {

    String FORMAT = "jpg";

    Optional<byte[]> loadModelPortraitById(String id) throws IOException;

    Optional<byte[]> loadModelPortraitByModelUrl(String modelUrl) throws IOException;

    //void writePortrait(String id, byte[] data) throws IOException;
    void writePortrait(String modelUrl, byte[] data) throws IOException;

    void removePortrait(String modelUrl) throws IOException;

    default String idForModelUrl(String modelUrl) {
        return UUID.nameUUIDFromBytes(modelUrl.getBytes(UTF_8)).toString();
    }
}
