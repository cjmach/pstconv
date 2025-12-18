/*
 *  Copyright 2024 Carlos Machado
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

import com.pff.PSTException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import javax.mail.Address;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Store;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;

/**
 *
 * @author cmachado
 */
public class PstConverterTest {
    PstConverter instance;
    
    @BeforeEach
    public void setUp() {
        instance = new PstConverter();
    }
    
    @AfterEach
    public void tearDown() {
        instance = null;
    }
    
    private void testConvertInputFile(MailMessageFormat format) {
        File inputFile = new File("src/test/resources/pt/cjmach/pstconv/outlook.pst");
        File outputDirectory = new File("mailbox");
        String encoding = StandardCharsets.ISO_8859_1.name();
        int expectedTotalMessageCount = 3;
        int expectedInboxMessageCount = 2; // expected count on the inbox folder, excluding child folders.
        Store store = null;
        
        try {
            PstConvertResult result = instance.convert(inputFile, outputDirectory, format, encoding);
            assertEquals(expectedTotalMessageCount, result.getMessageCount(), "Unexpected number of converted messages.");
            
            store = instance.createStore(outputDirectory, format, encoding);
            store.connect();
            
            // Root Folder / Inbox (in portuguese)
            Folder inbox = store.getFolder("Inicio do ficheiro de dados do Outlook").getFolder("Caixa de Entrada");
            inbox.open(Folder.READ_ONLY);
            
            Message[] messages = inbox.getMessages();
            assertEquals(expectedInboxMessageCount, messages.length, "Unexpected number of messages in inbox.");
            
            Message messageFromAbcd = null;
            for (Message message : messages) {
                Address[] from = message.getFrom();
                assertEquals(1, from.length);
                if ("abcd@as.pt".equals(from[0].toString())) {
                    messageFromAbcd = message;
                    break;
                }
            }
            assertNotNull(messageFromAbcd, "Message from abcd@as.pt not found on inbox.");
            
            String descriptorIdHeader = ((MimeMessage) messageFromAbcd).getHeader(PstConverter.DESCRIPTOR_ID_HEADER, null);
            assertEquals("2097252", descriptorIdHeader);
            
            MimeMultipart multiPart = (MimeMultipart) messageFromAbcd.getContent();
            MimeBodyPart bodyPart = (MimeBodyPart) multiPart.getBodyPart(0);
            MimeMultipart bodyMultiPart = (MimeMultipart) bodyPart.getContent();
            try (InputStream stream = bodyMultiPart.getBodyPart(0).getInputStream()) {
                String content = IOUtils.toString(stream, StandardCharsets.US_ASCII.name());
                assertEquals("Teste 23:34", content);
            }
        } catch (Exception ex) {
            fail(ex);
        } finally {
            if (store != null) {
                try {
                    store.close();
                } catch (MessagingException ignore) {}
            }
            try {
                FileUtils.deleteDirectory(outputDirectory);
            } catch (IOException ignore) { }
        }
    }
    
    @Test
    public void testConvertInputFileSuccess() {
        testConvertInputFile(MailMessageFormat.MBOX);
        testConvertInputFile(MailMessageFormat.EML);
    }

    /**
     * Test of convert method, of class PstConverter.
     */
    @Test
    public void testConvertInputFileNotFound() {
        String fileName = "/file/not/found.pst";
        File inputFile = new File(fileName);
        File outputDirectory = new File(".");
        MailMessageFormat format = MailMessageFormat.EML;
        String encoding = "UTF-8";
        FileNotFoundException ex = assertThrows(FileNotFoundException.class, () -> instance.convert(inputFile, outputDirectory, format, encoding));
        assertEquals(FileNotFoundException.class, ex.getClass());
    }
    
    @Test
    public void testConvertInputFileIllegal() {        
        File inputFile = new File("."); // invalid file
        File outputDirectory = new File(".");
        MailMessageFormat format = MailMessageFormat.EML;
        String encoding = "UTF-8";
        FileNotFoundException ex = assertThrows(FileNotFoundException.class, () -> instance.convert(inputFile, outputDirectory, format, encoding));
        assertEquals(FileNotFoundException.class, ex.getClass());
    }
    
    @Test
    public void testConvertOutputDirectoryIllegal() {
        File inputFile = new File("src/test/resources/pt/cjmach/pstconv/textfile.txt");
        File outputDirectory = new File("src/test/resources/pt/cjmach/pstconv/textfile.txt");
        MailMessageFormat format = MailMessageFormat.EML;
        String encoding = "UTF-8";
        assertThrows(PSTException.class, () -> instance.convert(inputFile, outputDirectory, format, encoding));
    }
    
    @Test
    public void testConvertOutputFormatNull() {
        File inputFile = new File("src/test/resources/pt/cjmach/pstconv/outlook.pst");
        File outputDirectory = new File(".");
        MailMessageFormat format = null;
        String encoding = "UTF-8";
        IllegalArgumentException iae = assertThrows(IllegalArgumentException.class, () -> instance.convert(inputFile, outputDirectory, format, encoding));
        assertEquals("format is null.", iae.getMessage());
    }
    
    @Test
    public void testConvertEncodingInvalid() {
        File inputFile = new File("src/test/resources/pt/cjmach/pstconv/outlook.pst");
        File outputDirectory = new File(".");
        MailMessageFormat format = MailMessageFormat.EML;
        String encoding = "invalid encoding";
        IllegalArgumentException iae = assertThrows(IllegalArgumentException.class, () -> instance.convert(inputFile, outputDirectory, format, encoding));
        assertEquals(encoding, iae.getMessage());
    }
}
