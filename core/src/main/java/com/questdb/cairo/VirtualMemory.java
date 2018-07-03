/*******************************************************************************
 *    ___                  _   ____  ____
 *   / _ \ _   _  ___  ___| |_|  _ \| __ )
 *  | | | | | | |/ _ \/ __| __| | | |  _ \
 *  | |_| | |_| |  __/\__ \ |_| |_| | |_) |
 *   \__\_\\__,_|\___||___/\__|____/|____/
 *
 * Copyright (C) 2014-2018 Appsicle
 *
 * This program is free software: you can redistribute it and/or  modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 ******************************************************************************/

package com.questdb.cairo;

import com.questdb.std.*;
import com.questdb.std.str.AbstractCharSequence;
import sun.nio.ch.DirectBuffer;

import java.io.Closeable;
import java.nio.ByteBuffer;

public class VirtualMemory implements Closeable {
    private static final int STRING_LENGTH_BYTES = 4;
    protected final LongList pages = new LongList(4, 0);
    private final ByteSequenceView bsview = new ByteSequenceView();
    private final CharSequenceView csview = new CharSequenceView();
    private final CharSequenceView csview2 = new CharSequenceView();
    private long pageSize;
    private int bits;
    private long mod;
    private long appendPointer = -1;
    private long pageHi = -1;
    private long pageLo = -1;
    private long baseOffset = 1;
    private long roOffsetLo = 0;
    private long roOffsetHi = 0;
    private long absolutePointer;

    public VirtualMemory(long pageSize) {
        this();
        setPageSize(pageSize);
    }

    protected VirtualMemory() {
    }

    public static int getStorageLength(CharSequence s) {
        if (s == null) {
            return STRING_LENGTH_BYTES;
        }

        return STRING_LENGTH_BYTES + s.length() * 2;
    }

    public long addressOf(long offset) {
        if (roOffsetLo < offset && offset < roOffsetHi) {
            return absolutePointer + offset;
        }
        return addressOf0(offset);
    }

    public void clearHotPage() {
        roOffsetLo = roOffsetHi = 0;
    }

    @Override
    public void close() {
        clearPages();
        appendPointer = -1;
        pageHi = -1;
        pageLo = -1;
        baseOffset = 1;
        clearHotPage();
    }

    public final long getAppendOffset() {
        return baseOffset + appendPointer;
    }

    public final BinarySequence getBin(long offset) {
        final long len = getLong(offset);
        if (len == -1) {
            return null;
        }
        return bsview.of(offset + 8, len);
    }

    public final long getBinLen(long offset) {
        return getLong(offset);
    }

    public boolean getBool(long offset) {
        return getByte(offset) == 1;
    }

    public final byte getByte(long offset) {
        if (roOffsetLo < offset && offset < roOffsetHi - 1) {
            return Unsafe.getUnsafe().getByte(absolutePointer + offset);
        }
        return getByte0(offset);
    }

    public final char getChar(long offset) {
        if (roOffsetLo < offset && offset < roOffsetHi - 2) {
            return Unsafe.getUnsafe().getChar(absolutePointer + offset);
        }
        return getChar0(offset);
    }

    public final double getDouble(long offset) {
        if (roOffsetLo < offset && offset < roOffsetHi - 8) {
            return Unsafe.getUnsafe().getDouble(absolutePointer + offset);
        }
        return getDouble0(offset);
    }

    public final float getFloat(long offset) {
        if (roOffsetLo < offset && offset < roOffsetHi - 4) {
            return Unsafe.getUnsafe().getFloat(absolutePointer + offset);
        }
        return getFloat0(offset);
    }

    public final int getInt(long offset) {
        if (roOffsetLo < offset && offset < roOffsetHi - 4) {
            return Unsafe.getUnsafe().getInt(absolutePointer + offset);
        }
        return getInt0(offset);
    }

    public long getLong(long offset) {
        if (roOffsetLo < offset && offset < roOffsetHi - 8) {
            return Unsafe.getUnsafe().getLong(absolutePointer + offset);
        }
        return getLong0(offset);
    }

    public final short getShort(long offset) {
        if (roOffsetLo < offset && offset < roOffsetHi - 2) {
            return Unsafe.getUnsafe().getShort(absolutePointer + offset);
        }
        return getShort0(offset);
    }

