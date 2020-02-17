package dev.sanda.mockeri.service;

import org.springframework.stereotype.Component;

@Component
public interface MockFactory<T> {
    T value();
    default String key(){return this.getClass().getSimpleName();}
}