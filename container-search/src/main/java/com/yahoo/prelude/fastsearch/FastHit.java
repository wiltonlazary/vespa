// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.fastsearch;

import com.yahoo.data.access.ObjectTraverser;
import com.yahoo.document.GlobalId;
import com.yahoo.net.URI;
import com.yahoo.search.dispatch.LeanHit;
import com.yahoo.search.query.Sorting;
import com.yahoo.search.result.FeatureData;
import com.yahoo.search.result.Hit;
import com.yahoo.search.result.Relevance;
import com.yahoo.data.access.Inspector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * A regular hit from a Vespa backend
 *
 * @author bratseth
 * @author Steinar Knutsen
 */
public class FastHit extends Hit {

    private static final byte[] emptyGID = new byte[GlobalId.LENGTH];
    /** The index of the content node this hit originated at */
    private int distributionKey;

    /** The local identifier of the content store for this hit on the node it originated at */
    private final int partId;

    /** The global id of this document in the backend node which produced it */
    private byte[] globalId;

    private transient byte[] sortData = null;
    // TODO: I suspect this one can be dropped.
    private transient Sorting sortDataSorting = null;

    /**
     * Summaries added to this hit which are not yet decoded into fields.
     * Fields are resolved by returning the first non-null value found by
     * 1) the field value from the Map of fields in the Hit supertype, and
     * 2) each of the summaries, reverse add order
     * This ensures that values set from code overwrites any value received as
     * summary data, and fetching a new summary overrides previous summaries.
     *
     * The reason we keep this rather than eagerly decoding into a the field map
     * is to reduce garbage collection and decoding cost, with the assumption
     * that most fields passes through the container with no processing most
     * of the time.
     */
    private List<SummaryData> summaries = Collections.emptyList();

    /** Removed field values, which should therefore not be returned if present in summary data */
    private Set<String> removedFields = null;

    /**
     * Creates an empty and temporarily invalid summary hit
     */
    public FastHit() {
        super(new Relevance(0.0));
        globalId = emptyGID;
        partId = 0;
        distributionKey = 0;
    }

    public FastHit(byte[] gid, double relevance, int partId, int distributionKey) {
        this(gid, new Relevance(relevance), partId, distributionKey);
    }
    public FastHit(byte[] gid, Relevance relevance, int partId, int distributionKey) {
        super(relevance);
        this.globalId = gid;
        this.partId = partId;
        this.distributionKey = distributionKey;
    }

    // Note: This constructor is only used for tests, production use is always of the empty constructor
    public FastHit(String uri, double relevancy) {
        this(uri, relevancy, null);
    }

    // Note: This constructor is only used for tests, production use is always of the empty constructor
    private FastHit(String uri, double relevance, String source) {
        super(new Relevance(relevance));
        partId = 0;
        distributionKey = 0;
        globalId = emptyGID;
        setId(uri);
        setSource(source);
        types().add("summary");
    }

    /** Returns false - this is a concrete hit containing requested content */
    public boolean isMeta() { return false; }

    /**
     * Returns the explicitly set uri if available, returns "index:[source]/[partid]/[id]" otherwise
     *
     * @return uri of hit
     */
    @Override
    public URI getId() {
        URI id = super.getId();
        if (id != null) return id;

        // Fallback to index:[source]/[partid]/[id]
        StringBuilder sb = new StringBuilder(64);
        sb.append("index:").append(getSource()).append('/').append(getPartId()).append('/');
        appendAsHex(globalId, sb);
        URI indexUri = new URI(sb.toString());
        assignId(indexUri);
        return indexUri;
    }

    /** Returns the global id of this document in the backend node which produced it */
    public GlobalId getGlobalId() { return new GlobalId(globalId); }
    public byte [] getRawGlobalId() { return globalId; }

    public void setGlobalId(byte [] globalId) { this.globalId = globalId; }

    public int getPartId() { return partId; }

    /** Returns the index of the node this hit originated at */
    public int getDistributionKey() { return distributionKey; }

    /** Sets the index of the node this hit originated at */
    public void setDistributionKey(int distributionKey) { this.distributionKey = distributionKey; }

    public void setSortData(byte[] data, Sorting sorting) {
        this.sortData = data;
        this.sortDataSorting = sorting;
    }

    @Override
    public int compareTo(Hit other) {
        int cmpRes = 0;
        if ((sortData != null) && (other instanceof FastHit) && hasSortData(((FastHit) other).sortDataSorting)) {
            cmpRes =  SortDataHitSorter.getComparator(sortDataSorting, null).compare(this, other);
        }
        return (cmpRes != 0) ? cmpRes : super.compareTo(other);
    }

