// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor.evaluation;

import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.functions.PrimitiveTensorFunction;
import com.yahoo.tensor.functions.TensorFunction;
import com.yahoo.tensor.functions.ToStringContext;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A tensor variable name which resolves to a tensor in the context at evaluation time
 *
 * @author bratseth
 */
public class VariableTensor<NAMETYPE extends Name> extends PrimitiveTensorFunction<NAMETYPE> {

    private final String name;
    private final Optional<TensorType> requiredType;

    public VariableTensor(String name) {
        this.name = name;
        this.requiredType = Optional.empty();
    }

    /** A variable tensor which must be compatible with the given type */
    public VariableTensor(String name, TensorType requiredType) {
        this.name = name;
        this.requiredType = Optional.of(requiredType);
    }

    @Override
    public List<TensorFunction<NAMETYPE>> arguments() { return Collections.emptyList(); }

    @Override
    public TensorFunction<NAMETYPE> withArguments(List<TensorFunction<NAMETYPE>> arguments) { return this; }

    @Override
    public PrimitiveTensorFunction<NAMETYPE> toPrimitive() { return this; }

    @Override
    public TensorType type(TypeContext<NAMETYPE> context) {
        TensorType givenType = context.getType(name);
        if (givenType == null) return null;
        verifyType(givenType);
        return givenType;
    }

    @Override
    public Tensor evaluate(EvaluationContext<NAMETYPE> context) {
        Tensor tensor = context.getTensor(name);
        if (tensor == null) return null;
        verifyType(tensor.type());
        return tensor;
    }

    @Override
    public String toString(ToStringContext<NAMETYPE> context) {
        return name;
    }

    @Override
    public int hashCode() { return Objects.hash("variableTensor", name, requiredType); }

    private void verifyType(TensorType givenType) {
        if (requiredType.isPresent() && ! givenType.isAssignableTo(requiredType.get()))
            throw new IllegalArgumentException("Variable '" + name + "' must be compatible with " +
                                               requiredType.get() + " but was " + givenType);
    }

}
