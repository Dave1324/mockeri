package org.sindaryn.mockeri.meta;

import org.sindaryn.datafi.annotations.NonCascadeUpdatable;
import org.sindaryn.datafi.annotations.NonCascadeUpdatables;
import org.sindaryn.datafi.reflection.ReflectionCache;
import org.sindaryn.mockeri.annotations.MockData;
import org.sindaryn.mockeri.annotations.NonMockable;
import org.sindaryn.mockeri.annotations.NonNullable;
import org.sindaryn.mockeri.service.NullMockFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.persistence.*;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.sindaryn.datafi.reflection.ReflectionCache.getClassFields;
import static org.sindaryn.mockeri.StaticUtils.collectibleType;
import static org.sindaryn.mockeri.StaticUtils.isEmbeddedEntity;
import static org.sindaryn.mockeri.meta.CircularReferenceException.*;
import static org.sindaryn.mockeri.meta.FieldReferenceType.*;
import static org.sindaryn.mockeri.meta.MockDataSource.*;

@Component
public class FieldMetaInfoFactory {
    @Autowired
    private ReflectionCache reflectionCache;
    @Autowired
    private CollectionInstantiator collectionInstantiator;
    private Map<Field, FieldMetaInfo> cache = new HashMap<>();

    public FieldMetaInfo fieldMetaInfo(Object parent, Field field){
        if(cache.get(field) != null) return cache.get(field);
        FieldMetaInfo fieldMetaInfo = new FieldMetaInfo();
        fieldMetaInfo.setToInstantiate(determineInstantiationStatus(field, parent));
        fieldMetaInfo.setOptional(determineOptionality(field));
        fieldMetaInfo.setUpdatable(determineUpdatability(field, parent));
        if(!fieldMetaInfo.isToInstantiate()) return fieldMetaInfo;
        fieldMetaInfo.setFieldReferenceType(determineFieldReferenceType(field));
        fieldMetaInfo.setMockDataSource(determineMockDataSource(field));
        fieldMetaInfo.setParent(parent);
        cache.put(field, fieldMetaInfo);
        return fieldMetaInfo;
    }

    private boolean determineUpdatability(Field field, Object parent) {
        return !(field.isAnnotationPresent(Column.class) && !field.getAnnotation(Column.class).updatable()) &&
               !field.isAnnotationPresent(NonCascadeUpdatable.class) &&
               !(
                   parent.getClass().isAnnotationPresent(NonCascadeUpdatables.class) &&
                   Arrays.asList(parent.getClass().getAnnotation(NonCascadeUpdatables.class).value())
                           .contains(field.getName())
               ) &&
                !field.isAnnotationPresent(Id.class) && !field.isAnnotationPresent(EmbeddedId.class);
    }

    private boolean determineOptionality(Field field) {
        return
                (field.isAnnotationPresent(Column.class) && field.getAnnotation(Column.class).nullable())       ||
                field.isAnnotationPresent(NonNullable.class)                                                    ||
                (field.isAnnotationPresent(OneToOne.class) && field.getAnnotation(OneToOne.class).optional())   ||
                (field.isAnnotationPresent(ManyToOne.class) && field.getAnnotation(ManyToOne.class).optional()) ||
                !hasNonNullableJoinColumn(field);
    }

    private MockDataSource determineMockDataSource(Field field) {
        if(!field.isAnnotationPresent(MockData.class)) return DEFAULT;
        MockData mockData = field.getAnnotation(MockData.class);
        if(!mockData.mockFactoryBean().equals(NullMockFactory.class)) return MOCK_FACTORY;
        if(!mockData.customKeyword().equals("")) return CUSTOM_KEYWORD;
        if(!mockData.keyword().equals(org.sindaryn.mockeri.service.KEYWORD.NULL)) return KEYWORD;
        if(ofSetIsNotEmpty(mockData)) return OF_SET;
        if(mockData.max() != -1 && mockData.min() != -1 && mockData.max() > mockData.min()) return MIN_MAX_RANGE;
        throw new IllegalArgumentException(
                "Cannot determine data mocking strategy for " + field.getDeclaringClass().getSimpleName() +
                "." + field.getName());
    }

    private boolean ofSetIsNotEmpty(MockData mockData) {
        return mockData.ofSet().length > 1 || !mockData.ofSet()[0].equals("");
    }

    private FieldReferenceType determineFieldReferenceType(Field field) {
        if(field.isAnnotationPresent(OneToOne.class))
            return SINGLE_FOREIGN_KEY;
        if(field.isAnnotationPresent(OneToMany.class) || field.isAnnotationPresent(ManyToMany.class))
            return FOREIGN_KEY_COLLECTION;
        if(Iterable.class.isAssignableFrom(field.getType()))
            return PRIMITIVE_COLLECTION;
        return SINGLE_PRIMITIVE;
    }

