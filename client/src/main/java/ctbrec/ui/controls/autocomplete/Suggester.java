package ctbrec.ui.controls.autocomplete;

import java.util.Optional;

public interface Suggester {

    public Optional<String> startsWith(String search);
    public Optional<String> fulltext(String search);
}