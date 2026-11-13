package ctbrec.ui.tabs.recorded;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import ctbrec.Config;
import ctbrec.MigrateModel5_1_2;
import ctbrec.Model;
import ctbrec.ModelGroup;
import ctbrec.image.LocalPortraitStore;
import ctbrec.image.PortraitStore;
import ctbrec.image.RemotePortraitStore;
import ctbrec.io.HttpException;
import ctbrec.io.json.ObjectMapperFactory;
import ctbrec.io.json.dto.ModelDto;
import ctbrec.io.json.mapper.MappingException;
import ctbrec.io.json.mapper.ModelMapper;
import ctbrec.notes.LocalModelNotesService;
import ctbrec.notes.ModelNotesService;
import ctbrec.notes.RemoteModelNotesService;
import ctbrec.sites.Site;
import ctbrec.sites.SiteUtil;
import ctbrec.ui.CamrecApplication;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONWriter;
import org.mapstruct.factory.Mappers;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class ModelImportExport {

    private static final String KEY_NOTES = "notes";
    private static final String KEY_GROUPS = "groups";
    private static final String KEY_PORTRAITS = "portraits";


    enum ExportIncludes {
        NOTES,
        GROUPS,
        PORTRAITS
    }

    private static final ObjectMapper mapper = ObjectMapperFactory.getMapper();

    public record ExportOptions(Set<ExportIncludes> includes, File targetFile) {
    }

    private ModelImportExport() {
    }

    public static void exportTo(List<Model> models, Set<ModelGroup> groups, Config config, ExportOptions exportOptions) throws IOException {
        StringBuilder sb = new StringBuilder();
        JSONWriter writer = new JSONWriter(sb);

        writer.object();
        writer.key("models");
        writer.array();
        var modelArray = models.stream()
                .map(Mappers.getMapper(ModelMapper.class)::toDto)
                .map(dto -> {
                    try {
                        return mapper.writeValueAsString(dto);
                    } catch (JsonProcessingException e) {
                        log.error("Error while serializing model {}", dto);
                        throw new MappingException(e);
                    }
                })
                .collect(Collectors.joining(","));
        sb.append(modelArray);
        writer.endArray();
        if (exportOptions.includes().contains(ExportIncludes.NOTES)) {
            ModelNotesService modelNotesService = getModelNotesService(config);
            writer.key(KEY_NOTES);
            writer.value(modelNotesService.loadAllModelNotes());
        }
        if (exportOptions.includes().contains(ExportIncludes.GROUPS)) {
            writer.key(KEY_GROUPS);
            writer.array();
            var groupArray = groups
                    .stream().map(grp -> {
                        try {
                            return mapper.writeValueAsString(grp);
                        } catch (JsonProcessingException e) {
                            log.error("Error while serializing model group {}", grp);
                            throw new MappingException(e);
                        }
                    })
                    .collect(Collectors.joining(","));
            sb.append(groupArray);
            writer.endArray();
        }
        if (exportOptions.includes().contains(ExportIncludes.PORTRAITS)) {
            PortraitStore portraitLoader = getPortraitStore(config);
            writer.key(KEY_PORTRAITS);
            writer.array();
            for (Model model : models) {
                try {
                    Optional<byte[]> portrait = portraitLoader.loadModelPortraitByModelUrl(model.getUrl());
                    if (portrait.isPresent()) {
                        writer.object();
                        writer.key("url").value(model.getUrl());
                        writer.key("id").value(portraitLoader.idForModelUrl(model.getUrl()));
                        writer.key("data").value(Base64.getEncoder().encodeToString(portrait.get()));
                        writer.endObject();
                    }
                } catch (HttpException e) {
                    if (e.getResponseCode() != 404) {
                        log.error("Error while loading portrait from server for {}", model, e);
                    }
                }
            }
            writer.endArray();
        }
        writer.endObject();
        Files.writeString(exportOptions.targetFile().toPath(), new JSONObject(sb.toString()).toString(2));
    }

    private static PortraitStore getPortraitStore(Config config) {
        PortraitStore portraitLoader;
        if (config.getSettings().localRecording) {
            portraitLoader = new LocalPortraitStore(config);
        } else {
            portraitLoader = new RemotePortraitStore(CamrecApplication.httpClient, config);
        }
        return portraitLoader;
    }

    @NotNull
    private static ModelNotesService getModelNotesService(Config config) {
        ModelNotesService modelNotesService;
        if (config.getSettings().localRecording) {
            modelNotesService = new LocalModelNotesService(config);
        } else {
            modelNotesService = new RemoteModelNotesService(CamrecApplication.httpClient, config);
        }
        return modelNotesService;
    }

    public static List<Model> importFrom(File target, List<Site> sites, Config config) throws IOException {
        JSONObject json = new JSONObject(Files.readString(target.toPath(), StandardCharsets.UTF_8));
        List<Model> models = readModels(json.getJSONArray("models"));
        models.forEach(m -> SiteUtil.getSiteForModel(sites, m).ifPresent(m::setSite));
        if (json.has(KEY_NOTES)) {
            importNotes(json.getJSONObject(KEY_NOTES), config);
        }
        if (json.has(KEY_GROUPS)) {
            importGroups(json.getJSONArray(KEY_GROUPS), config);
        }
        if (json.has(KEY_PORTRAITS)) {
            importPortraits(json.getJSONArray(KEY_PORTRAITS), config);
        }
        return models;
    }

    private static void importPortraits(JSONArray portraits, Config config) throws IOException {
        PortraitStore portraitStore = getPortraitStore(config);
        for (int i = 0; i < portraits.length(); i++) {
            JSONObject portrait = portraits.getJSONObject(i);
            String url = portrait.getString("url");
            // String id = portrait.getString("id");
            String dataBase64 = portrait.getString("data");
            portraitStore.writePortrait(url, Base64.getDecoder().decode(dataBase64));
        }
    }


    private static void importGroups(JSONArray groups, Config config) {
        for (int i = 0; i < groups.length(); i++) {
            JSONObject group = groups.getJSONObject(i);
            try {
                ModelGroup modelGroup = mapper.readValue(group.toString(), ModelGroup.class);
                config.getSettings().modelGroups.add(modelGroup);
            } catch (JsonProcessingException e) {
                log.error("Error while deserializing model group {}", group);
            }
        }
    }

    private static void importNotes(JSONObject notes, Config config) {
        ModelNotesService modelNotesService = getModelNotesService(config);
        JSONArray urls = notes.names();
        for (int i = 0; urls != null && i < urls.length(); i++) {
            String url = urls.getString(i);
            String note = notes.getString(url);
            try {
                modelNotesService.writeModelNotes(url, note);
            } catch (IOException e) {
                log.error("Error while importing model notes for {}", url, e);
            }
        }
    }

    private static List<Model> readModels(JSONArray models) {
        List<Model> result = new LinkedList<>();
        for (int i = 0; i < models.length(); i++) {
            JSONObject model = models.getJSONObject(i);
            MigrateModel5_1_2.migrate(model);
            try {
                ModelDto dto = mapper.readValue(model.toString(), ModelDto.class);
                result.add(Mappers.getMapper(ModelMapper.class).toModel(dto));
            } catch (Exception e) {
                log.error("Error while deserializing model {}", model.toString(2), e);
            }
        }
        return result;
    }
}