    private boolean determineInstantiationStatus(Field field, Object parent) {
        if(isAlreadyInitialized(field, parent)) return false;
        if(!isEmbeddedEntity(field, reflectionCache)) return true;
        if(field.isAnnotationPresent(NonMockable.class)) return false;
        if(field.isAnnotationPresent(NonNullable.class)) return true;
        Field backPointer;
        Class<?> collectionType;
        //one to one
        if(field.isAnnotationPresent(OneToOne.class)){
            OneToOne oneToOne = field.getAnnotation(OneToOne.class);
            backPointer = nonNullableOneToOneBackPointer(field, parent);
            if(backPointer != null){
                if(oneToOne.optional()) return false;
                else throwOneToOneCircularReferenceException(field, parent, backPointer);
            }
        }
        //one to many
        else if(field.isAnnotationPresent(OneToMany.class)){
            OneToMany oneToMany = field.getAnnotation(OneToMany.class);
            backPointer = nonNullableOneToManyBackPointer(field, parent);
            if(backPointer != null){
                collectionType = collectibleType(field, reflectionCache);
                if(hasNonNullableJoinColumn(field)) {
                    throwOneToManyCircularReferenceException(
                            field, collectionType, parent, backPointer);
                }
                initEmptyCollection(field, parent);
                return false;
            }
        }
        //many to many
        else if(field.isAnnotationPresent(ManyToMany.class)){
            ManyToMany manyToMany = field.getAnnotation(ManyToMany.class);
            backPointer = nonNullableManyToManyBackpointer(field, parent);
            if (backPointer != null) {
                collectionType = collectibleType(field, reflectionCache);
                if (hasNonNullableJoinColumn(field))
                    throwManyToManyCircularReferenceException(
                        field, collectionType, parent, backPointer);
                initEmptyCollection(field, parent);
                return false;
            }

        }
        //many to one
        else if(field.isAnnotationPresent(ManyToOne.class)){
            ManyToOne manyToOne = field.getAnnotation(ManyToOne.class);
            backPointer = nonNullableManyToOneBackpointer(field, parent);
            if(backPointer != null){
                if (hasNonNullableJoinColumn(field))
                    throwManyToOneCircularReferenceException(field, parent, backPointer);
                return false;
            }
        }
        //default
        return true;
    }

    private Field nonNullableManyToOneBackpointer(Field field, Object parent) {
        for (Field aField : getClassFields(field.getType())){
            if(aField.getType().equals(parent.getClass()) &&
                    field.isAnnotationPresent(ManyToOne.class) &&
                    !field.getAnnotation(ManyToOne.class).optional()){
                return aField;
            }
        }
        return null;
    }

    private Field nonNullableManyToManyBackpointer(Field field, Object parent) {
        for (Field aField : getClassFields(field.getType())){
            if(aField.getType().equals(parent.getClass()) &&
                    field.isAnnotationPresent(ManyToMany.class) &&
                    hasNonNullableJoinColumn(field)){
                return aField;
            }
        }
        return null;
    }

    private Field nonNullableOneToManyBackPointer(Field field, Object parent) {
        if (parent == null) return null;
        for (Field aField : getClassFields(field.getType())){
            if(aField.getType().equals(parent.getClass()) &&
               field.isAnnotationPresent(OneToMany.class) &&
               hasNonNullableJoinColumn(field)){
                return aField;
            }
        }
        return null;
    }

    private boolean hasNonNullableJoinColumn(Field field) {
        if(field.isAnnotationPresent(JoinColumn.class))
            return !field.getAnnotation(JoinColumn.class).nullable();
        if(field.isAnnotationPresent(JoinColumns.class)){
            for(JoinColumn joinColumn : field.getAnnotation(JoinColumns.class).value()){
                if(!joinColumn.nullable()){
                    return true;
                }
            }
        }
        return false;
    }

    private Field nonNullableOneToOneBackPointer(Field field, Object parent) {
        for (Field aField : getClassFields(field.getType())){
            if(aField.getType().equals(parent.getClass()) &&
               field.isAnnotationPresent(OneToOne.class) &&
               !field.getAnnotation(OneToOne.class).optional()){
                return aField;
            }
        }
        return null;
    }

    private void initEmptyCollection(Field field, Object parent) {
        try{
            field.setAccessible(true);
            field.set(parent, collectionInstantiator.instantiateCollection(field.getType(), collectibleType(field, reflectionCache)));
        }catch (Exception e){
            throw new RuntimeException(e);
        }
    }

    private boolean isAlreadyInitialized(Field field, Object parent) {
        return reflectionCache.getEntitiesCache().get(parent.getClass()
            .getSimpleName()).invokeGetter(parent, field.getName()) != null;
    }
}
