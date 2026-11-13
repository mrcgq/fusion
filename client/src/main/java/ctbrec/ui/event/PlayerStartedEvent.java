package ctbrec.ui.event;

import ctbrec.Model;
import ctbrec.ui.JavaFxModel;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@EqualsAndHashCode(of = "timestamp")
@ToString
@NoArgsConstructor
public class PlayerStartedEvent {

    private Model model;
    private Instant timestamp;

    public PlayerStartedEvent(Model model) {
        this(model, Instant.now());
    }

    public PlayerStartedEvent(Model model, Instant timestamp) {
        this.model = unwrap(model);
        this.timestamp = timestamp;
    }

    public void setModel(Model model) {
        this.model = unwrap(model);
    }

    private Model unwrap(Model model) {
        if (model instanceof JavaFxModel fxModel) {
            return fxModel.getDelegate();
        } else {
            return model;
        }
    }

}
