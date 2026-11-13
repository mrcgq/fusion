package ctbrec.ui.tabs;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import ctbrec.Config;
import ctbrec.GlobalThreadPool;
import ctbrec.Model;
import ctbrec.io.HttpException;
import ctbrec.recorder.Recorder;
import ctbrec.ui.AutosizeAlert;
import ctbrec.ui.Icon;
import ctbrec.ui.SiteUiFactory;
import ctbrec.ui.action.EditGroupAction;
import ctbrec.ui.action.PlayAction;
import ctbrec.ui.action.StopRecordingAction;
import ctbrec.ui.controls.Dialogs;
import ctbrec.ui.controls.RecordingIndicator;
import ctbrec.ui.controls.StreamPreview;
import javafx.animation.FadeTransition;
import javafx.animation.FillTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.Transition;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Shape;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.util.Duration;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;

import static ctbrec.Model.State.OFFLINE;
import static ctbrec.Model.State.ONLINE;
import static ctbrec.io.HttpConstants.*;
import static ctbrec.ui.Icon.*;

public class ThumbCell extends StackPane {

    private static final String ERROR = "Error";
    private static final Logger LOG = LoggerFactory.getLogger(ThumbCell.class);
    private static final Duration ANIMATION_DURATION = new Duration(250);

    private static final Image imgRecordIndicator = new Image(MEDIA_RECORD_16.url());
    private static final Image imgForceRecordIndicator = new Image(MEDIA_FORCE_RECORD_16.url());
    private static final Image imgPauseIndicator = new Image(MEDIA_PLAYBACK_PAUSE_16.url());
    private static final Image imgBookmarkIndicator = new Image(BOOKMARK_16.url());
    private static final Image imgGroupIndicator = new Image(Icon.GROUP_16.url());

    private ModelRecordingState modelRecordingState = ModelRecordingState.NOT;
    private final Model model;
    private final StreamPreview streamPreview;
    private final ImageView iv;
    private final Rectangle resolutionBackground;
    private final Paint resolutionOnlineColor = new Color(0.22, 0.8, 0.29, 1);
    private final Color resolutionOfflineColor = new Color(0.8, 0.28, 0.28, 1);
    private final Rectangle nameBackground;
    private final Rectangle topicBackground;
    private final Rectangle selectionOverlay;
    private final Text name;
    private final Text topic;
    private final Text resolutionTag;
    private final Recorder recorder;
    private final RecordingIndicator recordingIndicator;
    private final Tooltip recordingIndicatorTooltip;
    private StackPane previewTrigger;
    private final StackPane groupIndicator;
    private final Label groupIndicatorTooltipTrigger;
    private int index = 0;
    private static final Color colorNormal = Color.BLACK;
    private static final Color colorHighlight = Color.WHITE;
    private final Color colorRecording = new Color(0.8, 0.28, 0.28, .8);
    private final SimpleBooleanProperty selectionProperty = new SimpleBooleanProperty(false);
    private double imgAspectRatio;
    private final SimpleBooleanProperty preserveAspectRatio = new SimpleBooleanProperty(true);

    private final ObservableList<Node> thumbCellList;
    private boolean mouseHovering = false;
    private boolean recording;
    static LoadingCache<Model, int[]> resolutionCache = CacheBuilder.newBuilder()
            .expireAfterAccess(5, TimeUnit.MINUTES)
            .maximumSize(10000)
            .build(CacheLoader.from(ThumbCell::getStreamResolution));
    private final ThumbOverviewTab parent;
    private CompletableFuture<Boolean> startPreview;

