package ctbrec.ui.controls.table;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ctbrec.StringUtil;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

public class StatePersistingTableView<T> extends TableView<T> {

    private static final Logger LOG = LoggerFactory.getLogger(StatePersistingTableView.class);

    private Instant initialized;
    private TableViewStateStore stateStore;

    public StatePersistingTableView(TableViewStateStore stateStore) {
        super();
        this.stateStore = stateStore;
        setTableMenuButtonVisible(true);
        initialized = Instant.now();
    }

    public void restoreState() {
        restoreColumnOrder();
        restoreColumnWidths();
        restoreColumnVisibility();
        restoreSorting();

        addStateListeners();
    }

    private void addStateListeners() {
        // column order
        getColumns().addListener((ListChangeListener<? super TableColumn<T, ?>>) c -> saveColumnOrder());
        // column visibility
        getColumns().forEach(tc -> tc.visibleProperty().addListener((obs, oldV, newV) -> saveColumnVisibility()));
        // column width
        getColumns().forEach(tc -> tc.widthProperty().addListener((obs, oldV, newV) -> saveColumnWidths()));
        // sort order
        getSortOrder().addListener((ListChangeListener<? super TableColumn<T, ?>>) c -> saveSorting());
        getColumns().forEach(tc -> tc.sortTypeProperty().addListener((obs, oldV, newV) -> saveSorting()));
    }

    protected void restoreColumnVisibility() {
        Map<String, Boolean> visibility = stateStore.loadColumnVisibility();
        for (TableColumn<T, ?> tc : getColumns()) {
            tc.setVisible(visibility.getOrDefault(tc.getId(), tc.isVisible()));
        }
    }

    protected void restoreColumnWidths() {
        Map<String, Double> widths = stateStore.loadColumnWidths();
        for (TableColumn<T, ?> tc : getColumns()) {
            tc.setPrefWidth(widths.getOrDefault(tc.getId(), tc.getWidth()));
        }
    }

    protected void restoreColumnOrder() {
        List<String> order = stateStore.loadColumnOrder();
        ObservableList<TableColumn<T, ?>> tableColumns = getColumns();
        for (var i = 0; i < order.size(); i++) {
            for (var j = 0; j < getColumns().size(); j++) {
                if (Objects.equals(order.get(i), tableColumns.get(j).getId())) {
                    TableColumn<T, ?> col = tableColumns.get(j);
                    tableColumns.remove(j);
                    tableColumns.add(Math.min(i, tableColumns.size()), col);
                }
            }
        }
    }

    protected void restoreSorting() {
        String sortCol = stateStore.loadSortColumn();
        if (StringUtil.isNotBlank(sortCol)) {
            for (TableColumn<T, ?> col : getColumns()) {
                if (Objects.equals(sortCol, col.getId())) {
                    col.setSortType(stateStore.loadSortType());
                    getSortOrder().clear();
                    getSortOrder().add(col);
                    break;
                }
            }
        }
    }

    public void saveState() {
        saveColumnOrder();
        saveColumnWidths();
        saveColumnVisibility();
        saveSorting();
    }

    protected void saveSorting() {
        if (!getSortOrder().isEmpty()) {
            TableColumn<T, ?> col = getSortOrder().get(0);
            saveSetting(() -> stateStore.saveSorting(col.getId(), col.getSortType()));
        } else {
            saveSetting(() -> stateStore.saveSorting(null, stateStore.loadSortType()));
        }
    }

    protected void saveColumnVisibility() {
        saveSetting(() -> {
            Map<String, Boolean> columnIdToVisible = getColumns().stream().collect(Collectors.toMap(TableColumn::getId, TableColumn::isVisible));
            stateStore.saveColumnVisibility(columnIdToVisible);
        });
    }

    protected void saveColumnWidths() {
        saveSetting(() -> {
            Map<String, Double> columnIdToWidth = getColumns().stream().collect(Collectors.toMap(TableColumn::getId, TableColumn::getWidth));
            stateStore.saveColumnWidths(columnIdToWidth);
        });
    }

    protected void saveColumnOrder() {
        saveSetting(() -> {
            List<String> tableIds = getColumns().stream().map(TableColumn::getId).collect(Collectors.toList());
            stateStore.saveColumnOrder(tableIds);
        });
    }

    private void saveSetting(ThrowingRunnable r) {
        if (Duration.between(initialized, Instant.now()).getSeconds() > 1) {
            try {
                r.run();
            } catch (Exception e) {
                LOG.error("Couldn't safe table view state with prefix {}", stateStore.getName(), e);
            }
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception; // NOSONAR
    }
}
