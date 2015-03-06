/*
 * This is free and unencumbered software released into the public domain.
 *
 * Anyone is free to copy, modify, publish, use, compile, sell, or
 * distribute this software, either in source code form or as a compiled
 * binary, for any purpose, commercial or non-commercial, and by any
 * means.
 *
 * In jurisdictions that recognize copyright laws, the author or authors
 * of this software dedicate any and all copyright interest in the
 * software to the public domain. We make this dedication for the benefit
 * of the public at large and to the detriment of our heirs and
 * successors. We intend this dedication to be an overt act of
 * relinquishment in perpetuity of all present and future rights to this
 * software under copyright law.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS BE LIABLE FOR ANY CLAIM, DAMAGES OR
 * OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 *
 * For more information, please refer to <http://unlicense.org/>
 */

package de.corelogics.tools.android.binarymanifest.chunks;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

public class ResValidateImpl extends ResSource implements ResTarget {
    public ResValidateImpl(File file) throws IOException {
        super(file);
    }

    @Override
    public void close() throws IOException {
        sourceFile.close();
    }

    @Override
    public void writeU16(int val) {
        long pos = super.position();
        int read = readU16();
        if (read != val) {
            System.err.println("@" + pos + ": Ex=" + read + ", act=" + val);
        }
    }

    @Override
    public void writeU32(long val) {
        long pos = super.position();
        long read = readU32();
        if (read != val) {
            System.err.println("@" + pos + ": Ex=" + read + ", act=" + val);
        }
    }

    @Override
    public void writeU8(int val) {
        long pos = super.position();
        int read = readU8();
        if (read != val) {
            System.err.println("@" + pos + ": Ex=" + read + ", act=" + val);
        }
    }

    @Override
    public void write(byte[] data) {
        long pos = super.position();

        byte[] read = new byte[data.length];
        super.get(read);
        if (!Arrays.equals(read, data)) {
            throw new IllegalStateException("@" + pos + ": Ex:" + Arrays.toString(read) + ", act: " + Arrays.toString(data));
        }
    }
}
