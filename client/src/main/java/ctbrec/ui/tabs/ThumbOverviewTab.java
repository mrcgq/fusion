package ctbrec.ui.tabs;

import ctbrec.Config;
import ctbrec.GlobalThreadPool;
import ctbrec.Model;
import ctbrec.ModelGroup;
import ctbrec.recorder.Recorder;
import ctbrec.sites.Site;
import ctbrec.sites.mfc.MyFreeCamsClient;
import ctbrec.sites.mfc.MyFreeCamsModel;
import ctbrec.ui.DesktopIntegration;
import ctbrec.ui.SiteUiFactory;
import ctbrec.ui.TokenLabel;
import ctbrec.ui.action.SetThumbAsPortraitAction;
import ctbrec.ui.controls.*;
import ctbrec.ui.menu.ModelMenuContributor;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.concurrent.Worker.State;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

public class ThumbOverviewTab extends Tab implements TabSelectionListener {
    private static final Logger LOG = LoggerFactory.getLogger(ThumbOverviewTab.class);

    protected static BlockingQueue<Runnable> queue = new LinkedBlockingQueue<>();
    static ExecutorService threadPool = new ThreadPoolExecutor(2, 2, 10, TimeUnit.MINUTES, queue, createThreadFactory());

    protected FlowPane grid = new FlowPane();
    protected PaginatedScheduledService updateService;
    protected HBox pagination;
    protected List<ThumbCell> selectedThumbCells = Collections.synchronizedList(new ArrayList<>());

    List<ThumbCell> filteredThumbCells = Collections.synchronizedList(new ArrayList<>());
    Recorder recorder;
    private String filter;
    ReentrantLock gridLock = new ReentrantLock();
    ScrollPane scrollPane = new ScrollPane();
    TextField pageInput = new TextField(Integer.toString(1));
    Button pageFirst = new Button("1");
    Button pagePrev = new Button("◀");
    Button pageNext = new Button("▶");
    private volatile boolean updatesSuspended = false;
    ContextMenu popup;
    Site site;
    StackPane root = new StackPane();
    Task<List<Model>> searchTask;
    SearchPopover popover;
    SearchPopoverTreeList popoverTreeList = new SearchPopoverTreeList();
    double imageAspectRatio = 3.0 / 4.0;
    private final SimpleBooleanProperty preserveAspectRatio = new SimpleBooleanProperty(true);
    ProgressIndicator progressIndicator;
    Label noResultsFound = new Label("Nothing found!");
    Label errorLabel = new Label("");
    TokenLabel tokenBalance;

    private ComboBox<Integer> thumbWidth;

    public ThumbOverviewTab(String title, PaginatedScheduledService updateService, Site site) {
        super(title);
        this.updateService = updateService;
        this.site = site;
        setClosable(false);
        createGui();
        initializeUpdateService();
    }