    public final CharSequence getStr(long offset) {
        return getStr0(offset, csview);
    }

    public final CharSequence getStr0(long offset, CharSequenceView view) {
        final int len = getInt(offset);
        if (len == TableUtils.NULL_LEN) {
            return null;
        }

        if (len == 0) {
            return "";
        }

        return view.of(offset + STRING_LENGTH_BYTES, len);
    }

    public final CharSequence getStr2(long offset) {
        return getStr0(offset, csview2);
    }

    public final int getStrLen(long offset) {
        return getInt(offset);
    }

    public long hash(long offset, long size) {
        if (roOffsetLo < offset && offset < roOffsetHi - size) {
            long n = size - (size % 8);
            long address = absolutePointer + offset;

            long h = 179426491L;
            for (long i = 0; i < n; i += 8) {
                h = (h << 5) - h + Unsafe.getUnsafe().getLong(address + i);
            }

            for (; n < size; n++) {
                h = (h << 5) - h + Unsafe.getUnsafe().getByte(address + n);
            }
            return h;
        }

        return hashSlow(offset, size);
    }

    /**
     * Updates append pointer with address for the given offset. All put* functions will be
     * appending from this offset onwards effectively overwriting data. Size of virtual memory remains
     * unaffected until the moment memory has to be extended.
     *
     * @param offset position from 0 in virtual memory.
     */
    public void jumpTo(long offset) {
        assert offset > -1;
        final long p = offset - baseOffset;
        if (p > pageLo && p < pageHi) {
            appendPointer = p;
        } else {
            jumpTo0(offset);
        }
    }

    public long pageRemaining(long offset) {
        return getPageSize(pageIndex(offset)) - offsetInPage(offset);
    }

    public final long putBin(ByteBuffer buf) {
        if (buf instanceof DirectBuffer) {
            int pos = buf.position();
            int len = buf.remaining();
            buf.position(pos + len);
            return putBin(ByteBuffers.getAddress(buf) + pos, len);
        }
        return putBin0(buf);
    }

    public final long putBin(BinarySequence value) {
        final long offset = getAppendOffset();
        if (value == null) {
            putLong(TableUtils.NULL_LEN);
        } else {
            final long len = value.length();
            putLong(len);
            final long remaining = pageHi - appendPointer;
            if (len < remaining) {
                putBinSequence(value, 0, len);
                appendPointer += len;
            } else {
                putBin0(value, len, remaining);
            }
        }
        return offset;
    }

    public final long putBin(long from, long len) {
        final long offset = getAppendOffset();
        putLong(len > 0 ? len : TableUtils.NULL_LEN);
        if (len < 1) {
            return offset;
        }

        if (len < pageHi - appendPointer) {
            Unsafe.getUnsafe().copyMemory(from, appendPointer, len);
            appendPointer += len;
        } else {
            putBinSlit(from, len);
        }

        return offset;
    }

    public void putBool(boolean value) {
        putByte((byte) (value ? 1 : 0));
    }

    public void putBool(long offset, boolean value) {
        putByte(offset, (byte) (value ? 1 : 0));
    }

    public final void putByte(long offset, byte value) {
        if (roOffsetLo < offset && offset < roOffsetHi - 1) {
            Unsafe.getUnsafe().putByte(absolutePointer + offset, value);
        } else {
            putByteRnd(offset, value);
        }
    }

    public void putByte(byte b) {
        if (pageHi == appendPointer) {
            pageAt(getAppendOffset() + 1);
        }
        Unsafe.getUnsafe().putByte(appendPointer++, b);
    }

    public void putDouble(long offset, double value) {
        if (roOffsetLo < offset && offset < roOffsetHi - 8) {
            Unsafe.getUnsafe().putDouble(absolutePointer + offset, value);
        } else {
            putDoubleBytes(offset, value);
        }
    }

    public final void putDouble(double value) {
        if (pageHi - appendPointer > 7) {
            Unsafe.getUnsafe().putDouble(appendPointer, value);
            appendPointer += 8;
        } else {
            putDoubleBytes(value);
        }
    }

    public void putFloat(long offset, float value) {
        if (roOffsetLo < offset && offset < roOffsetHi - 4) {
            Unsafe.getUnsafe().putFloat(absolutePointer + offset, value);
        } else {
            putFloatBytes(offset, value);
        }
    }