    boolean hasSortData(Sorting sorting) {
        return sortData != null && sortDataSorting != null && sortDataSorting.equals(sorting);
    }

    static int compareSortData(FastHit left, FastHit right, Sorting sorting) {
        if (!left.hasSortData(sorting) || !right.hasSortData(sorting)) {
            return 0; // cannot sort
        }
        return LeanHit.compareData(left.sortData, right.sortData);
    }

    /** For internal use */
    public void addSummary(DocsumDefinition docsumDef, Inspector value) {
        if (removedFields != null)
            removedFields.removeAll(docsumDef.fields().keySet());
        if ( ! (summaries instanceof ArrayList) ) summaries = new ArrayList<>(8);
        summaries.add(0, new SummaryData(this, docsumDef, value, 1 + summaries.size()));
    }

    /**
     * Returns values for the features listed in
     * <a href="https://docs.vespa.ai/en/reference/schema-reference.html#summary-features">summary-features</a>
     * in the rank profile specified in the query producing this.
     */
    public FeatureData features() {
        FeatureData data = (FeatureData)getField("summaryfeatures");
        return data == null ? super.features() : data;
    }

    /**
     * <p>Returns a field value from this Hit. The value is either a stored value from the Document represented by
     * this Hit, or a generated value added during later processing.</p>
     *
     * <p>The values available from the matching Document are a <i>subset</i> of the values set in the document,
     * determined by the {@link #getFilled() filled} status of this Hit. More fields may be requested by requesting
     * further filling.</p>
     *
     * <p>Lookups on names which does not exists in the document and is not added by later processing
     * return null.</p>
     *
     * <p>Lookups on fields which exist in the document, in a summary class which is already requested
     * filled returns the following types, even when the field has no actual value:</p>
     *
     * <ul>
     *     <li><b>string and uri fields</b>: A Java String.<br>
     *     The empty string ("") if no value is assigned in the document.
     *
     *     <li><b>Dynamic summary string fields</b>: A Java String before JuniperSearcher and a HitField after.</li>
     *
     *     <li><b>Numerics</b>: The corresponding numeric Java type.<br>
     *     If the field has <i>no value</i> assigned in the document,
     *     the special numeric {@link com.yahoo.search.result.NanNumber#NaN} is returned.
     *
     *     <li><b>raw fields</b>: A {@link com.yahoo.prelude.hitfield.RawData} instance
     *
     *     <li><b>tensor fields</b>: A {@link com.yahoo.tensor.Tensor} instance
     *
     *     <li><b>multivalue fields</b>: A {@link com.yahoo.data.access.Inspector} instance
     * </ul>
     */
    @Override
    public Object getField(String name) {
        Object value = super.getField(name);
        if (value != null) return value;
        value = getSummaryValue(name);
        if (value != null)
            super.setField(name, value);
        return value;
    }

    @Override
    public Object setField(String name, Object value) {
        if (removedFields != null) {
            if (removedFields.remove(name)) {
                if (removedFields.isEmpty())
                    removedFields = null;
            }
        }
        Object oldValue = super.setField(name, value);
        if (oldValue != null) return oldValue;
        return getSummaryValue(name);
    }

    @Override
    public void forEachField(BiConsumer<String, Object> consumer) {
        super.forEachField(consumer);
        for (SummaryData summaryData : summaries)
            summaryData.forEachField(consumer);
    }

    @Override
    public void forEachFieldAsRaw(RawUtf8Consumer consumer) {
        super.forEachField(consumer);
        for (SummaryData summaryData : summaries)
            summaryData.forEachFieldAsRaw(consumer);
    }

    @Override
    public Map<String, Object> fields() {
        Map<String, Object> fields = new HashMap<>();
        for (Iterator<Map.Entry<String, Object>> i = fieldIterator(); i.hasNext(); ) {
            Map.Entry<String, Object> field = i.next();
            fields.put(field.getKey(), field.getValue());
        }
        return fields;
    }

    @Override
    public Iterator<Map.Entry<String, Object>> fieldIterator() {
        return new FieldIterator(this, super.fieldIterator());
    }

    /**
     * Returns the keys of the fields of this hit as a modifiable view.
     * This follows the rules of key sets returned from maps: Key removals are reflected
     * in the map, add and addAll is not supported.
     */
    @Override
    public Set<String> fieldKeys() {
        return new FieldSet(this);
    }

    /** Returns a modifiable iterator over the field names of this */
    private Iterator<String> fieldNameIterator() {
        return new FieldNameIterator(this, super.fieldKeys().iterator());
    }

