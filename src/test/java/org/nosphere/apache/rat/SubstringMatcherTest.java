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

import java.util.Collections;
import org.junit.jupiter.api.Test;

public class SubstringMatcherTest {

    @Test
    public void acceptsFiveCharacterCategory() {
        assertEquals("MYFOO", matcher("MYFOO").getLicenseFamilyCategory());
    }

    @Test
    public void acceptsShorterCategory() {
        assertEquals("MIT", matcher("MIT").getLicenseFamilyCategory());
    }

    @Test
    public void rejectsSixCharacterCategory() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> matcher("ABCDEF"));
        assertTrue(ex.getMessage().contains("ABCDEF"), ex.getMessage());
        assertTrue(ex.getMessage().contains("5 characters"), ex.getMessage());
    }

    @Test
    public void rejectsCategoryThatWouldTruncateOntoAKnownFamily() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> matcher("BSD-3-Clause"));
        assertTrue(ex.getMessage().contains("BSD-3-Clause"), ex.getMessage());
        assertTrue(ex.getMessage().contains("5 characters"), ex.getMessage());
    }

    @Test
    public void rejectsBlankCategory() {
        assertThrows(IllegalArgumentException.class, () -> matcher(""));
    }

    private static SubstringMatcher matcher(String category) {
        return new SubstringMatcher(category, "Name", Collections.singletonList("x"));
    }
}
