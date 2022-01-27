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

import com.pff.PSTAttachment;
import com.pff.PSTException;
import com.pff.PSTFile;
import com.pff.PSTFolder;
import com.pff.PSTMessage;
import com.pff.PSTObject;
import com.pff.PSTRecipient;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Properties;
import java.util.regex.Pattern;
import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.mail.Folder;
import javax.mail.Header;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.URLName;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.InternetHeaders;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.util.ByteArrayDataSource;
import net.fortuna.mstor.model.MStorStore;
import org.apache.tika.mime.MimeTypeException;
import org.apache.tika.mime.MimeTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Converts an Outlook OST/PST file to MBox or EML format.
 *
 * @author cmachado
 */
public class PstConverter {

    private static final Logger logger = LoggerFactory.getLogger(PstConverter.class);
    private static final Pattern FILENAME_PATTERN = Pattern.compile("[^\\p{ASCII}]");

    /**
     * Default constructor.
     */
    public PstConverter() {
    }

    /**
     * Converts an Outlook OST/PST file to MBox or EML format.
     *
     * @param inputFile The input PST file.
     * @param outputDirectory The directory where the email messages are
     * extracted to and saved.
     * @param format The output format (MBOX or EML).
     * @param encoding The charset encoding to use for character data.
     *
     * @throws PSTException
     * @throws IOException
     */
    public void convert(File inputFile, File outputDirectory, OutputFormat format, String encoding) throws PSTException, IOException {
        if (!inputFile.exists()) {
            throw new FileNotFoundException(String.format("No such file: %s.", inputFile.getAbsolutePath()));
        }
        if (!inputFile.isFile()) {
            throw new IllegalArgumentException(String.format("Not a file: %s.", inputFile.getAbsolutePath()));
        }
        if (outputDirectory.exists() && !outputDirectory.isDirectory()) {
            throw new IllegalArgumentException(String.format("Not a directory: %s.", outputDirectory.getAbsolutePath()));
        }
        if (format == null) {
            throw new IllegalArgumentException("format is null.");
        }
        Charset.forName(encoding); // throws UnsupportedCharsetException if encoding is invalid

        PSTFile pstFile = new PSTFile(inputFile);

        // see: https://docs.oracle.com/javaee/6/api/javax/mail/internet/package-summary.html#package_description
        System.setProperty("mail.mime.address.strict", "false");
        int messageCount = 0;
        switch (format) {
            case EML: {
                if (!outputDirectory.exists() && !outputDirectory.mkdirs()) {
                    throw new IOException("Failed to create output directory " + outputDirectory.getAbsolutePath());
                }
                Properties sessionProps = new Properties(System.getProperties());
                Session session = Session.getDefaultInstance(sessionProps);
                PSTFolder pstRootFolder = pstFile.getRootFolder();
                try {
                    messageCount = convertToEML(pstRootFolder, outputDirectory, "\\", session);
                } catch (PSTException | MessagingException | IOException ex) {
                    logger.error("Failed to convert PSTFile object for file {}. {}", inputFile, ex.getMessage());
                }
                break;
            }

            case MBOX: {
                Properties sessionProps = new Properties(System.getProperties());
                // see: https://github.com/benfortuna/mstor
                sessionProps.setProperty("mstor.mbox.metadataStrategy", "none");
                sessionProps.setProperty("mstor.mbox.encoding", encoding);
                sessionProps.setProperty("mstor.mbox.bufferStrategy", "default");

                Session session = Session.getDefaultInstance(sessionProps);

                MStorStore mboxStore = new MStorStore(session, new URLName("mstor:" + outputDirectory));
                try {
                    mboxStore.connect();
                    Folder mboxRootFolder = mboxStore.getDefaultFolder();
                    PSTFolder pstRootFolder = pstFile.getRootFolder();
                    messageCount = convertToMbox(pstRootFolder, mboxRootFolder, "\\", session);
                } catch (PSTException | MessagingException | IOException ex) {
                    logger.error("Failed to convert PSTFile object for file {}. {}", inputFile, ex.getMessage());
                } finally {
                    try {
                        mboxStore.close();
                    } catch (MessagingException ignore) {
                        // ignore exception
                    }
                }
                break;
            }
        }
        logger.info("Finished! Converted {} messages.", messageCount);
    }