    protected void createGui() {
        grid.setPadding(new Insets(5));
        grid.setHgap(5);
        grid.setVgap(5);

        progressIndicator = new ProgressIndicator();
        progressIndicator.setPrefSize(100, 100);

        var filterInput = new SearchBox(false);
        filterInput.setPromptText("Filter models on this page");
        filterInput.textProperty().addListener((observableValue, oldValue, newValue) -> {
            filter = filterInput.getText();
            gridLock.lock();
            try {
                filter();
                moveActiveRecordingsToFront();
            } finally {
                gridLock.unlock();
            }
        });
        var filterTooltip = new Tooltip("Filter the models by their name, stream description or #hashtags.\n\nIf the display of stream resolution is enabled, you can even filter for public rooms or by resolution.\n\nTry \"1080\" or \">720\" or \"public\"");
        filterInput.setTooltip(filterTooltip);
        filterInput.getStyleClass().remove("search-box-icon");

        var searchInput = new SearchBox();
        searchInput.setPromptText("Search Model");
        searchInput.prefWidth(200);
        searchInput.prefHeightProperty().bind(filterInput.heightProperty());
        searchInput.textProperty().addListener(search());
        searchInput.addEventHandler(KeyEvent.KEY_PRESSED, evt -> {
            if (evt.getCode() == KeyCode.ESCAPE) {
                popover.hide();
            }
        });

        popover = new SearchPopover();
        popover.maxWidthProperty().bind(popover.minWidthProperty());
        popover.prefWidthProperty().bind(popover.minWidthProperty());
        popover.setMinWidth(400);
        popover.maxHeightProperty().bind(popover.minHeightProperty());
        popover.prefHeightProperty().bind(popover.minHeightProperty());
        popover.setMinHeight(450);
        popover.pushPage(popoverTreeList);
        StackPane.setAlignment(popover, Pos.TOP_RIGHT);
        StackPane.setMargin(popover, new Insets(35, 50, 0, 0));

        var topBar = new HBox(5);
        HBox.setHgrow(filterInput, Priority.ALWAYS);
        topBar.getChildren().add(filterInput);
        if (site.supportsTips() && site.credentialsAvailable()) {
            var buyTokens = new Button("Buy Tokens");
            buyTokens.setOnAction(e -> DesktopIntegration.open(site.getBuyTokensLink()));
            tokenBalance = new TokenLabel(site);
            tokenBalance.setAlignment(Pos.CENTER_RIGHT);
            tokenBalance.prefHeightProperty().bind(buyTokens.heightProperty());
            topBar.getChildren().addAll(tokenBalance, buyTokens);
        }
        if (site.supportsSearch()) {
            topBar.getChildren().add(searchInput);
        }
        BorderPane.setMargin(topBar, new Insets(0, 5, 0, 5));

        scrollPane.setContent(grid);
        scrollPane.setFitToHeight(true);
        scrollPane.setFitToWidth(true);
        if (Config.getInstance().getSettings().fastScrollSpeed) {
            var scrollPaneSkin = new FasterVerticalScrollPaneSkin(scrollPane);
            scrollPane.setSkin(scrollPaneSkin);
        }
        BorderPane.setMargin(scrollPane, new Insets(5));

        pagination = new HBox(5);
        pagination.getChildren().add(pageFirst);
        pagination.getChildren().add(pagePrev);
        pagination.getChildren().add(pageNext);
        pagination.getChildren().add(pageInput);
        BorderPane.setMargin(pagination, new Insets(5));
        pageInput.setPrefWidth(50);
        pageInput.setOnAction(this::handlePageNumberInput);
        pageFirst.setTooltip(new Tooltip("First Page"));
        pageFirst.setOnAction(e -> changePageTo(1));
        pageFirst.setMinHeight(24);
        pageFirst.setMinWidth(28);
        pagePrev.setTooltip(new Tooltip("Previous Page"));
        pagePrev.setOnAction(e -> previousPage());
        pagePrev.setMinHeight(24);
        pagePrev.setMinWidth(28);
        pageNext.setTooltip(new Tooltip("Next Page"));
        pageNext.setOnAction(e -> nextPage());
        pageNext.setMinHeight(24);
        pageNext.setMinWidth(28);

        var thumbSizeSelector = new HBox(5);
        var l = new Label("Thumb Size");
        l.setPadding(new Insets(5, 0, 0, 0));
        thumbSizeSelector.getChildren().add(l);
        List<Integer> thumbWidths = new ArrayList<>();
        thumbWidths.add(180);
        thumbWidths.add(200);
        thumbWidths.add(220);
        thumbWidths.add(270);
        thumbWidths.add(360);
        thumbWidth = new ComboBox<>(FXCollections.observableList(thumbWidths));
        thumbWidth.getSelectionModel().select(Integer.valueOf(Config.getInstance().getSettings().thumbWidth));
        thumbWidth.setOnAction(e -> {
            Config.getInstance().getSettings().thumbWidth = thumbWidth.getSelectionModel().getSelectedItem();
            updateThumbSize();
        });
        thumbSizeSelector.getChildren().add(thumbWidth);
        BorderPane.setMargin(thumbSizeSelector, new Insets(5));

        var bottomPane = new BorderPane();
        bottomPane.setLeft(pagination);
        bottomPane.setRight(thumbSizeSelector);

        var borderPane = new BorderPane();
        borderPane.setPadding(new Insets(5));
        borderPane.setTop(topBar);
        borderPane.setCenter(scrollPane);
        borderPane.setBottom(bottomPane);

        root.getChildren().add(borderPane);
        root.getChildren().add(popover);
        setContent(root);

        scrollPane.setOnKeyReleased(this::keyReleased);
    }

