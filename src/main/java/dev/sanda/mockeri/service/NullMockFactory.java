package dev.sanda.mockeri.service;

import org.springframework.stereotype.Component;

@Component
public class NullMockFactory implements MockFactory {
    @Override
    public Object value() {
        return null;
    }
}
