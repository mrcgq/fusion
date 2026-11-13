/*
 * Copyright (c) 2008, 2014, Oracle and/or its affiliates.
 * All rights reserved. Use is subject to license terms.
 *
 * This file is available and licensed under the following license:
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *  - Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *  - Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the distribution.
 *  - Neither the name of Oracle Corporation nor the names of its
 *    contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package ctbrec.ui.controls;

import ctbrec.Config;
import ctbrec.GlobalThreadPool;
import ctbrec.Model;
import ctbrec.StringUtil;
import ctbrec.recorder.Recorder;
import ctbrec.ui.action.PlayAction;
import ctbrec.ui.action.SetThumbAsPortraitAction;
import ctbrec.ui.menu.ModelMenuContributor;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.shape.Rectangle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Popover page that displays a list of samples and sample categories for a given SampleCategory.
 */
public class SearchPopoverTreeList extends PopoverTreeList<Model> implements Popover.Page {
    private static final Logger LOG = LoggerFactory.getLogger(SearchPopoverTreeList.class);

    private Popover popover;

    private Recorder recorder;

    ContextMenu popup;

    @Override
    public ListCell<Model> call(ListView<Model> p) {
        return new SearchItemListCell();
    }

    @Override
    protected void itemClicked(Model model) {
        if (model == null) {
            return;
        }

        new PlayAction(this, model).execute();
    }

    @Override
    public void setPopover(Popover popover) {
        this.popover = popover;
    }

    @Override
    public Popover getPopover() {
        return popover;
    }

    @Override
    public Node getPageNode() {
        return this;
    }

    @Override
    public String getPageTitle() {
        return "Search Results";
    }

    @Override
    public String leftButtonText() {
        return null;
    }

    @Override
    public void handleLeftButton() {
        // not used
    }

    @Override
    public String rightButtonText() {
        return "Done";
    }

    @Override
    public void handleRightButton() {
        popover.hide();
    }

    @Override
    public void handleShown() {
        // not used
    }

    @Override
    public void handleHidden() {
        // not used
    }

    private class SearchItemListCell extends ListCell<Model> implements Skin<SearchItemListCell>, EventHandler<MouseEvent> {

        private Label title = new Label();
        private Button follow;
        private Button startRecording;
        private Model model;
        private ImageView thumb = new ImageView();
        private int thumbSize = 64;
        private Node tallest = thumb;

        private SearchItemListCell() {
            super();
            String highlightClass = "highlight";
            setSkin(this);
            getStyleClass().setAll("search-tree-list-cell");
            setOnMouseClicked(this);
            setOnMouseEntered(evt -> getStyleClass().add(highlightClass));
            setOnMouseExited(evt -> getStyleClass().remove(highlightClass));
            addEventHandler(ContextMenuEvent.CONTEXT_MENU_REQUESTED, event -> {
                popup = new CustomMouseBehaviorContextMenu();
                ModelMenuContributor.newContributor(this, Config.getInstance(), recorder) //
                        .withStartStopCallback(m -> setCursor(Cursor.DEFAULT)) //
                        .contributeToMenu(List.of(model), popup);
                var useImageAsPortrait = new MenuItem("Use As Portrait");
                useImageAsPortrait.setOnAction(e -> new SetThumbAsPortraitAction(this, model, thumb.getImage()).execute());
                popup.getItems().add(useImageAsPortrait);
                popup.show(this, event.getScreenX(), event.getScreenY());
                event.consume();
            });
            addEventHandler(MouseEvent.MOUSE_PRESSED, event -> {
                if (popup != null) {
                    popup.hide();
                }
            });

            Rectangle clip = new Rectangle(thumbSize, thumbSize);
            clip.setArcWidth(20);
            clip.arcHeightProperty().bind(clip.arcWidthProperty());
            thumb.setFitWidth(thumbSize);
            thumb.setFitHeight(thumbSize);
            thumb.setClip(clip);
            thumb.setSmooth(true);

            follow = new Button("Follow");
            follow.setOnAction(evt -> {
                setCursor(Cursor.WAIT);
                GlobalThreadPool.submit(new Task<Boolean>() {
                    @Override
                    protected Boolean call() throws Exception {
                        model.getSite().login();
                        return model.follow();
                    }

                    @Override
                    protected void done() {
                        try {
                            get();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            LOG.warn("Search failed: {}", e.getMessage());
                        } catch (Exception e) {
                            LOG.warn("Search failed: {}", e.getMessage());
                        }
                        Platform.runLater(() -> setCursor(Cursor.DEFAULT));
                    }
                });
            });
            startRecording = new Button("Record");
            startRecording.setOnAction(evt -> {
                setCursor(Cursor.WAIT);
                GlobalThreadPool.submit(new Task<Void>() {
                    @Override
                    protected Void call() throws Exception {
                        recorder.addModel(model);
                        return null;
                    }

                    @Override
                    protected void done() {
                        Platform.runLater(() -> setCursor(Cursor.DEFAULT));
                    }
                });
            });
            getChildren().addAll(thumb, title, follow, startRecording);

            startRecording.visibleProperty().bind(title.visibleProperty());
            thumb.visibleProperty().bind(title.visibleProperty());
        }

