// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.document;

import com.yahoo.schema.DocumentReference;

/**
 * A field that is imported from a concrete field in a referenced document type and given an alias name.
 *
 * @author geirst
 */
public abstract class ImportedField {

    private final String fieldName;
    private final DocumentReference reference;
    private final ImmutableSDField targetField;

    public ImportedField(String fieldName,
                         DocumentReference reference,
                         ImmutableSDField targetField) {
        this.fieldName = fieldName;
        this.reference = reference;
        this.targetField = targetField;
    }

    public String fieldName() {
        return fieldName;
    }

    public DocumentReference reference() {
        return reference;
    }

    public ImmutableSDField targetField() {
        return targetField;
    }

    public abstract ImmutableSDField asImmutableSDField();
}
