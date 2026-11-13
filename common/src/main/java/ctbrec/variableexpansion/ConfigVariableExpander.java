package ctbrec.variableexpansion;

import ctbrec.Config;
import ctbrec.variableexpansion.functions.AntlrSyntacErrorAdapter;

import java.util.Optional;

public class ConfigVariableExpander extends AbstractVariableExpander {

    public ConfigVariableExpander(Config config, AntlrSyntacErrorAdapter errorListener) {
        super(errorListener);
        placeholderValueSuppliers.put("recordingsDir", Optional.of(config.getSettings().recordingsDir));
    }

    public String expand(String input) {
        return fillInPlaceHolders(input, placeholderValueSuppliers);
    }
}
