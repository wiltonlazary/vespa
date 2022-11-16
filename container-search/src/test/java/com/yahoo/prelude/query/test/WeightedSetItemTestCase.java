// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query.test;

import com.yahoo.prelude.query.CompositeIndexedItem;
import com.yahoo.prelude.query.Item;
import com.yahoo.prelude.query.PureWeightedString;
import com.yahoo.prelude.query.WeightedSetItem;
import com.yahoo.prelude.query.WordItem;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.*;

public class WeightedSetItemTestCase {

    @Test
    void testTokenAPI() {
        WeightedSetItem ws = new WeightedSetItem("index");
        assertEquals(0, ws.getNumTokens());
        assertNull(ws.getTokenWeight("bogus"));

        // insert tokens
        assertEquals(Integer.valueOf(1), ws.addToken("foo"));
        assertEquals(Integer.valueOf(2), ws.addToken("bar", 2));
        assertEquals(Integer.valueOf(3), ws.addToken("baz", 3));

        // check state
        assertEquals(3, ws.getNumTokens());
        assertEquals(Integer.valueOf(1), ws.getTokenWeight("foo"));
        assertEquals(Integer.valueOf(2), ws.getTokenWeight("bar"));
        assertEquals(Integer.valueOf(3), ws.getTokenWeight("baz"));

        // add duplicate tokens
        assertEquals(Integer.valueOf(2), ws.addToken("foo", 2));
        assertEquals(Integer.valueOf(3), ws.addToken("baz", 2));

        // check state
        assertEquals(3, ws.getNumTokens());
        assertEquals(Integer.valueOf(2), ws.getTokenWeight("foo"));
        assertEquals(Integer.valueOf(2), ws.getTokenWeight("bar"));
        assertEquals(Integer.valueOf(3), ws.getTokenWeight("baz"));

        // remove token
        assertEquals(Integer.valueOf(2), ws.removeToken("bar"));
        assertEquals(2, ws.getNumTokens());
        assertNull(ws.getTokenWeight("bar"));

        // remove non-existing token
        assertNull(ws.removeToken("bogus"));
        assertEquals(2, ws.getNumTokens());
    }

    @Test
    void testNegativeWeight() {
        WeightedSetItem ws = new WeightedSetItem("index");
        assertEquals(Integer.valueOf(-10), ws.addToken("bad", -10));
        assertEquals(1, ws.getNumTokens());
        assertEquals(Integer.valueOf(-10), ws.getTokenWeight("bad"));
    }

    static class FakeWSItem extends CompositeIndexedItem {
        public FakeWSItem() { setIndexName("index"); }
        public ItemType getItemType() { return ItemType.WEIGHTEDSET; }
        public String getName() { return "WEIGHTEDSET"; }
        public int getNumWords() { return 1; }
        public String getIndexedString() { return ""; }

        public void add(String token, int weight) {
            WordItem w = new WordItem(token, getIndexName());
            w.setWeight(weight);
            super.addItem(w);
        }
    }

    @Test
    void testEncoding() {
        WeightedSetItem item = new WeightedSetItem("index");
        // need 2 alternative reference encoding, as the encoding
        // order is kept undefined to improve performance.
        FakeWSItem ref1 = new FakeWSItem();
        FakeWSItem ref2 = new FakeWSItem();

        item.addToken("foo", 10);
        item.addToken("bar", 20);
        ref1.add("foo", 10);
        ref1.add("bar", 20);
        ref2.add("bar", 20);
        ref2.add("foo", 10);

        ByteBuffer actual = ByteBuffer.allocate(128);
        ByteBuffer expect1 = ByteBuffer.allocate(128);
        ByteBuffer expect2 = ByteBuffer.allocate(128);
        expect1.put((byte) 15).put((byte) 2);
        Item.putString("index", expect1);
        new PureWeightedString("foo", 10).encode(expect1);
        new PureWeightedString("bar", 20).encode(expect1);
        expect2.put((byte) 15).put((byte) 2);
        Item.putString("index", expect2);
        new PureWeightedString("bar", 20).encode(expect2);
        new PureWeightedString("foo", 10).encode(expect2);

        assertEquals(3, item.encode(actual));

        actual.flip();
        expect1.flip();
        expect2.flip();

        if (actual.equals(expect1)) {
            assertNotEquals(actual, expect2);
        } else {
            assertEquals(actual, expect2);
        }
    }

}
