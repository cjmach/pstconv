/*
 *  Copyright 2022-2025 Carlos Machado
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
public enum MailMessageFormat {
    MBOX("mbox"), // NOI18N
    EML("eml"); // NOI18N

    public final String format;

    private MailMessageFormat(String format) {
        this.format = format;
    }
    
    /**
     * 
     * @return 
     */
    public static String[] getFormats() {
        MailMessageFormat[] values = values();
        String[] formats = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            formats[i] = values[i].format;
        }
        return formats;
    }

    /**
     * 
     * @param format
     * @return 
     */
    public static MailMessageFormat valueOfFormat(String format) {
        if (format == null) return null;
        for (MailMessageFormat e : values()) {
            if (e.format.equals(format)) {
                return e;
            }
        }
        return null;
    }
}
