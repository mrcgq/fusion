package ctbrec.recorder;

import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.IntStream;

@Slf4j
public class ThreadPoolScaler {
    private final ThreadPoolExecutor threadPool;
    private final int configuredPoolSize;

    private final int[] values = new int[20];
    private int index = -1;
    private Instant lastAdjustment = Instant.now();
    private Instant downScaleCoolDown = Instant.EPOCH;

    public ThreadPoolScaler(ThreadPoolExecutor threadPool, int configuredPoolSize) {
        this.threadPool = threadPool;
        this.configuredPoolSize = configuredPoolSize;
    }

    public void tick() {
        values[getNextIndex()] = threadPool.getActiveCount();
        adjustPoolSize();
    }

    private synchronized void adjustPoolSize() {
        Instant now = Instant.now();
        if (Duration.between(lastAdjustment, now).toMillis() > 500) {
            double average = calculateAverage();
            int coreSize = threadPool.getCorePoolSize();
            if (average > 0.65 * coreSize) {
                threadPool.setCorePoolSize(coreSize + 1);
                downScaleCoolDown = now.plusSeconds(30);
                if (log.isTraceEnabled()) {
                    log.trace("Adjusted scheduler pool size to {}", threadPool.getCorePoolSize());
                }
            } else if (average < 0.15 * coreSize) {
                int newValue = Math.max(configuredPoolSize, coreSize - 1);
                if (threadPool.getCorePoolSize() != newValue && now.isAfter(downScaleCoolDown)) {
                    threadPool.setCorePoolSize(newValue);
                    downScaleCoolDown = now.plusSeconds(10);
                    log.trace("Adjusted scheduler pool size to {}", threadPool.getCorePoolSize());
                }
            }
            lastAdjustment = now;
            if (log.isTraceEnabled()) {
                log.trace("Thread pool size is {}", threadPool.getCorePoolSize());
            }
        }
    }

    private double calculateAverage() {
        int sum = IntStream.of(values).sum();
        double average = sum / (double) values.length;
        return average;
    }

    private synchronized int getNextIndex() {
        index++;
        if (index == values.length) {
            index = 0;
        }
        return index;
    }
}