    public ThumbCell(ThumbOverviewTab parent, Model model, Recorder recorder, double aspectRatio) {
        this.parent = parent;
        this.thumbCellList = parent.grid.getChildren();
        this.model = model;
        this.recorder = recorder;
        this.imgAspectRatio = aspectRatio;
        recording = recorder.isTracked(model);
        model.setSuspended(recorder.isSuspended(model));
        model.setForcePriority(recorder.isForcePriority(model));
        this.setStyle("-fx-background-color: -fx-base");

        streamPreview = new StreamPreview();
        streamPreview.prefWidthProperty().bind(widthProperty());
        streamPreview.prefHeightProperty().bind(heightProperty());
        getChildren().add(streamPreview);

        iv = new ImageView();
        iv.setSmooth(true);
        iv.setPreserveRatio(true);
        getChildren().add(iv);

        topicBackground = new Rectangle();
        topicBackground.setFill(Color.BLACK);
        topicBackground.setOpacity(0);
        StackPane.setAlignment(topicBackground, Pos.TOP_LEFT);
        getChildren().add(topicBackground);

        resolutionBackground = new Rectangle(34, 16);
        resolutionBackground.setFill(resolutionOnlineColor);
        resolutionBackground.setVisible(false);
        resolutionBackground.setArcHeight(5);
        resolutionBackground.setArcWidth(resolutionBackground.getArcHeight());
        StackPane.setAlignment(resolutionBackground, Pos.TOP_RIGHT);
        StackPane.setMargin(resolutionBackground, new Insets(2));
        getChildren().add(resolutionBackground);

        topic = new Text();
        String txt = recording ? "    " : "";
        txt += model.getDescription();
        topic.setText(txt);

        topic.setFill(Color.WHITE);
        topic.setTextAlignment(TextAlignment.LEFT);
        topic.setOpacity(0);
        var margin = 4;
        StackPane.setMargin(topic, new Insets(margin));
        StackPane.setAlignment(topic, Pos.TOP_CENTER);
        getChildren().add(topic);

        nameBackground = new Rectangle();
        nameBackground.setFill(recording ? colorRecording : colorNormal);
        nameBackground.setOpacity(.7);
        StackPane.setAlignment(nameBackground, Pos.BOTTOM_CENTER);
        getChildren().add(nameBackground);

        name = new Text(model.getDisplayName());
        name.setFill(Color.WHITE);
        name.setTextAlignment(TextAlignment.CENTER);
        name.getStyleClass().add("thumbcell-name");
        StackPane.setAlignment(name, Pos.BOTTOM_CENTER);
        getChildren().add(name);

        resolutionTag = new Text();
        resolutionTag.setFill(Color.WHITE);
        resolutionTag.setVisible(false);
        StackPane.setAlignment(resolutionTag, Pos.TOP_RIGHT);
        StackPane.setMargin(resolutionTag, new Insets(2, 4, 2, 2));
        getChildren().add(resolutionTag);

        recordingIndicator = new RecordingIndicator(16);
        recordingIndicator.setCursor(Cursor.HAND);
        recordingIndicator.setOnMouseClicked(this::recordingInidicatorClicked);
        recordingIndicatorTooltip = new Tooltip("Pause Recording");
        Tooltip.install(recordingIndicator, recordingIndicatorTooltip);
        StackPane.setMargin(recordingIndicator, new Insets(3));
        StackPane.setAlignment(recordingIndicator, Pos.TOP_LEFT);
        getChildren().add(recordingIndicator);

        groupIndicator = new StackPane();
        groupIndicator.setMaxSize(24, 24);
        var groupIndicatorImg = new ImageView(imgGroupIndicator);
        groupIndicatorImg.setVisible(false);
        groupIndicatorImg.visibleProperty().bind(groupIndicator.visibleProperty());
        groupIndicatorTooltipTrigger = new Label();
        groupIndicatorTooltipTrigger.setPrefSize(16, 16);
        groupIndicatorTooltipTrigger.setMinSize(16, 16);
        groupIndicatorTooltipTrigger.visibleProperty().bind(groupIndicator.visibleProperty());
        groupIndicatorTooltipTrigger.setCursor(Cursor.HAND);
        groupIndicatorTooltipTrigger.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                new EditGroupAction(this, recorder, model).execute();
                e.consume();
            }
        });
        var groupIndicatorBackground = new Circle(12, Color.WHITE);
        groupIndicatorBackground.visibleProperty().bind(groupIndicator.visibleProperty());
        groupIndicatorBackground.setOpacity(0.7);
        groupIndicator.getChildren().addAll(groupIndicatorBackground, groupIndicatorImg, groupIndicatorTooltipTrigger);
        StackPane.setAlignment(groupIndicator, Pos.BOTTOM_RIGHT);
        getChildren().add(groupIndicator);

        if (Config.getInstance().getSettings().livePreviews) {
            getChildren().add(createPreviewTrigger());
        }

        selectionOverlay = new Rectangle();
        selectionOverlay.visibleProperty().bind(selectionProperty);
        selectionOverlay.widthProperty().bind(widthProperty());
        selectionOverlay.heightProperty().bind(heightProperty());
        StackPane.setAlignment(selectionOverlay, Pos.TOP_LEFT);
        getChildren().add(selectionOverlay);

        setOnMouseEntered(e -> {
            mouseHovering = true;
            Color normal = recording ? colorRecording : colorNormal;
            new ParallelTransition(changeColor(nameBackground, normal, colorHighlight), changeColor(name, colorHighlight, normal)).playFromStart();
            new ParallelTransition(changeOpacity(topicBackground, 0.7), changeOpacity(topic, 0.7)).playFromStart();
            new ParallelTransition(changeOpacity(nameBackground, 1), changeOpacity(name, 1)).playFromStart();
            if (Config.getInstance().getSettings().determineResolution) {
                resolutionBackground.setVisible(false);
                resolutionTag.setVisible(false);
            }
        });
        setOnMouseExited(e -> {
            mouseHovering = false;
            Color normal = recording ? colorRecording : colorNormal;
            new ParallelTransition(changeColor(nameBackground, colorHighlight, normal), changeColor(name, normal, colorHighlight)).playFromStart();
            new ParallelTransition(changeOpacity(topicBackground, 0), changeOpacity(topic, 0)).playFromStart();
            new ParallelTransition(changeOpacity(nameBackground, 0.7), changeOpacity(name, 0.7)).playFromStart();
            if (Config.getInstance().getSettings().determineResolution && !resolutionTag.getText().isEmpty()) {
                resolutionBackground.setVisible(true);
                resolutionTag.setVisible(true);
            }
        });
        setThumbWidth(Config.getInstance().getSettings().thumbWidth);

        setRecording(recording);
        update();
    }

    private void recordingInidicatorClicked(MouseEvent evt) {
        switch (modelRecordingState) {
            case RECORDING -> pauseResumeAction(true);
            case PAUSED -> pauseResumeAction(false);
            case BOOKMARKED -> forgetModel();
        }
    }

    private void forgetModel() {
        new StopRecordingAction(this, List.of(model), recorder)
                .execute()
                .thenAccept(r -> update());
    }

    private Node createPreviewTrigger() {
        var s = 24;
        previewTrigger = new StackPane();
        previewTrigger.setStyle("-fx-background-color: white;");
        previewTrigger.setOpacity(.8);
        previewTrigger.setMaxSize(s, s);

        var play = new Polygon(16, 8, 26, 15, 16, 22);
        StackPane.setMargin(play, new Insets(0, 0, 0, 3));
        play.setStyle("-fx-background-color: black;");
        previewTrigger.getChildren().add(play);

        var clip = new Circle(s / 2.0);
        clip.setTranslateX(clip.getRadius());
        clip.setTranslateY(clip.getRadius());
        previewTrigger.setClip(clip);
        StackPane.setAlignment(previewTrigger, Pos.BOTTOM_LEFT);
        StackPane.setMargin(previewTrigger, new Insets(0, 0, 24, 4));
        previewTrigger.setOnMouseEntered(evt -> startPreview());
        previewTrigger.setOnMouseExited(evt -> stopPreview());
        return previewTrigger;
    }

    private void stopPreview() {
        if (startPreview != null) {
            startPreview.cancel(true);
        }
        setPreviewVisible(previewTrigger, false);
    }

    private void startPreview() {
        previewTrigger.setCursor(Cursor.HAND);
        startPreview = CompletableFuture.supplyAsync(() -> {
            try {
                Thread.sleep(500);
                return true;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }, GlobalThreadPool.get()).whenComplete((result, exception) -> {
            startPreview = null;
            if (Boolean.TRUE.equals(result)) {
                setPreviewVisible(previewTrigger, true);
            }
        });
    }

    private void setPreviewVisible(Node previewTrigger, boolean visible) {
        parent.suspendUpdates(visible);
        iv.setVisible(!visible);
        topic.setVisible(!visible);
        topicBackground.setVisible(!visible);
        name.setVisible(!visible);
        nameBackground.setVisible(!visible);
        streamPreview.setVisible(visible);
        if (visible) {
            streamPreview.startStream(model);
        } else {
            streamPreview.stop();
        }
        recordingIndicator.setVisible(!visible);
        if (!visible) {
            updateRecordingIndicator();
        }
        previewTrigger.setCursor(visible ? Cursor.HAND : Cursor.DEFAULT);
    }

    public void setSelected(boolean selected) {
        selectionProperty.set(selected);
        selectionOverlay.setOpacity(selected ? .75 : 0);
        if (selected) {
            selectionOverlay.getStyleClass().add("thumbcell-selection-background");
        } else {
            selectionOverlay.getStyleClass().remove("thumbcell-selection-background");
        }
    }

    public boolean isSelected() {
        return selectionProperty.get();
    }

    public ObservableValue<Boolean> selectionProperty() {
        return selectionProperty;
    }

    private void updateResolutionTag() {
        ThumbOverviewTab.threadPool.submit(() -> {
            int[] resolution;
            String tagText;
            Paint resolutionBackgroundColor;
            try {
                resolution = resolutionCache.get(model);
                resolutionBackgroundColor = resolutionOnlineColor;
                final int w = resolution[1];
                tagText = w != Integer.MAX_VALUE ? Integer.toString(w) : "HD";
                if (w == 0) {
                    var state = model.getOnlineState(false);
                    tagText = state.name();
                    if (model.isOnline() && state == ONLINE) {
                        resolutionCache.invalidate(model);
                    } else {
                        resolutionBackgroundColor = resolutionOfflineColor;
                        if (state == ONLINE) {
                            // state can't be ONLINE while the model is offline
                            tagText = OFFLINE.name();
                        }
                    }
                } else {
                    var state = model.getOnlineState(true);
                    if (state != ONLINE) {
                        tagText = state.name();
                        resolutionBackgroundColor = resolutionOfflineColor;
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                tagText = "error";
                resolutionBackgroundColor = resolutionOfflineColor;
            } catch (ExecutionException | IOException e) {
                tagText = "error";
                resolutionBackgroundColor = resolutionOfflineColor;
            }

            final String text = tagText;
            final Paint c = resolutionBackgroundColor;
            Platform.runLater(() -> {
                String oldText = resolutionTag.getText();
                resolutionTag.setText(text);
                if (!mouseHovering) {
                    resolutionTag.setVisible(true);
                    resolutionBackground.setVisible(true);
                }
                resolutionBackground.setWidth(resolutionTag.getLayoutBounds().getWidth() + 4);
                resolutionBackground.setFill(c);
                if (!Objects.equals(oldText, text)) {
                    parent.filter();
                }
            });
        });
    }

    private void setImage(String url) {
        if (!Objects.equals(System.getenv("CTBREC_DEV"), "1")) {
            boolean updateThumbs = Config.getInstance().getSettings().updateThumbnails;
            if (updateThumbs || iv.getImage() == null) {
                GlobalThreadPool.submit(createThumbDownload(url));
            }
        }
    }

    private Runnable createThumbDownload(String url) {
        return () -> {
            Request req = new Request.Builder()
                    .url(url)
                    .header(ACCEPT, "*/*")
                    .header(ACCEPT_LANGUAGE, Locale.ENGLISH.getLanguage())
                    .header(USER_AGENT, Config.getInstance().getSettings().httpUserAgent)
                    .header(REFERER, getModel().getSite().getBaseUrl())
                    .build();
            try (Response resp = model.getSite().getHttpClient().executeWithCache(req)) {
                if (resp.isSuccessful()) {
                    // double width = 480;
                    // double height = width * imgAspectRatio;
                    InputStream bodyStream = Objects.requireNonNull(resp.body(), "HTTP body is null").byteStream();
                    // javafx supports only a few image formats (not webp, for example), so we go through ImageIO that does
                    var img = SwingFXUtils.toFXImage(ImageIO.read(bodyStream), null);
                    
                    if (img.progressProperty().get() == 1.0) {
                        if (img.isError()) {
                            throw new IOException(img.getException());
                        }
                        Platform.runLater(() -> {
                            iv.setImage(img);
                            setThumbWidth(Config.getInstance().getSettings().thumbWidth);
                        });
                    } else {
                        img.progressProperty().addListener((observable, oldValue, newValue) -> {
                            if (newValue.doubleValue() == 1.0) {
                                iv.setImage(img);
                                setThumbWidth(Config.getInstance().getSettings().thumbWidth);
                            }
                        });
                    }
                } else {
                    throw new HttpException(resp.code(), resp.message());
                }
            } catch (IOException e) {
                LOG.warn("Error loading thumbnail: {} {}", url, e.getLocalizedMessage());
            }
        };
    }

    Image getImage() {
        return iv.getImage();
    }

    private Transition changeColor(Shape shape, Color from, Color to) {
        var transition = new FillTransition(ANIMATION_DURATION, from, to);
        transition.setShape(shape);
        return transition;
    }

    private Transition changeOpacity(Shape shape, double opacity) {
        var transition = new FadeTransition(ANIMATION_DURATION, shape);
        transition.setFromValue(shape.getOpacity());
        transition.setToValue(opacity);
        return transition;
    }

    void startPlayer() {
        new PlayAction(this, model).execute();
    }

    private void setRecording(boolean recording) {
        this.recording = recording;
        Color c;
        if (recording) {
            c = mouseHovering ? colorHighlight : colorRecording;
        } else {
            c = mouseHovering ? colorHighlight : colorNormal;
        }
        nameBackground.setFill(c);
        updateRecordingIndicator();
    }

    private void updateRecordingIndicator() {
        if (recording) {
            recordingIndicator.setVisible(true);
            if (model.isSuspended()) {
                modelRecordingState = ModelRecordingState.PAUSED;
                recordingIndicator.setImage(imgPauseIndicator);
                recordingIndicatorTooltip.setText("Resume Recording");
            } else {
                modelRecordingState = ModelRecordingState.RECORDING;
                if (model.isForcePriority()) {
                    recordingIndicator.setImage(imgForceRecordIndicator);
                } else {
                    recordingIndicator.setImage(imgRecordIndicator);
                }
                recordingIndicatorTooltip.setText("Pause Recording");
            }
        } else {
            if (model.isMarkedForLaterRecording()) {
                recordingIndicator.setVisible(true);
                modelRecordingState = ModelRecordingState.BOOKMARKED;
                recordingIndicator.setImage(imgBookmarkIndicator);
                recordingIndicatorTooltip.setText("Forget Model");
            } else {
                recordingIndicator.setVisible(false);
                modelRecordingState = ModelRecordingState.NOT;
                recordingIndicator.setImage(null);
            }
        }
    }

    void pauseResumeAction(boolean pause) {
        setCursor(Cursor.WAIT);
        GlobalThreadPool.submit(() -> {
            try {
                if (pause) {
                    recorder.suspendRecording(model);
                } else {
                    recorder.resumeRecording(model);
                }
                setRecording(recording);
            } catch (Exception e1) {
                LOG.error("Couldn't pause/resume recording", e1);
                Platform.runLater(() -> {
                    Alert alert = new AutosizeAlert(Alert.AlertType.ERROR, getScene());
                    alert.setTitle(ERROR);
                    alert.setHeaderText("Couldn't pause/resume recording");
                    alert.setContentText("I/O error while pausing/resuming the recording: " + e1.getLocalizedMessage());
                    alert.showAndWait();
                });
            } finally {
                Platform.runLater(() -> setCursor(Cursor.DEFAULT));
            }
        });
    }

    CompletableFuture<Boolean> follow(boolean follow) {
        setCursor(Cursor.WAIT);
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (follow) {
                    SiteUiFactory.getUi(model.getSite()).login();
                    boolean followed = model.follow();
                    if (followed) {
                        return true;
                    } else {
                        Dialogs.showError(getScene(), "Couldn't follow model", "", null);
                        return false;
                    }
                } else {
                    SiteUiFactory.getUi(model.getSite()).login();
                    boolean unfollowed = model.unfollow();
                    if (unfollowed) {
                        Platform.runLater(() -> thumbCellList.remove(ThumbCell.this));
                        return true;
                    } else {
                        Dialogs.showError(getScene(), "Couldn't unfollow model", "", null);
                        return false;
                    }
                }
            } catch (Exception e1) {
                LOG.error("Couldn't follow/unfollow model {}", model.getName(), e1);
                String msg = "I/O error while following/unfollowing model " + model.getName() + ": ";
                Dialogs.showError(getScene(), "Couldn't follow/unfollow model", msg, e1);
                return false;
            } finally {
                Platform.runLater(() -> setCursor(Cursor.DEFAULT));
            }
        }, GlobalThreadPool.get());
    }

    public Model getModel() {
        return model;
    }

    public void setModel(Model model) {
        this.model.setName(model.getName());
        this.model.setDescription(model.getDescription());
        this.model.setPreview(model.getPreview());
        this.model.setTags(model.getTags());
        this.model.setUrl(model.getUrl());
        update();
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    protected void update() {
        model.setSuspended(recorder.isSuspended(model));
        model.setMarkedForLaterRecording(recorder.isMarkedForLaterRecording(model));
        setRecording(recorder.isTracked(model));
        updateRecordingIndicator();
        setImage(model.getPreview());
        String txt = (modelRecordingState != ModelRecordingState.NOT) ? "    " : "";
        txt += model.getDescription() != null ? model.getDescription() : "";
        topic.setText(txt);
        recorder.getModelGroup(model).ifPresentOrElse(group -> {
            var tooltip = group.getName() + ": " + group.getModelUrls().size() + " models:\n";
            tooltip += String.join("\n", group.getModelUrls());
            groupIndicatorTooltipTrigger.setTooltip(new Tooltip(tooltip));
            groupIndicator.setVisible(true);
        }, () -> groupIndicator.setVisible(false));

        if (Config.getInstance().getSettings().determineResolution) {
            updateResolutionTag();
        } else {
            resolutionBackground.setVisible(false);
            resolutionTag.setVisible(false);
        }

        requestLayout();
    }

    @Override
    public int hashCode() {
        final var prime = 31;
        var result = 1;
        result = prime * result + ((model == null) ? 0 : model.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        ThumbCell other = (ThumbCell) obj;
        if (model == null) {
            return other.model == null;
        } else return model.equals(other.model);
    }

    public void setThumbWidth(int width) {
        int height = (int) (width * imgAspectRatio);
        setSize(width, height);
        iv.prefHeight(width);
        iv.prefWidth(height);
    }

    private void setSize(int w, int h) {
        if (iv.getImage() != null) {
            double aspectRatio = iv.getImage().getWidth() / iv.getImage().getHeight();
            if (aspectRatio > 1) {
                iv.setFitWidth(w);
            } else {
                iv.setFitHeight(h);
            }
        }
        setMinSize(w, h);
        setPrefSize(w, h);
        nameBackground.setWidth(w);
        nameBackground.setHeight(25);
        topicBackground.setWidth(w);
        topicBackground.setHeight(h - nameBackground.getHeight());
        topic.prefHeight(getHeight() - 25);
        topic.maxHeight(getHeight() - 25);
        var margin = 4;
        topic.maxWidth(w - margin * 2.0);
        topic.setWrappingWidth(w - margin * 2.0);

        streamPreview.resizeTo(w, h);

        var clip = new Rectangle(w, h);
        clip.setArcWidth(10);
        clip.arcHeightProperty().bind(clip.arcWidthProperty());
        this.setClip(clip);
    }

    private static int[] getStreamResolution(Model model) {
        try {
            return model.getStreamResolution(false);
        } catch (ExecutionException e) {
            LOG.trace("Error loading stream resolution for model {}: {}", model, e.getLocalizedMessage());
            return new int[2];
        }
    }

    public void releaseResources() {
        iv.setImage(null);
    }

    public void setImageAspectRatio(double imageAspectRatio) {
        this.imgAspectRatio = imageAspectRatio;
    }

    public BooleanProperty preserveAspectRatioProperty() {
        return preserveAspectRatio;
    }

    private enum ModelRecordingState {
        RECORDING,
        PAUSED,
        BOOKMARKED,
        NOT
    }

    @Override
    protected void layoutChildren() {
        nameBackground.setHeight(name.getLayoutBounds().getHeight());
        resolutionBackground.setHeight(resolutionTag.getLayoutBounds().getHeight());
        topicBackground.setHeight(getHeight() - nameBackground.getHeight());

        StackPane.setMargin(groupIndicator, new Insets(0, 3, nameBackground.getHeight() + 4, 0));
        if (Config.getInstance().getSettings().livePreviews) {
            StackPane.setMargin(previewTrigger, new Insets(0, 0, nameBackground.getHeight() + 4, 4));
        }
        super.layoutChildren();
    }
}
