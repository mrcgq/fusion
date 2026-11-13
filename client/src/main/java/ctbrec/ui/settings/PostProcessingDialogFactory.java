package ctbrec.ui.settings;

import ctbrec.recorder.postprocessing.*;
import ctbrec.ui.controls.Dialogs;
import ctbrec.ui.settings.api.Preferences;
import javafx.collections.ObservableList;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.VPos;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Region;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class PostProcessingDialogFactory {

    static Map<Class<?>, Class<?>> ppToDialogMap = new HashMap<>();

    static {
        ppToDialogMap.put(Remux.class, RemuxerPaneFactory.class);
        ppToDialogMap.put(Script.class, ScriptPaneFactory.class);
        ppToDialogMap.put(Rename.class, RenamerPaneFactory.class);
        ppToDialogMap.put(Move.class, MoverPaneFactory.class);
        ppToDialogMap.put(DeleteTooShort.class, DeleteTooShortPaneFactory.class);
        ppToDialogMap.put(CreateContactSheet.class, CreateContactSheetPaneFactory.class);
    }

    private PostProcessingDialogFactory() {
    }

    public static void openNewDialog(PostProcessor pp, Scene scene, ObservableList<PostProcessor> stepList) {
        openDialog(pp, scene, stepList, true);
    }

    public static void openEditDialog(PostProcessor pp, Scene scene, ObservableList<PostProcessor> stepList) {
        openDialog(pp, scene, stepList, false);
    }

    private static void openDialog(PostProcessor pp, Scene scene, ObservableList<PostProcessor> stepList, boolean newEntry) {
        boolean ok;
        try {
            Optional<Preferences> preferences = createPreferences(pp);
            if (preferences.isPresent()) {
                Region view = preferences.get().getView(false);
                view.setMinWidth(600);
                CheckBox enabledCheckbox = new CheckBox("Enabled");
                enabledCheckbox.setSelected(pp.isEnabled());
                enabledCheckbox.selectedProperty().addListener((src, oldV, newV) -> pp.setEnabled(newV));
                GridPane container = new GridPane();
                container.add(enabledCheckbox, 0, 0);
                container.add(view, 0, 1);
                GridPane.setMargin(enabledCheckbox, new Insets(0, 0, 10, 0));
                GridPane.setValignment(view, VPos.CENTER);
                GridPane.setHalignment(view, HPos.CENTER);
                ok = Dialogs.showCustomInput(scene, "Configure " + pp.getName(), container);
                if (ok) {
                    preferences.get().save();
                    if (newEntry) {
                        stepList.add(pp);
                    }
                }
            } else if (newEntry) {
                stepList.add(pp);
            }
        } catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException
                 | InstantiationException | IOException e) {
            Dialogs.showError(scene, "New post-processing step", "Couldn't create dialog for " + pp.getName(), e);
        }
    }

    private static Optional<Preferences> createPreferences(PostProcessor pp) throws InstantiationException, IllegalAccessException,
            InvocationTargetException, NoSuchMethodException {
        Class<?> paneFactoryClass = ppToDialogMap.get(pp.getClass());
        if (paneFactoryClass != null) {
            AbstractPostProcessingPaneFactory factory = (AbstractPostProcessingPaneFactory) paneFactoryClass.getDeclaredConstructor().newInstance();
            return Optional.of(factory.createPostProcessorPane(pp));
        } else {
            return Optional.empty();
        }
    }
}
