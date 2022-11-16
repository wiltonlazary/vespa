// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.result;

import com.yahoo.collections.ArraySet;
import com.yahoo.component.provider.ListenableFreezableClass;
import com.yahoo.net.URI;
import com.yahoo.prelude.hitfield.HitField;
import com.yahoo.processing.Request;
import com.yahoo.processing.response.Data;
import com.yahoo.search.Query;
import com.yahoo.search.Searcher;

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.BiConsumer;

/**
 * <p>An item in the result of executing a query.</p>
 *
 * <p>Hits may be of the <i>meta</i> type, meaning that they contain some information
 * about the query or result which does not represent a particular matched item.
 * Meta hits are not counted in the hit count of the result, and should
 * usually never be filtered out.</p>
 *
 * <p>Some hit sources may produce hits which are not <i>filled</i>. A non-filled
 * hit may miss some or all of its property values. To fill those,
 * {@link com.yahoo.search.Searcher#fill fill} must be called on the search chain by the searcher
 * which requires those properties. This mechanism allows initial filtering to be
 * done of a lightweight version of the hits, which is cheaper if a significant
 * number of hits are filtered out.</p>
 *
 * <p>Do not cache this as it holds references to objects that should be garbage collected.</p>
 *
 * @author bratseth
 */
public class Hit extends ListenableFreezableClass implements Data, Comparable<Hit>, Cloneable {

    // Collection fields in hits are, when possible lazy because much of the work of a container
    // consists of allocating and then garbage collecting hits

    private static final String DOCUMENT_ID = "documentid";

    /** A collection of string keyed object properties. */
    private Map<String, Object> fields = null;
    private Map<String, Object> unmodifiableFieldMap = null;

    /** Metadata describing how a given searcher should treat this hit. */
    // TODO: The case for this is to allow multiple levels of federation searcher routing.
    //       Replace this by a cleaner specific solution to that problem.
    private Map<Searcher, Object> searcherSpecificMetaData;

    /** The id of this hit */
    private URI id;

    /** The types of this hit */
    private Set<String> types = null;

    /** The relevance of this hit */
    private Relevance relevance;

    /** Says whether this hit is cached */
    private boolean cached = false;

    /**
     * The summary classes for which this hit is filled. If this set
     * is 'null', it means that this hit is unfillable, which is
     * equivalent to a hit where all summary classes have already
     * been filled, or a hit where further filling will
     * yield no extra information, if you prefer to look at it that
     * way.
     */
    private Set<String> filled = null;
    private Set<String> unmodifiableFilled = null;

    /** The name of the source creating this hit */
    private String source = null;

    /**
     * Add number, assigned when adding the hit to a result,
     * used to order equal relevant hit by add order
     */
    private int addNumber = -1;

    /** The query which produced this hit. Used for multi phase searching */
    private Query query;

    /**
     * Set to true for hits which does not contain content,
     * but which contains meta information about the query or result
     */
    private boolean meta = false;

    /** If this is true, then this hit will not be counted as a concrete hit */
    private boolean auxiliary = false;

    /** The hit field used to store rank features */
    public static final String RANKFEATURES_FIELD = "rankfeatures";
    public static final String SDDOCNAME_FIELD = "sddocname";

    /** Creates an (invalid) empty hit. Id and relevance must be set before handoff */
    protected Hit() {}
    protected Hit(Relevance relevance) {
        this.relevance = relevance;
    }

    /**
     * Creates a minimal valid hit having relevance 1
     *
     * @param id the URI of a hit. This should be unique for this hit (but not for this
     *        <i>object instance</i> of course). For hit types refering to resources,
     *        this will be the full, valid url of the resource, for self-contained hits
     *        it is simply any unique string identification
     */
    public Hit(String id) {
        this(id, 1);
    }

    /**
     * Creates a minimal valid hit having relevance 1
     *
     * @param id the URI of a hit. This should be unique for this hit (but not for this
     *        <i>object instance</i> of course). For hit types referring to resources,
     *        this will be the full, valid url of the resource, for self-contained hits
     *        it is simply any unique string identification
     * @param query the query having this as a hit
     */
    public Hit(String id, Query query) {
        this(id, 1, query);
    }

