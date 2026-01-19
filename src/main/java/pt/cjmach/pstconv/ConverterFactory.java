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

import java.io.File;
import java.io.IOException;
import org.apache.commons.io.FilenameUtils;
import org.apache.tika.Tika;

/**
 *
 * @author mach
 */
public class ConverterFactory {
    private static final String PST_MIME_TYPE = "application/vnd.ms-outlook-pst";
    private ConverterFactory() { }
    
    public static OutlookFileConverter create(File inputFile) throws IOException {
        String mimeType = new Tika().detect(inputFile);
        if (PST_MIME_TYPE.equals(mimeType)) {
            String extension = FilenameUtils.getExtension(inputFile.getName());
            switch (extension.toLowerCase()) {
                case "pst":
                    return new PstConverter();
                case "ost":
                    return new OstConverter();
            }
        }
        return null;
    }
}