    /**
     * Traverses all PSTFolders recursively, starting from the root PSTFolder,
     * and extracts all email messages in EML format to a directory on the file
     * system.
     *
     * @param pstFolder
     * @param directory
     * @param path
     * @param session
     * @return
     * @throws PSTException
     * @throws IOException
     * @throws MessagingException
     */
    int convertToEML(PSTFolder pstFolder, File directory, String path, Session session) throws PSTException, IOException, MessagingException {
        int messageCount = 0;
        if (pstFolder.getContentCount() > 0) {
            PSTObject child = pstFolder.getNextChild();
            while (child != null) {
                PSTMessage pstMessage = (PSTMessage) child;
                MimeMessage message = convertToMimeMessage(pstMessage, session);

                String fileName = getEMLFileName(pstMessage.getSubject(), pstMessage.getDescriptorNodeId());
                File outputFile = new File(directory, fileName);
                try (FileOutputStream ouputStream = new FileOutputStream(outputFile)) {
                    message.writeTo(ouputStream);
                    messageCount++;
                } catch (IOException | MessagingException ex) {
                    logger.error("Failed to write EML file {}. {}", outputFile.getAbsolutePath(), ex.getMessage());
                }
                child = pstFolder.getNextChild();
            }
        }
        if (pstFolder.hasSubfolders()) {
            for (PSTFolder pstSubFolder : pstFolder.getSubFolders()) {
                String subPath = path + "\\" + pstSubFolder.getDisplayName();
                File subDirectory = new File(directory, pstSubFolder.getDisplayName());
                if (!subDirectory.exists() && !subDirectory.mkdir()) {
                    logger.warn("Failed to create subdirectory {}", subPath);
                    continue;
                }
                messageCount += convertToEML(pstSubFolder, subDirectory, subPath, session);
            }
        }
        return messageCount;
    }

    /**
     * Traverses all PSTFolders recursively, starting from the root PSTFolder,
     * and extracts all email messages to a mbox file.
     *
     * @param pstFolder
     * @param mboxFolder
     * @param path
     * @param session
     * @return
     * @throws PSTException
     * @throws IOException
     * @throws MessagingException
     */
    int convertToMbox(PSTFolder pstFolder, Folder mboxFolder, String path, Session session) throws PSTException, IOException, MessagingException {
        int messageCount = 0;
        if (pstFolder.getContentCount() > 0) {
            PSTObject child = pstFolder.getNextChild();

            MimeMessage[] messages = new MimeMessage[1];
            while (child != null) {
                PSTMessage pstMessage = (PSTMessage) child;
                messages[0] = convertToMimeMessage(pstMessage, session);
                try {
                    mboxFolder.appendMessages(messages);
                    messageCount++;
                } catch (MessagingException ex) {
                    logger.error("Failed to write to Mbox file. {}", ex.getMessage());
                }
                child = pstFolder.getNextChild();
            }
        }
        if (pstFolder.hasSubfolders()) {
            for (PSTFolder pstSubFolder : pstFolder.getSubFolders()) {
                String subPath = path + "\\" + pstSubFolder.getDisplayName();
                Folder mboxSubFolder = mboxFolder.getFolder(pstSubFolder.getDisplayName());
                if (!mboxSubFolder.exists()) {
                    if (!mboxSubFolder.create(Folder.HOLDS_FOLDERS | Folder.HOLDS_MESSAGES)) {
                        logger.warn("Failed to create Mbox sub folder {}", subPath);
                        continue;
                    }
                }
                mboxSubFolder.open(Folder.READ_WRITE);
                messageCount += convertToMbox(pstSubFolder, mboxSubFolder, subPath, session);
                mboxSubFolder.close(false);
            }
        }
        return messageCount;
    }

