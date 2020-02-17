package dev.sanda.mockeri.generator;


import dev.sanda.mockeri.annotations.CompositeEntity;
import dev.sanda.mockeri.annotations.MockEntity;
import dev.sanda.datafi.reflection.CachedEntityTypeInfo;
import dev.sanda.datafi.reflection.ReflectionCache;
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
            int min = Integer.parseInt(minQuantity);
            int max = Integer.parseInt(maxQuantity);
            int actualQuantity;
            for (Map.Entry<String, CachedEntityTypeInfo> entry : reflectionCache.getEntitiesCache().entrySet()) {
                String name = entry.getKey();
                CachedEntityTypeInfo type = entry.getValue();
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

    public static boolean isPersistable(CachedEntityTypeInfo type){
        return isPersistable(type.getClazz());
    }
    public static boolean isPersistable(Class<?> type) {
        return type.isAnnotationPresent(CompositeEntity.class);
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
