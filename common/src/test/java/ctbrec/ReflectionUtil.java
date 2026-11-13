package ctbrec;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class ReflectionUtil {

    private ReflectionUtil () {}

    @SuppressWarnings("unchecked")
    public static <T> T call(Object target, String methodName, Object...args) throws NoSuchMethodException, SecurityException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
        List<Class<?>> argTypes = Arrays.stream(args).map(arg -> arg.getClass()).collect(Collectors.toList());
        Class<?>[] argTypeArray = argTypes.toArray(new Class[0]);
        Method method = target.getClass().getDeclaredMethod(methodName, argTypeArray);
        method.setAccessible(true);
        return (T) method.invoke(target, args);
    }
}