    private void keyReleased(KeyEvent event) {
        if (event.getCode() == KeyCode.F5) {
            refresh();
        } else if (event.getCode() == KeyCode.A && event.isControlDown()) {
            selectAll();
        } else if (event.getCode() == KeyCode.RIGHT) {
            nextPage();
        } else if (event.getCode() == KeyCode.LEFT) {
            previousPage();
        } else if (event.getCode().getCode() >= KeyCode.DIGIT1.getCode() && event.getCode().getCode() <= KeyCode.DIGIT9.getCode()) {
            changePageTo(event.getCode().getCode() - 48);
        }
    }

    public void selectAll() {
        grid.getChildren().stream().filter(ThumbCell.class::isInstance).forEach(tc -> ((ThumbCell) tc).setSelected(true));
    }

    private void nextPage() {
        int page = updateService.getPage();
        page++;
        changePageTo(page);
    }

    private void previousPage() {
        int page = updateService.getPage();
        page = Math.max(1, --page);
        changePageTo(page);
    }

    private void changePageTo(int page) {
        pageInput.setText(Integer.toString(page));
        updateService.setPage(page);
        selectedThumbCells.clear();
        restartUpdateService();
    }

    private ChangeListener<? super String> search() {
        return (observableValue, oldValue, newValue) -> {
            if (searchTask != null) {
                searchTask.cancel(true);
            }
            if (newValue.length() < 2) {
                return;
            }
            searchTask = new ThumbOverviewTabSearchTask(site, popover, popoverTreeList, newValue);
            GlobalThreadPool.submit(searchTask);
        };
    }

    private void updateThumbSize() {
        int width = Config.getInstance().getSettings().thumbWidth;
        thumbWidth.getSelectionModel().select(Integer.valueOf(width));
        for (Node node : grid.getChildren()) {
            if (node instanceof ThumbCell cell) {
                cell.setThumbWidth(width);
            }
        }
        for (ThumbCell cell : filteredThumbCells) {
            cell.setThumbWidth(width);
        }
    }

    private void handlePageNumberInput(ActionEvent event) {
        try {
            var page = Integer.parseInt(pageInput.getText());
            page = Math.max(1, page);
            changePageTo(page);
        } catch (NumberFormatException e) {
            // noop
        } finally {
            pageInput.setText(Integer.toString(updateService.getPage()));
        }
    }

    private void restartUpdateService() {
        gridLock.lock();
        try {
            grid.getChildren().clear();
            filteredThumbCells.clear();
            deselected();
            selected();
        } finally {
            gridLock.unlock();
        }
    }

    void initializeUpdateService() {
        int refreshRate = Config.getInstance().getSettings().overviewUpdateIntervalInSecs;
        updateService.setPeriod(new Duration(TimeUnit.SECONDS.toMillis(refreshRate)));
        updateService.setOnSucceeded(event -> onSuccess());
        updateService.setOnFailed(this::onFail);
    }

    protected void onSuccess() {
        if (updatesSuspended) {
            return;
        }
        List<Model> models = filterModels(updateService.getValue());
        updateGrid(models);
    }

    private List<Model> filterModels(List<Model> models) {
        List<String> ignored = Config.getInstance().getSettings().ignoredModels;
        String filterBlacklist = Config.getInstance().getSettings().filterBlacklist;
        String filterWhitelist = Config.getInstance().getSettings().filterWhitelist;
        if (filterBlacklist.isBlank() && filterWhitelist.isBlank()) {
            return models.stream()
            		.filter(m -> !ignored.contains(m.getUrl()))
            		.collect(Collectors.toList());
        } else if (filterBlacklist.isBlank()) {
            return models.stream()
            		.filter(m -> !ignored.contains(m.getUrl()))
            		.filter(m -> matches(m, filterWhitelist, true))
            		.collect(Collectors.toList());
        }
        return models.stream()
        		.filter(m -> !ignored.contains(m.getUrl()))
        		.filter(m -> !matches(m, filterBlacklist, true))
        		.filter(m -> matches(m, filterWhitelist, true))
        		.collect(Collectors.toList());
    }

    protected void updateGrid(List<? extends Model> models) {
        gridLock.lock();
        try {
            ObservableList<Node> nodes = grid.getChildren();
            nodes.removeAll(progressIndicator, noResultsFound, errorLabel);

            // first remove models, which are not in the updated list
            removeModelsMissingInUpdate(nodes, models);

            // now update existing cells and create new ones models, which are new in the update
            createOrUpdateModelCells(nodes, models);

            // remove cells from selectedThumbCells, which are not visible anymore
            selectedThumbCells.retainAll(nodes);

            // reapply the filter
            filteredThumbCells.clear();
            filter();

            // move models, which are tracked by the recorder to the front
            moveActiveRecordingsToFront();
        } finally {
            gridLock.unlock();
        }
    }

