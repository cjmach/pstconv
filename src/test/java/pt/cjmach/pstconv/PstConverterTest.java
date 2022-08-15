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
import java.io.FileNotFoundException;
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

    /**
     * Test of convert method, of class PstConverter.
     */
    @Test
    public void testConvertInputFileNotFound() {
        String fileName = "/file/not/found.pst";
        File inputFile = new File(fileName);
        File outputDirectory = new File(".");
        OutputFormat format = OutputFormat.EML;
        String encoding = "UTF-8";
        assertThrows(FileNotFoundException.class, () -> instance.convert(inputFile, outputDirectory, format, encoding));
    }
    
    @Test
    public void testConvertInputFileIllegal() {        
        File inputFile = new File("."); // invalid file
        File outputDirectory = new File(".");
        OutputFormat format = OutputFormat.EML;
        String encoding = "UTF-8";
        assertThrows(FileNotFoundException.class, () -> instance.convert(inputFile, outputDirectory, format, encoding));
    }
    
    @Test
    public void testConvertOutputDirectoryIllegal() {
        File inputFile = new File("src/test/resources/pt/cjmach/pstconv/textfile.txt");
        File outputDirectory = new File("src/test/resources/pt/cjmach/pstconv/textfile.txt");
        OutputFormat format = OutputFormat.EML;
        String encoding = "UTF-8";
        assertThrows(PSTException.class, () -> instance.convert(inputFile, outputDirectory, format, encoding));
    }
    
    @Test
    public void testConvertOutputFormatNull() {
        File inputFile = new File("src/test/resources/pt/cjmach/pstconv/outlook.pst");
        File outputDirectory = new File(".");
        OutputFormat format = null;
        String encoding = "UTF-8";
        IllegalArgumentException iae = assertThrows(IllegalArgumentException.class, () -> instance.convert(inputFile, outputDirectory, format, encoding));
        assertEquals("format is null.", iae.getMessage());
    }
    
    @Test
    public void testConvertEncodingInvalid() {
        File inputFile = new File("src/test/resources/pt/cjmach/pstconv/outlook.pst");
        File outputDirectory = new File(".");
        OutputFormat format = OutputFormat.EML;
        String encoding = "invalid encoding";
        IllegalArgumentException iae = assertThrows(IllegalArgumentException.class, () -> instance.convert(inputFile, outputDirectory, format, encoding));
        assertEquals(encoding, iae.getMessage());
    }
}
