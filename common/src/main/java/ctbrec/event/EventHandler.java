package ctbrec.event;

import com.google.common.eventbus.Subscribe;
import ctbrec.Config;
import ctbrec.event.Event.Type;
import ctbrec.event.EventHandlerConfiguration.ActionConfiguration;
import ctbrec.event.EventHandlerConfiguration.PredicateConfiguration;
import lombok.EqualsAndHashCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

@EqualsAndHashCode(of = "id")
public class EventHandler {
    private static final Logger LOG = LoggerFactory.getLogger(EventHandler.class);

    private List<EventPredicate> predicates = new ArrayList<>();
    private final List<Action> actions;
    private String id;

    public EventHandler(EventHandlerConfiguration config) {
        id = config.getId();
        Type event = config.getEvent();
        actions = createActions(config);
        predicates = createPredicates(config);
        predicates.add(new EventTypePredicate(event));
    }

    public String getId() {
        return id;
    }

    public EventHandler(Action action, EventPredicate... predicates) {
        this(Collections.singletonList(action), predicates);
    }

    public EventHandler(List<Action> actions, EventPredicate... predicates) {
        this.actions = actions;
        Collections.addAll(this.predicates, predicates);
    }

    @Subscribe
    public void reactToEvent(Event evt) {
        if (Config.getInstance().getSettings().eventsSuspended) {
            LOG.debug("Events are suspended. Ignoring {}", evt);
            return;
        }

        try {
            boolean matches = true;
            for (Predicate<Event> predicate : predicates) {
                if (!predicate.test(evt)) {
                    matches = false;
                }
            }
            if (matches) {
                for (Consumer<Event> action : actions) {
                    action.accept(evt);
                }
            }
        } catch (Exception e) {
            LOG.error("Error while processing event", e);
        }
    }

    private List<EventPredicate> createPredicates(EventHandlerConfiguration config) {
        List<EventPredicate> predicateList = new ArrayList<>(config.getPredicates().size());
        for (PredicateConfiguration pc : config.getPredicates()) {

            try {
                @SuppressWarnings("unchecked")
                Class<EventPredicate> cls = (Class<EventPredicate>) Class.forName(pc.getType());
                if (cls == null) {
                    LOG.warn("Ignoring unknown action {}", cls);
                    continue;
                }
                EventPredicate predicate = cls.getDeclaredConstructor().newInstance();
                predicate.configure(pc);
                predicateList.add(predicate);
            } catch (InstantiationException | IllegalAccessException | ClassNotFoundException | IllegalArgumentException | InvocationTargetException |
                     NoSuchMethodException | SecurityException e) {
                LOG.warn("Error while creating action {} {}", pc.getType(), pc.getConfiguration(), e);
            }
        }
        return predicateList;
    }

    private List<Action> createActions(EventHandlerConfiguration config) {
        List<Action> actionList = new ArrayList<>(config.getActions().size());
        for (ActionConfiguration ac : config.getActions()) {
            try {
                @SuppressWarnings("unchecked")
                Class<Action> cls = (Class<Action>) Class.forName(ac.getType());
                if (cls == null) {
                    LOG.warn("Ignoring unknown action {}", cls);
                    continue;
                }
                Action action = cls.getDeclaredConstructor().newInstance();
                action.configure(ac);
                actionList.add(action);
            } catch (Exception e) {
                LOG.warn("Error while creating action {} {}", ac.getType(), ac.getConfiguration(), e);
            }
        }
        return actionList;
    }
}