    private void createOrUpdateModelCells(ObservableList<Node> nodes, List<? extends Model> models) {
        List<ThumbCell> positionChangedOrNew = new ArrayList<>();
        var index = 0;
        for (Model model : models) {
            var found = false;
            for (Node node : nodes) { // NOSONAR
                if (!(node instanceof ThumbCell cell))
                    continue;
                if (cell.getModel().equals(model)) {
                    found = true;
                    cell.setModel(model);
                    if (index != cell.getIndex()) {
                        cell.setIndex(index);
                        positionChangedOrNew.add(cell);
                    }
                    break;
                }
            }
            if (!found) {
                var newCell = createThumbCell(model, recorder);
                newCell.setIndex(index);
                positionChangedOrNew.add(newCell);
            }
            index++;
        }
        rearrangeCells(nodes, positionChangedOrNew);
    }

    private void rearrangeCells(ObservableList<Node> nodes, List<ThumbCell> positionChangedOrNew) {
        for (ThumbCell thumbCell : positionChangedOrNew) {
            nodes.remove(thumbCell);
            if (thumbCell.getIndex() < nodes.size()) {
                nodes.add(thumbCell.getIndex(), thumbCell);
            } else {
                nodes.add(thumbCell);
            }
        }
    }

    private void removeModelsMissingInUpdate(ObservableList<Node> nodes, List<? extends Model> models) {
        for (Iterator<Node> iterator = nodes.iterator(); iterator.hasNext(); ) {
            var node = iterator.next();
            if (!(node instanceof ThumbCell cell))
                continue;
            if (!models.contains(cell.getModel())) {
                iterator.remove();
            }
        }
    }

    ThumbCell createThumbCell(Model model, Recorder recorder) {
        var newCell = new ThumbCell(this, model, recorder, imageAspectRatio);
        newCell.setImageAspectRatio(imageAspectRatio);
        newCell.preserveAspectRatioProperty().bind(preserveAspectRatio);
        newCell.addEventHandler(ContextMenuEvent.CONTEXT_MENU_REQUESTED, event -> {
            suspendUpdates(true);
            popup = createContextMenu(newCell);
            popup.show(newCell, event.getScreenX(), event.getScreenY());
            popup.setOnHidden(e -> suspendUpdates(false));
            event.consume();
        });
        newCell.addEventHandler(MouseEvent.MOUSE_PRESSED, event -> {
            if (popup != null) {
                popup.hide();
                popup = null;
            }
        });
        newCell.selectionProperty().addListener((obs, oldValue, newValue) -> {
            if (Boolean.TRUE.equals(newValue)) {
                selectedThumbCells.add(newCell);
            } else {
                selectedThumbCells.remove(newCell);
            }
        });
        newCell.setOnMouseClicked(mouseClickListener);
        return newCell;
    }

    private ContextMenu createContextMenu(ThumbCell cell) {
        removeSelectionIfNeeded(cell);

        ContextMenu contextMenu = new CustomMouseBehaviorContextMenu();
        contextMenu.setAutoHide(true);
        contextMenu.setHideOnEscape(true);
        contextMenu.setAutoFix(true);

        var selectedModels = getSelectedThumbCells(cell).stream().map(ThumbCell::getModel).collect(Collectors.toList());
        ModelMenuContributor.newContributor(getTabPane(), Config.getInstance(), recorder)
                .withStartStopCallback(m ->
                        Platform.runLater(() -> {
                            getTabPane().setCursor(Cursor.DEFAULT);
                            getThumbCell(m).ifPresent(ThumbCell::update);
                        })
                )
                .withFollowCallback((mdl, fllw, success) -> {
                    if (Boolean.TRUE.equals(fllw) && Boolean.TRUE.equals(success)) {
                        Platform.runLater(() -> getThumbCell(mdl).ifPresent(this::showAddToFollowedAnimation));
                    }
                    if (Boolean.FALSE.equals(fllw)) {
                        Platform.runLater(() -> {
                            if (this instanceof FollowedTab) {
                                getThumbCell(mdl).ifPresent(thumbCell -> grid.getChildren().remove(thumbCell));
                            }
                            selectedThumbCells.clear();
                        });
                    }
                })
                .withIgnoreCallback(m -> getThumbCell(m).ifPresent(thumbCell -> {
                    grid.getChildren().remove(thumbCell);
                    selectedThumbCells.remove(thumbCell);
                }))
                .afterwards(() -> selectedModels.forEach(m -> getThumbCell(m).ifPresent(ThumbCell::update)))
                .contributeToMenu(selectedModels, contextMenu);

        var useImageAsPortrait = new MenuItem("Use As Portrait");
        useImageAsPortrait.setOnAction(e -> new SetThumbAsPortraitAction(getTabPane(), cell.getModel(), cell.getImage()).execute());

        var refresh = new MenuItem("Refresh Overview");
        refresh.setOnAction(e -> refresh());

        contextMenu.getItems().addAll(useImageAsPortrait, refresh);
        var model = cell.getModel();
        if (model instanceof MyFreeCamsModel && Objects.equals(System.getenv("CTBREC_DEV"), "1")) {
            var debug = new MenuItem("debug");
            debug.setOnAction(e -> MyFreeCamsClient.getInstance().getSessionState(model));
            contextMenu.getItems().add(debug);
        }

        return contextMenu;
    }

