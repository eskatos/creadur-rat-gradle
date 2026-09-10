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

import static org.nosphere.apache.rat.ConfigurationErrors.configurationError;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
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

final class RatConfigurationBuilder {

    private final RatWorkSpec spec;

    private final ReportConfiguration config = new ReportConfiguration();

    private Defaults allDefaults;

    RatConfigurationBuilder(RatWorkSpec spec) {
        this.spec = spec;
    }

    ReportConfiguration build() {
        config.setFrom(
                spec.getAddDefaultMatchers().get()
                        ? allDefaults()
                        : Defaults.builder().noDefault().build());
        List<SubstringMatcher> matchers = spec.getSubstringMatchers().get();
        registerSubstringMatchers(matchers);
        approveOnly(spec.getApprovedLicenses().get(), matchers);
        config.addSource(new FilesReportable(spec.getInputDir().getAsFile().get(), sortedInputFiles()));
        return config;
    }

    static String licenseFamilyTable(ReportConfiguration config) {
        Set<String> approved = config.getLicenseCategories(LicenseFilter.APPROVED);
        List<String> rows = new ArrayList<>();
        rows.add("License families:");
        for (ILicenseFamily family : config.getLicenseFamilies(LicenseFilter.ALL)) {
            String approval = approved.contains(family.getFamilyCategory()) ? "approved" : "not approved";
            rows.add("  " + LicenseFamily.of(family.getFamilyCategory(), family.getFamilyName()) + " - " + approval);
        }
        return String.join("\n", rows);
    }

    private Defaults allDefaults() {
        if (allDefaults == null) {
            allDefaults = Defaults.builder().build();
        }
        return allDefaults;
    }

    private List<File> sortedInputFiles() {
        List<File> files = new ArrayList<>(spec.getInputFiles().getFiles());
        Collections.sort(files);
        return files;
    }

    private void registerSubstringMatchers(List<SubstringMatcher> matchers) {
        for (int index = 0; index < matchers.size(); index++) {
            SubstringMatcher matcher = matchers.get(index);
            String category = matcher.getLicenseFamilyCategory();
            String name = matcher.getLicenseFamilyName();
            declareFamily(category, name);
            config.addLicense(ILicense.builder()
                    .setFamily(category)
                    .setName(name)
                    // Without an ID, Rat uses the category as id and drops the second license on a family,
                    // ours included, in favor of its own.
                    .setId(licenseId(category, index))
                    .setMatcher(headerMatcherFor(matcher.getSubstrings())));
        }
    }

    private void declareFamily(String category, String name) {
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

    private static String licenseId(String category, int index) {
        return category.trim() + "-" + (index + 1);
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

    // Add first, remove after. Any other order ends up with an empty approved set.
    private void approveOnly(List<String> approvedLicenses, List<SubstringMatcher> matchers) {
        if (approvedLicenses.isEmpty()) {
            return;
        }
        LicenseFamilies knownFamilies = knownFamilies(matchers);
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

    // Two family sets on purpose. approvedLicenses resolves against this one: all Rat defaults plus ours,
    // whatever addDefaultMatchers says, so approvedLicenses = ["MIT"] with defaults off still parses,
    // as in 0.15. Collision check in familyWithCategory uses the configured set instead.
    // Do not merge them.
    private LicenseFamilies knownFamilies(List<SubstringMatcher> matchers) {
        Set<LicenseFamily> families = new TreeSet<>();
        for (ILicenseFamily family : allDefaults().getLicenseSetFactory().getLicenseFamilies(LicenseFilter.ALL)) {
            families.add(LicenseFamily.of(family.getFamilyCategory(), family.getFamilyName()));
        }
        for (SubstringMatcher matcher : matchers) {
            families.add(LicenseFamily.of(matcher.getLicenseFamilyCategory(), matcher.getLicenseFamilyName()));
        }
        return new LicenseFamilies(families);
    }

    private static String resolveApprovedLicense(LicenseFamilies knownFamilies, String value) {
        try {
            return knownFamilies.resolve(value);
        } catch (LicenseFamilies.UnknownFamilyException | LicenseFamilies.AmbiguousFamilyException ex) {
            throw configurationError("approvedLicenses: " + ex.getMessage());
        }
    }
}
