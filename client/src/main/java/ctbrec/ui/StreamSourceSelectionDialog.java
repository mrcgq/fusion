package ctbrec.ui;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ctbrec.GlobalThreadPool;
import ctbrec.Model;
import ctbrec.recorder.download.StreamSource;
import ctbrec.ui.controls.Dialogs;
import javafx.concurrent.Task;
import javafx.concurrent.WorkerStateEvent;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.image.Image;
import javafx.stage.Stage;

public class StreamSourceSelectionDialog extends ChoiceDialog<StreamSource> {

    private static final Logger LOG = LoggerFactory.getLogger(StreamSourceSelectionDialog.class);

    public static final StreamSource BEST = new BestStreamSource();
    public static final StreamSource LOADING = new LoadingStreamSource();
    private Scene parent;
    private Model model;
    private Task<List<StreamSource>> loadStreamSources;


    public StreamSourceSelectionDialog(Scene parent, Model model) {
        this.parent = parent;
        this.model = model;
        if (parent != null) {
            getDialogPane().getScene().getStylesheets().addAll(parent.getStylesheets());
        }
        changeCursorTo(Cursor.WAIT);
        getItems().add(LOADING);
        setSelectedItem(LOADING);
        setTitle("Stream Quality");
        setHeaderText("Select your preferred stream quality for " + model.getDisplayName());
        var icon = Dialogs.class.getResourceAsStream("/icon.png");
        var stage = (Stage) getDialogPane().getScene().getWindow();
        stage.getIcons().add(new Image(icon));
        setResultConverter(bt -> (bt == ButtonType.OK) ? getSelectedItem() : null);
        loadStreamSources = new Task<List<StreamSource>>() {
            @Override
            protected List<StreamSource> call() throws Exception {
                List<StreamSource> sources = model.getStreamSources();
                Collections.sort(sources);
                sources.add(BEST);
                return sources;
            }
        };
        loadStreamSources.setOnFailed(this::onFailed);
        loadStreamSources.setOnSucceeded(this::onSucceeded);
        GlobalThreadPool.submit(loadStreamSources);
    }

    private void onFailed(WorkerStateEvent evt) {
        changeCursorTo(Cursor.DEFAULT);
        try {
            loadStreamSources.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.error("Couldn't fetch available stream sources", e);
        } catch (ExecutionException e) {
            LOG.error("Couldn't fetch available stream sources", e);
        }

        boolean confirmed = Dialogs.showConfirmDialog("Error loading stream resolutions", "Do you want to add the model anyway?", "Stream resolutions unknown", parent);
        if (confirmed) {
            getItems().clear();
            getItems().add(BEST);
            setSelectedItem(BEST);
        } else {
            close();
        }
    }

    private void onSucceeded(WorkerStateEvent evt) {
        changeCursorTo(Cursor.DEFAULT);
        List<StreamSource> sources;
        try {
            sources = loadStreamSources.get();
            getItems().remove(LOADING);
            getItems().addAll(sources);
            var selectedIndex = model.getStreamUrlIndex() > -1 ? Math.min(model.getStreamUrlIndex(), sources.size() - 1) : sources.size() - 1;
            setSelectedItem(getItems().get(selectedIndex));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            showError(e);
        } catch (ExecutionException e) {
            showError(e);
        }
    }

    void changeCursorTo(Cursor cursor) {
        parent.setCursor(cursor);
        getDialogPane().setCursor(cursor);
    }

    private void showError(Exception e) {
        Dialogs.showError(parent, getHeaderText(), getContentText(), e);
    }

    private static class BestStreamSource extends StreamSource {
        @Override
        public String toString() {
            return "Best";
        }
    }

    private static class LoadingStreamSource extends StreamSource {
        @Override
        public String toString() {
            return "Loading...";
        }
    }

    public int indexOf(StreamSource selectedSource) {
        int index = -1;
        if (selectedSource != LOADING && selectedSource != BEST) {
            index = getItems().indexOf(selectedSource);
        }
        return index;
    }
}
