package ctbrec.ui.settings;

import ctbrec.Config;
import ctbrec.Recording;
import ctbrec.StringUtil;
import ctbrec.UnknownModel;
import ctbrec.recorder.Recorder;
import ctbrec.sites.chaturbate.Chaturbate;
import ctbrec.ui.CamrecApplication;
import ctbrec.ui.controls.Dialogs;
import ctbrec.variableexpansion.ConfigVariableExpander;
import ctbrec.variableexpansion.ModelVariableExpander;
import ctbrec.variableexpansion.RecordingVariableExpander;
import ctbrec.variableexpansion.functions.AntlrSyntacErrorAdapter;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import lombok.extern.slf4j.Slf4j;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;

import java.nio.file.Paths;
import java.time.Instant;

@Slf4j
public class VariablePlayGroundDialogFactory {

    private GridPane pane;

    public void openDialog(Scene parent, Config config, Recorder recorder) {
        if (pane == null) {
            initGui(config, recorder);
        }

        Dialogs.showCustomInput(parent, "Playground", pane, (obs, oldV, newV) -> {
        });
    }

    private void initGui(Config config, Recorder recorder) {
        Chaturbate chaturbate = new Chaturbate();
        UnknownModel unknownModel = new UnknownModel();
        unknownModel.setName("Pussy_Galore");
        unknownModel.setDisplayName("Pussy Galore");
        unknownModel.setSite(chaturbate);
        unknownModel.setUrl("http://camsite.example/pussy_galore");
        Recording recording = new Recording();
        recording.setAbsoluteFile(Paths.get("ctbrec", "recs", "pussy_galore", "2023-02-26_14-05-56").toFile());
        recording.setStartDate(Instant.now());
        recording.setStatus(Recording.State.POST_PROCESSING);
        recording.setNote("notes about the recording");
        recording.setModel(unknownModel);

        pane = new GridPane();
        Label result = new Label();
        Label error = new Label();

        pane.add(new Label("Expression"), 0, 0);
        TextField input = new TextField();
        input.setMinWidth(600);
        pane.add(input, 1, 0);
        GridPane.setHgrow(input, Priority.ALWAYS);

        pane.add(error, 0, 1);
        GridPane.setColumnSpan(error, 2);

        pane.add(result, 0, 2);
        GridPane.setColumnSpan(result, 2);

        pane.setHgap(5);
        pane.vgapProperty().bind(pane.hgapProperty());

        AntlrSyntacErrorAdapter errorHandler = new AntlrSyntacErrorAdapter() {
            @Override
            public void syntaxError(Recognizer<?, ?> recognizer, Object o, int line, int pos, String s, RecognitionException e) {
                error.setText(String.format("Syntax error at %d:%d %s", line, pos, s));
            }
        };

        ModelVariableExpander modelVariableExpander = new ModelVariableExpander(unknownModel, CamrecApplication.modelNotesService, recorder, errorHandler);
        RecordingVariableExpander recordingVariableExpander = new RecordingVariableExpander(recording, errorHandler);
        ConfigVariableExpander configVariableExpander = new ConfigVariableExpander(config, errorHandler);

        input.setOnKeyTyped(evt -> {
            try {
                String r = input.getText();
                modelVariableExpander.getPlaceholderValueSuppliers().putAll(recordingVariableExpander.getPlaceholderValueSuppliers());
                modelVariableExpander.getPlaceholderValueSuppliers().putAll(configVariableExpander.getPlaceholderValueSuppliers());
                r = modelVariableExpander.expand(r);
                result.setText(r);
                if (StringUtil.isNotBlank(r)) {
                    error.setText("");
                }
            } catch (Exception e) {
                log.error("Error", e);
            }
        });
    }
}