    /**
     * Converts a PSTMessage to MimeMessage.
     *
     * @param message The PSTMessage object.
     * @param session The java mail session object.
     * @return A new MimeMessage object.
     * @throws MessagingException
     * @throws IOException
     * @throws PSTException
     * @see
     * <a href="https://www.independentsoft.de/jpst/tutorial/exporttomimemessages.html">Export
     * to MIME messages (.eml files)</a>
     */
    MimeMessage convertToMimeMessage(PSTMessage message, Session session) throws MessagingException, IOException, PSTException {
        MimeMessage mimeMessage = new MimeMessage(session);

        String messageHeaders = message.getTransportMessageHeaders();
        if (messageHeaders != null) {
            InternetHeaders headers = new InternetHeaders(new ByteArrayInputStream(messageHeaders.getBytes(StandardCharsets.UTF_8)));
            headers.removeHeader("Content-Type");

            Enumeration<Header> allHeaders = headers.getAllHeaders();

            while (allHeaders.hasMoreElements()) {
                Header header = allHeaders.nextElement();
                mimeMessage.addHeader(header.getName(), header.getValue());
            }
        } else {
            mimeMessage.setSubject(message.getSubject());
            mimeMessage.setSentDate(message.getClientSubmitTime());

            InternetAddress fromMailbox = new InternetAddress();

            String senderEmailAddress = message.getSenderEmailAddress();
            fromMailbox.setAddress(senderEmailAddress);

            String senderName = message.getSenderName();
            if (senderName != null && !senderName.isEmpty()) {
                fromMailbox.setPersonal(senderName);
            } else {
                fromMailbox.setPersonal(senderEmailAddress);
            }

            mimeMessage.setFrom(fromMailbox);

            for (int i = 0; i < message.getNumberOfRecipients(); i++) {
                PSTRecipient recipient = message.getRecipient(i);
                switch (recipient.getRecipientType()) {
                    case PSTRecipient.MAPI_TO:
                        mimeMessage.setRecipient(Message.RecipientType.TO, new InternetAddress(recipient.getEmailAddress(), recipient.getDisplayName()));
                        break;
                    case PSTRecipient.MAPI_CC:
                        mimeMessage.setRecipient(Message.RecipientType.CC, new InternetAddress(recipient.getEmailAddress(), recipient.getDisplayName()));
                        break;
                    case PSTRecipient.MAPI_BCC:
                        mimeMessage.setRecipient(Message.RecipientType.BCC, new InternetAddress(recipient.getEmailAddress(), recipient.getDisplayName()));
                        break;
                    default:
                        break;
                }
            }
        }

        MimeMultipart rootMultipart = new MimeMultipart();
        MimeMultipart contentMultipart = new MimeMultipart();

        String messageBody = message.getBody();
        String messageBodyHTML = message.getBodyHTML();
        boolean withoutBody = messageBody == null || messageBody.isEmpty();
        boolean withoutBodyHTML = messageBodyHTML == null || messageBodyHTML.isEmpty();
        if (!withoutBodyHTML) {
            MimeBodyPart htmlBodyPart = new MimeBodyPart();
            htmlBodyPart.setDataHandler(new DataHandler(new ByteArrayDataSource(messageBodyHTML, "text/html")));
            contentMultipart.addBodyPart(htmlBodyPart);
        } else if (!withoutBody) {
            MimeBodyPart textBodyPart = new MimeBodyPart();
            textBodyPart.setText(messageBody);
            contentMultipart.addBodyPart(textBodyPart);
        } else {
            MimeBodyPart textBodyPart = new MimeBodyPart();
            textBodyPart.setText("");
            textBodyPart.addHeaderLine("Content-Type: text/plain; charset=\"utf-8\"");
            textBodyPart.addHeaderLine("Content-Transfer-Encoding: quoted-printable");
            contentMultipart.addBodyPart(textBodyPart);
        }

        MimeBodyPart contentBodyPart = new MimeBodyPart();
        contentBodyPart.setContent(contentMultipart);

        rootMultipart.addBodyPart(contentBodyPart);
        for (int i = 0; i < message.getNumberOfAttachments(); i++) {
            PSTAttachment attachment = message.getAttachment(i);

            if (attachment != null) {
                byte[] data = getAttachmentBytes(attachment);
                MimeBodyPart attachmentBodyPart = new MimeBodyPart();

                String mimeTag = getAttachmentMimeTag(attachment);
                DataSource source = new ByteArrayDataSource(data, mimeTag);
                attachmentBodyPart.setDataHandler(new DataHandler(source));

                attachmentBodyPart.setContentID(attachment.getContentId());

                String fileName = coalesce("", attachment.getLongFilename(), attachment.getDisplayName(), attachment.getFilename());
                attachmentBodyPart.setFileName(fileName);

                rootMultipart.addBodyPart(attachmentBodyPart);
            }
        }

        mimeMessage.setContent(rootMultipart);
        return mimeMessage;
    }

