/*
 *  Copyright (C) 2010 Ryszard Wiśniewski <brut.alll@gmail.com>
 *  Copyright (C) 2010 Connor Tumbleson <connor.tumbleson@gmail.com>
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package brut.androlib.res.decoder;

import brut.androlib.BaseTest;
import brut.androlib.TestUtils;
import brut.androlib.res.decoder.data.NinePatchData;
import brut.common.BrutException;
import brut.directory.ExtFile;

import ar.com.hjg.pngj.ImageLineInt;
import ar.com.hjg.pngj.PngReader;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.File;
import java.nio.file.Files;

import org.junit.*;
import static org.junit.Assert.*;

public class MissingDiv9PatchTest extends BaseTest {

    @BeforeClass
    public static void beforeClass() throws Exception {
        TestUtils.copyResourceDir(MissingDiv9PatchTest.class, "res/decoder/issue1522", sTmpDir);
    }

    @Test
    public void assertMissingDivAdded() throws Exception {
        File file = new File(sTmpDir, "pip_dismiss_scrim.9.png");
        byte[] data;

        try (InputStream in = Files.newInputStream(file.toPath())) {
            ResNinePatchStreamDecoder decoder = new ResNinePatchStreamDecoder();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            decoder.decode(in, out);
            data = out.toByteArray();
        }

        PngReader pngr = new PngReader(new ByteArrayInputStream(data));
        int height = pngr.imgInfo.rows - 1;
        int channels = pngr.imgInfo.channels;

        for (int y = 1; y < height; y++) {
            ImageLineInt line = (ImageLineInt) pngr.readRow(y);
            int[] scanline = line.getScanline();
            int r = scanline[0];
            int g = scanline[1];
            int b = scanline[2];
            int a = scanline[3];
            int argb = (a << 24) | (r << 16) | (g << 8) | b;
            assertEquals("y coordinate failed at: " + y, NinePatchData.COLOR_TICK, argb);
        }
        pngr.end();
    }
}
