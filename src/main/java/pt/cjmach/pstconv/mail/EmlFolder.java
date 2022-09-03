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
package pt.cjmach.pstconv.mail;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Store;
import javax.mail.internet.MimeMessage;
import org.apache.commons.lang3.StringUtils;
import pt.cjmach.pstconv.PstConverter;

/**
 *
 * @author cmachado
 */
public class EmlFolder extends LocalFolder {
    public static final String EML_FILE_EXTENSION = ".eml";
    private static final FileFilter EML_FILE_FILTER = (File pathname) -> {
        return pathname.isFile() && pathname.getName().endsWith(EML_FILE_EXTENSION);
    };

    public EmlFolder(Store store, File directory) {
        super(store, directory, EML_FILE_FILTER);
    }

    @Override
    public void appendMessage(Message msg) throws MessagingException {
        String id = getDescriptorNodeId(msg);
        String fileName = getEMLFileName(msg.getSubject(), id);
        File outputFile = new File(directory, fileName);
        try (FileOutputStream ouputStream = new FileOutputStream(outputFile)) {
            msg.writeTo(ouputStream);
        } catch (IOException ex) {
            throw new MessagingException("Failed to write to file", ex);
        }
    }

    @Override
    protected LocalFolder createInstance(Store store, File directory, FileFilter fileFilter) {
        return new EmlFolder(store, directory);
    }

    @Override
    public Message getMessage(int msgnum) throws MessagingException  {
        File emlFile = getFileEntries()[msgnum];
        try (FileInputStream emlFileStream = new FileInputStream(emlFile)) {
            // TODO: Null session? Is it Ok?
            MimeMessage msg = new MimeMessage(null, emlFileStream);
            return msg;
        } catch (IOException ex) {
            throw new MessagingException("Failed to get message", ex);
        }
    }
    
    static String getDescriptorNodeId(Message msg) throws MessagingException {
        String[] values = msg.getHeader(PstConverter.DESCRIPTOR_ID_HEADER);
        if (values == null || values.length <= 0) {
            return "0";
        }
        return values[0];
    }

    /**
     * Generates a valid file name which concatenates the descriptor with e-mail
     * subject.
     *
     * @param subject The e-mail subject.
     * @param descriptorIndex The index value that uniquely identifies the
     * e-mail message.
     * @return A valid file name.
     */
    static String getEMLFileName(String subject, String descriptorIndex) {
        if (subject == null || subject.isEmpty()) {
            String fileName = descriptorIndex + "-NoSubject" + EML_FILE_EXTENSION;
            return fileName;
        }

        StringBuilder builder = new StringBuilder();
        builder.append(descriptorIndex).append("-");

        String normalizedSubject = StringUtils.stripAccents(subject);
        final char[] forbidden = {'\"', '*', '/', ':', '<', '>', '?', '\\', '|'};
        for (int i = 0; i < normalizedSubject.length(); i++) {
            char c = normalizedSubject.charAt(i);
            if (c >= 32 && c <= 126 && Arrays.binarySearch(forbidden, c) < 0) {
                builder.append(c);
            } else {
                builder.append('_');
            }
        }
        builder.append(EML_FILE_EXTENSION);
        return builder.toString();
    }
}
