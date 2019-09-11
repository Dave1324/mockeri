package org.sindaryn.mockeri.meta;

import lombok.val;
import org.reflections.Reflections;
import org.sindaryn.datafi.reflection.CachedEntityType;
import org.sindaryn.datafi.reflection.ReflectionCache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.net.URL;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.stream.Collectors;

import static com.google.common.collect.Maps.immutableEntry;
import static org.sindaryn.datafi.reflection.CachedEntityType.genDefaultInstance;
import static org.sindaryn.mockeri.generator.TestDataGenerator.randomFrom;

@Component
public class CollectionInstantiator {
    @Autowired
    private ReflectionCache reflectionCache;
    private Reflections javaUtils = new Reflections("java.util");
    private Map<Class<? extends Collection>, List<Class<? extends Collection>>> collectionTypes = new HashMap<>();
    private Map<Map.Entry<Class<?>, Class<?>>, Class<?>> cache = new HashMap<>();

    @PostConstruct
    private void init(){
        Collection<Class<? extends Collection>> allCollectionTypes = javaUtils.getSubTypesOf(Collection.class);
        Collection<Class<? extends Collection>> collectionInterfaces =
                allCollectionTypes.stream().filter(Class::isInterface).collect(Collectors.toList());
        Collection<Class<? extends Collection>> collectionImplementations =
                allCollectionTypes.stream().filter(type -> !type.isInterface()).collect(Collectors.toList());
        for(Class<? extends Collection> collectionInterface : collectionInterfaces){
            collectionTypes.put(collectionInterface, new ArrayList<>());
            for(Class<? extends Collection> collectionImplementation : collectionImplementations)
                if (collectionInterface.isAssignableFrom(collectionImplementation))
                    collectionTypes.get(collectionInterface).add(collectionImplementation);
        }
    }

    public Collection instantiateCollection(Class<?> collectionType, Class<?> collectableType){
        final Map.Entry<Class<?>, Class<?>> key = immutableEntry(collectionType, collectableType);
        if(cache.get(key) != null){
            return (Collection) genDefaultInstance(cache.get(key));
        }
        if(collectableType.equals(Collection.class)) return new ArrayList();

        Collection result = null;
        for(val entry : collectionTypes.entrySet()){
            if(entry.getKey().equals(collectionType))
                result = assignDefaultType(entry, collectableType);
            else if(entry.getKey().isAssignableFrom(collectionType)){
                result = (Collection)genDefaultInstance(collectionType);
            }
        }
        if(result == null)
            throw new IllegalArgumentException("unrecognized collection type: " + collectionType.getSimpleName());
        cache.put(key, result.getClass());
        return result;
    }

    @SuppressWarnings("unchecked")
    private Collection assignDefaultType(
            Map.Entry<Class<? extends Collection>, List<Class<? extends Collection>>> entry,
            Class<?> collectibleType) {
        if(collectibleType.equals(Collection.class)) return new ArrayList();
        try {
            Collection result = (Collection)genDefaultInstance(randomFrom(entry.getValue()));
            Object exampleInstance = getDefaultInstance(collectibleType);
            // test add
            result.add(exampleInstance);
            //test remove if
            result.removeIf(item -> item.toString().equals(exampleInstance.toString()));
            final Class<? extends Collection> resultClass = result.getClass();
            if(resultClass.equals(CopyOnWriteArrayList.class) || resultClass.equals(CopyOnWriteArraySet.class))
                throw new RuntimeException();
            return result;
        }
        catch (StackOverflowError stackOverflowError){
            throw new RuntimeException(stackOverflowError);
        }
        catch (Exception e){
            return assignDefaultType(entry, collectibleType);
        }
    }

    private Object getDefaultInstance(Class<?> collectableType) {
        CachedEntityType type = reflectionCache.getEntitiesCache()
       .get(collectableType.getSimpleName());
        if(type != null) return type.getDefaultInstance();
        if(Number.class.isAssignableFrom(collectableType)) return 0;
        if(collectableType.equals(Boolean.class)) return true;
        if(collectableType.equals(String.class)) return "";
        if(collectableType.equals(URL.class)) return dummyUrl();
        if(collectableType.equals(LocalDate.class)) return LocalDate.now();
        if(collectableType.equals(LocalDateTime.class)) return LocalDateTime.now();
        if(collectableType.equals(org.joda.time.LocalDate.class)) return org.joda.time.LocalDate.now();
        if(collectableType.equals(org.joda.time.LocalDateTime.class)) return org.joda.time.LocalDateTime.now();
        throw new RuntimeException("cannot instantiate field of type " + collectableType.getSimpleName());
    }
    private URL dummyUrl(){
        try{
            return new URL("https://www.google.com/");
        }catch (Exception e){
            throw new RuntimeException(e);
        }
    }
}
