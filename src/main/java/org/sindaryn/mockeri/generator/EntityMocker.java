package org.sindaryn.mockeri.generator;

import lombok.Getter;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.sindaryn.datafi.reflection.ReflectionCache;
import org.sindaryn.datafi.service.DataManager;
import org.sindaryn.mockeri.annotations.MockData;
import org.sindaryn.mockeri.meta.CollectionInstantiator;
import org.sindaryn.mockeri.meta.FieldMetaInfo;
import org.sindaryn.mockeri.meta.FieldMetaInfoFactory;
import org.sindaryn.mockeri.service.MockFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

import javax.transaction.Transactional;
import java.lang.reflect.Field;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import static org.sindaryn.datafi.StaticUtils.getId;
import static org.sindaryn.datafi.reflection.CachedEntityType.genDefaultInstance;
import static org.sindaryn.datafi.reflection.ReflectionCache.getClassFields;
import static org.sindaryn.mockeri.StaticUtils.*;
import static org.sindaryn.mockeri.generator.TestDataGenerator.randomFrom;
import static org.sindaryn.mockeri.meta.Args.INSTANTIATION_STACK;
import static org.sindaryn.mockeri.meta.Args.NO_PERSIST;
import static org.sindaryn.mockeri.meta.CircularReferenceException.throwGeneralCircularReferenceException;
import static org.sindaryn.mockeri.service.CustomizedMockers.getCustomKeyword;
import static org.sindaryn.mockeri.service.CustomizedMockers.getMockFactory;

@Transactional
@Component("EntityMocker")
@DependsOn("CustomizedMockers")
@SuppressWarnings("unchecked")
public class EntityMocker {
    @Autowired
    private ReflectionCache reflectionCache;
    @Autowired
    private TestDataGenerator testData;
    @Autowired
    private DataManager dataManager;
    @Autowired
    private FieldMetaInfoFactory fieldMetaInfoFactory;
    @Autowired
    @Getter
    private CollectionInstantiator collectionInstantiator;

    public <T> T instantiateEntity(Class<?> clazz){
        return instantiateEntity(clazz, new HashMap<String, Object>(){{
            put(INSTANTIATION_STACK, new Stack<>());
        }});
    }
    public <T> T instantiateEntity(String clazzName) {
        return instantiateEntity(reflectionCache.getEntitiesCache().get(clazzName).getClazz());
    }
    public <T> T instantiateTransientEntity(Class<?> clazz){
        return instantiateEntity(clazz, new HashMap<String, Object>(){{
            put(INSTANTIATION_STACK, new Stack<>());
            put(NO_PERSIST, true);
        }});
    }
    public Object mockFieldValue(Class<?> clazz, String fieldName){
        Object instance = reflectionCache.getEntitiesCache().get(clazz.getSimpleName()).getDefaultInstance();
        Collection<Field> fields = getClassFields(instance.getClass());
        Field field = null;
        for (Field aField : fields){
            if(aField.getName().equals(fieldName)) {
                field = aField;
                break;
            }
        }
        if(field == null)
            throw new IllegalArgumentException(
                    "Cannot find field by name " + fieldName + " in " + instance.getClass().getSimpleName());
        assignFieldValue(field, instance, new HashMap<String, Object>(){{
            put(INSTANTIATION_STACK, new Stack<>());
            put(NO_PERSIST, true);
        }});
        return reflectionCache
                .getEntitiesCache()
                .get(instance.getClass().getSimpleName())
                .invokeGetter(instance, field.getName());
    }
    public<T> T mockUpdate(Object toMockUpdate){
        Class<?> clazz = toMockUpdate.getClass();
        Object other = instantiateTransientEntity(toMockUpdate.getClass());
        getClassFields(clazz).forEach(field -> {
            final FieldMetaInfo fieldMetaInfo = fieldMetaInfoFactory.fieldMetaInfo(toMockUpdate, field);
            if(fieldMetaInfo.isUpdatable())
                setField(toMockUpdate, field,
                                 reflectionCache.getEntitiesCache().get(clazz.getSimpleName())
                                .invokeGetter(other, field.getName()));
        });
        return (T) toMockUpdate;
    }

