package ctbrec.variableexpansion.functions;

import ctbrec.variableexpansion.VarArgsFunction;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;

public class Format implements VarArgsFunction<Object, String> {
    @Override
    public String apply(Object... params) {
        return Optional.ofNullable(params).map(p -> {
                    try {
                        if (p.length == 0) {
                            return "";
                        }
                        String format = "yyyy-MM-dd_HH-mm-ss";
                        if (p.length > 1) {
                            format = (String) p[1];
                        }
                        ZonedDateTime zdt;
                        if (p != null && p.length > 0 && p[0] instanceof String date) {
                            try {
                                zdt = ZonedDateTime.parse(date);
                            } catch (DateTimeParseException e) {
                                return "";
                            }
                        } else {
                            zdt = (ZonedDateTime) p[0];
                        }
                        return DateTimeFormatter.ofPattern(format).format(zdt);
                    } catch (Exception e) {
                        return "";
                    }
                })
                .orElse("");
    }
}
