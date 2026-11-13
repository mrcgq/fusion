package ctbrec.ui.action;

import ctbrec.Model;
import ctbrec.notes.ModelNotesService;
import ctbrec.ui.CamrecApplication;
import ctbrec.ui.controls.Dialogs;
import javafx.scene.Cursor;
import javafx.scene.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Optional;

public class EditNotesAction {
    private static final Logger LOG = LoggerFactory.getLogger(EditNotesAction.class);

    private final Node source;
    private final Model model;
    private final Runnable callback;

    public EditNotesAction(Node source, Model selectedModel, Runnable callback) {
        this.source = source;
        this.model = selectedModel;
        this.callback = callback;
    }

    public void execute() {
        source.setCursor(Cursor.WAIT);
        ModelNotesService notesService = CamrecApplication.modelNotesService;
        try {
            String notes = notesService.loadModelNotes(model.getUrl()).orElse("");
            Optional<String> newNotes = Dialogs.showTextInput(source.getScene(), "Model Notes", "Notes for " + model.getName(), notes);
            newNotes.ifPresent(n -> {
                try {
                    if (!n.trim().isEmpty()) {
                        notesService.writeModelNotes(model.getUrl(), n);
                    } else {
                        notesService.removeModelNotes(model.getUrl());
                    }
                } catch (IOException e) {
                    LOG.warn("Couldn't save config", e);
                }
            });
            if (callback != null) {
                try {
                    callback.run();
                } catch (Exception e) {
                    LOG.error("Error while executing callback", e);
                }
            }
        } catch (Exception e) {
            Dialogs.showError(source.getScene(), "Model Notes", "Could not change model notes", e);
        }
        source.setCursor(Cursor.DEFAULT);
    }
}
