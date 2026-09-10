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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

public class LicenseFamiliesTest {

    @Test
    public void resolvesByName() {
        assertEquals("MIT  ", families().resolve("The MIT License"));
    }

    @Test
    public void resolvesByCategory() {
        assertEquals("MIT  ", families().resolve("MIT"));
    }

    @Test
    public void resolvesByPaddedCategory() {
        assertEquals("MIT  ", families().resolve("MIT  "));
    }

    @Test
    public void matchingIsCaseSensitive() {
        assertThrows(
                LicenseFamilies.UnknownFamilyException.class, () -> families().resolve("mit"));
    }

    @Test
    public void rejectsUnknownNameListingTheKnownFamilies() {
        LicenseFamilies.UnknownFamilyException ex = assertThrows(
                LicenseFamilies.UnknownFamilyException.class, () -> families().resolve("Apache License Version 2.0"));
        assertTrue(ex.getMessage().contains("Apache License Version 2.0"), ex.getMessage());
        assertTrue(ex.getMessage().contains("[AL   ] Apache License, [MIT  ] The MIT License"), ex.getMessage());
    }

    @Test
    public void rejectsBogusValue() {
        assertThrows(
                LicenseFamilies.UnknownFamilyException.class, () -> families().resolve("Bogus"));
    }

    @Test
    public void neverTruncatesAValueToMatchACategory() {
        LicenseFamilies withBsd = families(LicenseFamily.of("BSD-3", "BSD 3 clause"));
        assertThrows(LicenseFamilies.UnknownFamilyException.class, () -> withBsd.resolve("BSD-3-Clause"));
    }

    @Test
    public void rejectsNameSharedByTwoCategoriesListingBoth() {
        LicenseFamilies withMyMit = families(LicenseFamily.of("MYMIT", "The MIT License"));
        LicenseFamilies.AmbiguousFamilyException ex = assertThrows(
                LicenseFamilies.AmbiguousFamilyException.class, () -> withMyMit.resolve("The MIT License"));
        assertTrue(ex.getMessage().contains("MIT"), ex.getMessage());
        assertTrue(ex.getMessage().contains("MYMIT"), ex.getMessage());
    }

    @Test
    public void categoryIsTheEscapeHatchForAnAmbiguousName() {
        LicenseFamilies withMyMit = families(LicenseFamily.of("MYMIT", "The MIT License"));
        assertEquals("MYMIT", withMyMit.resolve("MYMIT"));
    }

    @Test
    public void twoFamiliesUnderOneCategoryAreOneCandidate() {
        LicenseFamilies withOwnMit = families(LicenseFamily.of("MIT", "My Own License"));
        assertEquals("MIT  ", withOwnMit.resolve("MIT"));
    }

    private static LicenseFamilies families(LicenseFamily... additional) {
        List<LicenseFamily> all = new ArrayList<>(
                Arrays.asList(LicenseFamily.of("MIT", "The MIT License"), LicenseFamily.of("AL", "Apache License")));
        all.addAll(Arrays.asList(additional));
        return new LicenseFamilies(all);
    }
}
