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
package pt.cjmach.pstconv.mail;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import javax.mail.Flags;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Store;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import pt.cjmach.pstconv.PstConverter;

/**
 *
 * @author cmachado
 */
public abstract class LocalFolder extends Folder {
    protected final File directory;
    private final FileFilter fileFilter;
    private Folder[] directoryEntries;
    private File[] fileEntries;

    protected LocalFolder(Store store, File directory, FileFilter fileFilter) {
        super(store);
        this.directory = directory;
        this.fileFilter = fileFilter;
        if (directory.exists()) {
            assert directory.isDirectory();
        }
    }
    
    protected abstract LocalFolder createInstance(Store store, File directory, FileFilter fileFilter);

    @Override
    public String getName() {
        return directory.getName();
    }

    @Override
    public String getFullName() {
        return directory.getPath();
    }

    @Override
    public Folder getParent() throws MessagingException {
        return createInstance(getStore(), directory.getParentFile(), fileFilter);
    }

    @Override
    public boolean exists() throws MessagingException {
        return directory.exists();
    }

    @Override
    public Folder[] list(String pattern) throws MessagingException {
        // TODO: Implement pattern matching
        if (directoryEntries == null) {
            File[] subdirs = directory.listFiles((FileFilter) DirectoryFileFilter.INSTANCE);
            if (subdirs == null) {
                return new Folder[0];
            }
            Folder[] result = new Folder[subdirs.length];
            for (int i = 0; i < subdirs.length; i++) {
                result[i] = createInstance(getStore(), subdirs[i], fileFilter);
            }
            directoryEntries = result;
        }
        return directoryEntries;
    }

    @Override
    public char getSeparator() throws MessagingException {
        return File.separatorChar;
    }

    @Override
    public int getType() throws MessagingException {
        return Folder.HOLDS_FOLDERS | Folder.HOLDS_MESSAGES;
    }

    @Override
    public boolean create(int type) throws MessagingException {
        if (exists()) {
            throw new MessagingException("Folder already exists");
        }
        return directory.mkdirs();
    }

    @Override
    public boolean hasNewMessages() throws MessagingException {
        return false;
    }

    @Override
    public Folder getFolder(String name) throws MessagingException {
        File file;

        // if path is absolute don't use relative file..
        if (name.startsWith("/")) {
            file = new File(name);
        } else {
            file = new File(directory, name);
        }
        return createInstance(getStore(), file, fileFilter);
    }

    @Override
    public boolean delete(boolean recurse) throws MessagingException {
        if (recurse) {
            try {
                FileUtils.deleteDirectory(directory);
                return true;
            } catch (IOException ex) {
                throw new MessagingException("Failed to delete directory", ex);
            }
        } else {
            return directory.delete();
        }
    }

    @Override
    public boolean renameTo(Folder f) throws MessagingException {
        return directory.renameTo(new File(f.getFullName()));
    }

    @Override
    public void open(int mode) throws MessagingException {

    }

    @Override
    public void close(boolean expunge) throws MessagingException {

    }

    @Override
    public boolean isOpen() {
        return true;
    }

    @Override
    public Flags getPermanentFlags() {
        return null;
    }

    File[] getFileEntries() throws MessagingException {
        if (fileEntries == null) {
            fileEntries = directory.listFiles(fileFilter);
            if (fileEntries == null) {
                throw new MessagingException("Failed to get files");
            }
        }
        return fileEntries;
    }

    @Override
    public int getMessageCount() throws MessagingException {
        return getFileEntries().length;
    }

    @Override
    public abstract Message getMessage(int msgnum) throws MessagingException;

    public abstract void appendMessage(Message msg) throws MessagingException;

    @Override
    public void appendMessages(Message[] msgs) throws MessagingException {
        for (Message msg : msgs) {
            appendMessage(msg);
        }
    }

    @Override
    public Message[] expunge() throws MessagingException {
        return new Message[0];
    }
    
    static String getDescriptorNodeId(Message msg) throws MessagingException {
        String[] values = msg.getHeader(PstConverter.DESCRIPTOR_ID_HEADER);
        if (values == null || values.length <= 0) {
            return "0";
        }
        return values[0];
    }
}
