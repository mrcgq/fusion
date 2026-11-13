package ctbrec.image;

import ctbrec.Config;
import ctbrec.StringUtil;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Optional;

@Slf4j
public record LocalPortraitStore(Config config) implements PortraitStore {

    @Override
    public Optional<byte[]> loadModelPortraitByModelUrl(String modelUrl) {
        String portraitId = config.getSettings().modelPortraits.get(modelUrl);
        return loadModelPortraitById(portraitId);
    }

    @Override
    public Optional<byte[]> loadModelPortraitById(String portraitId) {
        if (StringUtil.isNotBlank(portraitId)) {
            File configDir = config.getConfigDir();
            File portraitDir = new File(configDir, "portraits");
            File portraitFile = new File(portraitDir, portraitId + '.' + FORMAT);
            try {
                return Optional.of(Files.readAllBytes(portraitFile.toPath()));
            } catch (IOException e) {
                log.error("Couldn't load portrait file {}", portraitFile, e);
            }
        }
        return Optional.empty();
    }

    @Override
    public void writePortrait(String modelUrl, byte[] data) throws IOException {
        String portraitId = config.getSettings().modelPortraits.getOrDefault(modelUrl, idForModelUrl(modelUrl));
        File portraitFile = getPortraitFile(portraitId);
        Files.write(portraitFile.toPath(), data);
        config.getSettings().modelPortraits.put(modelUrl, portraitId);
        config.save();
    }

    @Override
    public void removePortrait(String modelUrl) throws IOException {
        String portraitId = config.getSettings().modelPortraits.get(modelUrl);
        File portraitFile = getPortraitFile(portraitId);
        Files.delete(portraitFile.toPath());
        config.getSettings().modelPortraits.remove(modelUrl);
        config.save();
    }

    private File getPortraitFile(String portraitId) throws IOException {
        File configDir = config.getConfigDir();
        File portraitDir = new File(configDir, "portraits");
        Files.createDirectories(portraitDir.toPath());
        return new File(portraitDir, portraitId + '.' + FORMAT);
    }
}
