package dev.sanda.mockeri.service;

import lombok.Getter;
import lombok.val;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component("CustomizedMockers")
public class CustomizedMockers {

    @Getter
    @Autowired
    private List<? extends MockFactory> mockFactories;

    @Autowired
    private List<? extends CustomKeywords> customKeywords;
    private static Map<String, List<Object>> customKeywordsMap;
    public static List<Object> getCustomKeyword(String keyword){
        val result = customKeywordsMap.get(keyword);
        if(result != null)
            return result;
        else
            throw new RuntimeException("cannot find custom dataset for keyword: " + keyword);
    }

    @PostConstruct
    private void init(){
        initMockFactories();
        initCustomKeywords();
    }

    @SuppressWarnings("unchecked")
    private void initCustomKeywords() {
        customKeywordsMap = new HashMap<>();
        customKeywords.forEach(customKeywords -> {
            if(!customKeywords.getClass().equals(NullCustomKeywords.class)){
                for (Object keyword : customKeywords.customKeywords().entrySet()){
                    val keywordEntry = (Map.Entry<String, List<Object>> )keyword;
                    customKeywordsMap.putIfAbsent(keywordEntry.getKey().toUpperCase(), keywordEntry.getValue());
                }
            }
        });
    }

    private void initMockFactories() {
        factoryMap = new HashMap<>();
        mockFactories.forEach(mockFactory -> {
            if(!mockFactory.getClass().equals(MockFactory.class))
                factoryMap.put(mockFactory.key(), mockFactory);
        });
    }

    private static Map<String, MockFactory> factoryMap;

    public static MockFactory getMockFactory(String typeName){
        val result = factoryMap.get(typeName);
        if(result != null)
            return result;
        else
            throw new RuntimeException("cannot find mock factory implementation for type: " + typeName);
    }
}
