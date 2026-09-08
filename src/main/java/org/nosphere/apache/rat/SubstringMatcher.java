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

import org.gradle.api.tasks.Input;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class SubstringMatcher implements Serializable {

    private final String licenseFamilyCategory;

    private final String licenseFamilyName;

    private final List<String> substrings;

    public SubstringMatcher(String licenseFamilyCategory, String licenseFamilyName, List<String> substrings) {
        this.licenseFamilyCategory = licenseFamilyCategory;
        this.licenseFamilyName = licenseFamilyName;
        this.substrings = Collections.unmodifiableList(new ArrayList<>(substrings));
    }

    @Input
    public String getLicenseFamilyCategory() {
        return licenseFamilyCategory;
    }

    @Input
    public String getLicenseFamilyName() {
        return licenseFamilyName;
    }

    @Input
    public List<String> getSubstrings() {
        return substrings;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SubstringMatcher that = (SubstringMatcher) o;
        return licenseFamilyCategory.equals(that.licenseFamilyCategory)
            && licenseFamilyName.equals(that.licenseFamilyName)
            && substrings.equals(that.substrings);
    }

    @Override
    public int hashCode() {
        return Objects.hash(licenseFamilyCategory, licenseFamilyName, substrings);
    }

    @Override
    public String toString() {
        return "SubstringMatcher(licenseFamilyCategory=" + licenseFamilyCategory
            + ", licenseFamilyName=" + licenseFamilyName
            + ", substrings=" + substrings + ")";
    }
}
