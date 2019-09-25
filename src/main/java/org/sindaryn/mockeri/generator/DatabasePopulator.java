package org.sindaryn.mockeri.generator;


import org.sindaryn.apifi.annotations.NonDirectlyExposable;
import org.sindaryn.datafi.reflection.CachedEntityType;
import org.sindaryn.datafi.reflection.ReflectionCache;
import org.sindaryn.mockeri.annotations.CompositeEntity;
import org.sindaryn.mockeri.annotations.MockEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class DatabasePopulator {
    @Value("${mockeri.quantity.min:20}")
    private String minQuantity;
    @Value("${mockeri.quantity.max:50}")
    private String maxQuantity;

    @Autowired
    private ReflectionCache reflectionCache;
    @Autowired
    private EntityMocker entityMocker;

    @PostConstruct
    private void populateDatabase(){
        if(isDummyPopulateMode()){
            int min = new Integer(minQuantity);
            int max = new Integer(maxQuantity);
            int actualQuantity;
            for (Map.Entry<String, CachedEntityType> entry : reflectionCache.getEntitiesCache().entrySet()) {
                String name = entry.getKey();
                CachedEntityType type = entry.getValue();
                MockEntity mockEntityAnnotation = type.getClazz().getAnnotation(MockEntity.class);
                if(mockEntityAnnotation != null) actualQuantity = mockEntityAnnotation.quantity();
                else actualQuantity = ThreadLocalRandom.current().nextInt(min, max);
                if (isPersistable(type)) {
                    instantiateEntities(name, actualQuantity);
                }
            }
        }
    }

    public static boolean isDummyPopulateMode() {
        return System.getenv("DUMMY_POPULATE") != null && System.getenv("DUMMY_POPULATE").equals("true");
    }

    public static boolean isPersistable(CachedEntityType type){
        return isPersistable(type.getClazz());
    }
    public static boolean isPersistable(Class<?> type) {
        boolean isAnnotatedAsCompositeEntity = type.isAnnotationPresent(CompositeEntity.class);
        boolean isMarkedForDirectApiExposure = !type.isAnnotationPresent(NonDirectlyExposable.class);
        if(isAnnotatedAsCompositeEntity)
            return false;
        return isMarkedForDirectApiExposure;
    }

    private void instantiateEntities(String name, int amountToAdd) {
        try{
            for (int i = 0; i < amountToAdd; i++)
                entityMocker.instantiateEntity(name);
        }catch (Exception e){
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }
}
