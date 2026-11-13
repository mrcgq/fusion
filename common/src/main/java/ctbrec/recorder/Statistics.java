package ctbrec.recorder;

import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.commons.collections.buffer.CircularFifoBuffer;
 

public class Statistics {
    public ReadWriteLock statsLock = new ReentrantReadWriteLock();
    public CircularFifoBuffer stats = new CircularFifoBuffer(100);
    
    public long errorCount = 0;
    
    public void add(Object val) {
        statsLock.writeLock().lock();
        try {
            stats.add(val);
        } finally {
            statsLock.writeLock().unlock();
        }
    }
}
