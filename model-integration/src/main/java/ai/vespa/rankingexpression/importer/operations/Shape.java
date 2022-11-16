// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.rankingexpression.importer.operations;

import ai.vespa.rankingexpression.importer.OrderedTensorType;
import com.yahoo.searchlib.rankingexpression.Reference;
import com.yahoo.searchlib.rankingexpression.evaluation.TensorValue;
import com.yahoo.tensor.IndexedTensor;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.functions.TensorFunction;

import java.util.List;

public class Shape extends IntermediateOperation {

    public Shape(String modelName, String nodeName, List<IntermediateOperation> inputs) {
        super(modelName, nodeName, inputs);
        createConstantValue();
    }

    @Override
    protected OrderedTensorType lazyGetType() {
        if ( ! allInputTypesPresent(1)) return null;

        OrderedTensorType inputType = inputs.get(0).type().get();
        return new OrderedTensorType.Builder(resultValueType())
                .add(TensorType.Dimension.indexed(vespaName(), inputType.dimensions().size()))
                .build();
    }

    @Override
    protected TensorFunction<Reference> lazyGetFunction() {
        return null; // will be added by function() since this is constant.
    }

    @Override
    public boolean isConstant() {
        return true;
    }

    @Override
    public Shape withInputs(List<IntermediateOperation> inputs) {
        return new Shape(modelName(), name(), inputs);
    }

    private void createConstantValue() {
        if (!allInputTypesPresent(1)) {
            return;
        }
        OrderedTensorType inputType = inputs.get(0).type().get();
        IndexedTensor.BoundBuilder builder = (IndexedTensor.BoundBuilder) Tensor.Builder.of(type().get().type());
        List<TensorType.Dimension> inputDimensions = inputType.dimensions();
        for (int i = 0; i < inputDimensions.size(); i++) {
            builder.cellByDirectIndex(i, inputDimensions.get(i).size().orElse(-1L));
        }
        this.setConstantValue(new TensorValue(builder.build()));
    }

    @Override
    public String operationName() { return "Shape"; }

}
