package ctbrec.io;

import java.lang.reflect.*;

public class BoundField {
    public Object object = null;
    public Field field = null;
    
    public BoundField(Object o, Field f) {
        object = o;
        field = f;
    }
    
    public Object get() throws IllegalAccessException {
        return field.get(object);
    }
    
    public void set(Object value) throws IllegalAccessException {
        field.set(object, value);
    }
    
    // by-path field resolver (i.e: "a.b.c")
    public static BoundField of(Object root, String path) throws NoSuchFieldException, IllegalAccessException {
        var keys = path.split("\\.");
        var result = new BoundField(root, root.getClass().getField(keys[0]));
        for (int i = 1; i<keys.length; i++) {
            result.object = result.field.get(result.object);
            result.field = result.object.getClass().getField(keys[i]);
        }        
        return result;
    }
}