// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.rule;

import com.yahoo.searchlib.rankingexpression.Reference;
import com.yahoo.searchlib.rankingexpression.evaluation.Context;
import com.yahoo.searchlib.rankingexpression.evaluation.Value;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.evaluation.TypeContext;

import java.util.Deque;
import java.util.Objects;

/**
 * An opaque name in a ranking expression. This is used to represent names passed to the context
 * and interpreted by the given context in a way which is opaque to the ranking expressions.
 *
 * @author Simon Thoresen Hult
 */
// TODO: This is achieved by ReferenceNode in almost all cases - remove this
public final class NameNode extends ExpressionNode {

    private final String name;

    public NameNode(String name) {
        this.name = name;
    }

    public String getValue() {
        return name;
    }

    @Override
    public StringBuilder toString(StringBuilder string, SerializationContext context, Deque<String> path, CompositeNode parent) {
        return string.append(name);
    }

    @Override
    public TensorType type(TypeContext<Reference> context) { throw new RuntimeException("Named nodes can not have a type"); }

    @Override
    public Value evaluate(Context context) {
        throw new RuntimeException("Name nodes should never be evaluated");
    }

    @Override
    public int hashCode() { return Objects.hash("name", name); }

}