    /**
     * Creates a minimal valid hit.
     *
     * @param id the URI of a hit. This should be unique for this hit (but not for this
     *        <i>object instance</i> of course). For hit types referring to resources,
     *        this will be the full, valid url of the resource, for self-contained hits
     *        it is simply any unique string identification
     * @param relevance a relevance measure, preferably normalized between 0 and 1
     * @throws IllegalArgumentException if the given relevance is not between 0 and 1
     */
    public Hit(String id, double relevance) {
        this(id,new Relevance(relevance));
    }

    /**
     * Creates a minimal valid hit.
     *
     * @param id the URI of a hit. This should be unique for this hit (but not for this
     *        <i>object instance</i> of course). For hit types referring to resources,
     *        this will be the full, valid url of the resource, for self-contained hits
     *        it is simply any unique string identification
     * @param relevance a relevance measure, preferably normalized between 0 and 1
     * @param query the query having this as a hit
     * @throws IllegalArgumentException if the given relevance is not between 0 and 1
     */
    public Hit(String id, double relevance, Query query) {
        this(id,new Relevance(relevance),query);
    }

    /**
     * Creates a minimal valid hit.
     *
     * @param id the URI of a hit. This should be unique for this hit (but not for this
     *        <i>object instance</i> of course). For hit types refering to resources,
     *        this will be the full, valid url of the resource, for self-contained hits
     *        it is simply any unique string identification
     * @param relevance the relevance of this hit
     * @throws IllegalArgumentException if the given relevance is not between 0 and 1000
     */
    public Hit(String id, Relevance relevance) {
        this(id, relevance, (String)null);
    }

    /**
     * Creates a minimal valid hit.
     *
     * @param id the URI of a hit. This should be unique for this hit (but not for this
     *        <i>object instance</i> of course). For hit types refering to resources,
     *        this will be the full, valid url of the resource, for self-contained hits
     *        it is simply any unique string identification
     * @param relevance the relevance of this hit
     * @param query the query having this as a hit
     * @throws IllegalArgumentException if the given relevance is not between 0 and 1000
     */
    public Hit(String id, Relevance relevance, Query query) {
        this(id, relevance,null, query);
    }

    /**
     * Creates a hit.
     *
     * @param id the URI of a hit. This should be unique for this hit (but not for this
     *        <i>object instance</i> of course). For hit types refering to resources,
     *        this will be the full, valid url of the resource, for self-contained hits
     *        it is simply any unique string identification
     * @param relevance a relevance measure, preferably normalized between 0 and 1
     * @param source the name of the source of this hit, or null if no source is being specified
     */
    public Hit(String id, double relevance, String source) {
        this(id, new Relevance(relevance), source, null);
    }

    /**
     * Creates a hit.
     *
     * @param id the URI of a hit. This should be unique for this hit (but not for this
     *        <i>object instance</i> of course). For hit types refering to resources,
     *        this will be the full, valid url of the resource, for self-contained hits
     *        it is simply any unique string identification
     * @param relevance a relevance measure, preferably normalized between 0 and 1
     * @param source the name of the source of this hit, or null if no source is being specified
     * @param query the query having this as a hit
     */
    public Hit(String id, double relevance, String source, Query query) {
        this(id, new Relevance(relevance), source);
    }

    /**
     * Creates a hit.
     *
     * @param id the URI of a hit. This should be unique for this hit (but not for this
     *        <i>object instance</i> of course). For hit types refering to resources,
     *        this will be the full, valid url of the resource, for self-contained hits
     *        it is simply any unique string identification
     * @param relevance the relevance of this hit
     * @param source the name of the source of this hit
     */
    public Hit(String id, Relevance relevance, String source) {
        this(id, relevance, source, null);
    }

    /**
     * Creates a hit.
     *
     * @param id the URI of a hit. This should be unique for this hit (but not for this
     *        <i>object instance</i> of course). For hit types refering to resources,
     *        this will be the full, valid url of the resource, for self-contained hits
     *        it is simply any unique string identification
     * @param relevance the relevance of this hit
     * @param source the name of the source of this hit
     * @param query the query having this as a hit
     */
    public Hit(String id, Relevance relevance, String source, Query query) {
        this.id = new URI(id);
        this.relevance = relevance;
        this.source = source;
        this.query = query;
    }

    /** Calls setId(new URI(id)) */
    public void setId(String id) {
        if (this.id != null) throw new IllegalStateException("Attempt to change id of " + this + " to " + id);
        if (id == null) throw new NullPointerException("Attempt to assign id of " + this + " to null");
        assignId(new URI(id));
    }


