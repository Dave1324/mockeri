package dev.sanda.mockeri.meta;

import lombok.Data;

@Data
public class FieldMetaInfo {
    private boolean toInstantiate;
    private boolean isOptional;
    private boolean isUpdatable;
    private FieldReferenceType fieldReferenceType;
    private MockDataSource mockDataSource;
    private Object parent;
}
