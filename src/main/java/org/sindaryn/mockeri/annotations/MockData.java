package org.sindaryn.mockeri.annotations;

import org.sindaryn.mockeri.service.KEYWORD;
import org.sindaryn.mockeri.service.MockFactory;
import org.sindaryn.mockeri.service.NullMockFactory;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static org.sindaryn.mockeri.service.KEYWORD.NULL;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface MockData {
    KEYWORD keyword() default NULL;
    String customKeyword() default "";
    String[] ofSet() default "";
    int max() default -1;
    int min() default -1;
    Class<? extends MockFactory> mockFactoryBean() default NullMockFactory.class;
}