    /**
     * Initializes the id of this hit.
     *
     * @throws NullPointerException if the uri is null
     * @throws IllegalStateException if the uri of this hit is already set
     */
    public void setId(URI id) {
        if (this.id != null) throw new IllegalStateException("Attempt to change id of " + this + " to " + id);
        assignId(id);
    }

    /**
     * Assigns a new or changed id to this hit.
     * As this is protected, reassigning isn't legal for Hits by default, however, subclasses may allow it
     * using this method.
     */
    protected final void assignId(URI id) {
        if (id == null) throw new NullPointerException("Attempt to assign id of " + this + " to null");
        this.id = id;
    }

    /** Returns the hit id */
    public URI getId() { return id; }

    /**
     * Returns the id to display, or null to not display (render) the id.
     * This is useful to avoid displaying ids when they are not assigned explicitly
     * but are just generated values for internal use.
     * This default implementation returns the field DOCUMENT_ID if set,
     * and {@link #getId()}.toString() otherwise.
     */
    public String getDisplayId() {
        String id = null;

        Object idField = getField(DOCUMENT_ID);
        if (idField != null)
            id = idField.toString();
        if (id == null)
            id = getId() == null ? null : getId().toString();
        return id;
    }

    /** Sets the relevance of this hit */
    public void setRelevance(Relevance relevance) {
        this.relevance = Objects.requireNonNull(relevance);
    }

    /** Does setRelevance(new Relevance(relevance) */
    public void setRelevance(double relevance) {
        setRelevance(new Relevance(relevance));
    }


    /** Returns the relevance of this hit */
    public Relevance getRelevance() { return relevance; }

    /** Sets whether this hit is returned from a cache. Default is false */
    public void setCached(boolean cached) { this.cached = cached; }

    /** Returns whether this hit was added to this result from a cache or not */
    public boolean isCached() { return cached; }

    /**
     * Tag this hit as fillable. This means that additional properties
     * for this hit may be obtained by fetching document
     * summaries. This also enables tracking of which summary classes
     * have been used for filling so far. Invoking this method
     * multiple times is allowed and will have no addition
     * effect. Note that a fillable hit may not be made unfillable.
     */
    public void setFillable() {
        if (filled == null) {
            filled = Collections.emptySet();
            unmodifiableFilled = filled;
        }
    }

    /**
     * Register that this hit has been filled with properties using
     * the given summary class. Note that this method will implicitly
     * tag this hit as fillable if it is currently not.
     *
     * @param summaryClass summary class used for filling
     */
    public void setFilled(String summaryClass) {
        if (filled == null || filled.isEmpty()) {
            filled = Collections.singleton(summaryClass);
            unmodifiableFilled = filled;
        } else if (filled.size() == 1) {
            filled = new HashSet<>(filled);
            unmodifiableFilled = Collections.unmodifiableSet(filled);

            filled.add(summaryClass);
        } else {
            filled.add(summaryClass);
        }
    }

    public boolean isFillable() {
        return filled != null;
    }

    /**
     * Returns the set of summary classes for which this hit is
     * filled as an unmodifiable set. If this set is 'null', it means that this hit is
     * unfillable, which is equivalent to a hit where all summary
     * classes have already been used for filling, or a hit where
     * further filling will yield no extra information, if you prefer
     * to look at it that way.
     *
     * Note that you might need to overload isFilled if you overload this one.
     */
    public Set<String> getFilled() {
        return unmodifiableFilled;
    }

    /**
     * Returns whether this hit has been filled with the properties
     * contained in the given summary class. Note that this method
     * will also return true if this hit is not fillable.
     */
    public boolean isFilled(String summaryClass) {
        return (filled == null) || filled.contains(summaryClass);
    }

    /** Sets the name of the source creating this hit */
    public void setSource(String source) { this.source = source; }

    /** Returns the name of the source creating this hit */
    public String getSource() { return source; }

    /**
     * Receive a callback on the given object for each field in this hit.
     * This is more efficient than accessing the fields as a map or iterator.
     */
    public void forEachField(BiConsumer<String, Object> consumer) {
        if (fields == null) return;
        fields.forEach(consumer);
    }

