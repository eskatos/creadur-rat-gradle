/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.nosphere.apache.rat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.apache.rat.Defaults;
import org.apache.rat.ReportConfiguration;
import org.apache.rat.analysis.IHeaderMatcher;
import org.apache.rat.analysis.matchers.OrMatcher;
import org.apache.rat.analysis.matchers.SimpleTextMatcher;
import org.apache.rat.license.ILicense;
import org.apache.rat.license.ILicenseFamily;
import org.apache.rat.license.LicenseSetFactory.LicenseFilter;
import org.gradle.api.GradleException;

final class RatConfigurationBuilder {

    private final RatWorkSpec spec;

    private final ReportConfiguration config = new ReportConfiguration();

    RatConfigurationBuilder(RatWorkSpec spec) {
        this.spec = spec;
    }

    ReportConfiguration build() {
        config.setFrom(defaults());
        List<SubstringMatcher> matchers = spec.getSubstringMatchers().get();
        registerSubstringMatchers(matchers);
        approveOnly(spec.getApprovedLicenses().get(), vocabulary(matchers));
        config.addSource(new FilesReportable(
                spec.getBaseDir().getAsFile().get(),
                new ArrayList<>(spec.getReportedFiles().getFiles())));
        return config;
    }

    static GradleException configurationError(String detail) {
        return new GradleException(
                "Apache Rat configuration error: " + detail + " failOnError does not apply to configuration errors.");
    }

    private Defaults defaults() {
        Defaults.Builder builder = Defaults.builder();
        if (!spec.getAddDefaultMatchers().get()) {
            builder.noDefault();
        }
        return builder.build();
    }

    private void registerSubstringMatchers(List<SubstringMatcher> matchers) {
        for (int index = 0; index < matchers.size(); index++) {
            SubstringMatcher matcher = matchers.get(index);
            String category = matcher.getLicenseFamilyCategory();
            String name = matcher.getLicenseFamilyName();
            ILicenseFamily existing = familyWithCategory(category);
            if (existing == null) {
                config.addFamily(ILicenseFamily.builder()
                        .setLicenseFamilyCategory(category)
                        .setLicenseFamilyName(name)
                        .build());
            } else if (!existing.getFamilyName().equals(name)) {
                throw configurationError("substringMatcher category '" + category + "' declared for license family '"
                        + name + "' is already the category of license family '" + existing.getFamilyName()
                        + "'. Apache Rat would merge the two. Use a category no other family uses, or name the"
                        + " existing family '" + existing.getFamilyName() + "' to attach the matcher to it.");
            }
            config.addLicense(ILicense.builder()
                    .setFamily(category)
                    .setName(name)
                    .setId(category.trim() + "-" + (index + 1))
                    .setMatcher(headerMatcherFor(matcher.getSubstrings())));
        }
    }

    private ILicenseFamily familyWithCategory(String category) {
        String paddedCategory = LicenseFamily.paddedCategory(category);
        for (ILicenseFamily family : config.getLicenseFamilies(LicenseFilter.ALL)) {
            if (family.getFamilyCategory().equals(paddedCategory)) {
                return family;
            }
        }
        return null;
    }

    private static IHeaderMatcher headerMatcherFor(List<String> substrings) {
        if (substrings.size() == 1) {
            return new SimpleTextMatcher(substrings.get(0));
        }
        List<IHeaderMatcher> textMatchers = new ArrayList<>();
        for (String substring : substrings) {
            textMatchers.add(new SimpleTextMatcher(substring));
        }
        return new OrMatcher(textMatchers, null);
    }

    private static LicenseFamilies vocabulary(List<SubstringMatcher> matchers) {
        ReportConfiguration defaults = new ReportConfiguration();
        defaults.setFrom(Defaults.builder().build());
        Set<LicenseFamily> families = new TreeSet<>();
        for (ILicenseFamily family : defaults.getLicenseFamilies(LicenseFilter.ALL)) {
            families.add(LicenseFamily.of(family.getFamilyCategory(), family.getFamilyName()));
        }
        for (SubstringMatcher matcher : matchers) {
            families.add(LicenseFamily.of(matcher.getLicenseFamilyCategory(), matcher.getLicenseFamilyName()));
        }
        return new LicenseFamilies(families);
    }

    private void approveOnly(List<String> approvedLicenses, LicenseFamilies knownFamilies) {
        if (approvedLicenses.isEmpty()) {
            return;
        }
        Set<String> approvedCategories = new TreeSet<>();
        for (String approvedLicense : approvedLicenses) {
            String category = resolveApprovedLicense(knownFamilies, approvedLicense);
            config.addApprovedLicenseCategory(category);
            approvedCategories.add(category);
        }
        Set<String> categoriesToRemove = new TreeSet<>(config.getLicenseCategories(LicenseFilter.APPROVED));
        categoriesToRemove.removeAll(approvedCategories);
        config.removeApprovedLicenseCategories(categoriesToRemove);
    }

    private static String resolveApprovedLicense(LicenseFamilies knownFamilies, String value) {
        try {
            return knownFamilies.resolve(value);
        } catch (IllegalArgumentException ex) {
            throw configurationError("approvedLicenses: " + ex.getMessage());
        }
    }
}
