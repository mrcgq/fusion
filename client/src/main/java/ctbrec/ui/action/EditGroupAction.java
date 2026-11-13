package ctbrec.ui.action;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import ctbrec.Model;
import ctbrec.ModelGroup;
import ctbrec.recorder.Recorder;
import ctbrec.ui.controls.Dialogs;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class EditGroupAction {

    private static final String DIALOG_TITLE = "Edit model group";

    private Node source;
    private Model model;
    private Recorder recorder;

    public EditGroupAction(Node source, Recorder recorder, Model model) {
        this.source = source;
        this.recorder = recorder;
        this.model = model;
    }

    public void execute() {
        execute(m -> {});
    }

    public void execute(Consumer<Model> callback) {
        source.setCursor(Cursor.WAIT);
        try {
            var dialog = new EditModelGroupDialog(model);
            boolean ok = Dialogs.showCustomInput(source.getScene(), DIALOG_TITLE, dialog);
            if (ok) {
                var group = dialog.getModelGroup();
                group.setName(dialog.getGroupName());
                group.getModelUrls().clear();
                group.getModelUrls().addAll(dialog.getUrls());
                recorder.saveModelGroup(group);
                if (dialog.getUrls().isEmpty()) {
                    boolean delete = Dialogs.showConfirmDialog(DIALOG_TITLE, "Do you want to delete the group?", "Group is empty", source.getScene());
                    if (delete) {
                        recorder.deleteModelGroup(group);
                    }
                }
            }
        } catch (Exception e) {
            Dialogs.showError(source.getScene(), DIALOG_TITLE, "Editing model group failed", e);
        } finally {
            Optional.ofNullable(callback).ifPresent(c -> c.accept(model));
            source.setCursor(Cursor.DEFAULT);
        }
    }

    private class EditModelGroupDialog extends GridPane {
        private TextField groupName;
        private ListView<String> urlListView;
        private ObservableList<String> urlList;
        private ModelGroup modelGroup;
        private List<String> urls;

        public EditModelGroupDialog(Model model) {
            Optional<ModelGroup> optionalModelGroup = recorder.getModelGroup(model);
            if (optionalModelGroup.isPresent()) {
                modelGroup = optionalModelGroup.get();
                urls = new ArrayList<>(modelGroup.getModelUrls());
                createGui(modelGroup);
            } else {
                Dialogs.showError(getScene(), DIALOG_TITLE, "No group found for model", null);
            }
        }

        public ModelGroup getModelGroup() {
            return modelGroup;
        }

        public List<String> getUrls() {
            return urls;
        }

        public String getGroupName() {
            return groupName.getText();
        }

        void createGui(ModelGroup modelGroup) {
            setHgap(5);
            vgapProperty().bind(hgapProperty());

            groupName = new TextField(modelGroup.getName());
            var up = createUpButton();
            var down = createDownButton();
            var add = createAddButton();
            var remove = createRemoveButton();
            var buttons = new VBox(3, up, down, add, remove);
            urlList = FXCollections.observableList(urls);
            urlList.addListener((ListChangeListener<String>) change -> urls = new ArrayList<>(urlList));
            urlListView = new ListView<>(urlList);
            urlListView.setPrefWidth(600);
            GridPane.setHgrow(urlListView, Priority.ALWAYS);

            var row = 0;
            add(groupName, 0, row++);
            add(urlListView, 0, row);
            add(buttons, 1, row);

            urlListView.getSelectionModel().selectedIndexProperty().addListener((obs, oldV, newV) -> {
                var idx = newV.intValue();
                boolean noSelection = idx == -1;
                up.setDisable(noSelection || idx == 0);
                down.setDisable(noSelection || idx == urlList.size() - 1);
                remove.setDisable(noSelection);
            });
        }

        private Button createUpButton() {
            var button = createButton("\u25B4", "Move up");
            button.setOnAction(evt -> {
                int idx = urlListView.getSelectionModel().getSelectedIndex();
                String selectedItem = urlListView.getSelectionModel().getSelectedItem();
                urlList.remove(idx);
                urlList.add(idx - 1, selectedItem);
                urlListView.getSelectionModel().select(idx - 1);
            });
            return button;
        }

        private Button createDownButton() {
            var button = createButton("\u25BE", "Move down");
            button.setOnAction(evt -> {
                int idx = urlListView.getSelectionModel().getSelectedIndex();
                String selectedItem = urlListView.getSelectionModel().getSelectedItem();
                urlList.remove(idx);
                urlList.add(idx + 1, selectedItem);
                urlListView.getSelectionModel().select(idx + 1);
            });
            return button;
        }

        private Button createAddButton() {
            var button = createButton("+", "Add selected URL");
            button.setDisable(false);
            button.setOnAction(evt -> Dialogs.showTextInput(getScene(), "Add URL", "Add new model URL", "").ifPresent(urlList::add));
            return button;
        }

        private Button createRemoveButton() {
            var button = createButton("-", "Remove selected URL");
            button.setOnAction(evt -> {
                String selectedItem = urlListView.getSelectionModel().getSelectedItem();
                if (selectedItem != null) {
                    urlList.remove(selectedItem);
                }
            });
            return button;
        }

        private Button createButton(String text, String tooltip) {
            var b = new Button(text);
            b.setTooltip(new Tooltip(tooltip));
            b.setDisable(true);
            b.setPrefSize(32, 32);
            return b;
        }
    }
}
