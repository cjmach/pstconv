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
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionGroup;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author cmachado
 */
public class Launcher {

    private static final String OPT_SHORT_HELP = "h";
    private static final String OPT_LONG_HELP = "help";
    private static final String OPT_SHORT_VERSION = "v";
    private static final String OPT_LONG_VERSION = "version";
    private static final String OPT_SHORT_INPUT = "i";
    private static final String OPT_LONG_INPUT = "input";
    private static final String OPT_SHORT_OUTPUT = "o";
    private static final String OPT_LONG_OUTPUT = "output";
    private static final String OPT_SHORT_FORMAT = "f";
    private static final String OPT_LONG_FORMAT = "format";
    private static final String OPT_SHORT_ENCODING = "e";
    private static final String OPT_LONG_ENCODING = "encoding";

    private static final Logger logger = LoggerFactory.getLogger(Launcher.class);

    /**
     *
     * @param args
     */
    public static void main(String[] args) {
        CommandLine cmdLine;
        try {
            cmdLine = cmdlineParse(args);
        } catch (ParseException ex) {
            logger.error("Invalid command line arguments. {}", ex.getMessage());
            System.exit(1);
            return; // Prevents 'value assigned but never used' warning.
        }
        if (cmdLine == null) { // Null when either help or version options are given.
            System.exit(0);
        }

        File inputFile = new File(cmdLine.getOptionValue(OPT_SHORT_INPUT));
        File outputDirectory = new File(cmdLine.getOptionValue(OPT_SHORT_OUTPUT));
        String formatValue = cmdLine.getOptionValue(OPT_SHORT_FORMAT, OutputFormat.EML.format);
        OutputFormat outputFormat = OutputFormat.valueOfFormat(formatValue);
        String encoding = cmdLine.getOptionValue(OPT_SHORT_ENCODING, "UTF-8");

        PstConverter converter = new PstConverter();
        try {
            converter.convert(inputFile, outputDirectory, outputFormat, encoding);
        } catch (PSTException | IOException ex) {
            logger.error("Failed to convert input file {}. {}", inputFile, ex.getMessage());
            System.exit(1);
        }
        // The application is not finished if the mstor storage provider is used. It 
        // creates a few threads that remain running after processing the PST file.
        // The workaround is to call System.exit().
        System.exit(0);
    }

    /**
     *
     * @param args
     * @return
     * @throws ParseException
     */
    static CommandLine cmdlineParse(String[] args) throws ParseException {
        Option helpOption = Option.builder(OPT_SHORT_HELP).longOpt(OPT_LONG_HELP)
                .desc("Print help and exit")
                .hasArg(false)
                .required(false)
                .build();
        Option versionOption = Option.builder(OPT_SHORT_VERSION).longOpt(OPT_LONG_VERSION)
                .desc("Print version and exit")
                .hasArg(false)
                .required(false)
                .build();
        Option inputOption = Option.builder(OPT_SHORT_INPUT).longOpt(OPT_LONG_INPUT)
                .desc("Path to OST/PST input file.")
                .hasArg()
                .argName("FILE")
                .type(String.class)
                .required()
                .build();
        Option outputOption = Option.builder(OPT_SHORT_OUTPUT).longOpt(OPT_LONG_OUTPUT)
                .desc("Path to Mbox/EML output directory. If it doesn't exist, the application will attempt to create it.")
                .hasArg()
                .argName("DIRECTORY")
                .type(String.class)
                .required()
                .build();
        Option formatOption = Option.builder(OPT_SHORT_FORMAT).longOpt(OPT_LONG_FORMAT)
                .desc("Convert input file to one of the following formats: mbox, eml. Default is eml.")
                .hasArg()
                .argName("FORMAT")
                .type(String.class)
                .required(false)
                .build();
        Option encodingOption = Option.builder(OPT_SHORT_ENCODING).longOpt(OPT_LONG_ENCODING)
                .desc("Encoding to use for reading character data. Default is UTF-8.")
                .hasArg()
                .argName("ENCODING")
                .type(String.class)
                .required(false)
                .build();

        Options allOptions = new Options()
                .addOption(helpOption)
                .addOption(versionOption)
                .addOption(inputOption)
                .addOption(outputOption)
                .addOption(formatOption)
                .addOption(encodingOption);

        if (checkForHelpOrVersion(allOptions, helpOption, versionOption, args)) {
            return null;
        }
        CommandLineParser parser = new DefaultParser();
        CommandLine cmdLine = parser.parse(allOptions, args);
        return cmdLine;
    }

    /**
     *
     * @param allOptions
     * @param helpOption
     * @param versionOption
     * @param args
     * @return
     * @throws ParseException
     */
    private static boolean checkForHelpOrVersion(Options allOptions, Option helpOption, Option versionOption, String[] args) throws ParseException {
        OptionGroup group = new OptionGroup()
                .addOption(helpOption)
                .addOption(versionOption);
        Options groupOptions = new Options().addOptionGroup(group);
        CommandLineParser parser = new DefaultParser();
        try {
            CommandLine cmdLine = parser.parse(groupOptions, args);
            if (cmdLine.hasOption(OPT_SHORT_HELP)) {
                HelpFormatter helpFormatter = new HelpFormatter();
                helpFormatter.printHelp("pstconv [OPTIONS]", allOptions);
                return true;
            }
            if (cmdLine.hasOption(OPT_SHORT_VERSION)) {
                System.out.println(getVersion());
                return true;
            }
            return false;
        } catch (ParseException ignore) {
            return false;
        }
    }

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