    /**
     * Receive a callback on the given object for each field in this hit,
     * where the callback will provide raw utf-8 byte data for strings whose data
     * is already available at this form.
     * This is the most resource efficient way of traversing all the fields of a hit
     * in renderers which produces utf-8.
     */
    public void forEachFieldAsRaw(RawUtf8Consumer consumer) {
        if (fields == null) return;
        fields.forEach(consumer);
    }

    /** Returns the fields of this as a read-only map. This is more costly than fieldIterator() */
    public Map<String, Object> fields() { return getUnmodifiableFieldMap(); }

    /** Returns a modifiable iterator over the fields of this */
    public Iterator<Map.Entry<String, Object>> fieldIterator() { return getFieldMap().entrySet().iterator(); }

    /**
     * Returns the keys of the fields of this hit as a modifiable view.
     * This follows the rules of key sets returned from maps: Key removals are reflected
     * in the map, add and addAll is not supported.
     */
    public Set<String> fieldKeys() {
        return getFieldMap().keySet();
    }

    /** Allocate room for the given number of fields to avoid resizing. */
    public void reserve(int minSize) {
        getFieldMap(minSize);
    }

    /**
     * Sets the value of a field
     *
     * @return the previous value, or null if none
     */
    public Object setField(String key, Object value) {
        return getFieldMap().put(key, value);
    }

    /** Returns a field value or null if not present */
    public Object getField(String value) { return fields != null ? fields.get(value) : null; }

    /** Removes all fields of this */
    public void clearFields() {
        getFieldMap().clear();
    }

    /**
     * Removes a field from this
     *
     * @return the removed value of the field, or null if none
     */
    public Object removeField(String field) {
        return getFieldMap().remove(field);
    }

    protected boolean hasField(String name) {
        return fields != null && fields.containsKey(name);
    }

    /** Returns whether any fields are set in this */
    protected boolean hasFields() {
        return fields != null && ! fields.isEmpty();
    }

    private Map<String, Object> getFieldMap() {
        return getFieldMap(2);
    }

    private Map<String, Object> getFieldMap(int minSize) {
        if (fields == null) {
            // Compensate for loadfactor and then some, rounded up....
            fields = new LinkedHashMap<>(2 * minSize);
        }
        return fields;
    }

    private Map<String, Object> getUnmodifiableFieldMap() {
        if (unmodifiableFieldMap == null) {
            if (fields == null) {
                return Collections.emptyMap();
            } else {
                unmodifiableFieldMap = Collections.unmodifiableMap(fields);
            }
        }
        return unmodifiableFieldMap;
    }

    public HitField buildHitField(String key, boolean forceNoPreTokenize) {
        Object o = getField(key);
        if (o == null) return null;
        if (o instanceof HitField) return (HitField)o;

        HitField h;
        if (forceNoPreTokenize)
            h = new HitField(key, o.toString(), false);
        else
            h = new HitField(key, o.toString());
        h.setOriginal(o);
        getFieldMap().put(key, h);
        return h;
    }

    /** Returns the types of this as a modifiable set. Modifications to this set are directly reflected in this hit */
    // TODO: This should not be exposed as a modifiable set
    public Set<String> types() {
        if (types == null)
            types = new ArraySet<>(1);
        return types;
    }

    /**
     * Returns the add number, assigned when adding the hit to a Result.
     *
     * Used to order equal relevant hit by add order. -1 if this hit
     * has never been added to a result.
     */
    int getAddNumber() { return addNumber; }

    /**
     * Sets the add number, assigned when adding the hit to a Result,
     * used to order equal relevant hit by add order.
     */
    void setAddNumber(int addNumber) { this.addNumber = addNumber; }

    /**
     * Returns whether this is a concrete hit, containing content of the requested
     * kind, or a meta hit containing information on the collection of hits,
     * the query, the service and so on. This default implementation return false.
     */
    public boolean isMeta() { return meta; }

    public void setMeta(boolean meta) { this.meta=meta; }

    /**
     * Auxiliary hits are not counted towards the concrete number of hits to satisfy in the users request.
     * Any kind of meta hit is auxiliary, but hits containing concrete results can also be auxiliary,
     * for example ads in a service which does not primarily serve ads, or groups in a hierarchical organization.
     *
     * @return true if the auxiliary value is true, or if this is a meta hit
     */
    public boolean isAuxiliary() {
        return isMeta() || auxiliary;
    }

