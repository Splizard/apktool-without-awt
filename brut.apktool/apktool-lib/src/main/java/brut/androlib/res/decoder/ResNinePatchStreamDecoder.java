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

import brut.androlib.exceptions.AndrolibException;
import brut.androlib.exceptions.NinePatchNotFoundException;
import brut.androlib.res.decoder.data.LayoutBounds;
import brut.androlib.res.decoder.data.NinePatchData;
import brut.util.BinaryDataInputStream;
import org.apache.commons.io.IOUtils;

import ar.com.hjg.pngj.ImageInfo;
import ar.com.hjg.pngj.ImageLineHelper;
import ar.com.hjg.pngj.ImageLineInt;
import ar.com.hjg.pngj.PngReader;
import ar.com.hjg.pngj.PngWriter;
import ar.com.hjg.pngj.chunks.ChunkCopyBehaviour;
import ar.com.hjg.pngj.chunks.PngChunkPLTE;
import ar.com.hjg.pngj.chunks.PngChunkTRNS;

import java.io.*;
import java.nio.ByteOrder;

public class ResNinePatchStreamDecoder implements ResStreamDecoder {

    @Override
    public void decode(InputStream in, OutputStream out) throws AndrolibException {
        try {
            byte[] data = IOUtils.toByteArray(in);
            if (data.length == 0) {
                return;
            }

            PngReader pngr = new PngReader(new ByteArrayInputStream(data));
            int w = pngr.imgInfo.cols;
            int h = pngr.imgInfo.rows;

            ImageInfo imInfo = new ImageInfo(w + 2, h + 2, 8, true);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PngWriter pngw = new PngWriter(baos, imInfo);

            pngw.copyChunksFrom(pngr.getChunksList(), ChunkCopyBehaviour.COPY_ALL_SAFE);

            int[][] pixels = new int[h + 2][w + 2];
            for (int y = 0; y < h + 2; y++) {
                for (int x = 0; x < w + 2; x++) {
                    pixels[y][x] = 0x00000000;
                }
            }

            boolean isIndexed = pngr.imgInfo.indexed;
            boolean isGrayscaleAlpha = pngr.imgInfo.greyscale && pngr.imgInfo.alpha;
            boolean hasAlpha = pngr.imgInfo.alpha;
            int channels = pngr.imgInfo.channels;

            PngChunkPLTE pal = null;
            PngChunkTRNS trns = null;
            if (isIndexed) {
                pal = (PngChunkPLTE) pngr.getChunksList().getById1(PngChunkPLTE.ID);
                trns = (PngChunkTRNS) pngr.getChunksList().getById1(PngChunkTRNS.ID);
            }

            for (int row = 0; row < h; row++) {
                ImageLineInt line = (ImageLineInt) pngr.readRow(row);
                int[] scanline = line.getScanline();

                if (isIndexed && pal != null) {
                    int[] rgba = ImageLineHelper.palette2rgba(line, pal, trns, null);
                    for (int col = 0; col < w; col++) {
                        int r = rgba[col * 4];
                        int g = rgba[col * 4 + 1];
                        int b = rgba[col * 4 + 2];
                        int a = rgba[col * 4 + 3];
                        pixels[row + 1][col + 1] = (a << 24) | (r << 16) | (g << 8) | b;
                    }
                } else {
                    for (int col = 0; col < w; col++) {
                        int argb;
                        if (isGrayscaleAlpha) {
                            int gray = scanline[col * channels];
                            int alpha = scanline[col * channels + 1];
                            argb = (alpha << 24) | (gray << 16) | (gray << 8) | gray;
                        } else if (pngr.imgInfo.greyscale) {
                            int gray = scanline[col * channels];
                            argb = 0xFF000000 | (gray << 16) | (gray << 8) | gray;
                        } else if (hasAlpha) {
                            int r = scanline[col * channels];
                            int g = scanline[col * channels + 1];
                            int b = scanline[col * channels + 2];
                            int a = scanline[col * channels + 3];
                            argb = (a << 24) | (r << 16) | (g << 8) | b;
                        } else {
                            int r = scanline[col * channels];
                            int g = scanline[col * channels + 1];
                            int b = scanline[col * channels + 2];
                            argb = 0xFF000000 | (r << 16) | (g << 8) | b;
                        }
                        pixels[row + 1][col + 1] = argb;
                    }
                }
            }
            pngr.end();

            NinePatchData np = findNinePatchData(data);
            drawHLine(pixels, h + 1, np.paddingLeft + 1, w - np.paddingRight);
            drawVLine(pixels, w + 1, np.paddingTop + 1, h - np.paddingBottom);

            int[] xDivs = np.xDivs;
            if (xDivs.length == 0) {
                drawHLine(pixels, 0, 1, w);
            } else {
                for (int i = 0; i < xDivs.length; i += 2) {
                    drawHLine(pixels, 0, xDivs[i] + 1, xDivs[i + 1]);
                }
            }

            int[] yDivs = np.yDivs;
            if (yDivs.length == 0) {
                drawVLine(pixels, 0, 1, h);
            } else {
                for (int i = 0; i < yDivs.length; i += 2) {
                    drawVLine(pixels, 0, yDivs[i] + 1, yDivs[i + 1]);
                }
            }

            try {
                LayoutBounds lb = findLayoutBounds(data);

                for (int i = 0; i < lb.left; i++) {
                    int x = 1 + i;
                    pixels[h + 1][x] = LayoutBounds.COLOR_TICK;
                }

                for (int i = 0; i < lb.right; i++) {
                    int x = w - i;
                    pixels[h + 1][x] = LayoutBounds.COLOR_TICK;
                }

                for (int i = 0; i < lb.top; i++) {
                    int y = 1 + i;
                    pixels[y][w + 1] = LayoutBounds.COLOR_TICK;
                }

                for (int i = 0; i < lb.bottom; i++) {
                    int y = h - i;
                    pixels[y][w + 1] = LayoutBounds.COLOR_TICK;
                }
            } catch (NinePatchNotFoundException ignored) {
            }

            for (int row = 0; row < h + 2; row++) {
                ImageLineInt line = new ImageLineInt(imInfo);
                int[] scanline = line.getScanline();
                for (int col = 0; col < w + 2; col++) {
                    int argb = pixels[row][col];
                    int r = (argb >> 16) & 0xFF;
                    int g = (argb >> 8) & 0xFF;
                    int b = argb & 0xFF;
                    int a = (argb >> 24) & 0xFF;
                    scanline[col * 4] = r;
                    scanline[col * 4 + 1] = g;
                    scanline[col * 4 + 2] = b;
                    scanline[col * 4 + 3] = a;
                }
                pngw.writeRow(line);
            }
            pngw.end();

            out.write(baos.toByteArray());
        } catch (IOException | NullPointerException ex) {
            throw new AndrolibException(ex);
        }
    }

