package ctbrec.ui.controls.table;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import javafx.scene.control.TableColumn.SortType;

public interface TableViewStateStore {
    List<String> loadColumnOrder();
    Map<String, Double> loadColumnWidths();
    Map<String, Boolean> loadColumnVisibility();
    String loadSortColumn();
    SortType loadSortType();

    void saveColumnOrder(List<String> columnIds) throws IOException;
    void saveColumnWidths(Map<String, Double> columnIdsToWidth) throws IOException;
    void saveColumnVisibility(Map<String, Boolean> columnIdsToVisibility) throws IOException;
    void saveSorting(String columnId, SortType sortType) throws IOException;
    String getName();
}
