package ctbrec.event;

import ctbrec.io.json.dto.ModelDto;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.*;

@Data
@EqualsAndHashCode(of = "id")
public class EventHandlerConfiguration {

    private String id;
    private String name;
    private Event.Type event;
    private List<PredicateConfiguration> predicates = new ArrayList<>();
    private List<ActionConfiguration> actions = new ArrayList<>();

    public EventHandlerConfiguration() {
        id = UUID.randomUUID().toString();
    }

    @Data
    public static class PredicateConfiguration {
        private String name;
        private String type;
        private List<ModelDto> models;
        private Map<String, Object> configuration = new HashMap<>();

        @Override
        public String toString() {
            return name;
        }
    }

    @Data
    public static class ActionConfiguration {
        private String name;
        private String type;
        private Map<String, Object> configuration = new HashMap<>();

        @Override
        public String toString() {
            return name;
        }
    }

    @Override
    public String toString() {
        return name + ", when:" + predicates + " do:" + actions + "]";
    }
}
