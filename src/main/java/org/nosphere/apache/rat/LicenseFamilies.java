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

import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;

final class LicenseFamilies {

    static final class UnknownFamilyException extends IllegalArgumentException {
        UnknownFamilyException(String message) {
            super(message);
        }
    }

    static final class AmbiguousFamilyException extends IllegalArgumentException {
        AmbiguousFamilyException(String message) {
            super(message);
        }
    }

    private final Set<LicenseFamily> families;

    LicenseFamilies(Collection<LicenseFamily> families) {
        this.families = new TreeSet<>(families);
    }

    String resolve(String value) {
        Set<String> categoriesNamed = categoriesNamed(value);
        if (categoriesNamed.size() > 1) {
            throw new AmbiguousFamilyException("License family name '" + value + "' matches several categories: "
                    + String.join(", ", trimmed(categoriesNamed)) + ". Use the category instead.");
        }
        if (categoriesNamed.size() == 1) {
            return categoriesNamed.iterator().next();
        }
        String category = categoryCalled(value);
        if (category != null) {
            return category;
        }
        throw new UnknownFamilyException("Unknown license family '" + value + "'. Known license families: " + families);
    }

    private Set<String> categoriesNamed(String name) {
        Set<String> categories = new TreeSet<>();
        for (LicenseFamily family : families) {
            if (family.getName().equals(name)) {
                categories.add(family.getCategory());
            }
        }
        return categories;
    }

    private String categoryCalled(String value) {
        if (value.length() > LicenseFamily.CATEGORY_LENGTH) {
            return null;
        }
        String padded = LicenseFamily.paddedCategory(value);
        for (LicenseFamily family : families) {
            if (family.getCategory().equals(padded)) {
                return padded;
            }
        }
        return null;
    }

    private static Set<String> trimmed(Set<String> categories) {
        Set<String> trimmed = new TreeSet<>();
        for (String category : categories) {
            trimmed.add(category.trim());
        }
        return trimmed;
    }
}