    private Optional<ThumbCell> getThumbCell(Model model) {
        for (Node node : grid.getChildren()) {
            if (node instanceof ThumbCell thumbCell && Objects.equals(thumbCell.getModel(), model)) {
                return Optional.of(thumbCell);
            }
        }
        return Optional.empty();
    }

    private void refresh() {
        if (updateService.isRunning()) {
            updateService.cancel();
            updateService.reset();
            updateService.restart();
        }
    }

    /*
     * check, if other cells are selected, too. in that case, we have to disable menu items, which make sense only for single selections. but only do that, if
     * the popup has been triggered on a selected cell. otherwise remove the selection and show the normal menu
     */
    private void removeSelectionIfNeeded(ThumbCell cell) {
        if ((selectedThumbCells.size() > 1 || selectedThumbCells.size() == 1 && selectedThumbCells.get(0) != cell) && !cell.isSelected()) {
            removeSelection();
        }
    }

    private List<ThumbCell> getSelectedThumbCells(ThumbCell cell) {
        if (selectedThumbCells.isEmpty()) {
            return Collections.singletonList(cell);
        } else {
            return selectedThumbCells;
        }
    }

    protected void follow(List<ThumbCell> selection, boolean follow) {

        for (ThumbCell thumbCell : selection) {
            thumbCell.follow(follow).thenAccept(success -> {
                if (follow && Boolean.TRUE.equals(success)) {
                    showAddToFollowedAnimation(thumbCell);
                }
            });
        }
        if (!follow) {
            selectedThumbCells.clear();
        }
    }

    private void showAddToFollowedAnimation(ThumbCell thumbCell) {
        Platform.runLater(() -> {
            var tx = thumbCell.getLocalToParentTransform();
            var iv = new ImageView();
            iv.setFitWidth(thumbCell.getWidth());
            root.getChildren().add(iv);
            StackPane.setAlignment(iv, Pos.TOP_LEFT);
            iv.setImage(thumbCell.getImage());
            double scrollPaneTopLeft = scrollPane.getVvalue() * (grid.getHeight() - scrollPane.getViewportBounds().getHeight());
            double offsetInViewPort = tx.getTy() - scrollPaneTopLeft;
            var duration = 500;
            var translate = new TranslateTransition(Duration.millis(duration), iv);
            translate.setFromX(0);
            translate.setFromY(0);
            translate.setByX(-tx.getTx() - 200);
            var tabProvider = SiteUiFactory.getUi(site).getTabProvider();
            var followedTab = tabProvider.getFollowedTab();
            translate.setByY(-offsetInViewPort + getFollowedTabYPosition(followedTab));
            StackPane.setMargin(iv, new Insets(offsetInViewPort, 0, 0, tx.getTx()));
            translate.setInterpolator(Interpolator.EASE_BOTH);
            var fade = new FadeTransition(Duration.millis(duration), iv);
            fade.setFromValue(1);
            fade.setToValue(.3);
            var scale = new ScaleTransition(Duration.millis(duration), iv);
            scale.setToX(0.1);
            scale.setToY(0.1);
            var pt = new ParallelTransition(translate, scale);
            pt.play();
            pt.setOnFinished(evt -> root.getChildren().remove(iv));
            var blink = new FollowTabBlinkTransition(followedTab);
            blink.play();
        });
    }

