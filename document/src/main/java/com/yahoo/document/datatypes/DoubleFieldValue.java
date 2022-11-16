// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.datatypes;

import com.yahoo.document.DataType;
import com.yahoo.document.Field;
import com.yahoo.document.PrimitiveDataType;
import com.yahoo.document.serialization.FieldReader;
import com.yahoo.document.serialization.FieldWriter;
import com.yahoo.document.serialization.XmlSerializationHelper;
import com.yahoo.document.serialization.XmlStream;
import com.yahoo.vespa.objects.Ids;

/**
 * A 64-bit float field value
 *
 * @author Einar M R Rosenvinge
 */
public final class DoubleFieldValue extends NumericFieldValue {

    private static class Factory extends PrimitiveDataType.Factory {
        @Override public FieldValue create() { return new DoubleFieldValue(); }
        @Override public FieldValue create(String value) { return new DoubleFieldValue(value); }
    }

    public static PrimitiveDataType.Factory getFactory() { return new Factory(); }
    public static final int classId = registerClass(Ids.document + 14, DoubleFieldValue.class);
    private double value;

    public DoubleFieldValue() {
        this(0.0);
    }

    public DoubleFieldValue(double value) {
        this.value = value;
    }

    public DoubleFieldValue(Double value) {
        this.value = value;
    }

    public DoubleFieldValue(String s) { value = Double.parseDouble(s); }

    @Override
    public DoubleFieldValue clone() {
        DoubleFieldValue val = (DoubleFieldValue) super.clone();
        val.value = value;
        return val;
    }

    @Override
    public void clear() {
        value = 0.0;
    }

    @Override
    public Number getNumber() {
        return value;
    }

    @Override
    public void assign(Object obj) {
        if (!checkAssign(obj)) {
            return;
        }
        if (obj instanceof Number) {
            value = ((Number) obj).doubleValue();
        } else if (obj instanceof NumericFieldValue) {
            value = (((NumericFieldValue) obj).getNumber().doubleValue());
        } else if (obj instanceof String || obj instanceof StringFieldValue) {
            value = Double.parseDouble(obj.toString());
        } else {
            throw new IllegalArgumentException("Class " + obj.getClass() + " not applicable to an " + this.getClass() + " instance.");
        }
    }

    public double getDouble() {
        return value;
    }

    @Override
    public Object getWrappedValue() {
        return value;
    }

    @Override
    public DataType getDataType() {
        return DataType.DOUBLE;
    }

    @Override
    public void printXml(XmlStream xml) {
        XmlSerializationHelper.printDoubleXml(this, xml);
    }

    @Override
    public String toString() {
        return "" + value;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        long temp;
        temp = value != +0.0d ? Double.doubleToLongBits(value) : 0L;
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DoubleFieldValue)) return false;
        if (!super.equals(o)) return false;

        DoubleFieldValue that = (DoubleFieldValue) o;
        if (Double.compare(that.value, value) != 0) return false;
        return true;
    }

    @Override
    public void serialize(Field field, FieldWriter writer) {
        writer.write(field, this);
    }

    /* (non-Javadoc)
      * @see com.yahoo.document.datatypes.FieldValue#deserialize(com.yahoo.document.Field, com.yahoo.document.serialization.FieldReader)
      */

    @Override
    public void deserialize(Field field, FieldReader reader) {
        reader.read(field, this);
    }

    @Override
    public int compareTo(FieldValue fieldValue) {
        int comp = super.compareTo(fieldValue);

        if (comp != 0) {
            return comp;
        }

        //types are equal, this must be of this type
        DoubleFieldValue otherValue = (DoubleFieldValue) fieldValue;
        return Double.compare(value, otherValue.value);
    }
}