    private NinePatchData findNinePatchData(byte[] data) throws NinePatchNotFoundException, IOException {
        BinaryDataInputStream in = new BinaryDataInputStream(data, ByteOrder.BIG_ENDIAN);
        findChunk(in, NinePatchData.MAGIC);
        return NinePatchData.read(in);
    }

    private LayoutBounds findLayoutBounds(byte[] data) throws NinePatchNotFoundException, IOException {
        BinaryDataInputStream in = new BinaryDataInputStream(data, ByteOrder.BIG_ENDIAN);
        findChunk(in, LayoutBounds.MAGIC);
        return LayoutBounds.read(in);
    }

    private void findChunk(BinaryDataInputStream in, int magic) throws NinePatchNotFoundException, IOException {
        in.skipBytes(8);
        for (;;) {
            int size;
            try {
                size = in.readInt();
            } catch (EOFException ignored) {
                throw new NinePatchNotFoundException();
            }
            if (in.readInt() == magic) {
                return;
            }
            in.skipBytes(size + 4);
        }
    }

    private void drawHLine(int[][] pixels, int y, int x1, int x2) {
        for (int x = x1; x <= x2; x++) {
            pixels[y][x] = NinePatchData.COLOR_TICK;
        }
    }

    private void drawVLine(int[][] pixels, int x, int y1, int y2) {
        for (int y = y1; y <= y2; y++) {
            pixels[y][x] = NinePatchData.COLOR_TICK;
        }
    }
}
