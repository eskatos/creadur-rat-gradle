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

import org.apache.rat.utils.Log;
import org.gradle.api.logging.Logger;

final class RatLogBridge implements Log {

    private final Logger logger;

    private final boolean verbose;

    RatLogBridge(Logger logger, boolean verbose) {
        this.logger = logger;
        this.verbose = verbose;
    }

    @Override
    public Level getLevel() {
        if (logger.isDebugEnabled()) {
            return Level.DEBUG;
        }
        if (verbose || logger.isInfoEnabled()) {
            return Level.INFO;
        }
        return Level.WARN;
    }

    @Override
    public void log(Level level, String message) {
        switch (level) {
            case DEBUG:
                logger.debug(message);
                break;
            case INFO:
                logInfo(message);
                break;
            case WARN:
                logger.warn(message);
                break;
            case ERROR:
                logger.error(message);
                break;
            default:
                break;
        }
    }

    private void logInfo(String message) {
        if (verbose) {
            logger.lifecycle(message);
        } else {
            logger.info(message);
        }
    }
}
