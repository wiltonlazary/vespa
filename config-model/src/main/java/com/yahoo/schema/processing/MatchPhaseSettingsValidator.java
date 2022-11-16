// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.schema.RankProfile;
import com.yahoo.schema.RankProfileRegistry;
import com.yahoo.schema.Schema;
import com.yahoo.schema.document.Attribute;
import com.yahoo.vespa.model.container.search.QueryProfiles;

/**
 * Validates the match phase settings for all registered rank profiles.
 *
 * @author geirst
 */
public class MatchPhaseSettingsValidator extends Processor {

    public MatchPhaseSettingsValidator(Schema schema, DeployLogger deployLogger, RankProfileRegistry rankProfileRegistry, QueryProfiles queryProfiles) {
        super(schema, deployLogger, rankProfileRegistry, queryProfiles);
    }

    @Override
    public void process(boolean validate, boolean documentsOnly) {
        if ( ! validate) return;
        if (documentsOnly) return;

        for (RankProfile rankProfile : rankProfileRegistry.rankProfilesOf(schema)) {
            RankProfile.MatchPhaseSettings settings = rankProfile.getMatchPhaseSettings();
            if (settings != null) {
                validateMatchPhaseSettings(rankProfile, settings);
            }
        }
    }

    private void validateMatchPhaseSettings(RankProfile rankProfile, RankProfile.MatchPhaseSettings settings) {
        String attributeName = settings.getAttribute();
        new AttributeValidator(schema.getName(),
                               rankProfile.name(),
                               schema.getAttribute(attributeName), attributeName).validate();
    }

    public static class AttributeValidator {

        private final String searchName;
        private final String rankProfileName;
        protected final Attribute attribute;
        private final String attributeName;

        public AttributeValidator(String searchName, String rankProfileName, Attribute attribute, String attributeName) {
            this.searchName = searchName;
            this.rankProfileName = rankProfileName;
            this.attribute = attribute;
            this.attributeName = attributeName;
        }

        public void validate() {
            validateThatAttributeExists();
            validateThatAttributeIsSingleNumeric();
            validateThatAttributeIsFastSearch();
        }

        protected void validateThatAttributeExists() {
            if (attribute == null) {
                failValidation("does not exists");
            }
        }

        protected void validateThatAttributeIsSingleNumeric() {
            if (!attribute.getCollectionType().equals(Attribute.CollectionType.SINGLE) ||
                 attribute.getType().equals(Attribute.Type.STRING) ||
                 attribute.getType().equals(Attribute.Type.PREDICATE))
            {
                failValidation("must be single value numeric, but it is '" +
                               attribute.getDataType().getName() + "'");
            }
        }

        protected void validateThatAttributeIsFastSearch() {
            if ( ! attribute.isFastSearch()) {
                failValidation("must be fast-search, but it is not");
            }
        }

        protected void failValidation(String what) {
            throw new IllegalArgumentException(createMessagePrefix() + what);
        }

        public String getValidationType() { return "match-phase"; }

        private String createMessagePrefix() {
            return "In search definition '" + searchName +
                    "', rank-profile '" + rankProfileName +
                    "': " + getValidationType() + " attribute '" + attributeName + "' ";
        }

    }

}
