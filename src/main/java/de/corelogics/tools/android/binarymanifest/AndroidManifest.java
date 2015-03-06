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

package de.corelogics.tools.android.binarymanifest;

import de.corelogics.tools.android.binarymanifest.chunks.ResSource;
import de.corelogics.tools.android.binarymanifest.chunks.ResTarget;
import de.corelogics.tools.android.binarymanifest.chunks.ResTargetImpl;
import de.corelogics.tools.android.binarymanifest.chunks.ResValidateImpl;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AndroidManifest {
    private final ResSource src;
    private ResChunkHeader fileHeader;
    private ResStringPool stringPool;
    private List<ResChunk> chunks = new ArrayList<>();


    private String versionName = null;
    private boolean versionNameChanged = false;

    private int versionCode = -1;
    private boolean versionCodeChanged = false;

    public AndroidManifest(File source) throws IOException {
        this.src = new ResSource(source);
        read();
    }

    public void setVersionCode(int versionCode) {
        this.versionCodeChanged = this.versionCode != versionCode;
        this.versionCode = versionCode;
    }

    public void setVersionName(String versionName) {
        this.versionNameChanged = !this.versionName.equals(versionName);
        this.versionName = versionName;
    }

    public int getVersionCode() {
        return versionCode;
    }

    public String getVersionName() {
        return versionName;
    }

    private void read() {
        this.fileHeader = new ResChunkHeader(src);
        while (src.position() < fileHeader.size) {
            long bufferStartPos = src.position();
            ResChunkHeader chunkHeader = new ResChunkHeader(src);
            if (chunkHeader.type == 0x1) {
                ResStringPoolHeader poolHeader = new ResStringPoolHeader(chunkHeader, src);
                this.stringPool = new ResStringPool(poolHeader, src);
                System.out.println(stringPool);
            } else if (chunkHeader.type == 0x0102) {
                ResXmlStartElement startElement = new ResXmlStartElement(chunkHeader, src);
                System.out.println(startElement);
                this.chunks.add(startElement);
            } else {
                UnknownResource unknown = new UnknownResource(chunkHeader, src);
                System.out.println(unknown);
                this.chunks.add(unknown);
            }
            src.position(bufferStartPos + chunkHeader.size);
        }
    }

    public void write(File file) throws IOException {
        ResTarget tgt = new ResTargetImpl(file, src.capacity() + 4096);
        writeTo(tgt);
        tgt.close();
    }

    public void validate(File file) throws IOException {
        ResTarget tgt = new ResValidateImpl(file);
        writeTo(tgt);
        tgt.close();
    }

    private void writeTo(ResTarget tgt) {
        this.fileHeader.writeTo(tgt);
        long startPos = tgt.position();
        this.stringPool.writeTo(tgt);
        for (ResChunk chunk : chunks) {
            chunk.writeTo(tgt);
        }
        long length = tgt.position() - startPos;
        tgt.position(0);
        long newLength = fileHeader.writeTo(tgt, (int) length);
        tgt.position(newLength);
    }

    private interface ResChunk {
        void writeTo(ResTarget tgt);
    }

    private static class ResChunkHeader {
        private long chunkOriginalStart;
        private int type;  //u16
        private int headerSize;  //u16
        private long size; //u32

        public ResChunkHeader(ResSource src) {
            chunkOriginalStart = src.position();
            type = src.readU16();
            headerSize = src.readU16();
            size = src.readU32();
        }

        public long writeTo(ResTarget tgt, long newSize) {
            tgt.writeU16(type);
            tgt.writeU16(headerSize);
            long newSizeAligned = newSize + 2 * tgt.LEN_U16 + tgt.LEN_U32;
            while (0 != newSizeAligned % 4) {
                newSizeAligned++;
            }
            tgt.writeU32(newSizeAligned);
            return newSizeAligned;
        }

        public void writeTo(ResTarget tgt) {
            tgt.writeU16(type);
            tgt.writeU16(headerSize);
            tgt.writeU32(size);
        }
    }

    private static class ResStringPoolHeader {
        private ResChunkHeader header;
        private long stringCount; // u32
        private long styleCount; // u32
        private long flags; // u32
        private long stringsStart; // u32
        private long stylesStart; // u32

        private ResStringPoolHeader(ResChunkHeader header, ResSource src) {
            this.header = header;
            this.stringCount = src.readU32();
            this.styleCount = src.readU32();
            this.flags = src.readU32();
            this.stringsStart = src.readU32();
            this.stylesStart = src.readU32();
        }

        @Override
        public String toString() {
            return "ResStringPoolHeader{" +
                    "header=" + header +
                    ", stringCount=" + stringCount +
                    ", styleCount=" + styleCount +
                    ", flags=" + flags +
                    ", stringsStart=" + stringsStart +
                    ", stylesStart=" + stylesStart +
                    '}';
        }

        public void writeTo(ResTarget tgt, int newStringCount, int newStringsStart, int stylesStart, int length) {
            header.writeTo(tgt, length + 5 * tgt.LEN_U32);
            tgt.writeU32(newStringCount);
            tgt.writeU32(styleCount);
            tgt.writeU32(flags);
            tgt.writeU32(newStringsStart);
            tgt.writeU32(stylesStart);
        }
    }

    private class ResStringPool implements ResChunk {
        private ResStringPoolHeader header;
        private long styleOffsets[];
        private List<String> strings = new ArrayList<>();
        private byte[] stylesData;

        public long getIndexOfNewVersionName() {
            return strings.indexOf(versionName);
        }

        public void writeTo(ResTarget tgt) {
            if (versionNameChanged) {
                strings.add(versionName);
            }
            int stringsStart = header.header.headerSize + strings.size() * tgt.LEN_U32 + styleOffsets.length * tgt.LEN_U32;
            int stylesStart = stringsStart;
            for (String st : strings) {
                stylesStart += (2 + st.length()) * 2;

            }
            int length = stylesStart + stylesData.length;

            header.writeTo(
                    tgt,
                    strings.size(),
                    stringsStart,
                    header.stylesStart > 0 ?
                        stylesStart :
                        0,
                    length - header.header.headerSize);

            int pos = 0;
            for (final String st : strings) {
                tgt.writeU32(pos);
                pos += (2+st.length()) * 2;
            }

            int styleShift = (int) (stylesStart - header.stylesStart);
            for (int i = 0; i < styleOffsets.length; i++) {
                tgt.writeU32(styleOffsets[i] + styleShift);
            }

            for (final String st : strings) {
                int stLen = st.length();
                if (stLen > 32767) {
                    int high = (stLen >> 16) & (0x8000);
                    int low = stLen & 0xffff;
                    tgt.writeU8(high);
                    tgt.writeU8(low);
                } else {
                    tgt.writeU16(stLen);
                }
                for (char c : st.toCharArray()) {
                    tgt.writeU16(c);
                }
                tgt.writeU16(0);
            }
            tgt.write(stylesData);
            while (tgt.position() % 4 != 0) {
                tgt.writeU8(0);
            }
        }

        public ResStringPool(ResStringPoolHeader header, ResSource src) {
            this.header = header;
            long stringOffsets[] = new long[(int) header.stringCount];
            this.styleOffsets = new long[(int) header.styleCount];
            for (int i = 0; i < stringOffsets.length; i++) {
                stringOffsets[i] = src.readU32();
            }
            for (int i = 0; i < this.styleOffsets.length; i++) {
                this.styleOffsets[i] = src.readU32();
            }
            long startOfStrings = header.header.chunkOriginalStart + header.stringsStart;

            for (int i = 0; i < stringOffsets.length; i++) {
                src.position(startOfStrings + stringOffsets[i]);
                int size = src.readU16();
                if (size > 32767) {
                    size = (size & 0x7fff) << 16 + src.readU16();
                }
                char[] c = new char[size];
                src.asCharBuffer().get(c);
                this.strings.add(String.valueOf(c));
            }

            if (header.styleCount > 0) {
                long startOfStyles = header.header.chunkOriginalStart + header.stylesStart;
                long endOfStyles = header.header.chunkOriginalStart + header.header.size;
                this.stylesData = new byte[(int) (endOfStyles - startOfStyles)];
                src.position((int) startOfStrings);
                src.get(this.stylesData);
            } else {
                this.stylesData = new byte[0];
            }
        }

        @Override
        public String toString() {
            return "ResStringPool{" +
                    "header=" + header +
                    ", styleOffsets=" + Arrays.toString(styleOffsets) +
                    ", strings=" + strings +
                    '}';
        }

        public String lookup(long index) {
            if (index >= 0 && index < strings.size()) {
                return strings.get((int) index);
            }
            return "<UNKNOWN>";
        }
    }

    private class ResStringPoolRef {
        private long index; // u32

        public ResStringPoolRef(ResSource src) {
            this.index = src.readU32();
        }

        public void writeTo(ResTarget tgt) {
            tgt.writeU32(index);
        }

        public void writeTo(ResTarget tgt, long newIndex) {
            tgt.writeU32(newIndex);
        }

        @Override
        public String toString() {
            return "ResStringPoolRef{" +
                    ", index=" + index +
                    ", val=" +
                    lookup() +
                    '}';
        }

        public String lookup() {
            return (4294967295L == index ? "<NONE>" : stringPool.lookup(index));
        }
    }

    private class UnknownResource implements ResChunk {
        private ResChunkHeader header;
        private long contentDataSize;

        public UnknownResource(ResChunkHeader header, ResSource src) {
            long startOfContent = src.position();
            this.header = header;
            this.contentDataSize = header.size - (startOfContent - header.chunkOriginalStart);
        }

        @Override
        public void writeTo(ResTarget tgt) {
            header.writeTo(tgt);
            src.position(header.chunkOriginalStart);
            new ResChunkHeader(src);
            src.copyTo(contentDataSize, tgt);
        }

        @Override
        public String toString() {
            return "UnknownResource{" +
                    "header=" + header +
                    ", contentDataSize=" + contentDataSize +
                    '}';
        }
    }

    private class ResXmlTreeNodeHeader {
        private ResChunkHeader header;
        private long sourceLineNumber; // u32
        private ResStringPoolRef comment;

        public ResXmlTreeNodeHeader(ResChunkHeader header, ResSource src) {
            this.header = header;
            this.sourceLineNumber = src.readU32();
            this.comment = new ResStringPoolRef(src);
            src.position(header.chunkOriginalStart + header.headerSize);
        }

        public void writeTo(ResTarget tgt) {
            header.writeTo(tgt);
            tgt.writeU32(sourceLineNumber);
            comment.writeTo(tgt);
        }

        @Override
        public String toString() {
            return "ResXmlTreeNodeHeader{" +
                    "header=" + header +
                    ", sourceLineNumber=" + sourceLineNumber +
                    ", comment=" + comment +
                    '}';
        }
    }

    private class ResValue {
        private int size; // u16
        private int reserved; // u8
        private int type; // u8
        private long data; // u32

        private ResValue(ResSource src) {
            this.size = src.readU16();
            this.reserved = src.readU8();
            this.type = src.readU8();
            this.data = src.readU32();
        }

        public void writeTo(ResTarget tgt) {
            tgt.writeU16(size);
            tgt.writeU8(reserved);
            tgt.writeU8(type);
            tgt.writeU32(data);
        }

        public void writeToWithNewVersionCode(ResTarget tgt) {
            if (type != 0x10) {
                throw new IllegalStateException();
            }
            tgt.writeU16(size);
            tgt.writeU8(reserved);
            tgt.writeU8(type);
            tgt.writeU32(AndroidManifest.this.versionCode);
        }

        public long writeToWithNewVersionIndex(ResTarget tgt, long newIndex) {
            if (type != 3) {
                throw new IllegalStateException();
            }
            tgt.writeU16(size);
            tgt.writeU8(reserved);
            tgt.writeU8(type);
            tgt.writeU32(newIndex);
            return newIndex;
        }

        @Override
        public String toString() {
            String val = "";
            switch (type) {
                case 3:
                    val = "(str:" + stringPool.lookup(data) + ")";
                    break;
                case 0x10:
                    val = "(dec:" + Long.toString(data) + ")";
            }
            return "ResValue{" +
                    "size=" + size +
                    ", reserved=" + reserved +
                    ", type=" + type +
                    ", data=" + data + val +
                    '}';
        }

        public String asString() {
            if (3 != type) {
                throw new IllegalStateException();
            }
            return stringPool.lookup(data);
        }

        public int asInt() {
            if (0x10 != type) {
                throw new IllegalStateException();
            }
            return (int) this.data;
        }
    }

    private class ResXmlAttribute {
        private ResStringPoolRef namespace;
        private ResStringPoolRef name;
        private ResStringPoolRef rawValue;
        private ResValue typedValue;
        private boolean isVersionCode = false;
        private boolean isVersionName = false;

        private ResXmlAttribute(ResSource src) {
            this.namespace = new ResStringPoolRef(src);
            this.name = new ResStringPoolRef(src);
            this.rawValue = new ResStringPoolRef(src);
            this.typedValue = new ResValue(src);
            
            if ("http://schemas.android.com/apk/res/android".equals(this.namespace.lookup())) {
                if (-1 == versionCode && "versionCode".equals(name.lookup())) {
                    versionCode = typedValue.asInt();
                    isVersionCode = true;
                } else if (null == versionName && "versionName".equals(name.lookup())) {
                    versionName = typedValue.asString();
                    isVersionName = true;
                }
            }
        }

        public void writeTo(ResTarget tgt) {
            this.namespace.writeTo(tgt);
            this.name.writeTo(tgt);
            if (isVersionName && versionNameChanged) {
                long newIndex = stringPool.getIndexOfNewVersionName();
                this.rawValue.writeTo(tgt, newIndex);
                typedValue.writeToWithNewVersionIndex(tgt, newIndex);
            } else if (isVersionCode && versionCodeChanged) {
                this.rawValue.writeTo(tgt);
                typedValue.writeToWithNewVersionCode(tgt);
            } else {
                this.rawValue.writeTo(tgt);
                typedValue.writeTo(tgt);
            }
        }
        
        @Override
        public String toString() {
            return "\n\tResXmlAttribute{" +
                    "namespace=" + namespace +
                    ", name=" + name +
                    ", rawValue=" + rawValue +
                    ", typedValue=" + typedValue +
                    '}';
        }
    }

    private class ResXmlStartElement implements ResChunk {
        private ResXmlTreeNodeHeader header;
        private ResStringPoolRef namespace;
        private ResStringPoolRef name;
        private int attributeStart; // u16
        private int attributeSize; // u16
        private int attributeCount; // u16
        private int idAttributeIndex; // u16
        private int classAttributeIndex; // u16
        private int styleAttributeIndex; // u16
        private ResXmlAttribute[] attributes;

        @Override
        public void writeTo(ResTarget tgt) {
            header.writeTo(tgt);
            namespace.writeTo(tgt);
            name.writeTo(tgt);
            tgt.writeU16(this.attributeStart);
            tgt.writeU16(this.attributeSize);
            tgt.writeU16(this.attributeCount);
            tgt.writeU16(this.idAttributeIndex);
            tgt.writeU16(this.classAttributeIndex);
            tgt.writeU16(this.styleAttributeIndex);
            for (int i = 0; i < this.attributeCount; i++) {
                this.attributes[i].writeTo(tgt);
            }
        }

        public ResXmlStartElement(ResChunkHeader chunkHeader, ResSource src) {
            this.header = new ResXmlTreeNodeHeader(chunkHeader, src);
            this.namespace = new ResStringPoolRef(src);
            this.name = new ResStringPoolRef(src);
            this.attributeStart = src.readU16();
            this.attributeSize = src.readU16();
            this.attributeCount = src.readU16();
            this.idAttributeIndex = src.readU16();
            this.classAttributeIndex = src.readU16();
            this.styleAttributeIndex = src.readU16();

            this.attributes = new ResXmlAttribute[this.attributeCount];
            for (int i = 0; i < this.attributeCount; i++) {
                this.attributes[i] = new ResXmlAttribute(src);
            }
        }

        @Override
        public String toString() {
            return "ResXmlStartElement{" +
                    "header=" + header +
                    ", namespace=" + namespace +
                    ", name=" + name +
                    ", attributeStart=" + attributeStart +
                    ", attributeSize=" + attributeSize +
                    ", attributeCount=" + attributeCount +
                    ", idAttributeIndex=" + idAttributeIndex +
                    ", classAttributeIndex=" + classAttributeIndex +
                    ", styleAttributeIndex=" + styleAttributeIndex +
                    ", attributes=" + Arrays.toString(attributes) +
                    '}';
        }
    }

}
