package ctbrec.ui.controls.autocomplete;

import java.util.Optional;

import javafx.collections.ObservableList;

public class ObservableListSuggester implements Suggester {

    private ObservableList<?> suggestions;

    public ObservableListSuggester(ObservableList<?> suggestions) {
        this.suggestions = suggestions;
    }

    @Override
    public Optional<String> startsWith(String search) {
        for (Object sug : suggestions) {
            boolean startsWith = sug.toString().toLowerCase().startsWith(search.toLowerCase());
            if (startsWith) {
                return Optional.of(sug.toString());
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<String> fulltext(String search) {
        for (Object sug : suggestions) {
            boolean startsWith = sug.toString().toLowerCase().contains(search.toLowerCase());
            if (startsWith) {
                return Optional.of(sug.toString());
            }
        }
        return Optional.empty();
    }

}
