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
import java.io.RandomAccessFile;
import java.nio.ByteOrder;
import java.nio.CharBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public class ResSource {
    protected final RandomAccessFile sourceFile;
    protected final MappedByteBuffer buffer;

    public ResSource(File source) throws IOException {
        this.sourceFile = new RandomAccessFile(source, "r");
        FileChannel inChannel = this.sourceFile.getChannel();
        this.buffer = inChannel.map(FileChannel.MapMode.READ_ONLY, 0, inChannel.size());
        buffer.order(ByteOrder.LITTLE_ENDIAN);
    }

    public int readU8() {
        return buffer.get() & 0xff;
    }

    public int readU16() {
        return (int)(buffer.getShort()) & 0xffff;
    }

    public long readU32() {
        return (long) buffer.getInt() & 0xffffffffL;
    }

    public long position() {
        return buffer.position();
    }

    public void position(long l) {
        buffer.position((int) l);
    }

    public CharBuffer asCharBuffer() {
        return buffer.asCharBuffer();
    }

    public void get(byte[] data) {
        buffer.get(data);
    }

    public int capacity() {
        return buffer.capacity();
    }

    public void copyTo(long len, ResTarget tgt) {
        for (long i = 0; i < len; i++) {
            tgt.writeU8(this.readU8());
        }
    }
}
