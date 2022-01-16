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

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.MissingOptionException;
import org.apache.commons.cli.ParseException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author cmachado
 */
public class LauncherTest {

    @Test
    public void testCmdlineParseMissingRequired() {
        String[] args = {"-i", "outlook.pst"};
        MissingOptionException ex = assertThrows(MissingOptionException.class, () -> Launcher.cmdlineParse(args));
        assertEquals(ex.getMessage(), "Missing required option: o");

        args[0] = "-o";
        args[1] = "dir";

        ex = assertThrows(MissingOptionException.class, () -> Launcher.cmdlineParse(args));
        assertEquals(ex.getMessage(), "Missing required option: i");

        ex = assertThrows(MissingOptionException.class, () -> Launcher.cmdlineParse(new String[0]));
        assertEquals(ex.getMessage(), "Missing required options: i, o");

        String[] ok = new String[]{"-i", "outlook.pst", "-o", "dir"};
        try {
            CommandLine cmdLine = Launcher.cmdlineParse(ok);
            assertNotNull(cmdLine);
        } catch (ParseException pe) {
            fail(pe.getMessage());
        }
    }

    @Test
    public void testCmdlineParseHelpOrVersion() throws ParseException {
        String[] args = {"-h"};
        CommandLine cmdLine = Launcher.cmdlineParse(args);
        assertNull(cmdLine);

        args[0] = "-v";
        cmdLine = Launcher.cmdlineParse(args);
        assertNull(cmdLine);
    }
}
