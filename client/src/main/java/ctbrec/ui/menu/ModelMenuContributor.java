package ctbrec.ui.menu;

import ctbrec.Config;
import ctbrec.Model;
import ctbrec.ModelGroup;
import ctbrec.recorder.Recorder;
import ctbrec.ui.DesktopIntegration;
import ctbrec.ui.action.AbstractModelAction.Result;
import ctbrec.ui.action.*;
import ctbrec.ui.controls.Dialogs;
import ctbrec.ui.tabs.FollowedTab;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TabPane;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static java.nio.charset.StandardCharsets.UTF_8;

public class ModelMenuContributor {

    private static final Logger LOG = LoggerFactory.getLogger(ModelMenuContributor.class);

    private final Config config;
    private final Recorder recorder;
    private final Node source;
    private Consumer<Model> startStopCallback;
    private TriConsumer<Model, Boolean, Boolean> followCallback;
    private Consumer<Model> ignoreCallback;
    private Consumer<Model> portraitCallback;
    private boolean removeWithIgnore = false;
    private Runnable callback;

    private ModelMenuContributor(Node source, Config config, Recorder recorder) {
        this.source = source;
        this.config = config;
        this.recorder = recorder;
    }

    public static ModelMenuContributor newContributor(Node source, Config config, Recorder recorder) {
        return new ModelMenuContributor(source, config, recorder);
    }

    public ModelMenuContributor withStartStopCallback(Consumer<Model> callback) {
        this.startStopCallback = callback;
        return this;
    }

    public ModelMenuContributor withFollowCallback(TriConsumer<Model, Boolean, Boolean> callback) {
        this.followCallback = callback;
        return this;
    }

    public ModelMenuContributor withIgnoreCallback(Consumer<Model> ignoreCallback) {
        this.ignoreCallback = ignoreCallback;
        return this;
    }

    public ModelMenuContributor withPortraitCallback(Consumer<Model> portraitCallback) {
        this.portraitCallback = portraitCallback;
        return this;
    }


    public ModelMenuContributor removeModelAfterIgnore(boolean yes) {
        this.removeWithIgnore = yes;
        return this;
    }

    public void contributeToMenu(List<Model> selectedModels, ContextMenu menu) {
        startStopCallback = Optional.ofNullable(startStopCallback).orElse(m -> {
        });
        followCallback = Optional.ofNullable(followCallback).orElse((m, f, s) -> {
        });
        ignoreCallback = Optional.ofNullable(ignoreCallback).orElse(m -> {
        });
        portraitCallback = Optional.ofNullable(portraitCallback).orElse(m -> {
        });
        callback = Optional.ofNullable(callback).orElse(() -> {
        });
        addOpenInPlayer(menu, selectedModels);
        addOpenInBrowser(menu, selectedModels);
        addCopyUrl(menu, selectedModels);
        menu.getItems().add(new SeparatorMenuItem());

        addStartOrStop(menu, selectedModels);
        addStartRecordingWithTimeLimit(menu, selectedModels);
        addRemoveTimeLimit(menu, selectedModels);
        addSwitchStreamSource(menu, selectedModels);
        addStartPaused(menu, selectedModels);
        addRecordLater(menu, selectedModels);
        addPauseResume(menu, selectedModels);
        addForceRecord(menu, selectedModels);
        addGroupMenu(menu, selectedModels);
        menu.getItems().add(new SeparatorMenuItem());

        addFollowUnfollow(menu, selectedModels);
        addSendTip(menu, selectedModels);
        addIgnore(menu, selectedModels);
        addOpenRecDir(menu, selectedModels);
        addNotes(menu, selectedModels);
        addPortrait(menu, selectedModels);
        addOpenOnCamGirlFinder(menu, selectedModels);
    }

    public ModelMenuContributor afterwards(Runnable callback) {
        this.callback = callback;
        return this;
    }

    private void addNotes(ContextMenu menu, List<Model> selectedModels) {
        var notes = new MenuItem("Notes");
        notes.setDisable(selectedModels.size() != 1);
        notes.setOnAction(e -> new EditNotesAction(source, selectedModels.get(0), callback).execute());
        menu.getItems().add(notes);
    }