        @Override
        public void handle(MouseEvent t) {
            if (t.getButton() == MouseButton.PRIMARY && t.getClickCount() == 2) {
                itemClicked(getItem());
            }
        }

        @Override
        protected void updateItem(Model model, boolean empty) {
            super.updateItem(model, empty);
            if (empty) {
                follow.setVisible(false);
                title.setVisible(false);
                this.model = null;
            } else {
                follow.setVisible(model.getSite().supportsFollow());
                follow.setDisable(!model.getSite().credentialsAvailable());
                title.setVisible(true);
                title.setText(model.getDisplayName());
                this.model = model;
                URL anonymousPng = getClass().getResource("/anonymous.png");
                String previewUrl = Optional.ofNullable(model.getPreview()).orElse(anonymousPng.toString());
                if (!Objects.equals(System.getenv("CTBREC_DEV"), "1") && StringUtil.isNotBlank(previewUrl)) {
                    Image img = new Image(previewUrl, true);
                    thumb.setImage(img);
                } else {
                    Image img = new Image(anonymousPng.toString(), true);
                    thumb.setImage(img);
                }
            }

        }

        @Override
        protected void layoutChildren() {
            super.layoutChildren();
            final Insets insets = getInsets();
            final double left = insets.getLeft();
            final double top = insets.getTop();
            final double w = getWidth() - left - insets.getRight();
            final double h = getHeight() - top - insets.getBottom();

            thumb.setLayoutX(left);
            thumb.setLayoutY((h - thumbSize) / 2);

            final double titleHeight = title.prefHeight(w);
            title.setLayoutX(left + thumbSize + 10);
            title.setLayoutY((h - titleHeight) / 2);
            title.resize(w, titleHeight);

            int buttonW = 50;
            int buttonH = 24;
            follow.setStyle("-fx-font-size: 10px;");
            follow.setLayoutX(w - buttonW - 20);
            follow.setLayoutY((h - buttonH) / 2);
            follow.resize(buttonW, buttonH);

            startRecording.setStyle("-fx-font-size: 10px;");
            startRecording.setLayoutX(w - 10);
            startRecording.setLayoutY((h - buttonH) / 2);
            startRecording.resize(buttonW, buttonH);
        }

        @Override
        protected double computeMinWidth(double height) {
            final Insets insets = getInsets();
            final double h = insets.getBottom() - insets.getTop();
            return (int) ((insets.getLeft() + tallest.minWidth(h) + tallest.minWidth(h) + insets.getRight()) + 0.5d);
        }

        @Override
        protected double computePrefWidth(double height) {
            final Insets insets = getInsets();
            final double h = insets.getBottom() - insets.getTop();
            return (int) ((insets.getLeft() + tallest.prefWidth(h) + tallest.prefWidth(h) + insets.getRight()) + 0.5d);
        }

        @Override
        protected double computeMaxWidth(double height) {
            final Insets insets = getInsets();
            final double h = insets.getBottom() - insets.getTop();
            return (int) ((insets.getLeft() + tallest.maxWidth(h) + tallest.maxWidth(h) + insets.getRight()) + 0.5d);
        }

        @Override
        protected double computeMinHeight(double width) {
            return thumbSize;
        }

        @Override
        protected double computePrefHeight(double width) {
            return thumbSize + 20.0;
        }

        @Override
        protected double computeMaxHeight(double width) {
            return thumbSize + 20.0;
        }

        @Override
        public SearchItemListCell getSkinnable() {
            return this;
        }

        @Override
        public Node getNode() {
            return null;
        }

        @Override
        public void dispose() {
            // nothing to do
        }
    }

    public void setRecorder(Recorder recorder) {
        this.recorder = recorder;
    }
}
