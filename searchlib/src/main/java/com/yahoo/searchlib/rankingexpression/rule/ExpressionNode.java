// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.rule;

import com.yahoo.searchlib.rankingexpression.Reference;
import com.yahoo.searchlib.rankingexpression.evaluation.Context;
import com.yahoo.searchlib.rankingexpression.evaluation.Value;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.evaluation.TypeContext;

import java.io.Serializable;
import java.util.Deque;

/**
 * Superclass of all expression nodes. Expression nodes have their identity determined by their content.
 * All expression nodes are immutable.
 *
 * @author Simon Thoresen Hult
 */
public abstract class ExpressionNode implements Serializable {

    /**
     * Returns this in serialized form.
     *
     * @param builder the StringBuilder that will be appended to
     * @param context the serialization context
     * @param path the call path to this, used for cycle detection, or null if this is a root
     * @param parent the parent node of this, or null if it is a root
     * @return the main script, referring to script instances.
     */
    public abstract StringBuilder toString(StringBuilder builder, SerializationContext context, Deque<String> path, CompositeNode parent);

    /**
     * Returns the type this will return if evaluated with the given context.
     *
     * @param context the variable type bindings to use for this evaluation
     * @throws IllegalArgumentException if there are variables which are not bound in the given map
     */
    public abstract TensorType type(TypeContext<Reference> context);

    /**
     * Returns the value of evaluating this expression over the given context.
     *
     * @param context the variable bindings to use for this evaluation
     * @throws IllegalArgumentException if there are variables which are not bound in the given map
     */
    public abstract Value evaluate(Context context);

    public final StringBuilder toString(SerializationContext context) {
        return toString(new StringBuilder(), context, null, null);
    }

    @Override
    public final boolean equals(Object obj) {
        return obj instanceof ExpressionNode && toString().equals(obj.toString());
    }

    /** Returns a hashcode computed from the data in this */
    @Override
    public abstract int hashCode();

    @Override
    public final String toString() {
        return toString(new SerializationContext()).toString();
    }

}
