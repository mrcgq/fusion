package ctbrec.io;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class BandwidthMeter {

    private static long[] records = new long[10000];
    private static int head = 0;
    private static int tail = 0;
    private static List<Listener> listeners = new ArrayList<>();
    private static long lastUpdate = 0;
    private static long throughput = 0;
    private static Duration timeframe = Duration.ofMinutes(1);

    private BandwidthMeter() {
    }

    public static synchronized void add(long bytes) {
        int idx = getNextIndex();
        records[idx] = bytes;

        if (lastUpdate + 1000 < System.currentTimeMillis()) {
            Instant last = Instant.ofEpochMilli(lastUpdate);
            Instant now = Instant.now();
            timeframe = Duration.between(last, now);
            calculateThroughput();
            fireEvent(getThroughput(), timeframe);
        }
    }

    private static void calculateThroughput() {
        throughput = 0;
        while (tail != head) {
            throughput += records[tail++];
            if (tail == records.length) {
                tail = 0;
            }
        }
        lastUpdate = System.currentTimeMillis();
    }

    private static int getNextIndex() {
        head++;
        if(head == records.length) {
            head = 0;
        }
        return head;
    }

    public static void setThroughput(long bytes, Duration d) {
        records[0] = bytes;
        timeframe = d;
        fireEvent(bytes, d);
    }

    private static void fireEvent(long throughput, Duration timeframe) {
        for (Listener listener : listeners) {
            listener.bandwidthCalculated(throughput, timeframe);
        }
    }

    /**
     * Get the throughput over the last 10 seconds
     * @return throughput in bytes
     */
    public static long getThroughput() {
        return throughput;
    }

    public static Duration getTimeframe() {
        return timeframe;
    }

    public static void addListener(Listener l) {
        listeners.add(l);
    }

    public static void removeListener(Listener l) {
        listeners.remove(l);
    }

    @FunctionalInterface
    public static interface Listener {
        void bandwidthCalculated(long bytes, Duration timeframe);
    }
}
