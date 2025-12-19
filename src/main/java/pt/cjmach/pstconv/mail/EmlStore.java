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
import javax.mail.Folder;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.URLName;

/**
 *
 * @author cmachado
 */
public class EmlStore extends Store {
    private final EmlFolder rootFolder;
    
    public EmlStore(Session session, File rootDirectory) {
        super(session, new URLName("file:" + rootDirectory));
        this.rootFolder = new EmlFolder(this, rootDirectory);
    }

    @Override
    public Folder getDefaultFolder() throws MessagingException {
        return rootFolder;
    }

    @Override
    public Folder getFolder(String name) throws MessagingException {
        return rootFolder.getFolder(name);
    }

    @Override
    public Folder getFolder(URLName url) throws MessagingException {
        return rootFolder.getFolder(url.getFile());
    }

    @Override
    protected boolean protocolConnect(String host, int port, String user, String password) throws MessagingException {
        return true;
    }
}
