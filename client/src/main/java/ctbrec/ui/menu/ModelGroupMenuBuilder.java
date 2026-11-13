package ctbrec.ui.menu;

import ctbrec.Model;
import ctbrec.recorder.Recorder;
import ctbrec.ui.action.*;
import javafx.scene.Node;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

public class ModelGroupMenuBuilder {

    private Model model;
    private Recorder recorder;
    private Node source;
    private Consumer<Model> callback;

    public ModelGroupMenuBuilder model(Model model) {
        this.model = Objects.requireNonNull(model, "Model cannot be null");
        return this;
    }

    public ModelGroupMenuBuilder recorder(Recorder recorder) {
        this.recorder = Objects.requireNonNull(recorder, "Recorder cannot be null");
        return this;
    }

    public ModelGroupMenuBuilder node(Node source) {
        this.source = Objects.requireNonNull(source, "Node cannot be null");
        return this;
    }

    public ModelGroupMenuBuilder callback(Consumer<Model> callback) {
        this.callback = callback;
        return this;
    }

    public Menu build() {
        Objects.requireNonNull(model, "Model has to be set");
        Objects.requireNonNull(recorder, "Recorder has to be set");
        Objects.requireNonNull(source, "Node has to be set");
        callback = Optional.ofNullable(callback).orElse(m -> {
        });

        var menu = new Menu("Group");

        var editGroup = new MenuItem("Edit group");
        editGroup.setOnAction(e -> new EditGroupAction(source, recorder, model).execute(callback));

        var resumeAllOfGroup = new MenuItem("Resume all in group");
        resumeAllOfGroup.setOnAction(e -> new ResumeGroupAction(source, recorder, model).execute(callback));

        var pauseAllOfGroup = new MenuItem("Pause all in group");
        pauseAllOfGroup.setOnAction(e -> new PauseGroupAction(source, recorder, model).execute(callback));

        var stopAllOfGroup = new MenuItem("Stop all in group");
        stopAllOfGroup.setOnAction(e -> new StopGroupAction(source, recorder, model).execute(callback));

        var laterAllOfGroup = new MenuItem("Record later all in group");
        laterAllOfGroup.setOnAction(e -> new LaterGroupAction(source, recorder, model).execute(callback));

        menu.getItems().addAll(editGroup, resumeAllOfGroup, pauseAllOfGroup, stopAllOfGroup, laterAllOfGroup);
        return menu;
    }
}
