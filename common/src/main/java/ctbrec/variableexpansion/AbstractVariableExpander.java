package ctbrec.variableexpansion;

import ctbrec.variableexpansion.antlr.PostProcessingLexer;
import ctbrec.variableexpansion.antlr.PostProcessingParser;
import ctbrec.variableexpansion.functions.AntlrSyntacErrorAdapter;
import lombok.extern.slf4j.Slf4j;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ConsoleErrorListener;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
abstract class AbstractVariableExpander {

    protected Map<String, Optional<Object>> placeholderValueSuppliers = new HashMap<>();

    protected AntlrSyntacErrorAdapter errorListener;

    AbstractVariableExpander(@Nullable AntlrSyntacErrorAdapter errorListener) {
        this.errorListener = errorListener;
    }

    protected String fillInPlaceHolders(String input, Map<String, Optional<Object>> variables) {
        try (StringReader reader = new StringReader(input)) {
            CharStream s = CharStreams.fromReader(reader);
            PostProcessingLexer lexer = new PostProcessingLexer(s);
            lexer.removeErrorListener(ConsoleErrorListener.INSTANCE);
            CommonTokenStream tokens = new CommonTokenStream(lexer);
            PostProcessingParser parser = new PostProcessingParser(tokens);
            parser.removeErrorListener(ConsoleErrorListener.INSTANCE);
            Optional.ofNullable(errorListener).ifPresent(parser::addErrorListener);
            PostProcessingParser.LineContext ctx = parser.line();
            ParserVisitor visitor = new ParserVisitor(variables);
            return visitor.visit(ctx);
        } catch (IOException e) {
            log.error("Couldn't fill in placeholders", e);
        }

        return input;
    }

    public Map<String, Optional<Object>> getPlaceholderValueSuppliers() {
        return placeholderValueSuppliers;
    }
}
