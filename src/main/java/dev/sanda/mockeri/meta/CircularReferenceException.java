package dev.sanda.mockeri.meta;

import java.lang.reflect.Field;

public class CircularReferenceException extends RuntimeException {
    private CircularReferenceException(String s) {
        super(s);
    }

    public static void throwOneToOneCircularReferenceException(Field field, Object parent, Field backpointer) {
        throw new CircularReferenceException(
                "Two way non-nullable one-to-one relationship detected: " +
                        (parent != null ? parent.getClass().getSimpleName() : field.getDeclaringClass()) + "." + field.getName() +
                        "<-->" +
                        field.getType() + "." + backpointer.getName());
    }
    public static void throwOneToManyCircularReferenceException(Field field, Class<?> collectionType, Object parent, Field backpointer) {
        throw new CircularReferenceException(
                "Two way non-nullable one-to-many relationship detected: " +
                        (parent != null ? parent.getClass().getSimpleName() : field.getDeclaringClass()) + "." + field.getName() +
                        "<-->" 
                         + collectionType.getSimpleName() + "." + backpointer.getName());
    }

    public static void throwManyToManyCircularReferenceException(Field field, Class<?> collectionType, Object parent, Field backpointer) {
        throw new CircularReferenceException(
                "Two way non-nullable many-to-many relationship detected: " +
                        (parent != null ? parent.getClass().getSimpleName() : field.getDeclaringClass()) + "." + field.getName() +
                        "<-->"
                        + collectionType.getSimpleName() + "." + backpointer.getName());
    }
    public static void throwManyToOneCircularReferenceException(Field field, Object parent, Field backpointer) {
        throw new CircularReferenceException(
                "Two way non-nullable many-to-one relationship detected: " +
                        (parent != null ? parent.getClass().getSimpleName() : field.getDeclaringClass()) +
                        "." + field.getName() +
                        "<-->" +
                        field.getType() + "." + backpointer.getName());
    }
    public static void throwGeneralCircularReferenceException(Field field, Object parent) {
        throw new CircularReferenceException(
                "Circular reference found: " +
                        parent.getClass().getSimpleName() + "." + field.getName() +
                        " contains a circular dependency to entity: " + field.getType().getSimpleName());
    }
}