    private  <T> T instantiateEntity(Class<?> clazz, Map<String, Object> args){
        Stack instantiationStack = (Stack)args.get(INSTANTIATION_STACK);
        if(instantiationStack.contains(clazz))
            return preExistingInstance(clazz, args);
        final String indentation = indentation(instantiationStack);
        log(indentation + "Instantiating " + clazz.getSimpleName());
        Object instance = genDefaultInstance(clazz);
        instantiationStack.push(clazz);
        getClassFields(clazz).forEach(field -> assignFieldValue(field, instance, args));
        instantiationStack.pop();
        if (args.get(NO_PERSIST) == null) {
            log(indentation + "Persisting instance of " + clazz.getSimpleName());
            dataManager.setType(clazz);
            return (T) dataManager.save(instance);
        } else {
            log(indentation + "Returning transient instance of " + clazz.getSimpleName());
            return (T) instance;
        }
    }

    private void assignFieldValue(Field field, Object parent, Map<String, Object> args) {
        FieldMetaInfo fieldMetaInfo = fieldMetaInfoFactory.fieldMetaInfo(parent, field);
        if(!fieldMetaInfo.isToInstantiate()) return;
        switch (fieldMetaInfo.getMockDataSource()){
            case DEFAULT: autoAssign(field, parent, fieldMetaInfo, args);
                break;
            case KEYWORD: assignFromKeyword(field, parent);
                break;
            case CUSTOM_KEYWORD: assignFromCustomKeyword(field, parent);
                break;
            case OF_SET: assignFromOfSet(field, parent);
                break;
            case MIN_MAX_RANGE: assignFromMinMaxRange(field, parent);
                break;
            case MOCK_FACTORY: assignFromMockFactory(field, parent);
                break;
        }
    }
    private void autoAssign(Field field, Object parent, FieldMetaInfo fieldMetaInfo, Map<String, Object> args) {
        switch (fieldMetaInfo.getFieldReferenceType()){
            case SINGLE_PRIMITIVE: autoAssignSinglePrimitive(field, parent);
                break;
            case PRIMITIVE_COLLECTION: autoAssignPrimitivesCollection(field, parent);
                break;
            case SINGLE_FOREIGN_KEY: autoAssignSingleForeignKey(field, parent, fieldMetaInfo, args);
                break;
            case FOREIGN_KEY_COLLECTION: autoAssignForeignKeyCollection(field, parent, fieldMetaInfo, args);
                break;
        }
    }
    private void autoAssignForeignKeyCollection(Field field, Object parent, FieldMetaInfo fieldMetaInfo, Map<String, Object> args) {
        if(args.get(NO_PERSIST) != null && fieldMetaInfo.isOptional()) return;
        Class<?> collectibleType = collectibleType(field, reflectionCache);
        Collection<Object> values = collectionInstantiator.instantiateCollection(field.getType(), collectibleType);
        Map<Object, Object> valuesMap = new HashMap<>();
        for(int i = 0; i < ThreadLocalRandom.current().nextInt(5, 10); i++){
            final Object value = instantiateEntity(collectibleType, args);
            if(value != null)
                valuesMap.putIfAbsent(getId(value, reflectionCache), value);
        }
        removeAnyCircularSelfReference(valuesMap, parent);
        values.addAll(valuesMap.values());
        if(values.isEmpty() && !fieldMetaInfo.isOptional())
            throwGeneralCircularReferenceException(field, parent);
        setField(parent, field, values);
    }

    private void removeAnyCircularSelfReference(Map<Object, Object> valuesMap, Object parent) {
        Object parentId = getId(parent, reflectionCache);
        if(valuesMap.get(parentId) != null)
            valuesMap.remove(parentId);
    }

