/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.processengine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

class InjectionContext {
    private static Logger logger = LoggerFactory.getLogger(InjectionContext.class);

    private final Map<Class, Object> injections;

    private InjectionContext() {
        injections = new HashMap<>();
    }

    private InjectionContext(InjectionContext src) {
        injections = new HashMap<>(src.injections);
    }

    public static InjectionContext create() {
        return new InjectionContext();
    }

    public InjectionContext add(Class aClass, Object object) {
        InjectionContext result = new InjectionContext(this);
        result.injections.put(aClass, object);
        return result;
    }

    public void inject(Object object) {
        Class<?> objectClass = object.getClass();
        while (objectClass != Object.class) {
            processClass(object, objectClass);
            objectClass = objectClass.getSuperclass();
        }
    }

    private void processClass(Object object, Class<?> objectClass) {
        Field[] declaredFields = objectClass.getDeclaredFields();
        for (Field field : declaredFields) {
            Inject[] annotationsByType = field.getAnnotationsByType(Inject.class);
            if (annotationsByType.length == 0) {
                continue;
            }

            Class<?> fieldClass = field.getType();
            Object injectedObject = injections.get(fieldClass);
            if (injectedObject == null) {
                for (Class eachClass : injections.keySet()) {
                    if (fieldClass.isAssignableFrom(eachClass)) {
                        injectedObject = injections.get(eachClass);
                    }
                }

                if (injectedObject == null) {
                    logger.warn("Unable to find injection for '{}' field in class {}", field.getName(), objectClass.getName());
                }
            }

            field.setAccessible(true);
            try {
                field.set(object, injectedObject);
            } catch (IllegalAccessException e) {
                logger.warn("Error setting object to '{}' field in class {}", field.getName(), objectClass.getName());
            }
        }
    }
}
