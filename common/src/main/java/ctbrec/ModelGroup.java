package ctbrec;

import java.io.Serializable;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class ModelGroup implements Serializable {
    private static final long serialVersionUID = 1L;

    private UUID id;
    private String name;
    private List<String> modelUrls = new LinkedList<>();

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getModelUrls() {
        return modelUrls;
    }

    public void setModelUrls(List<String> modelUrls) {
        this.modelUrls = modelUrls;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        ModelGroup other = (ModelGroup) obj;
        return Objects.equals(id, other.id);
    }

    @Override
    public String toString() {
        return "ModelGroup [id=" + id + ", name=" + name + ", models=" + modelUrls + "]";
    }

    public void add(String modelUrl) {
        modelUrls.add(modelUrl);
    }

    public void add(Model model) {
        modelUrls.add(model.getUrl());
    }
}
