// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.xml;

import com.yahoo.text.XML;
import org.w3c.dom.Element;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Replaces model id references in configs by their url.
 *
 * @author lesters
 * @author bratseth
 */
public class ModelIdResolver {

    private static final Map<String, String> providedModels =
            Map.of("minilm-l6-v2", "https://data.vespa.oath.cloud/onnx_models/sentence_all_MiniLM_L6_v2.onnx",
                   "mpnet-base-v2", "https://data.vespa.oath.cloud/onnx_models/sentence-all-mpnet-base-v2.onnx",
                   "bert-base-uncased", "https://data.vespa.oath.cloud/onnx_models/bert-base-uncased-vocab.txt");

    /**
     * Finds any config values of type 'model' below the given config element and
     * supplies the url attribute of them if a model id is specified and hosted is true
     * (regardless of whether an url is already specified).
     *
     * @param component the XML element of any component
     */
    public static void resolveModelIds(Element component, boolean hosted) {
        if ( ! hosted) return;
        for (Element config : XML.getChildren(component, "config")) {
            for (Element value : XML.getChildren(config))
                transformModelValue(value);
        }
    }

    /** Expands a model config value into regular config values. */
    private static void transformModelValue(Element value) {
        if ( ! value.hasAttribute("model-id")) return;
        value.setAttribute("url", modelIdToUrl(value.getTagName(), value.getAttribute("model-id")));
    }

    private static String modelIdToUrl(String valueName, String modelId) {
        if ( ! providedModels.containsKey(modelId))
            throw new IllegalArgumentException("Unknown model id '" + modelId + "' on '" + valueName + "'. Available models are [" +
                                               providedModels.keySet().stream().sorted().collect(Collectors.joining(", ")) + "]");
        return providedModels.get(modelId);
    }

}
