// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.test;

import com.yahoo.io.GrowableByteBuffer;
import com.yahoo.search.Query;
import com.yahoo.search.query.Ranking;
import com.yahoo.search.query.ranking.RankFeatures;
import com.yahoo.search.query.ranking.RankProperties;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.serialization.TypedBinaryFormat;
import com.yahoo.text.Utf8;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author geirst
 */
public class RankFeaturesTestCase {

    @Test
    void requireThatRankPropertiesTakesBothStringAndObject() {
        RankProperties p = new RankProperties();
        p.put("string", "b");
        p.put("object", 7);
        assertEquals("7", p.get("object").get(0));
        assertEquals("b", p.get("string").get(0));
    }

    @Test
    @SuppressWarnings("deprecation")
    void requireThatRankFeaturesUsingDoubleAndDoubleToStringEncodeTheSameWay() {
        RankFeatures withDouble = new RankFeatures(new Ranking(new Query()));
        withDouble.put("query(myDouble)", 3.8);
        assertEquals(3.8, withDouble.getDouble("query(myDouble)").getAsDouble(), 0.000001);

        RankFeatures withString = new RankFeatures(new Ranking(new Query()));
        withString.put("query(myDouble)", String.valueOf(3.8));

        RankProperties withDoubleP = new RankProperties();
        withDouble.prepare(withDoubleP);
        RankProperties withStringP = new RankProperties();
        withString.prepare(withStringP);

        byte[] withDoubleEncoded = encode(withDoubleP);
        byte[] withStringEncoded = encode(withStringP);
        assertEquals(Arrays.toString(withStringEncoded), Arrays.toString(withDoubleEncoded));
    }

    @Test
    void requireThatSingleTensorIsBinaryEncoded() {
        TensorType type = new TensorType.Builder().mapped("x").mapped("y").mapped("z").build();
        Tensor tensor = Tensor.from(type, "{ {x:a, y:b, z:c}:2.0, {x:a, y:b, z:c2}:3.0 }");
        assertTensorEncodingAndDecoding(type, "query(my_tensor)", "my_tensor", tensor);
        assertTensorEncodingAndDecoding(type, "$my_tensor", "my_tensor", tensor);
    }

    @Test
    void requireThatMultipleTensorsAreBinaryEncoded() {
        TensorType type = new TensorType.Builder().mapped("x").mapped("y").mapped("z").build();
        Tensor tensor1 = Tensor.from(type, "{ {x:a, y:b, z:c}:2.0, {x:a, y:b, z:c2}:3.0 }");
        Tensor tensor2 = Tensor.from(type, "{ {x:a, y:b, z:c}:5.0 }");
        assertTensorEncodingAndDecoding(type, Arrays.asList(
                new Entry("query(tensor1)", "tensor1", tensor1),
                new Entry("$tensor2", "tensor2", tensor2)));
    }

    private static class Entry {
        final String key;
        final String normalizedKey;
        final Tensor tensor;
        Entry(String key, String normalizedKey, Tensor tensor) {
            this.key = key;
            this.normalizedKey = normalizedKey;
            this.tensor = tensor;
        }
    }

    private static void assertTensorEncodingAndDecoding(TensorType type, List<Entry> entries) {
        RankProperties properties = createRankPropertiesWithTensors(entries);
        assertEquals(entries.size(), properties.asMap().size());

        Map<String, Object> decodedProperties = decode(type, encode(properties));
        assertEquals(entries.size(), properties.asMap().size());
        assertEquals(entries.size(), decodedProperties.size());
        for (Entry entry : entries) {
            assertEquals(entry.tensor, decodedProperties.get(entry.normalizedKey));
        }
    }

    private static void assertTensorEncodingAndDecoding(TensorType type, String key, String normalizedKey, Tensor tensor) {
        assertTensorEncodingAndDecoding(type, Arrays.asList(new Entry(key, normalizedKey, tensor)));
    }

    private static RankProperties createRankPropertiesWithTensors(List<Entry> entries) {
        RankFeatures features = new RankFeatures(new Ranking(new Query()));
        for (Entry entry : entries) {
            features.put(entry.key, entry.tensor);
        }
        RankProperties properties = new RankProperties();
        features.prepare(properties);
        return properties;
    }

    private static byte[] encode(RankProperties properties) {
        ByteBuffer buffer = ByteBuffer.allocate(512);
        properties.encode(buffer, true);
        byte[] result = new byte[buffer.position()];
        buffer.rewind();
        buffer.get(result);
        return result;
    }

    private static Map<String, Object> decode(TensorType type, byte[] encodedProperties) {
        GrowableByteBuffer buffer = GrowableByteBuffer.wrap(encodedProperties);
        byte[] mapNameBytes = new byte[buffer.getInt()];
        buffer.get(mapNameBytes);
        int numEntries = buffer.getInt();
        Map<String, Object> result = new HashMap<>();
        for (int i = 0; i < numEntries; ++i) {
            byte[] keyBytes = new byte[buffer.getInt()];
            buffer.get(keyBytes);
            String key = Utf8.toString(keyBytes);
            byte[] value = new byte[buffer.getInt()];
            buffer.get(value);
            if (key.contains(".type")) {
                result.put(key, Utf8.toString(value));
            } else {
                result.put(key, TypedBinaryFormat.decode(Optional.of(type), GrowableByteBuffer.wrap(value)));
            }
        }
        return result;
    }

}