    private void addPortrait(ContextMenu menu, List<Model> selectedModels) {
        var portrait = new MenuItem("Select Portrait");
        portrait.setDisable(selectedModels.size() != 1);
        portrait.setOnAction(e -> new SetPortraitAction(source, selectedModels.get(0), portraitCallback).execute());
        menu.getItems().add(portrait);
    }

    private void addOpenRecDir(ContextMenu menu, List<Model> selectedModels) {
        if (selectedModels == null || selectedModels.isEmpty()) {
            return;
        }
        var model = selectedModels.get(0);
        var openRecDir = new MenuItem("Open recording directory");
        openRecDir.setDisable(selectedModels.size() != 1);
        openRecDir.setOnAction(e -> {
            new OpenRecordingsDir(source, model).execute();
            executeCallback();
        });
        menu.getItems().add(openRecDir);
    }

    private void addOpenInBrowser(ContextMenu menu, List<Model> selectedModels) {
        var openInBrowser = new MenuItem("Open in browser");
        openInBrowser.setOnAction(e -> selectedModels.forEach(model -> DesktopIntegration.open(model.getUrl())));
        menu.getItems().add(openInBrowser);
    }

    private void addOpenOnCamGirlFinder(ContextMenu menu, List<Model> selectedModels) {
        var openOnCamGirlFinder = new MenuItem("Search on CamGirlFinder");
        openOnCamGirlFinder.setOnAction(e -> {
            for (Model model : selectedModels) {
                String preview = model.getPreview();
                if (preview != null && !preview.isEmpty()) {
                    String query = URLEncoder.encode(preview, UTF_8);
                    DesktopIntegration.open("https://camgirlfinder.net/search?url=" + query);
                } else {
                    String query = URLEncoder.encode(model.getName(), UTF_8);
                    DesktopIntegration.open("https://camgirlfinder.net/models?m=" + query + "&p=a&g=a");
                }
            }
        });
        menu.getItems().add(openOnCamGirlFinder);
    }

    private void addCopyUrl(ContextMenu menu, List<Model> selectedModels) {
        if (selectedModels == null || selectedModels.isEmpty()) {
            return;
        }
        var copyUrl = new MenuItem("Copy URL");
        copyUrl.setOnAction(e -> {
            var sb = new StringBuilder();
            for (Model model : selectedModels) {
                sb.append(model.getUrl()).append('\n');
            }
            sb.deleteCharAt(sb.length() - 1);
            final var content = new ClipboardContent();
            content.putString(sb.toString());
            Clipboard.getSystemClipboard().setContent(content);
        });
        menu.getItems().add(copyUrl);
    }

    private void addSendTip(ContextMenu menu, List<Model> selectedModels) {
        var model = selectedModels.get(0);
        var site = model.getSite();
        if (site.supportsTips()) {
            var sendTip = new MenuItem("Send Tip");
            sendTip.setOnAction(e -> new TipAction(model, source).execute());
            sendTip.setDisable(!site.credentialsAvailable());
            sendTip.setDisable(selectedModels.size() != 1);
            menu.getItems().add(sendTip);
        }
    }

    private void addIgnore(ContextMenu menu, List<Model> selectedModels) {
        var ignore = new MenuItem("Ignore");
        ignore.setOnAction(e -> ignore(selectedModels));
        menu.getItems().add(ignore);
    }

    private void ignore(List<Model> selectedModels) {
        new IgnoreModelsAction(source, selectedModels, recorder, removeWithIgnore).execute(ignoreCallback);
    }

    private void addFollowUnfollow(ContextMenu menu, List<Model> selectedModels) {
        var site = selectedModels.get(0).getSite();
        if (site.supportsFollow()) {
            var follow = new MenuItem("Follow");
            follow.setOnAction(e -> new FollowUnfollowHandler(source, recorder, followCallback).follow(selectedModels));
            var unfollow = new MenuItem("Unfollow");
            unfollow.setOnAction(e -> new FollowUnfollowHandler(source, recorder, followCallback).unfollow(selectedModels));

            var followOrUnFollow = isFollowedTab() ? unfollow : follow;
            followOrUnFollow.setDisable(!site.credentialsAvailable());
            menu.getItems().add(followOrUnFollow);
            followOrUnFollow.setDisable(!site.credentialsAvailable());
        }
    }

