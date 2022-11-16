// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation.change;

import com.yahoo.config.application.api.ValidationOverrides;
import com.yahoo.config.model.api.ConfigChangeAction;
import com.yahoo.config.model.api.ServiceInfo;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.documentmodel.NewDocumentType;
import com.yahoo.schema.derived.AttributeFields;
import com.yahoo.schema.document.Attribute;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.application.validation.change.search.ChangeMessageBuilder;
import com.yahoo.vespa.model.application.validation.change.search.DocumentTypeChangeValidator;
import com.yahoo.vespa.model.content.cluster.ContentCluster;
import com.yahoo.vespa.model.search.StreamingSearchCluster;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Validates the changes between all current and next streaming search clusters in a Vespa model.
 *
 * @author geirst
 */
public class StreamingSearchClusterChangeValidator implements ChangeValidator {

    @Override
    public List<ConfigChangeAction> validate(VespaModel current, VespaModel next, ValidationOverrides overrides, Instant now) {
        List<ConfigChangeAction> result = new ArrayList<>();
        current.getContentClusters().forEach((clusterName, currentCluster) -> {
            ContentCluster nextCluster = next.getContentClusters().get(clusterName);
            if (nextCluster != null) {
                List<StreamingSearchCluster> nextStreamingClusters = nextCluster.getSearch().getStreamingClusters();
                currentCluster.getSearch().getStreamingClusters().forEach(currentStreamingCluster -> {
                    Optional<StreamingSearchCluster> nextStreamingCluster = findStreamingCluster(currentStreamingCluster.getClusterName(), nextStreamingClusters);
                    if (nextStreamingCluster.isPresent()) {
                        result.addAll(validateStreamingCluster(currentCluster, currentStreamingCluster,
                                nextCluster, nextStreamingCluster.get(), overrides, now));
                    }
                });
            }
        });
        return result;
    }

    private static Optional<StreamingSearchCluster> findStreamingCluster(String clusterName, List<StreamingSearchCluster> clusters) {
        return clusters.stream()
                .filter(cluster -> cluster.getClusterName().equals(clusterName))
                .findFirst();
    }

    private static List<ConfigChangeAction> validateStreamingCluster(ContentCluster currentCluster,
                                                                     StreamingSearchCluster currentStreamingCluster,
                                                                     ContentCluster nextCluster,
                                                                     StreamingSearchCluster nextStreamingCluster,
                                                                     ValidationOverrides overrides,
                                                                     Instant now) {
        List<VespaConfigChangeAction> result = new ArrayList<>();

        result.addAll(validateDocumentTypeChanges(currentCluster.id(),
                                                  getDocumentType(currentCluster, currentStreamingCluster),
                                                  getDocumentType(nextCluster, nextStreamingCluster), overrides, now));
        result.addAll(validateAttributeFastAccessAdded(currentCluster.id(),
                                                       currentStreamingCluster.derived().getAttributeFields(),
                                                       nextStreamingCluster.derived().getAttributeFields()));
        result.addAll(validateAttributeFastAccessRemoved(currentCluster.id(),
                                                         currentStreamingCluster.derived().getAttributeFields(),
                                                         nextStreamingCluster.derived().getAttributeFields()));

        return modifyActions(result, getSearchNodeServices(nextCluster), nextStreamingCluster.getDocTypeName());
    }

    private static List<VespaConfigChangeAction> validateDocumentTypeChanges(ClusterSpec.Id id,
                                                                             NewDocumentType currentDocType,
                                                                             NewDocumentType nextDocType,
                                                                             ValidationOverrides overrides,
                                                                             Instant now) {
        return new DocumentTypeChangeValidator(id, currentDocType, nextDocType).validate();
    }

    private static NewDocumentType getDocumentType(ContentCluster cluster, StreamingSearchCluster streamingCluster) {
        return cluster.getDocumentDefinitions().get(streamingCluster.getDocTypeName());
    }

    private static List<VespaConfigChangeAction> validateAttributeFastAccessAdded(ClusterSpec.Id id,
                                                                                  AttributeFields currentAttributes,
                                                                                  AttributeFields nextAttributes) {
        return validateAttributeFastAccessChanged(id, nextAttributes, currentAttributes, "add");
    }

    private static List<VespaConfigChangeAction> validateAttributeFastAccessRemoved(ClusterSpec.Id id,
                                                                                    AttributeFields currentAttributes,
                                                                                    AttributeFields nextAttributes) {
        return validateAttributeFastAccessChanged(id, currentAttributes, nextAttributes, "remove");
    }

    private static List<VespaConfigChangeAction> validateAttributeFastAccessChanged(ClusterSpec.Id id,
                                                                                    AttributeFields lhsAttributes,
                                                                                    AttributeFields rhsAttributes,
                                                                                    String change) {
        return lhsAttributes.attributes().stream()
                .filter(attr -> attr.isFastAccess() &&
                        !hasFastAccessAttribute(attr.getName(), rhsAttributes))
                .map(attr -> new VespaRestartAction(id, new ChangeMessageBuilder(attr.getName()).addChange(change + " fast-access attribute").build()))
                .collect(Collectors.toList());
    }

    private static boolean hasFastAccessAttribute(String attrName, AttributeFields attributes) {
        Attribute attr = attributes.getAttribute(attrName);
        return (attr != null && attr.isFastAccess());
    }

    private static List<ServiceInfo> getSearchNodeServices(ContentCluster cluster) {
        return cluster.getSearch().getSearchNodes().stream()
                .map(node -> node.getServiceInfo())
                .collect(Collectors.toList());
    }

    private static List<ConfigChangeAction> modifyActions(List<VespaConfigChangeAction> result,
                                                          List<ServiceInfo> services,
                                                          String docTypeName) {
        return result.stream()
                .map(action -> action.modifyAction("Document type '" + docTypeName + "': " + action.getMessage(),
                                                   services, docTypeName))
                .collect(Collectors.toList());
    }

}
