/*******************************************************************************
 *    ___                  _   ____  ____
 *   / _ \ _   _  ___  ___| |_|  _ \| __ )
 *  | | | | | | |/ _ \/ __| __| | | |  _ \
 *  | |_| | |_| |  __/\__ \ |_| |_| | |_) |
 *   \__\_\\__,_|\___||___/\__|____/|____/
 *
 * Copyright (C) 2014-2017 Appsicle
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

import com.questdb.log.Log;
import com.questdb.log.LogFactory;
import com.questdb.std.Misc;
import com.questdb.std.Unsafe;
import com.questdb.std.str.Path;

import java.io.Closeable;

public class BitmapIndexWriter implements Closeable {
    private static final Log LOG = LogFactory.getLog(BitmapIndexWriter.class);
    private final ReadWriteMemory keyMem;
    private final ReadWriteMemory valueMem;
    private final int blockCapacity;
    private final int blockValueCountMod;
    private final Cursor cursor = new Cursor();
    private long valueMemSize;
    private long keyCount;
    private long seekValueCount;
    private long seekValueBlockOffset;
    private final BitmapIndexUtils.ValueBlockSeeker SEEKER = this::seek;

    public BitmapIndexWriter(CairoConfiguration configuration, Path path, CharSequence name, int valueBlockCapacity) {
        long pageSize = configuration.getFilesFacade().getMapPageSize();
        int plen = path.length();

        try {
            BitmapIndexUtils.keyFileName(path, name);

            boolean exists = configuration.getFilesFacade().exists(path);
            this.keyMem = new ReadWriteMemory(configuration.getFilesFacade(), path, pageSize);
            if (!exists) {
                initKeyMemory(this.keyMem, valueBlockCapacity);
            }

            long keyMemSize = this.keyMem.getAppendOffset();
            // check if key file header is present
            if (keyMemSize < BitmapIndexUtils.KEY_FILE_RESERVED) {
                LOG.error().$("file too short [corrupt] ").$(path).$();
                throw CairoException.instance(0).put("Index file too short: ").put(path);
            }

            // verify header signature
            if (this.keyMem.getByte(BitmapIndexUtils.KEY_RESERVED_OFFSET_SIGNATURE) != BitmapIndexUtils.SIGNATURE) {
                LOG.error().$("unknown format [corrupt] ").$(path).$();
                throw CairoException.instance(0).put("Unknown format: ").put(path);
            }

            // verify key count
            this.keyCount = this.keyMem.getLong(BitmapIndexUtils.KEY_RESERVED_OFFSET_KEY_COUNT);
            if (keyMemSize != keyMemSize()) {
                LOG.error().$("key count does not match file length [corrupt] of ").$(path).$(" [keyCount=").$(this.keyCount).$(']').$();
                throw CairoException.instance(0).put("Key count does not match file length of ").put(path);
            }

            // check if sequence is intact
            if (this.keyMem.getLong(BitmapIndexUtils.KEY_RESERVED_OFFSET_SEQUENCE_CHECK) != this.keyMem.getLong(BitmapIndexUtils.KEY_RESERVED_OFFSET_SEQUENCE)) {
                LOG.error().$("sequence mismatch [corrupt] at ").$(path).$();
                throw CairoException.instance(0).put("Sequence mismatch on ").put(path);
            }

            this.valueMemSize = this.keyMem.getLong(BitmapIndexUtils.KEY_RESERVED_OFFSET_VALUE_MEM_SIZE);

            BitmapIndexUtils.valueFileName(path.trimTo(plen), name);

            this.valueMem = new ReadWriteMemory(configuration.getFilesFacade(), path, pageSize);

            if (this.valueMem.getAppendOffset() != this.valueMemSize) {
                LOG.error().$("incorrect file size [corrupt] of ").$(path).$(" [expected=").$(this.valueMemSize).$(']').$();
                throw CairoException.instance(0).put("Incorrect file size of ").put(path);
            }

            // block value count is always a power of two
            // to calculate remainder we use faster 'x & (count-1)', which is equivalent to (x % count)
            this.blockValueCountMod = this.keyMem.getInt(BitmapIndexUtils.KEY_RESERVED_OFFSET_BLOCK_VALUE_COUNT) - 1;
            this.blockCapacity = (this.blockValueCountMod + 1) * 8 + BitmapIndexUtils.VALUE_BLOCK_FILE_RESERVED;
        } catch (CairoException e) {
            this.close();
            throw e;
        }
    }

    /**
     * Adds key-value pair to index. If key already exists, value is appended to end of list of existing values. Otherwise
     * new value list is associated with the key.
     * <p>
     * Index is updated atomically as far as concurrent reading is concerned. Please refer to notes on classes that
     * are responsible for reading bitmap indexes, such as {@link BitmapIndexBackwardReader}.
     *
     * @param key   int key
     * @param value long value
     */
    public void add(int key, long value) {
        assert key > -1 : "key must be positive integer: " + key;
        final long offset = BitmapIndexUtils.getKeyEntryOffset(key);
        if (key < keyCount) {
            // when key exists we have possible outcomes with regards to values
            // 1. last value block has space if value cell index is not the last in block
            // 2. value block is full and we have to allocate a new one
            // 3. value count is 0. This means key was created as byproduct of adding sparse key value
            // second option is supposed to be less likely because we attempt to
            // configure block capacity to accommodate as many values as possible
            long valueBlockOffset = keyMem.getLong(offset + BitmapIndexUtils.KEY_ENTRY_OFFSET_LAST_VALUE_BLOCK_OFFSET);
            long valueCount = keyMem.getLong(offset + BitmapIndexUtils.KEY_ENTRY_OFFSET_VALUE_COUNT);
            int valueCellIndex = (int) (valueCount & blockValueCountMod);
            if (valueCellIndex > 0) {
                // this is scenario #1: key exists and there is space in last block to add value
                // we don't need to allocate new block, just add value and update value count on key
                assert valueBlockOffset + blockCapacity <= valueMemSize;
                appendValue(offset, valueBlockOffset, valueCount, valueCellIndex, value);
            } else if (valueCount == 0) {
                // this is scenario #3: we are effectively adding a new key and creating new block
                initValueBlockAndStoreValue(offset, value);
            } else {
                // this is scenario #2: key exists but last block is full. We need to create new block and add value there
                assert valueBlockOffset + blockCapacity <= valueMemSize;
                addValueBlockAndStoreValue(offset, valueBlockOffset, valueCount, value);
            }
        } else {
            // This is a new key. Because index can have sparse keys whenever we think "key exists" we must deal
            // with holes left by this branch, which allocates new key. All key entries that have been
            // skipped during creation of new key will have been initialized with zeroes. This includes counts and
            // block offsets.
            initValueBlockAndStoreValue(offset, value);
            // here we also need to update key count
            // we don't just increment key count, in case this addition creates sparse key set
            updateKeyCount(key);
        }
    }

    @Override
    public void close() {
        if (keyMem != null) {
            keyMem.jumpTo(keyMemSize());
            Misc.free(keyMem);
        }

        if (valueMem != null) {
            valueMem.jumpTo(valueMemSize);
            Misc.free(valueMem);
        }
    }

    public BitmapIndexCursor getCursor(int key) {
        if (key < keyCount) {
            cursor.of(key);
            return cursor;
        }
        return BitmapIndexEmptyCursor.INSTANCE;
    }

    /**
     * Rolls values back. Removes values that are strictly greater than given maximum. Empty value blocks
     * will also be removed as well as blank space at end of value memory.
     *
     * @param maxValue maximum value allowed in index.
     */
    public void rollbackValues(long maxValue) {

        long maxValueBlockOffset = 0;
        for (int k = 0; k < keyCount; k++) {
            long offset = BitmapIndexUtils.getKeyEntryOffset(k);
            long valueCount = keyMem.getLong(offset + BitmapIndexUtils.KEY_ENTRY_OFFSET_VALUE_COUNT);

            // do we have anything for the key?
            if (valueCount > 0) {
                long blockOffset = keyMem.getLong(offset + BitmapIndexUtils.KEY_ENTRY_OFFSET_LAST_VALUE_BLOCK_OFFSET);
                BitmapIndexUtils.seekValueBlock(valueCount, blockOffset, valueMem, maxValue, blockValueCountMod, SEEKER);

                if (valueCount != seekValueCount || blockOffset != seekValueBlockOffset) {
                    // set new value count
                    keyMem.jumpTo(offset + BitmapIndexUtils.KEY_ENTRY_OFFSET_VALUE_COUNT);
                    keyMem.putLong(seekValueCount);

                    if (blockOffset != seekValueBlockOffset) {
                        Unsafe.getUnsafe().storeFence();
                        keyMem.skip(8);
                        keyMem.putLong(seekValueBlockOffset);
                        Unsafe.getUnsafe().storeFence();
                    } else {
                        // skip first and last offset
                        keyMem.skip(16);
                    }
                    keyMem.putLong(seekValueCount);
                }

                if (seekValueBlockOffset > maxValueBlockOffset) {
                    maxValueBlockOffset = seekValueBlockOffset;
                }
            }
        }
        valueMemSize = maxValueBlockOffset + blockCapacity;
        updateValueMemSize();
    }

    private void addValueBlockAndStoreValue(long offset, long valueBlockOffset, long valueCount, long value) {
        long newValueBlockOffset = allocateValueBlockAndStore(value);

        // update block linkage before we increase count
        // this is important to index readers, which will act on value count they read

        // we subtract 8 because we just written long value
        // update this block reference to previous block
        valueMem.jumpTo(valueMemSize - BitmapIndexUtils.VALUE_BLOCK_FILE_RESERVED);
        valueMem.putLong(valueBlockOffset);

        // update previous block' "next" block reference to this block
        valueMem.jumpTo(valueBlockOffset + blockCapacity - BitmapIndexUtils.VALUE_BLOCK_FILE_RESERVED + 8);
        valueMem.putLong(newValueBlockOffset);

        // update count and last value block offset for the key
        // in atomic fashion
        // we make sure count is always written _after_ new value block is added
        Unsafe.getUnsafe().storeFence();
        keyMem.jumpTo(offset);
        keyMem.putLong(valueCount + 1);
        Unsafe.getUnsafe().storeFence();

        // don't set first block offset here
        // it would have been done when this key was first created
        keyMem.skip(8);

        // write last block offset because it changed in this scenario
        keyMem.putLong(newValueBlockOffset);
        Unsafe.getUnsafe().storeFence();

        // write count check
        keyMem.putLong(valueCount + 1);
        Unsafe.getUnsafe().storeFence();

        // we are done adding value to new block of values
    }

    private long allocateValueBlockAndStore(long value) {
        long newValueBlockOffset = valueMemSize;

        // store our value
        valueMem.jumpTo(newValueBlockOffset);
        valueMem.putLong(value);

        valueMemSize += blockCapacity;

        // reserve memory for value block
        valueMem.jumpTo(valueMemSize);

        // must update value mem size in key memory header
        // so that index can be opened correctly next time it loads
        updateValueMemSize();
        return newValueBlockOffset;
    }

    private void appendValue(long offset, long valueBlockOffset, long valueCount, int valueCellIndex, long value) {
        // first set value
        valueMem.jumpTo(valueBlockOffset + valueCellIndex * 8);
        valueMem.putLong(value);

        Unsafe.getUnsafe().storeFence();

        // update count and last value block offset for the key
        // in atomic fashion
        keyMem.jumpTo(offset);
        keyMem.putLong(valueCount + 1);

        // don't change block offsets here
        keyMem.skip(16);

        // write count check
        keyMem.putLong(valueCount + 1);
    }

    static void initKeyMemory(VirtualMemory keyMem, int blockValueCount) {
        keyMem.putByte(BitmapIndexUtils.SIGNATURE);
        keyMem.putLong(1); // SEQUENCE
        Unsafe.getUnsafe().storeFence();
        keyMem.putLong(0); // VALUE MEM SIZE
        keyMem.putInt(blockValueCount); // BLOCK VALUE COUNT
        keyMem.putLong(0); // KEY COUNT
        Unsafe.getUnsafe().storeFence();
        keyMem.putLong(1); // SEQUENCE CHECK
        keyMem.skip(BitmapIndexUtils.KEY_FILE_RESERVED - keyMem.getAppendOffset());
    }

    private void initValueBlockAndStoreValue(long offset, long value) {
        long newValueBlockOffset = allocateValueBlockAndStore(value);

        // don't need to update linkage, value count is less than block size
        // index readers must not access linkage information in this case

        // now update key entry in atomic fashion
        // update count and last value block offset for the key
        // in atomic fashion
        Unsafe.getUnsafe().storeFence();
        keyMem.jumpTo(offset);
        keyMem.putLong(1);
        Unsafe.getUnsafe().storeFence();

        // first and last blocks are the same
        keyMem.putLong(newValueBlockOffset);
        keyMem.putLong(newValueBlockOffset);
        Unsafe.getUnsafe().storeFence();

        // write count check
        keyMem.putLong(1);
        Unsafe.getUnsafe().storeFence();
    }

    private long keyMemSize() {
        return this.keyCount * BitmapIndexUtils.KEY_ENTRY_SIZE + BitmapIndexUtils.KEY_FILE_RESERVED;
    }

    private void seek(long count, long offset) {
        this.seekValueCount = count;
        this.seekValueBlockOffset = offset;
    }

    private void updateKeyCount(int key) {
        keyCount = key + 1;

        // also write key count to header of key memory
        keyMem.jumpTo(BitmapIndexUtils.KEY_RESERVED_OFFSET_SEQUENCE);
        long seq = keyMem.getLong(BitmapIndexUtils.KEY_RESERVED_OFFSET_SEQUENCE) + 1;
        keyMem.putLong(seq);
        Unsafe.getUnsafe().storeFence();

        keyMem.jumpTo(BitmapIndexUtils.KEY_RESERVED_OFFSET_KEY_COUNT);
        keyMem.putLong(keyCount);
        Unsafe.getUnsafe().storeFence();

        keyMem.jumpTo(BitmapIndexUtils.KEY_RESERVED_OFFSET_SEQUENCE_CHECK);
        keyMem.putLong(seq);
    }

    private void updateValueMemSize() {
        keyMem.jumpTo(BitmapIndexUtils.KEY_RESERVED_OFFSET_SEQUENCE);
        long seq = keyMem.getLong(BitmapIndexUtils.KEY_RESERVED_OFFSET_SEQUENCE) + 1;
        keyMem.putLong(seq);
        Unsafe.getUnsafe().storeFence();

        keyMem.jumpTo(BitmapIndexUtils.KEY_RESERVED_OFFSET_VALUE_MEM_SIZE);
        keyMem.putLong(valueMemSize);
        keyMem.jumpTo(BitmapIndexUtils.KEY_RESERVED_OFFSET_SEQUENCE_CHECK);
        Unsafe.getUnsafe().storeFence();
        keyMem.putLong(seq);
    }

    private class Cursor implements BitmapIndexCursor {
        private long valueBlockOffset;
        private long valueCount;

        @Override
        public boolean hasNext() {
            return valueCount > 0;
        }

        @Override
        public long next() {
            long cellIndex = getValueCellIndex(--valueCount);
            long result = valueMem.getLong(valueBlockOffset + cellIndex * 8);
            if (cellIndex == 0 && valueCount > 0) {
                // we are at edge of block right now, next value will be in previous block
                jumpToPreviousValueBlock();
            }
            return result;
        }

        private long getPreviousBlock(long currentValueBlockOffset) {
            return valueMem.getLong(currentValueBlockOffset + blockCapacity - BitmapIndexUtils.VALUE_BLOCK_FILE_RESERVED);
        }

        private long getValueCellIndex(long absoluteValueIndex) {
            return absoluteValueIndex & blockValueCountMod;
        }

        private void jumpToPreviousValueBlock() {
            valueBlockOffset = getPreviousBlock(valueBlockOffset);
        }

        void of(int key) {
            assert key > -1 : "key must be positive integer: " + key;
            long offset = BitmapIndexUtils.getKeyEntryOffset(key);
            this.valueCount = keyMem.getLong(offset + BitmapIndexUtils.KEY_ENTRY_OFFSET_VALUE_COUNT);
            this.valueBlockOffset = keyMem.getLong(offset + BitmapIndexUtils.KEY_ENTRY_OFFSET_LAST_VALUE_BLOCK_OFFSET);
        }
    }
}
