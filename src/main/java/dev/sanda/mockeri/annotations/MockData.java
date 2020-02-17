package dev.sanda.mockeri.annotations;

import dev.sanda.mockeri.service.KeyWord;
import dev.sanda.mockeri.service.MockFactory;
import dev.sanda.mockeri.service.NullMockFactory;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static dev.sanda.mockeri.service.KeyWord.NULL;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface MockData {
    KeyWord keyword() default NULL;
    String customKeyword() default "";
    String[] ofSet() default "";
    int max() default -1;
    int min() default -1;
    Class<? extends MockFactory> mockFactoryBean() default NullMockFactory.class;
}