    public final void putFloat(float value) {
        if (pageHi - appendPointer > 3) {
            Unsafe.getUnsafe().putFloat(appendPointer, value);
            appendPointer += 4;
        } else {
            putFloatBytes(value);
        }
    }

    public void putInt(long offset, int value) {
        if (roOffsetLo < offset && offset < roOffsetHi - 4) {
            Unsafe.getUnsafe().putInt(absolutePointer + offset, value);
        } else {
            putIntBytes(offset, value);
        }
    }

    public final void putInt(int value) {
        if (pageHi - appendPointer > 3) {
            Unsafe.getUnsafe().putInt(appendPointer, value);
            appendPointer += 4;
        } else {
            putIntBytes(value);
        }
    }

    public void putLong(long offset, long value) {
        if (roOffsetLo < offset && offset < roOffsetHi - 8) {
            Unsafe.getUnsafe().putLong(absolutePointer + offset, value);
        } else {
            putLongBytes(offset, value);
        }
    }

    public final void putLong(long value) {
        if (pageHi - appendPointer > 7) {
            Unsafe.getUnsafe().putLong(appendPointer, value);
            appendPointer += 8;
        } else {
            putLongBytes(value);
        }
    }

    public final long putNullBin() {
        final long offset = getAppendOffset();
        putLong(TableUtils.NULL_LEN);
        return offset;
    }

    public final long putNullStr() {
        final long offset = getAppendOffset();
        putInt(TableUtils.NULL_LEN);
        return offset;
    }

    public final void putNullStr(long offset) {
        putInt(offset, TableUtils.NULL_LEN);
    }

    public void putShort(long offset, short value) {
        if (roOffsetLo < offset && offset < roOffsetHi - 2) {
            Unsafe.getUnsafe().putShort(absolutePointer + offset, value);
        } else {
            putShortBytes(offset, value);
        }
    }

    public final void putShort(short value) {
        if (pageHi - appendPointer > 1) {
            Unsafe.getUnsafe().putShort(appendPointer, value);
            appendPointer += 2;
        } else {
            putShortBytes(value);
        }
    }

    public final long putStr(CharSequence value) {
        return value == null ? putNullStr() : putStr0(value, 0, value.length());
    }

    public final long putStr(CharSequence value, int pos, int len) {
        if (value == null) {
            return putNullStr();
        }
        return putStr0(value, pos, len);
    }

    public void putStr(long offset, CharSequence value) {
        if (value == null) {
            putNullStr(offset);
        } else {
            putStr(offset, value, 0, value.length());
        }
    }

    public void putStr(long offset, CharSequence value, int pos, int len) {
        putInt(offset, len);
        if (roOffsetLo < offset && offset < roOffsetHi - len * 2 - 4) {
            copyStrChars(value, pos, len, absolutePointer + offset + 4);
        } else {
            putStrSplit(offset + 4, value, pos, len);
        }
    }

    /**
     * Skips given number of bytes. Same as logically appending 0-bytes. Advantage of this method is that
     * no memory write takes place.
     *
     * @param bytes number of bytes to skip
     */
    public void skip(long bytes) {
        assert bytes >= 0;
        if (pageHi - appendPointer > bytes) {
            appendPointer += bytes;
        } else {
            skip0(bytes);
        }
    }

    public void zero() {
        for (int i = 0, n = pages.size(); i < n; i++) {
            long address = pages.getQuick(i);
            if (address == 0) {
                address = allocateNextPage(i);
                pages.setQuick(i, address);
            }
            Unsafe.getUnsafe().setMemory(address, pageSize, (byte) 0);
        }
    }

    private static void copyStrChars(CharSequence value, int pos, int len, long address) {
        for (int i = 0; i < len; i++) {
            char c = value.charAt(i + pos);
            Unsafe.getUnsafe().putChar(address + 2 * i, c);
        }
    }

    private long addressOf0(long offset) {
        return computeHotPage(pageIndex(offset)) + offsetInPage(offset);
    }

    protected long allocateNextPage(int page) {
        return Unsafe.malloc(getMapPageSize());
    }

    protected long cachePageAddress(int index, long address) {
        pages.extendAndSet(index, address);
        return address;
    }