    private static boolean isMimeTypeKnown(String mime) {
        MimeTypes types = MimeTypes.getDefaultMimeTypes();
        try {
            types.forName(mime);
            return true;
        } catch (MimeTypeException ex) {
            logger.warn("Unknown mime type {}", mime);
            return false;
        }
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
    private static String getEMLFileName(String subject, long descriptorIndex) {
        if (subject == null || subject.isEmpty()) {
            String fileName = descriptorIndex + "-NoSubject.eml";
            return fileName;
        }
        
        StringBuilder builder = new StringBuilder();
        builder.append(descriptorIndex).append("-");
        
        final char[] forbidden = {'\"', '*', '/', ':', '<', '>', '?', '\\', '|', 0x7F};
        for (int i = 0; i < subject.length(); i++) {
            char c = subject.charAt(i);
            if (c >= 32 && c <= 126 && Arrays.binarySearch(forbidden, c) < 0) {
                builder.append(c);
            } else {
                builder.append('_');
            }
        }
        builder.append(".eml");
        return builder.toString();
    }

    /**
     * Extracts the content of the PSTAttachment.
     *
     * @param attachment
     * @return A byte array with the attachment content.
     * @throws PSTException If it's not possible to get the attachment input
     * stream.
     * @throws IOException If an error occurs when reading bytes from the input
     * stream.
     */
    private static byte[] getAttachmentBytes(PSTAttachment attachment) throws PSTException, IOException {
        try (InputStream input = attachment.getFileInputStream()) {
            int nread;
            byte[] buffer = new byte[4096];
            try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                while ((nread = input.read(buffer, 0, 4096)) != -1) {
                    output.write(buffer, 0, nread);
                }
                output.flush();
                byte[] result = output.toByteArray();
                return result;
            }
        }
    }

    private static String getAttachmentMimeTag(PSTAttachment attachment) {
        String mimeTag = attachment.getMimeTag();
        // mimeTag should contain a valid mime type, but sometimes it doesn't.
        // To prevent throwing exceptions when the MimeMessage is validated, the
        // mimeTag value is first checked with isMimeTypeKnown(). If it's not 
        // known, the mime type is set to 'application/octet-stream.
        if (mimeTag == null || mimeTag.isEmpty()) {
            return "application/octet-stream";
        }
        if (isMimeTypeKnown(mimeTag)) {
            return mimeTag;
        }
        return "application/octet-stream";
    }

    private static String coalesce(String defaultValue, String... args) {
        for (String arg : args) {
            if (arg != null) {
                return arg;
            }
        }
        return defaultValue;
    }
}