    private void autoAssignSingleForeignKey(Field field, Object parent, FieldMetaInfo fieldMetaInfo,Map<String, Object> args) {
        if(args.get(NO_PERSIST) != null && fieldMetaInfo.isOptional()) return;
        final Object value = instantiateEntity(field.getType(), args);
        if(value == null && !fieldMetaInfo.isOptional())
            throwGeneralCircularReferenceException(field, parent);
        if(isCircularSelfReference(parent, value))
            handleSingleForeignKeyCircularSelfReference(field, fieldMetaInfo, value, parent);
        else setField(parent, field, value);
    }

    private void handleSingleForeignKeyCircularSelfReference(Field field, FieldMetaInfo fieldMetaInfo, Object value, Object parent) {
        dataManager.setType(field.getType());
        if (dataManager.count() <= 1) {
            if (!fieldMetaInfo.isOptional())
                throwGeneralCircularReferenceException(field, parent);
            else return;
        }
        //else
        Object parentId = getId(parent, reflectionCache);
        List<Object> allValues =
                                (List<Object>) dataManager.findAll().stream()
                                .filter(_value -> !getId(_value, reflectionCache).equals(parentId))
                                .collect(Collectors.toList());
        if(allValues.size() == 1) setField(parent, field, allValues.get(0));
        else setField(parent, field, randomFrom(allValues));
    }

    private boolean isCircularSelfReference(Object parent, Object value){
        return  value.getClass().equals(parent.getClass()) &&
                getId(value, reflectionCache).equals(getId(parent, reflectionCache));
    }

    private void autoAssignPrimitivesCollection(Field field, Object parent) {

        switch (primitiveCollectionType(field)){
            case "String": setField(parent, field, testData.collectionOfStrings(field.getType()));
                break;
            case "Double": setField(parent, field, testData.collectionOfDoubles(field.getType()));
                break;
            case "Long" : setField(parent, field, testData.collectionOfLongs(field.getType()));
                break;
            case "Integer" : setField(parent, field, testData.collectionOfIntegers(field.getType()));
                break;
            case "Boolean" :setField(parent, field, testData.collectionOfBooleans(field.getType()));
                break;
            case "LocalDateTime" : setField(parent, field, testData.collectionOfLocalDateTimes(field.getType()));
                break;
            case "LocalDate" : setField(parent, field, testData.collectionOfLocalDates(field.getType()));
                break;
            case "URL" : setField(parent, field, testData.collectionOfUrls(field.getType()));
        }
    }
    private void autoAssignSinglePrimitive(Field field, Object parent) {
        switch (field.getType().getSimpleName()){
            case "String": setField(parent, field, testData.dummySentence());
                break;
            case "Double": setField(parent, field, testData.aDouble());
                break;
            case "Long" : setField(parent, field, ThreadLocalRandom.current().nextLong());
                break;
            case "Integer" : setField(parent, field, ThreadLocalRandom.current().nextInt());
                break;
            case "Boolean" :setField(parent, field, ThreadLocalRandom.current().nextBoolean());
                break;
            case "LocalDateTime" : setField(parent, field, testData.aLocalDateTime());
                break;
            case "LocalDate" : setField(parent, field, testData.aLocalDate());
                break;
            case "URL" : setField(parent, field, toUrlType(randomFrom(testData.getWebsites())));
        }
    }

