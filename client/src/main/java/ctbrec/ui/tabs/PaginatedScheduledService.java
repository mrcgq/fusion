package ctbrec.ui.tabs;

import ctbrec.Model;
import javafx.concurrent.ScheduledService;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public abstract class PaginatedScheduledService extends ScheduledService<List<Model>> {

    protected int page = 1;
}