    /** Removes all fields of this */
    @Override
    public void clearFields() {
        summaries.clear();
        if (removedFields != null)
            removedFields = null;
        super.clearFields();
    }

    /**
     * Removes a field from this
     *
     * @return the removed value of the field, or null if none
     */
    @Override
    public Object removeField(String name) {
        Object removedValue = super.removeField(name);
        if (removedValue == null)
            removedValue = getSummaryValue(name);

        if (removedValue != null) {
            if (removedFields == null)
                removedFields = new HashSet<>(2);
            removedFields.add(name);
        }

        return removedValue;
    }

    private Set<String> mapFieldKeys() {
        return super.fieldKeys();
    }

    /** Returns whether this field is present <b>in the field map in the parent hit</b> */
    // Note: If this is made public it must be changed to also check the summary data
    //       (and internal usage must change to another method).
    @Override
    protected boolean hasField(String name) {
        return super.hasField(name);
    }

    /** Returns whether any fields are present <b>in the field map in the parent hit</b> */
    // Note: If this is made public it must be changed to also check the summary data
    //       (and internal usage must change to another method).
    @Override
    protected boolean hasFields() {
        return super.hasFields();
    }

    private Object getSummaryValue(String name) {
        if (removedFields != null && removedFields.contains(name))
            return null;
        // fetch from last added summary with the field
        for (SummaryData summaryData : summaries) {
            Object value = summaryData.getField(name);
            if (value != null) return value;
        }
        return null;
    }

    @Override
    public String toString() {
        return super.toString() + " [fasthit, globalid: " + new GlobalId(globalId).toString() + ", partId: " +
               partId + ", distributionkey: " + distributionKey + "]";
    }

    @Override
    public int hashCode() {
        if (getId() == null) {
            throw new IllegalStateException("This hit must have a 'uri' field, and this field must be filled through " +
                                            "Execution.fill(Result)) before hashCode() is accessed.");
        } else {
            return super.hashCode();
        }
    }

    private static void appendAsHex(byte[] gid, StringBuilder sb) {
        for (byte b : gid) {
            String hex = Integer.toHexString(0xFF & b);
            if (hex.length() == 1) {
                sb.append('0');
            }
            sb.append(hex);
        }
    }

    /** A set view of all the field names in this hit. Add/addAll is not supported but remove is. */
    private static class FieldSet implements Set<String> {

        // A set implementation which tries to avoid creating an actual set when possible.
        // With more work this could be optimized to avoid creating the set in additional cases.

        private final FastHit hit;

        /** The computed set of fields. Lazily created as it is not always needed. */
        private Set<String> fieldSet = null;

        FieldSet(FastHit hit) {
            this.hit = hit;
        }

        @Override
        public int size() {
            return createSet().size();
        }

        @Override
        public boolean isEmpty() {
            if ( ! hit.hasFields() && hit.summaries.isEmpty()) return true;
            return createSet().isEmpty();
        }

        @Override
        public boolean contains(Object o) {
            String field = (String)o;
            if (hit.hasField(field)) return true;
            return createSet().contains(field);
        }

        @Override
        public Object[] toArray() {
            return createSet().toArray();
        }

        @Override
        public <T> T[] toArray(T[] a) {
            return createSet().toArray(a);
        }

        @Override
        public boolean add(String s) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean remove(Object o) {
            String field = (String)o;
            boolean removed = hit.removeField(field) != null;
            if (fieldSet != null)
                fieldSet.remove(field);
            return removed;
        }

        @Override
        public boolean containsAll(Collection<?> c) {
            for (Object field : c)
                if ( ! contains(field))
                    return false;
            return true;
        }

        @Override
        public boolean addAll(Collection<? extends String> c) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean retainAll(Collection<?> c) {
            Set<?> toRetain = c instanceof Set<?> ? (Set<?>)c : new HashSet<Object>(c);
            boolean anyRemoved = false;
            for (Iterator<String> i = iterator(); i.hasNext(); ) {
                String field = i.next();
                if (toRetain.contains(field)) continue;

                i.remove();
                anyRemoved = true;
            }
            return anyRemoved;
        }

        @Override
        public boolean removeAll(Collection<?> c) {
            boolean anyRemoved = false;
            for (Object field : c)
                if (remove(field))
                    anyRemoved = true;
            return anyRemoved;
        }

        @Override
        public void clear() {
            hit.clearFields();
            fieldSet = null;
        }

        @Override
        public Iterator<String> iterator() {
            return hit.fieldNameIterator();
        }

