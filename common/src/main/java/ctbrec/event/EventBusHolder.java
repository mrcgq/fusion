package ctbrec.event;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.eventbus.AsyncEventBus;
import com.google.common.eventbus.EventBus;

public class EventBusHolder {
    private static final Logger LOG = LoggerFactory.getLogger(EventBusHolder.class);
    private static Map<String, EventHandler> handlers = new HashMap<>();

    private EventBusHolder() {}

    public static final EventBus BUS = new AsyncEventBus(Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r);
        t.setName("EventBus-" + UUID.randomUUID().toString().substring(0, 8));
        t.setPriority(Thread.NORM_PRIORITY - 1);
        return t;
    }));


    public static void register(EventHandler handler) {
        if(handlers.containsKey(handler.getId())) {
            LOG.warn("EventHandler with ID {} is already registered", handler.getId());
        } else {
            BUS.register(handler);
            handlers.put(handler.getId(), handler);
            LOG.debug("EventHandler with ID {} has been added", handler.getId());
        }
    }

    public static void unregister(String id) {
        EventHandler handler = handlers.get(id);
        if(handler != null) {
            BUS.unregister(handler);
            handlers.remove(id);
            LOG.debug("EventHandler with ID {} has been removed", id);
        }
    }
}
