package ctbrec.variableexpansion.functions;

import ctbrec.StringUtil;
import ctbrec.variableexpansion.VarArgsFunction;

import java.util.Optional;

public class Sanitize implements VarArgsFunction<Object, String> {
    @Override
    public String apply(Object... params) {
        if (params == null || params.length == 0) {
            return "";
        }
        return Optional.ofNullable(params).map(p -> p[0]).map(String.class::cast).map(StringUtil::sanitize).orElse("");
    }
}
