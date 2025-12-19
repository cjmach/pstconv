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

import pt.cjmach.pstconv.mail.EmlStore;
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
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.CoderResult;
import java.nio.charset.MalformedInputException;
import java.util.Date;
import java.util.Enumeration;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.mail.Folder;
import javax.mail.Header;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.URLName;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.InternetHeaders;
import javax.mail.internet.MailDateFormat;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.util.ByteArrayDataSource;
import net.fortuna.mstor.model.MStorStore;
import org.apache.commons.lang3.time.StopWatch;
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
    private static final MailDateFormat RFC822_DATE_FORMAT = new MailDateFormat();
    /**
     * Name of the custom header added to each converted message to allow to
     * easily trace back the original message from OST/PST file.
     */
    public static final String DESCRIPTOR_ID_HEADER = "X-Outlook-Descriptor-Id"; // NOI18N

    /**
     * Default constructor.
     */
    public PstConverter() {
    }

    Store createStore(File directory, MailMessageFormat format, String encoding) {
        switch (format) {
            case EML: {
                Properties sessionProps = new Properties(System.getProperties());
                Session session = Session.getDefaultInstance(sessionProps);
                return new EmlStore(session, directory);
            }

            case MBOX: {
                // see: https://github.com/micronode/mstor#system-properties
                System.setProperty("mstor.mbox.metadataStrategy", "none"); // NOI18N
                System.setProperty("mstor.mbox.encoding", encoding); // NOI18N
                System.setProperty("mstor.mbox.bufferStrategy", "default"); // NOI18N
                System.setProperty("mstor.cache.disabled", "true"); // NOI18N

                Properties sessionProps = new Properties(System.getProperties());
                Session session = Session.getDefaultInstance(sessionProps);
                return new MStorStore(session, new URLName("mstor:" + directory)); // NOI18N
            }
            default:
                throw new IllegalArgumentException("Unsupported mail format: " + format);
        }
    }

    /**
     * Extracts the Outlook Descriptor ID Header value from each previously
     * converted message by this converter. This method can be used to test if a
     * PST file conversion was executed as expected, allowing the comparison
     * between the returned set of ids with the ones found on the original PST.
     *
     * @param directory Directory where to find the messages.
     * @param format The message format (MBOX or EML).
     * @param encoding The message encoding.
     * @return A set with all the found message ids.
     * @throws MessagingException
     */
    public Set<Long> extractDescriptorIds(File directory, MailMessageFormat format, String encoding) throws MessagingException {
        if (!directory.exists()) {
            throw new IllegalArgumentException(String.format("Inexistent directory: %s", directory.getAbsolutePath()));
        }
        if (format == null) {
            throw new IllegalArgumentException("format is null.");
        }
        Charset.forName(encoding); // throws UnsupportedCharsetException if encoding is invalid

        // see: https://docs.oracle.com/javaee/6/api/javax/mail/internet/package-summary.html#package_description
        System.setProperty("mail.mime.address.strict", "false"); // NOI18N
        Set<Long> result = new TreeSet<>();
        Store store = createStore(directory, format, encoding);
        try {
            store.connect();
            Folder mboxRootFolder = store.getDefaultFolder();
            extractDescriptorIds(mboxRootFolder, result);
        } finally {
            try {
                store.close();
            } catch (MessagingException ignore) {
            }
        }
        return result;
    }

    void extractDescriptorIds(Folder folder, Set<Long> ids) throws MessagingException {
        folder.open(Folder.READ_ONLY);
        try {
            for (Folder subFolder : folder.list()) {
                extractDescriptorIds(subFolder, ids);
            }
            if ((folder.getType() & Folder.HOLDS_MESSAGES) != 0) {
                try {
                    for (Message msg : folder.getMessages()) {
                        String[] headerValues = msg.getHeader(DESCRIPTOR_ID_HEADER);
                        if (headerValues != null && headerValues.length > 0) {
                            long id = Long.parseLong(headerValues[0]);
                            ids.add(id);
                        }
                    }
                } catch (MessagingException ex) {
                    logger.warn("Failed to get messages for folder " + folder.getFullName(), ex);
                }
            }
        } finally {
            if (folder.isOpen()) {
                try {
                    folder.close(false);
                } catch (MessagingException | NullPointerException ignore) {
                }
            }
        }
    }

    /**
     * Converts an Outlook OST/PST file to MBox or EML format.
     *
     * @param inputFile The input PST file.
     * @param outputDirectory The directory where the email messages are
     * extracted to and saved.
     * @param format The output format (MBOX or EML).
     * @param encoding The charset encoding to use for character data.
     * @return number of successfully converted messages and the duration of the
     * operation in milliseconds.
     *
     * @throws PSTException
     * @throws MessagingException
     * @throws IOException
     */
    public PstConvertResult convert(File inputFile, File outputDirectory, MailMessageFormat format, String encoding) throws PSTException, MessagingException, IOException {
        PSTFile pstFile = new PSTFile(inputFile); // throws FileNotFoundException is file doesn't exist.
        return convert(pstFile, outputDirectory, format, encoding);
    }

    /**
     * Converts an Outlook OST/PST file to MBox or EML format.
     *
     * @param pstFile The input PST file.
     * @param outputDirectory The directory where the email messages are
     * extracted to and saved.
     * @param format The output format (MBOX or EML).
     * @param encoding The charset encoding to use for character data.
     * @return number of successfully converted messages.
     *
     * @throws PSTException
     * @throws MessagingException
     * @throws IOException
     */
    public PstConvertResult convert(PSTFile pstFile, File outputDirectory, MailMessageFormat format, String encoding) throws PSTException, MessagingException, IOException {
        if (outputDirectory.exists() && !outputDirectory.isDirectory()) {
            throw new IllegalArgumentException(String.format("Not a directory: %s.", outputDirectory.getAbsolutePath()));
        }
        if (format == null) {
            throw new IllegalArgumentException("format is null.");
        }

        Charset charset = Charset.forName(encoding); // throws UnsupportedCharsetException if encoding is invalid

        // see: https://docs.oracle.com/javaee/6/api/javax/mail/internet/package-summary.html#package_description
        System.setProperty("mail.mime.address.strict", "false"); // NOI18N
        long messageCount = 0;

        if (!outputDirectory.exists() && !outputDirectory.mkdirs()) {
            throw new IOException("Failed to create output directory " + outputDirectory.getAbsolutePath());
        }
        StopWatch watch = StopWatch.createStarted();
        Store store = createStore(outputDirectory, format, encoding);
        try {
            store.connect();
            Folder rootFolder = store.getDefaultFolder();
            PSTFolder pstRootFolder = pstFile.getRootFolder();
            messageCount = convert(pstRootFolder, rootFolder, "\\", charset);
            watch.stop();
        } catch (PSTException | MessagingException | IOException ex) {
            logger.error("Failed to convert PSTFile object.", ex);
            throw ex;
        } finally {
            try {
                store.close();
            } catch (MessagingException ignore) {
                // ignore exception
            }
        }
        return new PstConvertResult(messageCount, watch.getTime());
    }

    /**
     * Traverses all PSTFolders recursively, starting from the root PSTFolder,
     * and extracts all email messages to a javax.mail.Folder.
     *
     * @param pstFolder
     * @param mailFolder
     * @param path
     * @param charset
     * @return
     * @throws PSTException
     * @throws IOException
     * @throws MessagingException
     */
    long convert(PSTFolder pstFolder, Folder mailFolder, String path, Charset charset) throws PSTException, IOException, MessagingException {
        long messageCount = 0;
        if (pstFolder.getContentCount() > 0) {
            PSTObject child = pstFolder.getNextChild();

            MimeMessage[] messages = new MimeMessage[1];
            while (child != null) {
                String errorMsg = "Failed to append message id {} to folder {}.";
                PSTMessage pstMessage = (PSTMessage) child;
                try {
                    messages[0] = convertToMimeMessage(pstMessage, charset);
                    mailFolder.appendMessages(messages);
                    messageCount++;
                } catch (MessagingException ex) {
                    // if the cause of the MessagingException is a MalformedInputException,
                    // then it was probably thrown due to the encoding set by the user on 
                    // the command line.
                    if (ex.getCause() instanceof MalformedInputException) {
                        MalformedInputException mie = (MalformedInputException) ex.getCause();
                        if (mie.getStackTrace().length > 0) {
                            String className = mie.getStackTrace()[0].getClassName();
                            // if the class that throwed the exception is CoderResult,
                            // then it was caused by an encoding/decoding error.
                            if (CoderResult.class.getName().equals(className)) {
                                errorMsg = String.format("Exception thrown caused by invalid encoding setting: %s. %s",
                                        charset.displayName(), errorMsg);
                            }
                        }
                    }
                    logger.error(errorMsg, child.getDescriptorNodeId(), mailFolder.getFullName(), ex);
                } catch (PSTException | IOException ex) {
                    // Handle other exceptions as well and move on to the next 
                    // PST message.
                    logger.error(errorMsg,
                            child.getDescriptorNodeId(), mailFolder.getFullName(), ex);
                }
                try {
                    child = pstFolder.getNextChild();
                } catch (IndexOutOfBoundsException ex) {
                    // This exception is thrown by java-libpst on more recent 
                    // versions (0.9.5-SNAPSHOT). It only happens when the PST 
                    // content is read from a stream.
                    logger.error("Index out of bounds when trying to get next child on folder {} ({}).", 
                            pstFolder.getDisplayName(), pstFolder.getDescriptorNodeId());
                    // Try to continue to the next PST folder.
                    break;
                }
            }
        }
        if (pstFolder.hasSubfolders()) {
            for (PSTFolder pstSubFolder : pstFolder.getSubFolders()) {
                String folderName = PstUtil.normalizeString(pstSubFolder.getDisplayName());
                String subPath = path + "\\" + folderName;
                Folder mboxSubFolder = mailFolder.getFolder(folderName);
                if (!mboxSubFolder.exists()) {
                    if (!mboxSubFolder.create(Folder.HOLDS_FOLDERS | Folder.HOLDS_MESSAGES)) {
                        logger.warn("Failed to create mail sub folder {}.", subPath);
                        continue;
                    }
                }
                mboxSubFolder.open(Folder.READ_WRITE);
                messageCount += convert(pstSubFolder, mboxSubFolder, subPath, charset);
                mboxSubFolder.close(false);
            }
        }
        return messageCount;
    }

    /**
     * Converts a PSTMessage to MimeMessage.
     *
     * @param message The PSTMessage object.
     * @param charset
     * @return A new MimeMessage object.
     * @throws MessagingException
     * @throws IOException
     * @throws PSTException
     * @see
     * <a href="https://www.independentsoft.de/jpst/tutorial/exporttomimemessages.html">Export
     * to MIME messages (.eml files)</a>
     */
    MimeMessage convertToMimeMessage(PSTMessage message, Charset charset) throws MessagingException, IOException, PSTException {
        MimeMessage mimeMessage = new MimeMessage((Session) null);

        convertMessageHeaders(message, mimeMessage, charset);
        // Add custom header to easily track the original message from OST/PST file.
        mimeMessage.addHeader(DESCRIPTOR_ID_HEADER, Long.toString(message.getDescriptorNodeId()));

        MimeMultipart rootMultipart = new MimeMultipart();
        convertMessageBody(message, rootMultipart);
        convertAttachments(message, rootMultipart);
        mimeMessage.setContent(rootMultipart);
        return mimeMessage;
    }

    void convertMessageHeaders(PSTMessage message, MimeMessage mimeMessage, Charset charset) throws IOException, MessagingException, PSTException {
        String messageHeaders = message.getTransportMessageHeaders();
        if (messageHeaders != null && !messageHeaders.isEmpty()) {
            try (InputStream headersStream = new ByteArrayInputStream(messageHeaders.getBytes(charset))) {
                InternetHeaders headers = new InternetHeaders(headersStream);
                headers.removeHeader("Content-Type"); // NOI18N

                Enumeration<Header> allHeaders = headers.getAllHeaders();

                while (allHeaders.hasMoreElements()) {
                    Header header = allHeaders.nextElement();
                    mimeMessage.addHeader(header.getName(), header.getValue());
                }
                String dateHeader = mimeMessage.getHeader("Date", null); // NOI18N
                if (dateHeader == null || dateHeader.isEmpty()) {
                    mimeMessage.addHeader("Date", RFC822_DATE_FORMAT.format(message.getMessageDeliveryTime())); // NOI18N
                }
            }
        } else {
            mimeMessage.setSubject(message.getSubject());
            Date sentDate = message.getClientSubmitTime();
            if (sentDate == null) {
                mimeMessage.addHeader("Date", ""); // NOI18N
            } else {
                mimeMessage.setSentDate(sentDate);
            }

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
    }

    void convertMessageBody(PSTMessage message, MimeMultipart rootMultipart) throws IOException, MessagingException {
        MimeMultipart contentMultipart = new MimeMultipart();
        String messageBody = message.getBody();
        String messageBodyHTML = message.getBodyHTML();

        if (messageBodyHTML != null && !messageBodyHTML.isEmpty()) {
            MimeBodyPart htmlBodyPart = new MimeBodyPart();
            htmlBodyPart.setDataHandler(new DataHandler(new ByteArrayDataSource(messageBodyHTML, "text/html"))); // NOI18N
            contentMultipart.addBodyPart(htmlBodyPart);
        } else if (messageBody != null && !messageBody.isEmpty()) {
            MimeBodyPart textBodyPart = new MimeBodyPart();
            textBodyPart.setText(messageBody);
            contentMultipart.addBodyPart(textBodyPart);
        } else {
            MimeBodyPart textBodyPart = new MimeBodyPart();
            textBodyPart.setText("");
            textBodyPart.addHeaderLine("Content-Type: text/plain; charset=\"utf-8\""); // NOI18N
            textBodyPart.addHeaderLine("Content-Transfer-Encoding: quoted-printable"); // NOI18N
            contentMultipart.addBodyPart(textBodyPart);
        }
        MimeBodyPart contentBodyPart = new MimeBodyPart();
        contentBodyPart.setContent(contentMultipart);
        rootMultipart.addBodyPart(contentBodyPart);
    }

    void convertAttachments(PSTMessage message, MimeMultipart rootMultipart) throws MessagingException, PSTException, IOException {
        for (int i = 0; i < message.getNumberOfAttachments(); i++) {
            PSTAttachment attachment = message.getAttachment(i);

            if (attachment != null) {
                byte[] data = getAttachmentBytes(attachment);
                if (data == null) {
                    logger.warn("Failed to extract bytes of attachment {} from message {}.", 
                            attachment.getDescriptorNodeId(), message.getDescriptorNodeId());
                    // try to add the attachment, which may still be useful even without its contents.
                    data = new byte[0];
                }
                
                MimeBodyPart attachmentBodyPart = new MimeBodyPart();
                try {
                    String mimeTag = getAttachmentMimeTag(attachment);
                    DataSource source = new ByteArrayDataSource(data, mimeTag);
                    attachmentBodyPart.setDataHandler(new DataHandler(source));

                    attachmentBodyPart.setContentID(attachment.getContentId());

                    String fileName = coalesce("attachment-" + attachment.getDescriptorNodeId(), // NOI18N
                            attachment.getLongFilename(), attachment.getDisplayName(), attachment.getFilename());
                    attachmentBodyPart.setFileName(fileName);

                    rootMultipart.addBodyPart(attachmentBodyPart);
                } catch (NullPointerException ex) {
                    logger.warn("Failed to convert attachment {} from message {}.", 
                            attachment.getDescriptorNodeId(), message.getDescriptorNodeId());
                }
            }
        }
    }

    static boolean isMimeTypeKnown(String mime) {
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
     * Extracts the content of the PSTAttachment.
     *
     * @param attachment
     * @return A byte array with the attachment content.
     * @throws PSTException If it's not possible to get the attachment input
     * stream.
     * @throws IOException If an error occurs when reading bytes from the input
     * stream.
     */
    static byte[] getAttachmentBytes(PSTAttachment attachment) throws PSTException, IOException {
        InputStream input;
        try {
            input = attachment.getFileInputStream();
        } catch (NullPointerException ex) {
            return null;
        }
        try {
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
        } finally {
            try {
                input.close();
            } catch (IOException ignore) { }
        }
    }

    static String getAttachmentMimeTag(PSTAttachment attachment) {
        String mimeTag = null;
        try {
            mimeTag = attachment.getMimeTag();
        } catch (NullPointerException ignore) { }
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

    static String coalesce(String defaultValue, String... args) {
        for (String arg : args) {
            if (arg != null && !arg.isEmpty()) {
                return arg;
            }
        }
        return defaultValue;
    }
}
