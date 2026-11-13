package ctbrec.ui.controls.table;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import ctbrec.Config;
import ctbrec.Settings;
import ctbrec.StringUtil;
import javafx.scene.control.TableColumn.SortType;

public class SettingTableViewStateStore implements TableViewStateStore {

    private final Config config;
    private final String settingPrefix;
    private final String columnOrderSetting;
    private final String columnWidthSetting;
    private final String columnVisibilitySetting;
    private final String sortColumnSetting;
    private final String sortTypeSetting;

    public SettingTableViewStateStore(Config config, String settingPrefix) {
        this.config = config;
        this.settingPrefix = settingPrefix;
        columnOrderSetting = settingPrefix + "ColumnOrder";
        columnWidthSetting = settingPrefix + "ColumnWidth";
        columnVisibilitySetting = settingPrefix + "ColumnVisibility";
        sortColumnSetting = settingPrefix + "SortColumn";
        sortTypeSetting = settingPrefix + "SortType";
    }

    @Override
    public List<String> loadColumnOrder() {
        return loadSetting(columnOrderSetting);
    }

    @Override
    public Map<String, Double> loadColumnWidths() {
        return loadSetting(columnWidthSetting);
    }

    @Override
    public Map<String, Boolean> loadColumnVisibility() {
        return loadSetting(columnVisibilitySetting);
    }

    @Override
    public String loadSortColumn() {
        return loadSetting(sortColumnSetting);
    }

    @Override
    public SortType loadSortType() {
        String sortType = loadSetting(sortTypeSetting);
        if (StringUtil.isBlank(sortType)) {
            return SortType.ASCENDING;
        } else {
            return SortType.valueOf(sortType);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T loadSetting(String name) {
        try {
            Field field = Settings.class.getDeclaredField(name);
            return (T) field.get(config.getSettings());
        } catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException e) {
            throw new TableViewStateStoreException(e);
        }
    }

    private void setSetting(String name, Object value) {
        try {
            Field field = Settings.class.getDeclaredField(name);
            field.set(config.getSettings(), value); // NOSONAR
        } catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException e) {
            throw new TableViewStateStoreException(e);
        }
    }

    @Override
    public void saveColumnOrder(List<String> columnIds) throws IOException {
        setSetting(columnOrderSetting, columnIds);
        save();
    }

    @Override
    public void saveColumnWidths(Map<String, Double> columnIdsToWidth) throws IOException {
        setSetting(columnWidthSetting, columnIdsToWidth);
        save();
    }

    @Override
    public void saveColumnVisibility(Map<String, Boolean> columnIdsToVisibility) throws IOException {
        setSetting(columnVisibilitySetting, columnIdsToVisibility);
        save();
    }

    @Override
    public void saveSorting(String columnId, SortType sortType) throws IOException {
        setSetting(sortColumnSetting, columnId);
        setSetting(sortTypeSetting, sortType.name());
        save();
    }

    private void save() throws IOException {
        config.save();
    }

    @Override
    public String getName() {
        return settingPrefix;
    }
}
