package ctbrec.ui.action;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import ctbrec.Model;
import ctbrec.ModelGroup;
import ctbrec.StringUtil;
import ctbrec.recorder.Recorder;
import ctbrec.ui.controls.Dialogs;
import ctbrec.ui.controls.autocomplete.ObservableListSuggester;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Region;

public class AddToGroupAction {

    private Node source;
    private List<Model> models;
    private Recorder recorder;

    public AddToGroupAction(Node source, Recorder recorder, List<Model> models) {
        this.source = source;
        this.recorder = recorder;
        this.models = models;
    }

    public void execute() {
        execute(() -> {});
    }

    public void execute(Runnable callback) {
        source.setCursor(Cursor.WAIT);
        try {
            var dialog = new AddModelGroupDialog();
            boolean ok = Dialogs.showCustomInput(source.getScene(), "Add model to group", dialog.getMainPane(), (obs, ov, nv) -> dialog.requestFocus());
            if (ok) {
                String text = dialog.getText();
                if (StringUtil.isBlank(text)) {
                    return;
                }
                Set<ModelGroup> modelGroups = recorder.getModelGroups();
                Optional<ModelGroup> existingGroup = modelGroups.stream().filter(mg -> mg.getName().equalsIgnoreCase(text)).findFirst();
                if (existingGroup.isPresent()) {
                    for (Model model : models) {
                        existingGroup.get().add(model);
                    }
                    recorder.saveModelGroup(existingGroup.get());
                } else {
                    var group = new ModelGroup();
                    group.setId(UUID.randomUUID());
                    group.setName(text);
                    for (Model model : models) {
                        group.add(model);
                    }
                    modelGroups.add(group);
                    recorder.saveModelGroup(group);
                }
            }
        } catch (IOException | InvalidKeyException | NoSuchAlgorithmException e) {
            Dialogs.showError(source.getScene(), "Add model to group", "Saving model group failed", e);
        } finally {
            callback.run();
            source.setCursor(Cursor.DEFAULT);
        }
    }

    private class AddModelGroupDialog {
        private ComboBox<ModelGroupListItem> comboBox;
        private TextField editor;
        private ObservableListSuggester suggester;

        public String getText() {
            return comboBox.getEditor().getText();
        }

        void requestFocus() {
            editor.requestFocus();
            editor.positionCaret(0);
            editor.selectAll();
        }

        Region getMainPane() {
            var dialogPane = new GridPane();
            Set<ModelGroup> modelGroups;
            modelGroups = recorder.getModelGroups();
            List<ModelGroupListItem> comboBoxItems = modelGroups.stream().map(ModelGroupListItem::new).sorted().collect(Collectors.toList());
            ObservableList<ModelGroupListItem> comboBoxModel = FXCollections.observableArrayList(comboBoxItems);
            comboBox = new ComboBox<>(comboBoxModel);
            comboBox.setEditable(true);
            editor = comboBox.getEditor();
            comboBox.getEditor().addEventHandler(KeyEvent.KEY_RELEASED, evt -> {
                if (evt.getCode().isLetterKey() || evt.getCode().isDigitKey()) {
                    autocomplete(false);
                } else if (evt.getCode() == KeyCode.ENTER) {
                    if (editor.getSelection().getLength() > 0) {
                        editor.selectRange(0, 0);
                        editor.insertText(editor.lengthProperty().get(), ":");
                        editor.positionCaret(editor.lengthProperty().get());
                        evt.consume();
                    }
                } else if (evt.getCode() == KeyCode.SPACE && evt.isControlDown()) {
                    autocomplete(true);
                }
            });
            comboBox.setPlaceholder(new Label(" type in a name to a add a new group "));
            dialogPane.add(new Label("Model group "), 0, 0);
            dialogPane.add(comboBox, 1, 0);
            suggestInitialName(modelGroups);
            suggester = new ObservableListSuggester(comboBoxModel);
            return dialogPane;
        }

        private void suggestInitialName(Set<ModelGroup> modelGroups) {
            String bestName = models.get(0).getDisplayName();
            for (ModelGroup modelGroup : modelGroups) {
                if (StringUtil.percentageOfEquality(bestName, modelGroup.getName()) > 70) {
                    bestName = modelGroup.getName();
                    break;
                }
            }
            editor.setText(bestName);
        }

        private void autocomplete(boolean fulltextSearch) {
            String oldtext = getOldText();
            if(oldtext.isEmpty()) {
                return;
            }

            Optional<String> match;
            if (fulltextSearch) {
                match = suggester.fulltext(oldtext);
            } else {
                match = suggester.startsWith(oldtext);
            }

            if (match.isPresent()) {
                editor.setText(match.get());
                int pos = oldtext.length();
                editor.positionCaret(pos);
                editor.selectRange(pos, match.get().length());
            }
        }

        private String getOldText() {
            if(editor.getSelection().getLength() > 0) {
                return editor.getText().substring(0, editor.getSelection().getStart());
            } else {
                return editor.getText();
            }
        }
    }

    private static class ModelGroupListItem implements Comparable<ModelGroupListItem> {
        private ModelGroup modelGroup;

        public ModelGroupListItem(ModelGroup modelGroup) {
            this.modelGroup = modelGroup;
        }

        @Override
        public String toString() {
            return this.modelGroup.getName();
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(modelGroup);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            ModelGroupListItem other = (ModelGroupListItem) obj;
            return java.util.Objects.equals(modelGroup, other.modelGroup);
        }

        @Override
        public int compareTo(ModelGroupListItem o) {
            return this.modelGroup.getName().compareTo(o.modelGroup.getName());
        }
    }
}
