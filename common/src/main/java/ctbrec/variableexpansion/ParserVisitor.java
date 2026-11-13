package ctbrec.variableexpansion;

import ctbrec.variableexpansion.antlr.PostProcessingBaseVisitor;
import ctbrec.variableexpansion.antlr.PostProcessingParser;
import ctbrec.variableexpansion.functions.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ParserVisitor extends PostProcessingBaseVisitor<String> {

    private final Map<String, VarArgsFunction<Object, String>> functions = new HashMap<>();
    private final Map<String, Optional<Object>> variables;

    public ParserVisitor(Map<String, Optional<Object>> variables) {
        this.variables = variables;
        functions.put("trim", new Trim());
        functions.put("upper", new Upper());
        functions.put("lower", new Lower());
        functions.put("format", new Format());
        functions.put("capitalize", new Capitalize());
        functions.put("sanitize", new Sanitize());
        functions.put("orElse", new OrElse());
    }

    @Override
    public String visitFunctionCall(PostProcessingParser.FunctionCallContext ctx) {
        String identifier = ctx.identifier().getText();
        VarArgsFunction<Object, String> function = functions.get(identifier);
        if (function != null) {
            List<PostProcessingParser.ParameterContext> parameters = ctx.parameter();
            List<String> params = parameters.stream().map(this::visitParameter).toList();
            return function.apply(params.toArray(new Object[0]));
        } else {
            return ctx.getText();
        }
    }

    @Override
    public String visitVariable(PostProcessingParser.VariableContext ctx) {
        String identifier = ctx.identifier().getText();
        Optional<Object> value = variables.get(identifier);
        if (value != null && value.isPresent()) { // NOSONAR
            return value.get().toString();
        } else {
            return ctx.getText();
        }
    }

    @Override
    public String visitText(PostProcessingParser.TextContext ctx) {
        return ctx.getText();
    }

    @Override
    protected String aggregateResult(String aggregate, String nextResult) {
        return Optional.ofNullable(aggregate).orElse("") + Optional.ofNullable(nextResult).orElse("");
    }
}
