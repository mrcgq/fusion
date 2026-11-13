package ctbrec.variableexpansion.functions;

import ctbrec.variableexpansion.VarArgsFunction;

import java.util.Optional;

public class Trim implements VarArgsFunction<Object, String> {
    @Override
    public String apply(Object... params) {
        if (params == null || params.length == 0) {
            return "";
        }
        return Optional.ofNullable(params).map(sa -> sa[0]).map(String.class::cast).map(String::trim).orElse("");
    }
}