    private void assignFromKeyword(Field field, Object parent) {
        switch (field.getAnnotation(MockData.class).keyword()){
            case PAST_DATE: setField(parent, field, testData.pastDate());
                break;
            case FUTURE_DATE: setField(parent, field, testData.futureDate());
                break;
            case NAME: setField(parent, field, randomFrom(testData.getFirstNames()));
                break;
            case ADDRESS: setField(parent, field, randomFrom(testData.getAddresses()));
                break;
            case CITY: setField(parent, field, randomFrom(testData.getCities()));
                break;
            case STATE: setField(parent, field, randomFrom(testData.getStateOrProvinces()));
                break;
            case COUNTRY: setField(parent, field, randomFrom(testData.getCountries()));
                break;
            case ZIP: setField(parent, field, randomFrom(testData.getZipCodes()));
                break;
            case PHONE: setField(parent, field, randomFrom(testData.getPhone1s()));
                break;
            case EMAIL: setField(parent, field, randomFrom(testData.getEmails()));
                break;
            case PARAGRAPH: setField(parent, field, testData.dummyParagraph());
                break;
            case COMPANY: setField(parent, field, randomFrom(testData.getCompanies()));
                break;
            case URL: setField(parent, field, randomFrom(testData.getWebsites()));
                break;
            case PASSWORD: setField(parent, field, testData.password());
                break;
        }
    }
    private void assignFromCustomKeyword(Field field, Object parent) {
        String customKeyword = field.getAnnotation(MockData.class).customKeyword();
        List<Object> dataset = getCustomKeyword(customKeyword.toUpperCase());
        setField(parent, field, randomFrom(dataset));
    }
    private void assignFromOfSet(Field field, Object parent) {
        MockData mockData = field.getAnnotation(MockData.class);
        setField(parent, field, randomFrom(Arrays.asList(mockData.ofSet())));
    }
    private void assignFromMinMaxRange(Field field, Object parent) {
        MockData mockData = field.getAnnotation(MockData.class);
        switch (field.getType().getSimpleName()){
            case "Double": {
                setField(parent, field, ThreadLocalRandom.current().nextDouble(mockData.min(), mockData.max()));
            } break;
            case "Float" : {
                setField(parent, field, (float)ThreadLocalRandom.current().nextDouble(mockData.min(), mockData.max()));
            } break;
            case "Long"  : {
                setField(parent, field, ThreadLocalRandom.current().nextLong(mockData.min(), mockData.max()));
            } break;
            case "Integer": {
                setField(parent, field, ThreadLocalRandom.current().nextInt(mockData.min(), mockData.max()));
            } break;
            case "Short" : {
                setField(parent, field, (short)ThreadLocalRandom.current().nextInt(mockData.min(), mockData.max()));
            } break;
            case "BigDecimal" : {
                setField(parent, field, generateRandomBigDecimalFromRange(mockData.min(), mockData.max()));
            } break;
        }
    }
    private void assignFromMockFactory(Field field, Object parent) {
        Class<?> mockFactoryType = field.getAnnotation(MockData.class).mockFactoryBean();
        MockFactory mockFactory = getMockFactory(mockFactoryType.getSimpleName());
        setField(parent, field, mockFactory.value());
    }
    private void setField(Object parent, Field field, Object value) {
        try{
            field.setAccessible(true);
            field.set(parent, value);
        }catch (Exception e){
            e.printStackTrace();
throw new RuntimeException(e);
        }
    }
    private final static Logger logger = Logger.getLogger(EntityMocker.class);
    private void log(String s) {
        logger.log(Level.DEBUG, s);
    }
    private String indentation(Stack instantiationStack){
        StringBuilder stringBuilder = new StringBuilder("|\n|");
        for(int i = 0; i < instantiationStack.size(); i++) stringBuilder.append("-");
        return stringBuilder.toString();
    }
    private <T> T preExistingInstance(Class<?> clazz, Map<String, Object> args) {
        dataManager.setType(clazz);
        List<Object> preExistingInstances = dataManager.findAll();
        String indentation = indentation((Stack)args.get(INSTANTIATION_STACK));
        if(preExistingInstances.isEmpty()) {
            log( indentation + "Cannot find previous instance of " + clazz.getSimpleName() + " to assign, assigning null value.");
            return null;
        }
        log(indentation + "Assigning previous instance of " + clazz.getSimpleName());
        if(preExistingInstances.size() == 1) return (T) preExistingInstances.get(0);
        return (T) randomFrom(preExistingInstances);
    }
}