    private boolean isFollowedTab() {
        if (source instanceof TabPane tabPane) {
            return tabPane.getSelectionModel().getSelectedItem() instanceof FollowedTab;
        }
        return false;
    }

    private void addSwitchStreamSource(ContextMenu menu, List<Model> selectedModels) {
        var model = selectedModels.get(0);
        if (!recorder.isTracked(model)) {
            return;
        }
        var switchStreamSource = new MenuItem("Switch resolution");
        switchStreamSource.setOnAction(e -> switchStreamSource(selectedModels.get(0)));
        menu.getItems().add(switchStreamSource);
        switchStreamSource.setDisable(selectedModels.size() != 1);
    }

    private void switchStreamSource(Model selectedModel) {
        new SwitchStreamResolutionAction(source, selectedModel, recorder).execute();
    }

    private void addGroupMenu(ContextMenu menu, List<Model> selectedModels) {
        var model = selectedModels.get(0);
        var addToGroup = new MenuItem("Add to group");
        addToGroup.setOnAction(e -> new AddToGroupAction(source, recorder, selectedModels).execute(callback));
        var groupSubMenu = new ModelGroupMenuBuilder() // @formatter:off
                .model(model)
                .recorder(recorder)
                .node(source)
                .callback(m -> callback.run())
                .build(); // @formatter:on
        Optional<ModelGroup> modelGroup = recorder.getModelGroup(model);
        menu.getItems().add(modelGroup.isEmpty() ? addToGroup : groupSubMenu);
    }

    private void addPauseResume(ContextMenu menu, List<Model> selectedModels) {
        var first = selectedModels.get(0);
        if (recorder.isTracked(first)) {
            var pause = new MenuItem("Pause Recording");
            pause.setOnAction(e -> new PauseResumeHandler(source, recorder, callback).pause(selectedModels));
            var resume = new MenuItem("Resume Recording");
            resume.setOnAction(e -> new PauseResumeHandler(source, recorder, callback).resume(selectedModels));
            var pauseResume = recorder.isSuspended(first) ? resume : pause;
            menu.getItems().add(pauseResume);
        }
    }

    private void addForceRecord(ContextMenu menu, List<Model> selectedModels) {
        var forcePriority = new MenuItem("Enable Force Recording");
        forcePriority.setOnAction(e -> {
            for (Model model : selectedModels) {
                model.setMarkedForLaterRecording(false);
                model.setSuspended(false);
            }
            if (!recorder.isTracked(selectedModels.get(0))) {
                startStopAction(selectedModels, true);
            }
            new ForcePriorityHandler(source, recorder, callback).forcePriority(selectedModels);
        });
        var resumePriority = new MenuItem("Disable Force Recording");
        resumePriority.setOnAction(e -> new ForcePriorityHandler(source, recorder, callback).resumePriority(selectedModels));
        var forceResumePriority = recorder.isForcePriority(selectedModels.get(0)) ? resumePriority : forcePriority;
        menu.getItems().add(forceResumePriority);
    }

    private void addRecordLater(ContextMenu menu, List<Model> selectedModels) {
        var first = selectedModels.get(0);
        var recordLater = new MenuItem("Record Later");
        recordLater.setOnAction(e -> recordLater(selectedModels, true));
        var removeRecordLater = new MenuItem("Forget Model");
        removeRecordLater.setOnAction(e -> recordLater(selectedModels, false));
        var addRemoveBookmark = recorder.isMarkedForLaterRecording(first) ? removeRecordLater : recordLater;
        if (recorder.isTracked(first)) {
            menu.getItems().add(recordLater);
        } else {
            menu.getItems().add(addRemoveBookmark);
        }
    }

    private void recordLater(List<Model> selectedModels, boolean recordLater) {
        var confirmed = true;
        if (!recordLater) {
            confirmed = showRemoveConfirmationDialog(selectedModels);
        }
        if (confirmed) {
            selectedModels.forEach(m -> m.setMarkedForLaterRecording(recordLater));
            new MarkForLaterRecordingAction(source, selectedModels, recordLater, recorder).execute(m -> executeCallback());
        }
    }

