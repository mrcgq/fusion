package ctbrec.ui.settings.api;

import ctbrec.ui.controls.SearchBox;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Insets;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.ScrollPane.ScrollBarPolicy;
import javafx.scene.layout.*;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import static java.util.Optional.ofNullable;

@Slf4j
public class Preferences {

    private final Category[] categories;
    private final PreferencesStorage preferencesStorage;

    private TreeView<Category> categoryTree;

    private Runnable restartRequiredCallback = () -> {
    };

    private Preferences(PreferencesStorage preferencesStorage, Category... categories) {
        this.preferencesStorage = preferencesStorage;
        this.categories = categories;
        for (Category category : categories) {
            assignPreferencesStorage(category, preferencesStorage);
        }
    }

    private void assignPreferencesStorage(Category cat, PreferencesStorage preferencesStorage) {
        if (cat.hasSubCategories()) {
            for (Category sub : cat.getSubCategories()) {
                assignPreferencesStorage(sub, preferencesStorage);
            }
        } else {
            for (Group group : cat.getGroups()) {
                for (Setting setting : group.getSettings()) {
                    setting.setPreferencesStorage(preferencesStorage);
                }
            }
        }
    }

    public static Preferences of(PreferencesStorage preferencesStorage, Category... categories) {
        return new Preferences(preferencesStorage, categories);
    }

    public void save() throws IOException {
        preferencesStorage.save(this);
    }

