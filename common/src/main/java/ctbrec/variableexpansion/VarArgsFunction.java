package ctbrec.variableexpansion;

@FunctionalInterface
public interface VarArgsFunction<I, O> {

    O apply(I... params);
}
