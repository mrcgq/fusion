package ctbrec.recorder.download;

import ctbrec.Config;
import ctbrec.Model;
import ctbrec.Settings;
import ctbrec.UnknownModel;
import ctbrec.recorder.download.hls.*;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLong;

import static ctbrec.recorder.download.StreamSource.UNKNOWN;

@Slf4j
public abstract class AbstractDownload implements RecordingProcess {

    protected AtomicLong downloadedBytes = new AtomicLong();
    protected Instant startTime;
    protected Instant rescheduleTime = Instant.now();
    protected Model model = new UnknownModel();

    protected Config config;
    protected SplittingStrategy splittingStrategy;
    protected ExecutorService downloadExecutor;
    protected int selectedResolution = UNKNOWN;

    @Override
    public void init(Config config, Model model, Instant startTime, ExecutorService executorService) throws IOException {
        this.config = config;
        this.model = model;
        this.startTime = startTime;
        this.downloadExecutor = executorService;
        splittingStrategy = initSplittingStrategy(config.getSettings());
    }

    @Override
    public Instant getStartTime() {
        return startTime;
    }

    @Override
    public Instant getRescheduleTime() {
        return rescheduleTime;
    }

    protected SplittingStrategy initSplittingStrategy(Settings settings) {
        SplittingStrategy strategy;
        switch (settings.splitStrategy) {
            case TIME:
                strategy = new TimeSplittingStrategy();
                break;
            case SIZE:
                strategy = new SizeSplittingStrategy();
                break;
            case TIME_OR_SIZE:
                SplittingStrategy timeSplittingStrategy = new TimeSplittingStrategy();
                SplittingStrategy sizeSplittingStrategy = new SizeSplittingStrategy();
                strategy = new CombinedSplittingStrategy(timeSplittingStrategy, sizeSplittingStrategy);
                break;
            case DONT:
            default:
                strategy = new NoopSplittingStrategy();
                break;
        }
        strategy.init(settings);
        return strategy;
    }

    @Override
    public Model getModel() {
        return model;
    }

    @Override
    public int getSelectedResolution() {
        return selectedResolution;
    }

    @Override
    public void awaitEnd() {
        // do nothing per default
    }

    protected StreamSource selectStreamSource(List<StreamSource> streamSources) throws ExecutionException {
        if (model.getStreamUrlIndex() >= 0 && model.getStreamUrlIndex() < streamSources.size()) {
            // TODO don't use the index, but the bandwidth. if the bandwidth does not match, take the closest one
            log.debug("Model stream index: {}", model.getStreamUrlIndex());
            streamSources.forEach(ss -> log.debug(ss.toString()));
            StreamSource source = streamSources.get(model.getStreamUrlIndex());
            log.debug("{} selected {}", model.getName(), source);
            selectedResolution = source.getHeight();
            return source;
        } else {
            // filter out stream resolutions, which are out of range of the configured min and max
            List<StreamSource> filteredStreamSources = streamSources.stream()
                    .filter(this::isResolutionInLimits)
                    .filter(this::isBitRateInLimits)
                    .toList();
            if (filteredStreamSources.isEmpty()) {
                // TODO save, why a stream has been filtered out and convey this information to the UI, so that the user understands why a recording
                //  doesn't start
                throw new ExecutionException(new NoStreamFoundException("No stream left in playlist"));
            } else {
                StreamSource source = filteredStreamSources.get(filteredStreamSources.size() - 1);
                log.debug("{} selected {}", model.getName(), source);
                selectedResolution = source.getHeight();
                return source;
            }
        }
    }

    private boolean isResolutionInLimits(StreamSource src) {
        int minRes = Config.getInstance().getSettings().minimumResolution;
        int maxRes = Config.getInstance().getSettings().maximumResolution;
        boolean checkResolutionByMinSide = Config.getInstance().getSettings().checkResolutionByMinSide;
        int resolution = checkResolutionByMinSide ? Math.min(src.getHeight(), src.getWidth()) : src.getHeight();
        return resolution == 0 || resolution == UNKNOWN || (minRes <= resolution && maxRes >= resolution);
    }

    private boolean isBitRateInLimits(StreamSource src) {
        int bitrateLimit = Config.getInstance().getSettings().restrictBitrate * 1024;
        return bitrateLimit == 0
                || src.getBandwidth() == UNKNOWN
                || src.getBandwidth() <= bitrateLimit;
    }

    @Override
    public AtomicLong getDownloadedBytes() {
        return downloadedBytes;
    }
}