    private void clearPages() {
        int n = pages.size();
        if (n > 0) {
            for (int i = 0; i < n; i++) {
                release(i, pages.getQuick(i));
            }
        }
        pages.erase();
    }

    /**
     * Computes boundaries of read-only memory page to enable fast-path check of offsets
     */
    private long computeHotPage(int page) {
        long pageAddress = getPageAddress(page);
        assert pageAddress != 0;
        roOffsetLo = pageOffset(page) - 1;
        roOffsetHi = roOffsetLo + getPageSize(page) + 1;
        absolutePointer = pageAddress - roOffsetLo - 1;
        return pageAddress;
    }

    private void copyBufBytes(ByteBuffer buf, int pos, int len) {
        for (int i = 0; i < len; i++) {
            byte c = buf.get(pos + i);
            Unsafe.getUnsafe().putByte(appendPointer + i, c);
        }
    }

    protected void ensurePagesListCapacity(long size) {
        pages.ensureCapacity(pageIndex(size) + 1);
    }

    private byte getByte0(long offset) {
        return Unsafe.getUnsafe().getByte(computeHotPage(pageIndex(offset)) + offsetInPage(offset));
    }

    private char getChar0(long offset) {
        int page = pageIndex(offset);
        long pageOffset = offsetInPage(offset);
        final long pageSize = getPageSize(page);

        if (pageSize - pageOffset > 1) {
            return Unsafe.getUnsafe().getChar(computeHotPage(page) + pageOffset);
        }

        return getCharBytes(page, pageOffset, pageSize);
    }

    char getCharBytes(int page, long pageOffset, long pageSize) {
        char value = 0;
        long pageAddress = getPageAddress(page);

        for (int i = 0; i < 2; i++) {
            if (pageOffset == pageSize) {
                pageAddress = getPageAddress(++page);
                pageOffset = 0;
            }
            char b = (char) (Unsafe.getUnsafe().getByte(pageAddress + pageOffset++));
            value = (char) ((b << (8 * i)) | value);
        }

        return value;
    }

    private double getDouble0(long offset) {
        int page = pageIndex(offset);
        long pageOffset = offsetInPage(offset);
        final long pageSize = getPageSize(page);

        if (pageSize - pageOffset > 7) {
            return Unsafe.getUnsafe().getDouble(computeHotPage(page) + pageOffset);
        }
        return getDoubleBytes(page, pageOffset, pageSize);
    }

    double getDoubleBytes(int page, long pageOffset, long pageSize) {
        return Double.longBitsToDouble(getLongBytes(page, pageOffset, pageSize));
    }

    private float getFloat0(long offset) {
        int page = pageIndex(offset);
        long pageOffset = offsetInPage(offset);

        if (getPageSize(page) - pageOffset > 3) {
            return Unsafe.getUnsafe().getFloat(computeHotPage(page) + pageOffset);
        }
        return getFloatBytes(page, pageOffset);
    }

    float getFloatBytes(int page, long pageOffset) {
        return Float.intBitsToFloat(getIntBytes(page, pageOffset));
    }

    private int getInt0(long offset) {
        int page = pageIndex(offset);
        long pageOffset = offsetInPage(offset);

        if (getPageSize(page) - pageOffset > 3) {
            return Unsafe.getUnsafe().getInt(computeHotPage(page) + pageOffset);
        }
        return getIntBytes(page, pageOffset);
    }

    int getIntBytes(int page, long pageOffset) {
        int value = 0;
        long pageAddress = getPageAddress(page);
        final long pageSize = getPageSize(page);

        for (int i = 0; i < 4; i++) {
            if (pageOffset == pageSize) {
                pageAddress = getPageAddress(++page);
                pageOffset = 0;
            }
            int b = Unsafe.getUnsafe().getByte(pageAddress + pageOffset++) & 0xff;
            value = (b << (8 * i)) | value;
        }
        return value;
    }

    private long getLong0(long offset) {
        int page = pageIndex(offset);
        long pageOffset = offsetInPage(offset);
        final long pageSize = getPageSize(page);

        if (pageSize - pageOffset > 7) {
            return Unsafe.getUnsafe().getLong(computeHotPage(page) + pageOffset);
        }
        return getLongBytes(page, pageOffset, pageSize);
    }

