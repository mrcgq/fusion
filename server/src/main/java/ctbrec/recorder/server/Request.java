package ctbrec.recorder.server;

import ctbrec.Model;
import ctbrec.ModelGroup;
import ctbrec.Recording;
import lombok.Data;

@Data
public class Request {
    private String action;
    private Model model;
    private Recording recording;
    private ModelGroup modelGroup;
}