        @Override
        public int hashCode() {
            return createSet().hashCode();
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) return true;
            if ( ! (o instanceof Set)) return false;
            return createSet().equals(o);
        }

        @Override
        public String toString() {
            return createSet().toString();
        }

        private Set<String> createSet() {
            if (this.fieldSet != null) return this.fieldSet;
            if ( ! hit.hasFields() && hit.summaries.isEmpty()) return Collections.emptySet(); // shortcut

            Set<String> fields = new HashSet<>();
            if (hit.hasFields())
                fields.addAll(hit.mapFieldKeys());

            for (SummaryData summaryData : hit.summaries)
                summaryData.data.traverse((ObjectTraverser)(name, __) -> fields.add(name));
            if (hit.removedFields != null)
                fields.removeAll(hit.removedFields);

            this.fieldSet = fields;

            return fields;
        }

    }

    /** Summary data (values of a number of fields) received for this hit */
    private static class SummaryData {

        private final FastHit hit;
        private final DocsumDefinition type;
        private final Inspector data;

        /** The index from the end of this summary in the list of summaries */
        private final int index;

        SummaryData(FastHit hit, DocsumDefinition type, Inspector data, int index) {
            this.hit = hit;
            this.type = type;
            this.data = data;
            this.index = index;
        }

        Object getField(String name) {
            return type.convert(name, data.field(name));
        }

        void forEachField(BiConsumer<String, Object> consumer) {
            data.traverse((ObjectTraverser)(name, value) -> {
                if (!shadowed(name) && !removed(name)) {
                    Object convertedValue = type.convert(name, value);
                    if (convertedValue != null)
                        consumer.accept(name, convertedValue);
                }
            });
        }

        void forEachFieldAsRaw(RawUtf8Consumer consumer) {
            data.traverse((ObjectTraverser)(name, value) -> {
                if (!shadowed(name) && !removed(name)) {
                    DocsumField fieldType = type.fields().get(name);
                    if (fieldType != null) {
                        if (fieldType.isString()) {
                            byte[] utf8Value = value.asUtf8();
                            consumer.accept(name, utf8Value, 0, utf8Value.length);
                        } else {
                            Object convertedValue = fieldType.convert(value);
                            if (convertedValue != null)
                                consumer.accept(name, convertedValue);
                        }
                    }
                }
            });
        }

        Iterator<Map.Entry<String, Object>> fieldIterator() {
            return new SummaryDataFieldIterator(this, type, data.fields().iterator());
        }

        Iterator<String> fieldNameIterator() {
            return new SummaryDataFieldNameIterator(this, data.fields().iterator());
        }

        /**
         * Returns whether this field is present in the map properties
         * or a summary added later in this hit
         */
        private boolean shadowed(String name) {
            if (hit.hasField(name)) return true;
            for (int i = 0; i < hit.summaries.size() - index; i++) {
                if (hit.summaries.get(i).type.fields().containsKey(name))
                    return true;
            }
            return false;
        }

        private boolean removed(String fieldName) {
            return hit.removedFields != null && hit.removedFields.contains(fieldName);
        }

        /**
         * Abstract superclass of iterators over SummaryData content which takes care of skipping unknown,
         * removed and already returned fields. Read only.
         */
        private static abstract class SummaryDataIterator<VALUE> implements Iterator<VALUE> {

            private final SummaryData summaryData;
            private final Iterator<Map.Entry<String, Inspector>> fieldIterator;

            /** The next value or null if none, eagerly read because we need to skip removed and overwritten values */
            private VALUE next;

            SummaryDataIterator(SummaryData summaryData, Iterator<Map.Entry<String, Inspector>> fieldIterator) {
                this.summaryData = summaryData;
                this.fieldIterator = fieldIterator;
            }

            @Override
            public boolean hasNext() {
                return next != null;
            }

            @Override
            public VALUE next() {
                if (next == null) throw new NoSuchElementException();

                VALUE returnValue = next;
                advanceNext();
                return returnValue;
            }

            protected abstract VALUE toValue(Map.Entry<String, Inspector> field);

            void advanceNext() {
                while (fieldIterator.hasNext()) {
                    Map.Entry<String, Inspector> nextEntry = fieldIterator.next();
                    String fieldName = nextEntry.getKey();
                    next = toValue(nextEntry);
                    if ( next != null && ! summaryData.shadowed(fieldName) && ! summaryData.removed(fieldName))
                        return;
                }
                next = null;
            }

        }

        /** Iterator over the fields in a SummaryData instance. Read only. */
        private static class SummaryDataFieldIterator extends SummaryDataIterator<Map.Entry<String, Object>> {

            private final DocsumDefinition type;

            SummaryDataFieldIterator(SummaryData summaryData,
                                     DocsumDefinition type,
                                     Iterator<Map.Entry<String, Inspector>> fieldIterator) {
                super(summaryData, fieldIterator);
                this.type = type;
                advanceNext();
            }

            @Override
            protected Map.Entry<String, Object> toValue(Map.Entry<String, Inspector> field) {
                Object convertedValue = type.convert(field.getKey(), field.getValue());
                if (convertedValue == null) return null;
                return new SummaryFieldEntry(field.getKey(), convertedValue);
            }

            private static final class SummaryFieldEntry implements Map.Entry<String, Object> {

                private final String key;
                private final Object value;

                SummaryFieldEntry(String key, Object value) {
                    this.key = key;
                    this.value = value;
                }

                @Override
                public String getKey() { return key; }

                @Override
                public Object getValue() { return value; }

                @Override
                public Object setValue(Object value) { throw new UnsupportedOperationException(); }

            }

        }

        /** Iterator over the field names in a SummaryData instance. Read only. */
        private static class SummaryDataFieldNameIterator extends SummaryDataIterator<String> {

            SummaryDataFieldNameIterator(SummaryData summaryData,
                                         Iterator<Map.Entry<String, Inspector>> fieldIterator) {
                super(summaryData, fieldIterator);
                advanceNext();
            }

            @Override
            protected String toValue(Map.Entry<String, Inspector> field) { return field.getKey(); }

        }
    }

    /**
     * Abstract superclass of iterators over all the field content of a FastHit.
     * This handles iterating over the iterators of Hit and the SummaryData instances of the FastHit,
     * to provide a view of all the summary data of the FastHit.
     * Iteration over fields of each piece of data (of Hit or a SummaryData instance) is delegated to the
     * iterators of those types.
     */
    private static abstract class SummaryIterator<VALUE> implements Iterator<VALUE> {

        private final FastHit hit;

        /** -1 means that the current iterator is the map iterator of the parent hit, not any summary data iterator */
        private int currentSummaryDataIndex = -1;
        private Iterator<VALUE> currentIterator;
        private VALUE previousReturned = null;

        SummaryIterator(FastHit hit, Iterator<VALUE> mapFieldsIterator) {
            this.hit = hit;
            this.currentIterator = mapFieldsIterator;
        }

        @Override
        public boolean hasNext() {
            if (currentIterator.hasNext()) return true;
            return nextIterator();
        }

        @Override
        public VALUE next() {
            if (currentIterator.hasNext() || nextIterator()) return previousReturned = currentIterator.next();
            throw new NoSuchElementException();
        }

        @Override
        public void remove() {
            if (previousReturned == null)
                throw new IllegalStateException();
            if ( ! ( currentIterator instanceof SummaryData.SummaryDataIterator))
                currentIterator.remove(); // remove from the map
            if (hit.removedFields == null)
                hit.removedFields = new HashSet<>();
            hit.removedFields.add(nameOf(previousReturned));
            previousReturned = null;
        }

        protected abstract String nameOf(VALUE value);
        protected abstract Iterator<VALUE> iteratorFor(SummaryData summary);

        /** Advanced to the next non-empty iterator, if any */
        private boolean nextIterator() {
            while (++currentSummaryDataIndex < hit.summaries.size()) {
                currentIterator = iteratorFor(hit.summaries.get(currentSummaryDataIndex));
                if (currentIterator.hasNext())
                    return true;
            }
            return false;
        }

    }

    /** Iterator over all the field content of a FastHit */
    private static class FieldIterator extends SummaryIterator<Map.Entry<String, Object>> {

        FieldIterator(FastHit hit, Iterator<Map.Entry<String, Object>> mapFieldsIterator) {
            super(hit, mapFieldsIterator);
        }

        @Override
        protected String nameOf(Map.Entry<String, Object> value) {
            return value.getKey();
        }

        @Override
        protected Iterator<Map.Entry<String, Object>> iteratorFor(SummaryData summary) {
            return summary.fieldIterator();
        }

    }

    /** Iterator over all the field names stored in a FastHit */
    private static class FieldNameIterator extends SummaryIterator<String> {

        FieldNameIterator(FastHit hit, Iterator<String> mapFieldNamesIterator) {
            super(hit, mapFieldNamesIterator);
        }

        @Override
        protected String nameOf(String value) {
            return value;
        }

        @Override
        protected Iterator<String> iteratorFor(SummaryData summary) {
            return summary.fieldNameIterator();
        }

    }

}
