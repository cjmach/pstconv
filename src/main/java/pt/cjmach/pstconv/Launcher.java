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

import com.pff.PSTException;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import javax.mail.MessagingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 *
 * @author cmachado
 */
@Command(name = "pstconv", 
        description = "Converts a Microsoft Outlook OST/PST file to EML/MBOX format.")
public class Launcher implements Callable<Integer> {
    /**
     * 
     */
    private static final Logger logger = LoggerFactory.getLogger(Launcher.class);

    /**
     * 
     */
    @Option(names = {"-i", "--input"}, paramLabel = "FILE", required = true,
            description = "Path to OST/PST input file. Required option.")
    private File inputFile;
    /**
     * 
     */
    @Option(names = {"-o", "--output"}, paramLabel = "DIRECTORY", required = true,
            description = "Path to Mbox/EML output directory. If it doesn't exist, the application will attempt to create it. Required option.")
    private File outputDirectory;
    /**
     * 
     */
    @Option(names = {"-f", "--format"}, paramLabel = "FORMAT", defaultValue = "EML",
            description = "Convert input file to one of the following formats: ${COMPLETION-CANDIDATES}. Default is ${DEFAULT-VALUE}.")
    private OutputFormat outputFormat;
    /**
     * 
     */
    @Option(names = {"-e", "--encoding"}, paramLabel = "ENCODING", defaultValue = "UTF-8",
            description = "Encoding to use for reading character data. Default is ${DEFAULT-VALUE}.")
    private String encoding;
    /**
     * 
     */
    @Option(names = {"-v", "--version"}, versionHelp = true, description = "Print version and exit.")
    private boolean versionRequested = false;
    /**
     * 
     */
    @Option(names = {"-h", "--help"}, usageHelp = true, description = "Print help and exit.")
    private boolean helpRequested = false;

    /**
     * 
     * @return
     * @throws Exception 
     */
    @Override
    public Integer call() throws Exception {
        PstConverter converter = new PstConverter();
        try {
            converter.convert(inputFile, outputDirectory, outputFormat, encoding);
        } catch (PSTException | MessagingException | IOException ex) {
            return 1;
        }
        // The application is not finished if the mstor storage provider is used. It 
        // creates a few threads that remain running after processing the PST file.
        // The workaround is to call System.exit().
        return 0;
    }

    /**
     *
     * @param args
     */
    public static void main(String[] args) {
        CommandLine cmdLine = new CommandLine(new Launcher());
        cmdLine.setCaseInsensitiveEnumValuesAllowed(true);
        int exitCode = cmdLine.execute(args);
        if (cmdLine.isVersionHelpRequested()) {
            String version = getVersion();
            System.out.println("pstconv " + version);
        }
        System.exit(exitCode);
    }

    /**
     * 
     * @return 
     */
    private static String getVersion() {
        try {
            Manifest manifest = new Manifest(Launcher.class.getResourceAsStream("/META-INF/MANIFEST.MF"));
            Attributes attributes = manifest.getMainAttributes();
            String version = attributes.getValue("Implementation-Version");
            return version;
        } catch (IOException ex) {
            logger.error("Could not read MANIFEST.MF file", ex);
            return "";
        }
    }
}