    long getLongBytes(int page, long pageOffset, long pageSize) {
        long value = 0;
        long pageAddress = getPageAddress(page);

        for (int i = 0; i < 8; i++) {
            if (pageOffset == pageSize) {
                pageAddress = getPageAddress(++page);
                pageOffset = 0;
            }
            long b = Unsafe.getUnsafe().getByte(pageAddress + pageOffset++) & 0xff;
            value = (b << (8 * i)) | value;
        }
        return value;
    }

    protected long getMapPageSize() {
        return pageSize;
    }

    /**
     * Provides address of page for read operations. Memory writes never call this.
     *
     * @param page page index, starting from 0
     * @return native address of page
     */
    protected long getPageAddress(int page) {
        return pages.getQuick(page);
    }

    protected long getPageSize(int page) {
        return getMapPageSize();
    }

    private short getShort0(long offset) {
        int page = pageIndex(offset);
        long pageOffset = offsetInPage(offset);
        final long pageSize = getPageSize(page);

        if (pageSize - pageOffset > 1) {
            return Unsafe.getUnsafe().getShort(computeHotPage(page) + pageOffset);
        }

        return getShortBytes(page, pageOffset, pageSize);
    }

    short getShortBytes(int page, long pageOffset, long pageSize) {
        short value = 0;
        long pageAddress = getPageAddress(page);

        for (int i = 0; i < 2; i++) {
            if (pageOffset == pageSize) {
                pageAddress = getPageAddress(++page);
                assert pageAddress != 0;
                pageOffset = 0;
            }
            short b = (short) (Unsafe.getUnsafe().getByte(pageAddress + pageOffset++) & 0xff);
            value = (short) ((b << (8 * i)) | value);
        }

        return value;
    }

    private long hashSlow(long offset, long size) {
        long n = size - (size & 7);
        long h = 179426491L;
        for (long i = 0; i < n; i += 8) {
            h = (h << 5) - h + getLong(offset + i);
        }

        for (; n < size; n++) {
            h = (h << 5) - h + getByte(offset + n);
        }
        return h;
    }

    private void jumpTo0(long offset) {
        int page = pageIndex(offset);
        pageLo = mapWritePage(page);
        pageHi = pageLo + getPageSize(page);
        baseOffset = pageOffset(page + 1) - pageHi;
        appendPointer = pageLo + offsetInPage(offset);
        pageLo--;
    }

    private long mapRandomWritePage(long offset) {
        int page = pageIndex(offset);
        long pageAddress = mapWritePage(page);
        assert pageAddress != 0;
        roOffsetLo = pageOffset(page) - 1;
        roOffsetHi = roOffsetLo + getPageSize(page) + 1;
        absolutePointer = pageAddress - roOffsetLo - 1;
        return pageAddress;
    }

    protected long mapWritePage(int page) {
        long address;
        if (page < pages.size()) {
            address = pages.getQuick(page);
            if (address != 0) {
                return address;
            }
        }
        return cachePageAddress(page, allocateNextPage(page));
    }

    long offsetInPage(long offset) {
        return offset & mod;
    }

    private void pageAt(long offset) {
        int page = pageIndex(offset);
        updateLimits(page, mapWritePage(page));
    }

    protected final int pageIndex(long offset) {
        return (int) (offset >> bits);
    }

    protected final long pageOffset(int page) {
        return ((long) page << bits);
    }

    private void putBin0(BinarySequence value, long len, long remaining) {
        long pos = 0;
        do {
            putBinSequence(value, pos, remaining);
            pos += remaining;
            len -= remaining;

            pageAt(baseOffset + pageHi);
            remaining = pageHi - appendPointer;
            if (len < remaining) {
                putBinSequence(value, pos, len);
                appendPointer += len;
                break;
            }
        } while (true);
    }

    private long putBin0(ByteBuffer buf) {
        final long offset = getAppendOffset();

        if (buf == null) {
            putLong(TableUtils.NULL_LEN);
            return offset;
        }

        int pos = buf.position();
        int len = buf.remaining();
        buf.position(pos + len);

        putLong(len);

        if (len < pageHi - appendPointer) {
            copyBufBytes(buf, pos, len);
            appendPointer += len;
        } else {
            putBinSplit(buf, pos, len);
        }

        return offset;
    }

