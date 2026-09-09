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

final class Fixtures {

    static final String FOO_MARKER = "FOO-MARKER";

    static final String BAR_MARKER = "BAR-MARKER";

    static final byte[] PNG = {
        (byte) 0x89,
        0x50,
        0x4E,
        0x47,
        0x0D,
        0x0A,
        0x1A,
        0x0A,
        0x00,
        0x00,
        0x00,
        0x0D,
        0x49,
        0x48,
        0x44,
        0x52,
        0x00,
        0x00,
        0x00,
        0x01,
        0x00,
        0x00,
        0x00,
        0x01,
        0x08,
        0x06,
        0x00,
        0x00,
        0x00,
        0x1F,
        0x15,
        (byte) 0xC4,
        (byte) 0x89
    };

    static final String MIT_LICENSE_TEXT = String.join(
            "\n",
            "Copyright (c) 2026 Example",
            "",
            "Permission is hereby granted, free of charge, to any person obtaining a copy",
            "of this software and associated documentation files (the \"Software\"), to deal",
            "in the Software without restriction, including without limitation the rights",
            "to use, copy, modify, merge, publish, distribute, sublicense, and/or sell",
            "copies of the Software, and to permit persons to whom the Software is",
            "furnished to do so, subject to the following conditions:",
            "",
            "The above copyright notice and this permission notice shall be included in all",
            "copies or substantial portions of the Software.",
            "",
            "THE SOFTWARE IS PROVIDED \"AS IS\", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR",
            "IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,",
            "FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE",
            "AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER",
            "LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,",
            "OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE",
            "SOFTWARE.",
            "");

    private static final String[] APACHE_LICENSE_HEADER = {
        "Licensed to the Apache Software Foundation (ASF) under one",
        "or more contributor license agreements.  See the NOTICE file",
        "distributed with this work for additional information",
        "regarding copyright ownership.  The ASF licenses this file",
        "to you under the Apache License, Version 2.0 (the",
        "\"License\"); you may not use this file except in compliance",
        "with the License.  You may obtain a copy of the License at",
        "",
        "  http://www.apache.org/licenses/LICENSE-2.0",
        "",
        "Unless required by applicable law or agreed to in writing,",
        "software distributed under the License is distributed on an",
        "\"AS IS\" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY",
        "KIND, either express or implied.  See the License for the",
        "specific language governing permissions and limitations",
        "under the License."
    };

    static String commentedApacheLicenseHeader() {
        StringBuilder header = new StringBuilder();
        for (String line : APACHE_LICENSE_HEADER) {
            header.append("# ").append(line).append("\n");
        }
        return header.toString();
    }

    private Fixtures() {}
}
