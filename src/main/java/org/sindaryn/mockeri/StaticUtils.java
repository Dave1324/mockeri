package org.sindaryn.mockeri;

import lombok.val;
import org.sindaryn.datafi.reflection.CachedEntityField;
import org.sindaryn.datafi.reflection.ReflectionCache;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import javax.persistence.ElementCollection;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import javax.persistence.Id;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.net.URL;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.sindaryn.datafi.StaticUtils.toPascalCase;
import static org.sindaryn.mockeri.meta.Args.INSTANTIATION_STACK;

public class StaticUtils {
    public static BufferedReader getBufferedReaderFor(String rsc, ResourceLoader resourceLoader) {
        try {
            Resource resource = resourceLoader.getResource("classpath:" + rsc);
            InputStream inputStream = resource.getInputStream();
            return new BufferedReader(new InputStreamReader(inputStream));
        } catch(Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String randomString(){
        return randomString(23);
    }
    public static String randomString( int len ){
        final String AB = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
        SecureRandom rnd = new SecureRandom();
        StringBuilder sb = new StringBuilder( len );
        for( int i = 0; i < len; i++ )
            sb.append( AB.charAt( rnd.nextInt(AB.length()) ) );
        return sb.toString();
    }

    public static boolean isEmbeddedEntity(Field field, ReflectionCache reflectionCache){
        if(!field.getDeclaringClass().isAnnotationPresent(Entity.class)) return false;
        Class<?> type = field.getType();
        if (primitiveTypeOrEnum(type) != null
                || isCollectionOrMapOfPrimitives(field.getName(), field.getDeclaringClass().getSimpleName(), reflectionCache))
            return false;
        else
            return !isId(field);
    }

    private static Class<? extends Serializable> primitiveTypeOrEnum(Class<?> type) {
        switch (type.getSimpleName()){
            case "String": return String.class;
            case "Double": return Double.class;
            case "Float" : return Float.class;
            case "Long"  : return Long.class;
            case "Integer": return Integer.class;
            case "Short" : return Short.class;
            case "Character" : return Character.class;
            case "Byte" : return Byte.class;
            case "Boolean" : return Boolean.class;
            case "LocalDateTime" : return LocalDateTime.class;
            case "LocalDate" : return LocalDate.class;
            case "URL" : return URL.class;
            case "BigDecimal" : return BigDecimal.class;
        }
        if(type.isEnum())
            return Enum.class;
        if(URL.class.equals(type)) return URL.class;
        return null;
    }

    static boolean isCollectionOrMapOfPrimitives(String fieldName, String declaringClassName, ReflectionCache reflectionCache) {
        fieldName = fieldName.substring(0, 1).toLowerCase() + fieldName.substring(1);
        val temp =
                reflectionCache
                        .getEntitiesCache()
                        .get(declaringClassName);
        try {
            CachedEntityField field =
                    reflectionCache
                            .getEntitiesCache()
                            .get(declaringClassName)
                            .getFields()
                            .get(fieldName);
            boolean isCollectionOrMap = field.isCollectionOrMap();
            if(!isCollectionOrMap) return false;
            return field.getField().isAnnotationPresent(ElementCollection.class);
        }catch (Exception e){
            throw new RuntimeException(e);
        }
    }
    public static boolean isId(Field type){
        return type.getAnnotation(EmbeddedId.class) != null || type.getAnnotation(Id.class) != null;
    }

    public static Class<?> collectibleType(Field field, ReflectionCache reflectionCache){
        try {
            Method method = field.getDeclaringClass().getMethod("get" + toPascalCase(field.getName()));
            String typeName = method.getGenericReturnType().getTypeName();
            int start = typeName.indexOf("<") + 1;
            int end = typeName.indexOf(">");
            String nameWithPackage = typeName.substring(start, end);
            start = nameWithPackage.lastIndexOf(".") + 1;
            String simpleTypeName = nameWithPackage.substring(start);
            return reflectionCache.getEntitiesCache().get(simpleTypeName).getClazz();
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    public static String primitiveCollectionType(Field field){
        try {
            Method method = field.getDeclaringClass().getMethod("get" + toPascalCase(field.getName()));
            String typeName = method.getGenericReturnType().getTypeName();
            int start = typeName.indexOf("<") + 1;
            int end = typeName.indexOf(">");
            String nameWithPackage = typeName.substring(start, end);
            start = nameWithPackage.lastIndexOf(".") + 1;
            return nameWithPackage.substring(start);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    public static BigDecimal generateRandomBigDecimalFromRange(int lowerBound, int upperBound) {
        BigDecimal min = new BigDecimal(lowerBound + ".0");
        BigDecimal max = new BigDecimal(upperBound + ".0");
        BigDecimal randomBigDecimal = min.add(new BigDecimal(Math.random()).multiply(max.subtract(min)));
        return randomBigDecimal.setScale(2,BigDecimal.ROUND_HALF_UP);
    }
}