    private void putBinSequence(BinarySequence value, long pos, long len) {
        long offset = 0L;
        while (true) {
            long copied = value.copyTo(appendPointer + offset, pos, len);
            if (copied == len) {
                break;
            }
            len -= copied;
            pos += copied;
            offset += copied;
        }
    }

    private void putBinSlit(long start, long len) {
        do {
            int half = (int) (pageHi - appendPointer);
            if (len <= half) {
                Unsafe.getUnsafe().copyMemory(start, appendPointer, len);
                appendPointer += len;
                break;
            }

            Unsafe.getUnsafe().copyMemory(start, appendPointer, half);
            pageAt(getAppendOffset() + half);  // +1?
            len -= half;
            start += half;
        } while (true);
    }

    private void putBinSplit(ByteBuffer buf, int pos, int len) {
        int start = pos;
        do {
            int half = (int) (pageHi - appendPointer);

            if (len <= half) {
                copyBufBytes(buf, start, len);
                appendPointer += len;
                break;
            }

            copyBufBytes(buf, start, half);
            pageAt(getAppendOffset() + half); // +1?
            len -= half;
            start += half;
        } while (true);
    }

    private void putByteRnd(long offset, byte value) {
        Unsafe.getUnsafe().putByte(mapRandomWritePage(offset) + offsetInPage(offset), value);
    }

    void putDoubleBytes(double value) {
        putLongBytes(Double.doubleToLongBits(value));
    }

    void putDoubleBytes(long offset, double value) {
        putLongBytes(offset, Double.doubleToLongBits(value));
    }

    void putFloatBytes(float value) {
        putIntBytes(Float.floatToIntBits(value));
    }

    void putFloatBytes(long offset, float value) {
        putIntBytes(offset, Float.floatToIntBits(value));
    }

    void putIntBytes(int value) {
        putByte((byte) (value & 0xff));
        putByte((byte) ((value >> 8) & 0xff));
        putByte((byte) ((value >> 16) & 0xff));
        putByte((byte) ((value >> 24) & 0xff));
    }

    void putIntBytes(long offset, int value) {
        putByte(offset, (byte) (value & 0xff));
        putByte(offset + 1, (byte) ((value >> 8) & 0xff));
        putByte(offset + 2, (byte) ((value >> 16) & 0xff));
        putByte(offset + 3, (byte) ((value >> 24) & 0xff));
    }

    void putLongBytes(long value) {
        putByte((byte) (value & 0xffL));
        putByte((byte) ((value >> 8) & 0xffL));
        putByte((byte) ((value >> 16) & 0xffL));
        putByte((byte) ((value >> 24) & 0xffL));
        putByte((byte) ((value >> 32) & 0xffL));
        putByte((byte) ((value >> 40) & 0xffL));
        putByte((byte) ((value >> 48) & 0xffL));
        putByte((byte) ((value >> 56) & 0xffL));
    }

    void putLongBytes(long offset, long value) {
        putByte(offset, (byte) (value & 0xffL));
        putByte(offset + 1, (byte) ((value >> 8) & 0xffL));
        putByte(offset + 2, (byte) ((value >> 16) & 0xffL));
        putByte(offset + 3, (byte) ((value >> 24) & 0xffL));
        putByte(offset + 4, (byte) ((value >> 32) & 0xffL));
        putByte(offset + 5, (byte) ((value >> 40) & 0xffL));
        putByte(offset + 6, (byte) ((value >> 48) & 0xffL));
        putByte(offset + 7, (byte) ((value >> 56) & 0xffL));
    }

    void putShortBytes(short value) {
        putByte((byte) (value & 0xff));
        putByte((byte) ((value >> 8) & 0xff));
    }

    void putShortBytes(long offset, short value) {
        putByte(offset, (byte) (value & 0xff));
        putByte(offset + 1, (byte) ((value >> 8) & 0xff));
    }

    private void putSplitChar(char c) {
        Unsafe.getUnsafe().putByte(pageHi - 1, (byte) c);
        pageAt(baseOffset + pageHi);
        Unsafe.getUnsafe().putByte(appendPointer++, (byte) (c >> 8));
    }

    private long putStr0(CharSequence value, int pos, int len) {
        final long offset = getAppendOffset();
        putInt(len);
        if (pageHi - appendPointer < len * 2) {
            putStrSplit(value, pos, len);
        } else {
            copyStrChars(value, pos, len, appendPointer);
            appendPointer += len * 2;
        }
        return offset;
    }