    public void setAuxiliary(boolean auxiliary) { this.auxiliary = auxiliary; }

    /** Returns the query which produced this hit, or null if not known */
    public Query getQuery() { return query; }

    /** Returns the query which produced this hit as a request, or null if not known */
    public Request request() { return query; }

    /** Sets the query which produced this. This is ignored (except if this is a HitGroup) if a query is already set */
    public final void setQuery(Query query) {
        if (this.query == null || this instanceof HitGroup) {
            this.query = query;
        }
    }

    /**
     * Returns the features computed for this hit. This is never null but may be empty.
     * This default implementation always returns empty.
     */
    public FeatureData features() {
        return FeatureData.empty();
    }

    /** Attach some data to this hit for this searcher */
    public void setSearcherSpecificMetaData(Searcher searcher, Object data) {
        if (searcherSpecificMetaData == null) {
            searcherSpecificMetaData = Collections.singletonMap(searcher, data);
        } else {
            if (searcherSpecificMetaData.size() == 1) {
                Object tmp = searcherSpecificMetaData.get(searcher);
                if (tmp != null) {
                    searcherSpecificMetaData = Collections.singletonMap(searcher, data);
                } else {
                    searcherSpecificMetaData = new TreeMap<>(searcherSpecificMetaData);
                    searcherSpecificMetaData.put(searcher, data);
                }
            } else {
                searcherSpecificMetaData.put(searcher, data);
            }
        }
    }

    /** Returns data attached to this hit for this searcher, or null if none */
    public Object getSearcherSpecificMetaData(Searcher searcher) {
        return searcherSpecificMetaData != null ? searcherSpecificMetaData.get(searcher) : null;
    }

    final void setFilledInternal(Set<String> filled) {
        this.filled = filled;
        unmodifiableFilled = (filled != null) ? Collections.unmodifiableSet(filled) : null;
    }

    /**
     * Gives access to the modifiable backing set of filled summaries.
     * This set might be unmodifiable if the size is less than or equal to 1
     *
     * @return the set of filled summaries.
     */
    final Set<String> getFilledInternal() {
        return filled;
    }

    /** Releases the resources held by this, making it irreversibly unusable */
    protected void close() {
        query = null;
        fields = null;
        unmodifiableFieldMap = null;
    }

    /** Returns true if the argument is a hit having the same id as this */
    @Override
    public boolean equals(Object other) {
        if (other == this) return true;
        if ( ! (other instanceof Hit)) return false;
        return getId().equals(((Hit) other).getId());
    }

    /** Returns the hashCode of this hit: The hashcode of its id */
    @Override
    public int hashCode() {
        if (getId() == null)
            throw new IllegalStateException("Id has not been set.");

        return getId().hashCode();
    }

    /** Compares this hit to another hit */
    @Override
    public int compareTo(Hit other) {
        // higher relevance is before
        int result = other.getRelevance().compareTo(getRelevance());
        if (result != 0)
            return result;

        // lower addnumber is before
        result = this.getAddNumber() - other.getAddNumber();
        if (result != 0)
            return result;

        // if all else fails, compare ids
        if (this.getId() == null && other.getId() == null)
            return 0;
        else if (other.getId() == null)
            return -1;
        else if (this.getId() == null)
            return 1;
        else
            return this.getId().compareTo(other.getId());
    }

    @Override
    public Hit clone() {
        Hit hit = (Hit) super.clone();

        hit.fields = fields != null ? new LinkedHashMap<>(fields) : null;
        hit.unmodifiableFieldMap = null;
        if (types != null)
            hit.types = new LinkedHashSet<>(types);
        if (filled != null) {
            hit.setFilledInternal(new HashSet<>(filled));
        }

        return hit;
    }

    @Override
    public String toString() {
        return "hit " + getId() + " (relevance " + getRelevance() + ")";
    }

    public interface RawUtf8Consumer extends BiConsumer<String, Object> {

        /**
         * Called for fields which are available as UTF-8 instead of accept(String, Object).
         *
         * @param fieldName the name of the field
         * @param utf8Data raw utf-8 data. The receiver <b>must not</b> modify this data
         * @param offset the start index in the utf8Data array of the data to accept
         * @param length the length starting from offset in the utf8Data array of the data to accept
         */
        void accept(String fieldName, byte[] utf8Data, int offset, int length);

    }

}
