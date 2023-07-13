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

import java.util.Arrays;
import org.apache.commons.lang3.StringUtils;

/**
 *
 * @author mach
 */
public final class PstUtil {
    
    private PstUtil() { }
    
    public static String normalizeString(String str) {
        StringBuilder builder = new StringBuilder();
        String normalizedSubject = StringUtils.stripAccents(str);
        final char[] forbidden = {'\"', '*', '/', ':', '<', '>', '?', '\\', '|'};
        for (int i = 0; i < normalizedSubject.length(); i++) {
            char c = normalizedSubject.charAt(i);
            if (c >= 32 && c <= 126 && Arrays.binarySearch(forbidden, c) < 0) {
                builder.append(c);
            } else {
                builder.append('_');
            }
        }
        return builder.toString();
    }
}
