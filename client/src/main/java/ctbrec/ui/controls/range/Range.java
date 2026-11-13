package ctbrec.ui.controls.range;

import java.util.List;

public interface Range<T> {

    public T getMinimum();
    public T getMaximum();
    public List<T> getTicks();
}
