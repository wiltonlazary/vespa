// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.builder.xml;

import com.yahoo.component.AbstractComponent;
import com.yahoo.config.model.ConfigModel;
import com.yahoo.config.model.ConfigModelContext;
import com.yahoo.config.model.ConfigModelInstanceFactory;
import com.yahoo.config.model.ConfigModelRepo;
import com.yahoo.config.model.api.ConfigModelPlugin;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.vespa.model.VespaModel;
import org.w3c.dom.Element;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.List;

/**
 * Builds a config model using DOM parsers
 *
 * @author vegardh
 */
public abstract class ConfigModelBuilder<MODEL extends ConfigModel> extends AbstractComponent implements ConfigModelPlugin {

    private final Class<MODEL> configModelClass;

    public ConfigModelBuilder(Class<MODEL> configModelClass) {
        this.configModelClass = configModelClass;
    }

    /**
     * Method that must return the XML elements this builder handles. Subclasses must implement this in order to
     * get called when one of the elements have been encountered when parsing.
     *
     * @return a list of elements that this builder handles
     */
    public abstract List<ConfigModelId> handlesElements();

    /**
     * Convenience hook called from {@link #build}. Implement this method to build a config model.
     *
     * @param spec the XML element that this builder should handle
     * @param modelContext a model context that contains the application package and other data needed by the
     *                     config model constructor
     */
    public abstract void doBuild(MODEL model, Element spec, ConfigModelContext modelContext);

    /**
     * Builds an instance of this component model.
     * This calls instantiate(...), instance.setUp(...), doBuild(instance, ...).
     *
     * @param deployState a global deployment state used for this model.
     * @param parent the root config producer this should be added to
     * @param spec the XML element this is constructed from
     */
    public final MODEL build(DeployState deployState, VespaModel vespaModel, ConfigModelRepo configModelRepo,
                             AbstractConfigProducer<?> parent, Element spec) {
        ConfigModelContext context = ConfigModelContext.create(deployState, vespaModel, configModelRepo, parent, getIdString(spec));
        return build(new DefaultModelInstanceFactory(), spec, context);
    }

    /**
     * Builds an instance of this component model.
     * This calls instantiate(...), instance.setUp(...), doBuild(instance, ...).
     *
     * @param factory A factory capable of creating models.
     * @param spec the XML element this is constructed from
     * @param context A context object containing various data used by builders.
     */
    public MODEL build(ConfigModelInstanceFactory<MODEL> factory, Element spec, ConfigModelContext context) {
        MODEL model = factory.createModel(context);
        doBuild(model, spec, context);
        return model;
    }

    public Class<MODEL> getModelClass() {
        return configModelClass;
    }

    private static String getIdString(Element spec) {
        String idString = XmlHelper.getIdString(spec);
        if (idString.isEmpty())
            idString = spec.getTagName();
        return idString;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof ConfigModelBuilder)) {
            return false;
        }
        ConfigModelBuilder<?> otherBuilder = (ConfigModelBuilder<?>) other;
        List<ConfigModelId> thisIds = this.handlesElements();
        List<ConfigModelId> otherIds = otherBuilder.handlesElements();
        if (thisIds.size() != otherIds.size()) {
            return false;
        }
        for (int i = 0; i < thisIds.size(); i++) {
            if (!thisIds.get(i).equals(otherIds.get(i))) {
                return false;
            }
        }
        return true;
    }


    private class DefaultModelInstanceFactory implements ConfigModelInstanceFactory<MODEL> {
        @Override
        public MODEL createModel(ConfigModelContext context) {
            try {
                Constructor<MODEL> constructor = configModelClass.getConstructor(ConfigModelContext.class);
                return constructor.newInstance(context);
            } catch (NoSuchMethodException | InvocationTargetException | InstantiationException | IllegalAccessException e) {
                throw new RuntimeException("Error constructing model '" + configModelClass.getName() + "'", e);
            }
        }
    }

}