    private void putStrSplit(long offset, CharSequence value, int pos, int len) {
        int start = pos;
        do {
            int half = (int) ((roOffsetHi - offset) / 2);

            if (len <= half) {
                copyStrChars(value, start, len, absolutePointer + offset);
                break;
            }

            copyStrChars(value, start, half, absolutePointer + offset);
            offset += half * 2;
            if (offset < roOffsetHi) {
                char c = value.charAt(start + half);
                putByte(offset, (byte) c);
                putByte(offset + 1, (byte) (c >> 8));
                offset += 2;
                half++;
            } else {
                mapRandomWritePage(offset);
            }

            len -= half;
            start += half;
        } while (true);
    }

    private void putStrSplit(CharSequence value, int pos, int len) {
        int start = pos;
        do {
            int half = (int) ((pageHi - appendPointer) / 2);

            if (len <= half) {
                copyStrChars(value, start, len, appendPointer);
                appendPointer += len * 2;
                break;
            }

            copyStrChars(value, start, half, appendPointer);

            if (half * 2 < pageHi - appendPointer) {
                putSplitChar(value.charAt(start + half++));
            } else {
                pageAt(getAppendOffset() + half * 2);
            }

            len -= half;
            start += half;
        } while (true);
    }

    protected void release(int page, long address) {
        if (address != 0) {
            Unsafe.free(address, getPageSize(page));
        }
    }

    protected final void setPageSize(long pageSize) {
        clearPages();
        this.pageSize = Numbers.ceilPow2(pageSize);
        this.bits = Numbers.msb(this.pageSize);
        this.mod = this.pageSize - 1;
        clearHotPage();
    }

    private void skip0(long bytes) {
        jumpTo(getAppendOffset() + bytes);
    }

    protected final void updateLimits(int page, long pageAddress) {
        pageLo = pageAddress - 1;
        pageHi = pageAddress + getPageSize(page);
        baseOffset = pageOffset(page + 1) - pageHi;
        this.appendPointer = pageAddress;
    }

    public class CharSequenceView extends AbstractCharSequence {
        private int len;
        private long offset;

        @Override
        public int length() {
            return len;
        }

        @Override
        public char charAt(int index) {
            return VirtualMemory.this.getChar(offset + index * 2);
        }

        CharSequenceView of(long offset, int len) {
            this.offset = offset;
            this.len = len;
            return this;
        }
    }

    private class ByteSequenceView implements BinarySequence {
        private long offset;
        private long len = -1;
        private long lastIndex = -1;
        private int page;
        private long pageAddress;
        private long pageOffset;
        private long pageSize;

        public byte byteAt(long index) {
            byte c;

            if (index == lastIndex + 1 && pageOffset < pageSize) {
                c = Unsafe.getUnsafe().getByte(pageAddress + pageOffset);
                pageOffset++;
            } else {
                c = updatePosAndGet(index);
            }
            lastIndex = index;
            return c;
        }

        @Override
        public long copyTo(long address, long start, long length) {
            long offset = this.offset + start;
            int page = pageIndex(offset);
            long pageSize = getPageSize(page);
            long pageAddress = getPageAddress(page);
            long offsetInPage = offsetInPage(offset);
            long len = Math.min(length, pageSize - offsetInPage);
            assert len > -1;
            long srcAddress = pageAddress + offsetInPage;
            Unsafe.getUnsafe().copyMemory(srcAddress, address, len);
            return len;
        }

        public long length() {
            return len;
        }

        ByteSequenceView of(long offset, long len) {
            this.offset = offset;
            this.len = len;
            this.lastIndex = -1;
            this.page = pageIndex(offset);
            this.pageSize = getPageSize(page);
            this.pageAddress = getPageAddress(page);
            this.pageOffset = offsetInPage(offset);
            return this;
        }

        private byte updatePosAndGet(long index) {
            byte c;
            long offset = this.offset + index;
            page = pageIndex(offset);
            pageSize = getPageSize(page);
            pageAddress = getPageAddress(page);
            pageOffset = offsetInPage(offset);
            c = Unsafe.getUnsafe().getByte(pageAddress + pageOffset);
            pageOffset++;
            return c;
        }
    }
}