    private void addStartPaused(ContextMenu menu, List<Model> selectedModels) {
        if (!recorder.isTracked(selectedModels.get(0))) {
            var addPaused = new MenuItem("Add in paused state");
            menu.getItems().add(addPaused);
            addPaused.setOnAction(e -> {
                for (Model model : selectedModels) {
                    model.setMarkedForLaterRecording(false);
                    model.setSuspended(true);
                }
                startStopAction(selectedModels, true);
            });
        }
    }

    private void addStartRecordingWithTimeLimit(ContextMenu menu, List<Model> selectedModels) {
        var model = selectedModels.get(0);
        String text;
        EventHandler<ActionEvent> eventHandler;
        if (recorder.isTracked(model)) {
            text = "Record Until";
            eventHandler = e -> {
                for (Model selectedModel : selectedModels) {
                    new SetStopDateAction(source, selectedModel, recorder)
                            .execute()
                            .thenAccept(r -> executeCallback());
                }
            };
        } else {
            text = "Start Recording Until";
            eventHandler = e -> new StartRecordingAction(source, selectedModels, recorder)
                    .showRecordUntilDialog()
                    .execute()
                    .thenAccept(r -> executeCallback());
        }

        var start = new MenuItem(text);
        start.setOnAction(eventHandler);
        menu.getItems().add(start);
    }

    private void addRemoveTimeLimit(ContextMenu menu, List<Model> selectedModels) {
        var model = selectedModels.get(0);
        if (!model.isRecordingTimeLimited()) {
            return;
        }
        var removeTimeLimit = new MenuItem("Remove Time Limit");
        removeTimeLimit.setOnAction(e -> removeTimeLimit(model));
        menu.getItems().add(removeTimeLimit);
    }

    private void removeTimeLimit(Model selectedModel) {
        new RemoveTimeLimitAction(source, selectedModel, recorder) //
                .execute() //
                .whenComplete((result, exception) -> executeCallback());
    }

    private void addOpenInPlayer(ContextMenu menu, List<Model> selectedModels) {
        var openInPlayer = new MenuItem("Open in Player");
        openInPlayer.setOnAction(e -> selectedModels.forEach(m -> new PlayAction(source, m).execute()));
        menu.getItems().add(openInPlayer);

        if (config.getSettings().singlePlayer && selectedModels.size() > 1) {
            openInPlayer.setDisable(true);
        }
    }

    private void addStartOrStop(ContextMenu menu, List<Model> selectedModels) {
        var start = new MenuItem("Start Recording");
        start.setOnAction(e -> {
            for (Model model : selectedModels) {
                model.setMarkedForLaterRecording(false);
                model.setSuspended(false);
            }
            startStopAction(selectedModels, true);
        });
        var stop = new MenuItem("Stop Recording");
        stop.setOnAction(e -> startStopAction(selectedModels, false));
        var startStop = recorder.isTracked(selectedModels.get(0)) ? stop : start;
        menu.getItems().add(startStop);
    }

    private void startStopAction(List<Model> selection, boolean start) {
        if (start) {
            startRecording(selection);
        } else {
            stopRecording(selection);
        }
    }

    private void startRecording(List<Model> models) {
        new StartRecordingAction(source, models, recorder).execute()
                .whenComplete((r, ex) -> {
                    if (ex != null) {
                        LOG.error("Error while starting recordings", ex);
                    }
                    r.stream().map(Result::getModel).forEach(startStopCallback);
                });
    }

    private void stopRecording(List<Model> models) {
        var confirmed = showRemoveConfirmationDialog(models);
        if (confirmed) {
            new StopRecordingAction(source, models, recorder).execute()
                    .whenComplete((r, ex) -> {
                        if (ex != null) {
                            LOG.error("Error while stopping recordings", ex);
                        }
                        r.stream().map(Result::getModel).forEach(startStopCallback);
                    });
        }
    }

    private boolean showRemoveConfirmationDialog(List<Model> models) {
        if (Config.getInstance().getSettings().confirmationForDangerousActions) {
            int n = models.size();
            String plural = n > 1 ? "s" : "";
            String header = "This will remove " + n + " model" + plural;
            return Dialogs.showConfirmDialog("Stop Recording", "Continue?", header, source.getScene());
        } else {
            return true;
        }
    }

    private void executeCallback() {
        try {
            callback.run();
        } catch (Exception e) {
            LOG.error("Error while executing menu callback", e);
        }
    }
}