    public Region getView(boolean withNavigation) {
        for (Category category : categories) {
            initializeCategory(category);
        }
        var search = new SearchBox(true);
        search.textProperty().addListener(this::filterTree);
        TreeItem<Category> categoryTreeItems = createCategoryTree(categories, new TreeItem<>(), null);
        categoryTree = new TreeView<>(categoryTreeItems);
        categoryTree.showRootProperty().set(false);
        var leftSide = new VBox(search, categoryTree);
        VBox.setVgrow(categoryTree, Priority.ALWAYS);
        VBox.setMargin(search, new Insets(2));
        VBox.setMargin(categoryTree, new Insets(2));

        var main = new BorderPane();
        if (withNavigation) {
            main.setLeft(leftSide);
        }
        main.setCenter(new Label("Center"));
        BorderPane.setMargin(leftSide, new Insets(2));
        categoryTree.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                Category cat = newV.getValue();
                Node gui = cat.getGuiOrElse(() -> createGui(cat));
                ScrollPane scrollPane = new ScrollPane(gui);
                scrollPane.setPadding(new Insets(20));
                scrollPane.setHbarPolicy(ScrollBarPolicy.NEVER);
                scrollPane.setFitToWidth(true);
                scrollPane.setFitToHeight(true);
                main.setCenter(scrollPane);
            }
        });
        categoryTree.getSelectionModel().select(0);
        main.setBorder(Border.EMPTY);
        return main;
    }

    public Region getView() {
        return getView(true);
    }

    private void filterTree(ObservableValue<? extends String> obs, String oldV, String newV) {
        String q = ofNullable(newV).orElse("").toLowerCase().trim();
        TreeItem<Category> filteredCategoryTree = createCategoryTree(categories, new TreeItem<>(), q);
        categoryTree.setRoot(filteredCategoryTree);
        expandAll(categoryTree.getRoot());

        TreeItem<Category> parent = categoryTree.getRoot();
        while (!parent.getChildren().isEmpty()) {
            parent = parent.getChildren().get(0);
            categoryTree.getSelectionModel().select(parent);
        }

        for (Category category : categories) {
            if (q.length() > 2) {
                HighlightingSupport.highlightMatches(category, q);
            } else {
                HighlightingSupport.removeHighlights(category);
            }
        }
    }

    private void initializeCategory(Category category) {
        category.getGuiOrElse(() -> createGui(category));
    }

    private void expandAll(TreeItem<Category> treeItem) {
        treeItem.setExpanded(true);
        for (TreeItem<Category> child : treeItem.getChildren()) {
            expandAll(child);
        }
    }

    private Node createGui(Category cat) {
        try {
            if (cat.hasSubCategories()) {
                return new Label(cat.getName());
            } else if (cat.hasGroups()) {
                return createPaneWithGroups(cat);
            } else {
                return createGrid(cat.getGroups()[0].getSettings());
            }
        } catch (Exception e) {
            log.error("Error creating the GUI", e);
            return new Label(e.getLocalizedMessage());
        }
    }

    private Node createPaneWithGroups(Category cat) {
        var pane = new VBox();
        for (Group grp : cat.getGroups()) {
            var groupLabel = new Label(grp.getName());
            groupLabel.getStyleClass().add("settings-group-label");
            VBox.setMargin(groupLabel, new Insets(20, 0, 10, 20));
            pane.getChildren().add(groupLabel);
            Node parameterGrid = createGrid(grp.getSettings());
            pane.getChildren().add(parameterGrid);
            VBox.setMargin(parameterGrid, new Insets(0, 0, 0, 40));
        }
        return pane;
    }

    private Node createGrid(Setting[] settings) {
        var pane = new GridPane();
        pane.setHgap(2);
        pane.vgapProperty().bind(pane.hgapProperty());
        var row = 0;
        for (Setting setting : settings) {
            var node = setting.getGui();
            var label = new Label(setting.getName());
            label.setMinHeight(34);
            label.labelForProperty().set(node);
            label.setTooltip(new Tooltip(setting.getName()));
            pane.addRow(row++, label, node);
            GridPane.setVgrow(label, Priority.ALWAYS);
            GridPane.setValignment(label, VPos.CENTER);
            GridPane.setMargin(node, new Insets(5, 0, 5, 10));
            GridPane.setValignment(node, VPos.CENTER);
            GridPane.setHgrow(node, Priority.ALWAYS);
        }
        return pane;
    }

    /**
     * Creates a tree of the given categories. Filters out categories, which don't match the filter
     *
     * @param filter may be null
     */
    private TreeItem<Category> createCategoryTree(Category[] categories, TreeItem<Category> parent, String filter) {
        for (Category category : categories) {
            TreeItem<Category> child = new TreeItem<>(category);
            if (category.hasSubCategories()) {
                createCategoryTree(category.getSubCategories(), child, filter);
                if (!child.getChildren().isEmpty()) {
                    parent.getChildren().add(child);
                }
            } else if (category.contains(filter)) {
                parent.getChildren().add(child);
            }
        }
        return parent;
    }

    public void expandTree() {
        expandAll(categoryTree.getRoot());
    }

    public void traverse(Consumer<Setting> visitor) {
        for (Category category : categories) {
            visit(category, visitor);
        }
    }

    private void visit(Category cat, Consumer<Setting> visitor) {
        for (Group group : cat.getGroups()) {
            for (Setting setting : group.getSettings()) {
                visitor.accept(setting);
            }
        }
        if (cat.hasSubCategories()) {
            for (Category subcat : cat.getSubCategories()) {
                visit(subcat, visitor);
            }
        }
    }

    public Optional<Setting> getSetting(String key) {
        var search = new SettingSearchVisitor(key);
        traverse(search);
        return search.getResult();
    }

    private static class SettingSearchVisitor implements Consumer<Setting> {
        Setting result;
        private final String key;

        public SettingSearchVisitor(String key) {
            this.key = key;
        }

        @Override
        public void accept(Setting s) {
            if (Objects.equals(key, ofNullable(s.getKey()).orElse(""))) {
                result = s;
            }
        }

        public Optional<Setting> getResult() {
            return Optional.ofNullable(result);
        }
    }

    public void onRestartRequired(Runnable callback) {
        this.restartRequiredCallback = callback;
    }

    public Runnable getRestartRequiredCallback() {
        return restartRequiredCallback;
    }
}
