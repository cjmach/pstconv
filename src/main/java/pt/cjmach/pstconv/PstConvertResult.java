/*
 *  Copyright 2022 Carlos Machado
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package pt.cjmach.pstconv;

/**
 *
 * @author cmachado
 */
public class PstConvertResult {
    private final long messageCount;
    private final long durationInMillis;

    public PstConvertResult(long messageCount, long durationInMillis) {
        this.messageCount = messageCount;
        this.durationInMillis = durationInMillis;
    }

    /**
     * @return The number of successfully converted messages.
     */
    public long getMessageCount() {
        return messageCount;
    }

    /**
     * @return The time taken to convert to entire PST file, in milliseconds.
     */
    public long getDurationInMillis() {
        return durationInMillis;
    }
}