    private double getFollowedTabYPosition(Tab followedTab) {
        var tabPane = getTabPane();
        int idx = Math.max(0, tabPane.getTabs().indexOf(followedTab));
        for (Node node : tabPane.getChildrenUnmodifiable()) {
            Parent p = (Parent) node;
            for (Node child : p.getChildrenUnmodifiable()) {
                if (child.getStyleClass().contains("headers-region")) {
                    Parent tabContainer = (Parent) child;
                    Node tab = tabContainer.getChildrenUnmodifiable().get(tabContainer.getChildrenUnmodifiable().size() - idx - 1);
                    return tab.getLayoutX() - 85;
                }
            }
        }
        return 0;
    }

    private final EventHandler<MouseEvent> mouseClickListener = e -> {
        ThumbCell cell = (ThumbCell) e.getSource();
        if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) {
            cell.setSelected(false);
            cell.startPlayer();
        } else if (e.getButton() == MouseButton.PRIMARY && e.isControlDown()) {
            if (popup == null) {
                cell.setSelected(!cell.isSelected());
            }
        } else if (e.getButton() == MouseButton.PRIMARY) {
            removeSelection();
        }
    };

    protected void onFail(WorkerStateEvent event) {
        if (event.getSource().getException() != null) {
            if (event.getSource().getException() instanceof SocketTimeoutException) {
                LOG.debug("Fetching model list timed out");
                errorLabel.setText("Timeout while updating");
            } else {
                LOG.error("Couldn't update model list", event.getSource().getException());
                errorLabel.setText("Error while updating " + event.getSource().getException().getLocalizedMessage());
            }
        } else {
            LOG.error("Couldn't update model list {}", event.getEventType());
            errorLabel.setText("Couldn't update model list " + event.getEventType());
        }
        grid.getChildren().removeAll(progressIndicator, noResultsFound, errorLabel);
        grid.getChildren().add(errorLabel);
    }

    void filter() {
        filteredThumbCells.sort((c1, c2) -> {
            if (c1.getIndex() < c2.getIndex())
                return -1;
            if (c1.getIndex() > c2.getIndex())
                return 1;
            return c1.getModel().getName().compareTo(c2.getModel().getName());
        });

        if (filter == null || filter.isEmpty()) {
            for (ThumbCell thumbCell : filteredThumbCells) {
                insert(thumbCell);
            }
            filteredThumbCells.clear();
        } else {
            // remove the ones from grid, which don't match
            for (Iterator<Node> iterator = grid.getChildren().iterator(); iterator.hasNext(); ) {
                var node = iterator.next();
                if (node instanceof ThumbCell cell) {
                    var m = cell.getModel();
                    if (!matches(m, filter, false)) {
                        iterator.remove();
                        filteredThumbCells.add(cell);
                        cell.setSelected(false);
                    }
                }
            }

            // add the ones, which might have been filtered before, but now match
            for (Iterator<ThumbCell> iterator = filteredThumbCells.iterator(); iterator.hasNext(); ) {
                var thumbCell = iterator.next();
                var m = thumbCell.getModel();
                if (matches(m, filter, false)) {
                    iterator.remove();
                    insert(thumbCell);
                }
            }
        }

        if (grid.getChildren().size() > 1 && grid.getChildren().contains(noResultsFound)) {
            grid.getChildren().remove(noResultsFound);
        } else if (grid.getChildren().isEmpty()) {
            grid.getChildren().add(noResultsFound);
        }
    }

    private void moveActiveRecordingsToFront() {
        List<ThumbCell> thumbsToMove = new ArrayList<>();
        ObservableList<Node> thumbs = grid.getChildren();
        for (int i = thumbs.size() - 1; i >= 0; i--) {
            var node = thumbs.get(i);
            if (node instanceof ThumbCell) {
                ThumbCell thumb = (ThumbCell) thumbs.get(i);
                if (recorder.isTracked(thumb.getModel())) {
                    thumbs.remove(i);
                    thumbsToMove.add(0, thumb);
                }
            }
        }
        thumbs.addAll(0, thumbsToMove);
    }

    private void insert(ThumbCell thumbCell) {
        if (grid.getChildren().contains(thumbCell)) {
            return;
        }

        if (thumbCell.getIndex() < grid.getChildren().size() - 1) {
            grid.getChildren().add(thumbCell.getIndex(), thumbCell);
        } else {
            grid.getChildren().add(thumbCell);
        }
    }

    private boolean matches(Model m, String filter, Boolean anyMatch) {
        try {
            String[] tokens = filter.split(" ");
            var tokensMissing = false;
            for (String token : tokens) {
            	if (anyMatch && modelPropertiesMatchToken(token, m)) {
            		return true;
            	} else if (!modelPropertiesMatchToken(token, m)) {
                    tokensMissing = true;
                }
            }
            return !tokensMissing;
        } catch (NumberFormatException | ExecutionException | IOException e) {
            LOG.error("Error while filtering model list", e);
            return false;
        }
    }

    private boolean modelPropertiesMatchToken(String token, Model m) throws IOException, ExecutionException {
        int[] resolution = Optional.ofNullable(ThumbCell.resolutionCache.getIfPresent(m)).orElse(new int[2]);
        String searchText = createSearchText(m);
        var tokensMissing = false;
        if (token.matches(">\\d+")) {
            var res = Integer.parseInt(token.substring(1));
            if (resolution[1] < res) {
                tokensMissing = true;
            }
        } else if (token.matches("<\\d+")) {
            var res = Integer.parseInt(token.substring(1));
            if (resolution[1] > res) {
                tokensMissing = true;
            }
        } else if (token.equals("public")) {
            if (m.getOnlineState(true) != ctbrec.Model.State.ONLINE) {
                tokensMissing = true;
            }
        } else {
            var negated = false;
            if (token.startsWith("!")) {
                negated = true;
                token = token.substring(1);
            }
            boolean tokenFound = searchText.toLowerCase().contains(token.toLowerCase());
            tokensMissing = !tokenFound && !negated || tokenFound && negated;
        }
        return !tokensMissing;
    }

    private String createSearchText(Model m) {
        var searchTextBuilder = new StringBuilder(m.getName());
        searchTextBuilder.append(' ');
        searchTextBuilder.append(m.getDisplayName());
        searchTextBuilder.append(' ');
        for (String tag : m.getTags()) {
            searchTextBuilder.append(tag).append(' ');
        }
        int[] resolution = Optional.ofNullable(ThumbCell.resolutionCache.getIfPresent(m)).orElse(new int[2]);
        searchTextBuilder.append(resolution[1]);
        searchTextBuilder.append(' ');
        searchTextBuilder.append(Optional.ofNullable(m.getDescription()).orElse(""));
        searchTextBuilder.append(' ');
        searchTextBuilder.append(recorder.getModelGroup(m).map(ModelGroup::getName).orElse(""));
        return searchTextBuilder.toString().trim();
    }

    public void setRecorder(Recorder recorder) {
        this.recorder = recorder;
        popoverTreeList.setRecorder(recorder);
    }

    @Override
    public void selected() {
        grid.getChildren().removeAll(noResultsFound, errorLabel);
        if (grid.getChildren().isEmpty()) {
            grid.getChildren().add(progressIndicator);
        }
        queue.clear();
        if (updateService != null) {
            var s = updateService.getState();
            if (s != State.SCHEDULED && s != State.RUNNING) {
                updateService.reset();
                updateService.restart();
            }
        }
        updateThumbSize();
        updateTokenLabel();
    }

    private void updateTokenLabel() {
        if (tokenBalance != null) {
            tokenBalance.loadBalance();
        }
    }

    @Override
    public void deselected() {
        if (updateService != null) {
            updateService.cancel();
        }
        queue.clear();

        for (Iterator<Node> iterator = grid.getChildren().iterator(); iterator.hasNext(); ) {
            var node = iterator.next();
            if (node instanceof ThumbCell thumbCell) {
                thumbCell.releaseResources();
                iterator.remove();
            }
        }
    }

    void suspendUpdates(boolean suspend) {
        this.updatesSuspended = suspend;
    }

    private void removeSelection() {
        while (!selectedThumbCells.isEmpty()) {
            selectedThumbCells.get(0).setSelected(false);
        }
    }

    private static int threadCounter = 0;

    private static ThreadFactory createThreadFactory() {
        return r -> {
            var t = new Thread(r);
            t.setDaemon(true);
            t.setPriority(Thread.MIN_PRIORITY);
            t.setName("ResolutionDetector-" + threadCounter++);
            return t;
        };
    }

    public void setImageAspectRatio(double imageAspectRatio) {
        this.imageAspectRatio = imageAspectRatio;
    }

    public BooleanProperty preserveAspectRatioProperty() {
        return preserveAspectRatio;
    }
}
