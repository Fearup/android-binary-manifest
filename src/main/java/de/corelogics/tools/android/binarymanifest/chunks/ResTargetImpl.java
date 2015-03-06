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

import sun.nio.ch.DirectBuffer;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public class ResTargetImpl implements ResTarget {
    private RandomAccessFile sourceFile;
    private final MappedByteBuffer buffer;
    private final FileChannel outputChannel;
    private final File outputFile;

    public ResTargetImpl(File file, long initialSize) throws IOException {
        outputFile = file;
        this.sourceFile = new RandomAccessFile(outputFile, "rw");
        this.sourceFile.setLength(initialSize);
        this.outputChannel = this.sourceFile.getChannel();
        this.buffer = outputChannel.map(FileChannel.MapMode.READ_WRITE, 0, outputChannel.size());
        buffer.order(ByteOrder.LITTLE_ENDIAN);
    }

    @Override
    public void close() throws IOException {
        int length = buffer.position();
        outputChannel.close();
        try {
            sourceFile.close();
        } catch(IOException ignored) {}
        ((DirectBuffer) buffer).cleaner().clean();
        this.sourceFile = new RandomAccessFile(outputFile, "rw");
        sourceFile.setLength(length);
        sourceFile.close();
    }

    @Override
    public long position() {
        return buffer.position();
    }

    @Override
    public void position(long position) {
        buffer.position((int) position);
    }

    @Override
    public void writeU16(int val) {
        buffer.putShort((short) val);
    }

    @Override
    public void writeU32(long val) {
        buffer.putInt((int) (val & 0xffffffffL));
    }

    @Override
    public void writeU8(int val) {
        buffer.put((byte)val);
    }

    @Override
    public void write(byte[] data) {
        buffer.put(data);
    }
}
