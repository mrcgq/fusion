package ctbrec.variableexpansion;

import ctbrec.Model;
import ctbrec.ModelGroup;
import ctbrec.StringUtil;
import ctbrec.notes.ModelNotesService;
import ctbrec.recorder.Recorder;
import ctbrec.sites.Site;
import ctbrec.variableexpansion.functions.AntlrSyntacErrorAdapter;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;
import java.util.UUID;

import static java.util.Optional.ofNullable;

@Slf4j
public class ModelVariableExpander extends AbstractVariableExpander {

    public ModelVariableExpander(Model model, ModelNotesService modelNotesService, Recorder recorder, AntlrSyntacErrorAdapter errorListener) {
        super(errorListener);
        Optional<ModelGroup> modelGroup = Optional.ofNullable(recorder).flatMap(r -> r.getModelGroup(model));
        placeholderValueSuppliers.put("modelName", ofNullable(model.getName()));
        placeholderValueSuppliers.put("modelDisplayName", ofNullable(model.getDisplayName()));
        placeholderValueSuppliers.put("modelNotes", getSanitizedModelNotes(modelNotesService, model));
        placeholderValueSuppliers.put("siteName", ofNullable(model).map(Model::getSite).map(Site::getName));
        placeholderValueSuppliers.put("modelGroupName", modelGroup.map(ModelGroup::getName));
        placeholderValueSuppliers.put("modelGroupId", modelGroup.map(ModelGroup::getId).map(UUID::toString));
    }

    public String expand(String input) {
        return fillInPlaceHolders(input, placeholderValueSuppliers);
    }

    private Optional<Object> getSanitizedModelNotes(ModelNotesService modelNotesService, Model m) {
        try {
            return modelNotesService.loadModelNotes(m.getUrl()).map(StringUtil::sanitize);
        } catch (Exception e) {
            log.warn("Could not load model notes", e);
            return Optional.empty();
        }
    }
}
