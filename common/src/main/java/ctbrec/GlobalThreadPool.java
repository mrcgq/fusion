package ctbrec;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class GlobalThreadPool {

    private static final ExecutorService threadPool = Executors.newWorkStealingPool(20);

    private GlobalThreadPool() {
    }

    public static Future<?> submit(Runnable runnable) { // NOSONAR
        return threadPool.submit(runnable);
    }

    public static <T> Future<T> submit(Callable<T> callable) {
        return threadPool.submit(callable);
    }

    public static ExecutorService get() {
        return threadPool;
    }
}
