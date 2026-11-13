package ctbrec.variableexpansion.functions;

import ctbrec.StringUtil;
import ctbrec.variableexpansion.VarArgsFunction;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OrElse implements VarArgsFunction<Object, String> {

    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\$\\{[^${}]+}");

    @Override
    public String apply(Object... params) {
        if (params == null || params.length < 1) {
            return "";
        }
        String fallback = "";
        if (params.length >= 2) {
            fallback = String.valueOf(params[1]);
        }

        Optional<String> result = Optional.ofNullable(params[0]).map(String::valueOf);
        if (result.isPresent() && StringUtil.isNotBlank(result.get())) {
            String param = result.get();
            Matcher m = VARIABLE_PATTERN.matcher(param);
            if (m.find()) {
                return fallback;
            } else {
                return result.get();
            }
        } else {
            return fallback;
        }
    }
}
