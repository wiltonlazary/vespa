// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.collections;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * A map holding multiple items at each key (using ArrayList and HashMap).
 *
 * @author  bratseth
 */
public class ListMap<K, V> {

    private boolean frozen = false;

    private Map<K, List<V>> map;

    public ListMap() {
        this(HashMap.class);
    }

    /** Copy constructor. This will not be frozen even if the argument map is */
    public ListMap(ListMap<K, V> original) {
        map = new HashMap<>();
        original.map.forEach((k, v) -> this.map.put(k, new ArrayList<>(v)));
    }

    @SuppressWarnings("unchecked")
    public ListMap(@SuppressWarnings("rawtypes") Class<? extends Map> implementation) {
        try {
            this.map = implementation.getDeclaredConstructor().newInstance();
        } catch (InvocationTargetException e) {
            // For backwards compatibility from when this method used implementation.newInstance()
            throw new IllegalArgumentException(e.getCause());
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    /** Puts an element into this. Multiple elements at the same position are added to the list at this key */
    public void put(K key, V value) {
        List<V> list = map.get(key);
        if (list == null) {
            list = new ArrayList<>();
            map.put(key, list);
        }
        list.add(value);
    }

    /** Put a key without adding a new value, such that there is an empty list of values if no values are already added */
    public void put(K key) {
        List<V> list = map.get(key);
        if (list == null) {
            list = new ArrayList<>();
            map.put(key, list);
        }
    }

    /** Put this map in the state where it has just the given value of the given key */
    public void replace(K key, V value) {
        List<V> list = map.get(key);
        if (list == null) {
            put(key);
        }
        else {
            list.clear();
            list.add(value);
        }
    }

    public void removeAll(K key) {
        map.remove(key);
    }

    public boolean removeValue(K key, V value) {
        List<V> list = map.get(key);
        if (list != null)
            return list.remove(value);
        else
            return false;
    }

    /**
     * Removes the value at the given index.
     *
     * @return the removed value
     * @throws IndexOutOfBoundsException if there is no value at the given index for this key
     */
    public V removeValue(K key, int index) {
        List<V> list = map.get(key);
        if (list != null)
            return list.remove(index);
        else
            throw new IndexOutOfBoundsException("The list at '" + key + "' is empty");
    }

    /**
     * Returns the List containing the elements with this key, or an empty list
     * if there are no elements for this key.
     * The returned list can be modified to add and remove values if the value exists.
     */
    public List<V> get(K key) {
        List<V> list = map.get(key);
        if (list == null) return ImmutableList.of();;
        return list;
    }

    /** The same as get */
    public List<V> getList(K key) {
        return get(key);
    }

    /** Returns the entries of this. Entries will be unmodifiable if this is frozen. */
    public Set<Map.Entry<K,List<V>>> entrySet() { return map.entrySet(); }

    /** Returns the keys of this */
    public Set<K> keySet() { return map.keySet(); }

    /** Returns the list values of this */
    public Collection<List<V>> values() { return map.values(); }

    /**
     * Irreversibly prevent changes to the content of this.
     * If this is already frozen, this method does nothing.
     */
    public void freeze() {
        if (frozen) return;
        frozen = true;

        for (Map.Entry<K,List<V>> entry : map.entrySet())
            entry.setValue(ImmutableList.copyOf(entry.getValue()));
        this.map = ImmutableMap.copyOf(this.map);
    }

    /** Returns whether this allows changes */
    public boolean isFrozen() { return frozen; }

    @Override
    public String toString() {
        return "ListMap{" +
                "frozen=" + frozen +
                ", map=" + map +
                '}';
    }

    /** Returns the number of keys in this map */
    public int size() { return map.size(); }

    public void forEach(BiConsumer<K, List<V>> action) { map.forEach(action); }

}
