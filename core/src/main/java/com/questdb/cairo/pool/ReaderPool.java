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

package com.questdb.cairo.pool;

import com.questdb.cairo.CairoConfiguration;
import com.questdb.cairo.CairoException;
import com.questdb.cairo.TableReader;
import com.questdb.cairo.pool.ex.EntryLockedException;
import com.questdb.cairo.pool.ex.EntryUnavailableException;
import com.questdb.cairo.pool.ex.PoolClosedException;
import com.questdb.common.PoolConstants;
import com.questdb.log.Log;
import com.questdb.log.LogFactory;
import com.questdb.std.ConcurrentHashMap;
import com.questdb.std.Unsafe;

import java.util.Arrays;
import java.util.Map;

public class ReaderPool extends AbstractPool implements ResourcePool<TableReader> {

    private static final Log LOG = LogFactory.getLog(ReaderPool.class);
    private static final long UNLOCKED = -1L;
    private static final long NEXT_STATUS = Unsafe.getFieldOffset(Entry.class, "nextStatus");
    private static final int ENTRY_SIZE = 32;
    private static final long LOCK_OWNER = Unsafe.getFieldOffset(Entry.class, "lockOwner");
    private static final int NEXT_OPEN = 0;
    private static final int NEXT_ALLOCATED = 1;
    private static final int NEXT_LOCKED = 2;
    private final ConcurrentHashMap<Entry> entries = new ConcurrentHashMap<>();
    private final int maxSegments;
    private final int maxEntries;

    public ReaderPool(CairoConfiguration configuration) {
        super(configuration, configuration.getInactiveReaderTTL());
        this.maxSegments = configuration.getReaderPoolSegments();
        this.maxEntries = maxSegments * ENTRY_SIZE;
    }

    @Override
    public TableReader get(CharSequence name) {

        checkClosed();

        Entry e = entries.get(name);

        long thread = Thread.currentThread().getId();

        if (e == null) {
            e = new Entry(0, clock.getTicks());
            Entry other = entries.putIfAbsent(name, e);
            if (other != null) {
                e = other;
            }
        }

        long lockOwner = e.lockOwner;

        if (lockOwner != UNLOCKED) {
            LOG.info().$('\'').$(name).$("' is locked [owner=").$(lockOwner).$(']').$();
            throw EntryLockedException.INSTANCE;
        }

        do {
            for (int i = 0; i < ENTRY_SIZE; i++) {
                if (Unsafe.cas(e.allocations, i, UNALLOCATED, thread)) {
                    // got lock, allocate if needed
                    R r = Unsafe.arrayGet(e.readers, i);
                    if (r == null) {

                        try {
                            LOG.info().$("open '").$(name).$("' [at=").$(e.index).$(':').$(i).$(']').$();
                            r = new R(this, e, i, name);
                        } catch (CairoException ex) {
                            Unsafe.arrayPutOrdered(e.allocations, i, UNALLOCATED);
                            throw ex;
                        }

                        Unsafe.arrayPut(e.readers, i, r);
                        notifyListener(thread, name, PoolListener.EV_CREATE, e.index, i);
                    } else {
                        r.reload();
                        notifyListener(thread, name, PoolListener.EV_GET, e.index, i);
                    }

                    if (isClosed()) {
                        Unsafe.arrayPut(e.readers, i, null);
                        r.goodby();
                        LOG.info().$('\'').$(name).$("' born free").$();
                        return r;
                    }

                    LOG.debug().$('\'').$(name).$("' is assigned [at=").$(e.index).$(':').$(i).$(", thread=").$(thread).$(']').$();
                    return r;
                }
            }

            LOG.debug().$("Thread ").$(thread).$(" is moving to entry ").$(e.index + 1).$();

            // all allocated, create next entry if possible
            if (Unsafe.getUnsafe().compareAndSwapInt(e, NEXT_STATUS, NEXT_OPEN, NEXT_ALLOCATED)) {
                LOG.debug().$("Thread ").$(thread).$(" allocated entry ").$(e.index + 1).$();
                e.next = new Entry(e.index + 1, clock.getTicks());
            }
            e = e.next;
        } while (e != null && e.index < maxSegments);

        // max entries exceeded
        notifyListener(thread, name, PoolListener.EV_FULL, -1, -1);
        LOG.info().$('\'').$(name).$("' is busy [thread=").$(thread).$(", retries=").$(this.maxSegments).$(']').$();
        throw EntryUnavailableException.INSTANCE;
    }

    public int getBusyCount() {
        int count = 0;
        for (Map.Entry<CharSequence, Entry> me : entries.entrySet()) {
            Entry e = me.getValue();
            do {
                for (int i = 0; i < ENTRY_SIZE; i++) {
                    if (Unsafe.arrayGetVolatile(e.allocations, i) != UNALLOCATED && Unsafe.arrayGet(e.readers, i) != null) {
                        count++;
                    }
                }
                e = e.next;
            } while (e != null);
        }
        return count;
    }

    public int getMaxEntries() {
        return maxEntries;
    }

    public boolean lock(CharSequence name) {

        checkClosed();

        Entry e = entries.get(name);
        if (e == null) {
            e = new Entry(0, clock.getTicks());
            Entry other = entries.putIfAbsent(name, e);
            if (other != null) {
                e = other;
            }
        }

        long thread = Thread.currentThread().getId();

        if (Unsafe.cas(e, LOCK_OWNER, UNLOCKED, thread) || Unsafe.cas(e, LOCK_OWNER, thread, thread)) {
            do {
                for (int i = 0; i < ENTRY_SIZE; i++) {
                    if (Unsafe.cas(e.allocations, i, UNALLOCATED, thread)) {
                        closeReader(thread, e, i, PoolListener.EV_LOCK_CLOSE, PoolConstants.CR_NAME_LOCK);
                    } else if (Unsafe.cas(e.allocations, i, thread, thread)) {
                        // same thread, don't need to order reads
                        if (Unsafe.arrayGet(e.readers, i) != null) {
                            // this thread has busy reader, it should close first
                            e.lockOwner = -1L;
                            return false;
                        }
                    } else {
                        LOG.info().$("'").$(name).$("' is busy [at=").$(e.index).$(':').$(i).$(", owner=").$(Unsafe.arrayGet(e.allocations, i)).$(", thread=").$(thread).$(']').$();
                        e.lockOwner = -1L;
                        return false;
                    }
                }

                if (e.next == null) {
                    // prevent new entries from being created
                    if (Unsafe.getUnsafe().compareAndSwapInt(e, NEXT_STATUS, NEXT_OPEN, NEXT_LOCKED)) {
                        break;
                    } else {
                        // right, failed to lock next entry
                        // are we failing to read what next entry is?
                        if (e.next == null && e.lockOwner != thread) {
                            // lost the race
                            LOG.info().$("'").$(name).$("' is busy [at=").$(e.index + 1).$(':').$(0).$(", owner=unknown").$(", thread=").$(thread).$(']').$();
                            e.lockOwner = -1L;
                            return false;
                        }
                    }
                }
                e = e.next;

            } while (e != null);
        } else {
            LOG.error().$('\'').$(name).$("' already locked [owner=").$(e.lockOwner).$(']').$();
            notifyListener(thread, name, PoolListener.EV_LOCK_BUSY, -1, -1);
            return false;
        }
        notifyListener(thread, name, PoolListener.EV_LOCK_SUCCESS, -1, -1);
        LOG.info().$('\'').$(name).$("' locked [thread=").$(thread).$(']').$();
        return true;
    }

    public void unlock(CharSequence name) {
        Entry e = entries.get(name);
        long thread = Thread.currentThread().getId();
        if (e == null) {
            LOG.info().$('\'').$(name).$("' not found, cannot unlock").$();
            notifyListener(thread, name, PoolListener.EV_NOT_LOCKED, -1, -1);
            return;
        }

        if (e.lockOwner == thread) {
            entries.remove(name);
        }
        notifyListener(thread, name, PoolListener.EV_UNLOCKED, -1, -1);
        LOG.info().$('\'').$(name).$("' unlocked").$();
    }

    private void checkClosed() {
        if (isClosed()) {
            LOG.info().$("is closed");
            throw PoolClosedException.INSTANCE;
        }
    }

    @Override
    protected void closePool() {
        super.closePool();
        LOG.info().$("closed").$();
    }

    @Override
    protected boolean releaseAll(long deadline) {
        long thread = Thread.currentThread().getId();
        boolean removed = false;
        int casFailures = 0;
        int closeReason = deadline < Long.MAX_VALUE ? PoolConstants.CR_IDLE : PoolConstants.CR_POOL_CLOSE;

        for (Map.Entry<CharSequence, Entry> me : entries.entrySet()) {

            Entry e = me.getValue();

            do {
                for (int i = 0; i < ENTRY_SIZE; i++) {
                    if (deadline > Unsafe.arrayGetVolatile(e.releaseTimes, i) && Unsafe.arrayGet(e.readers, i) != null) {
                        if (Unsafe.cas(e.allocations, i, UNALLOCATED, thread)) {
                            // check if deadline violation still holds
                            if (deadline > Unsafe.arrayGet(e.releaseTimes, i)) {
                                removed = true;
                                closeReader(thread, e, i, PoolListener.EV_EXPIRE, closeReason);
                            }
                            Unsafe.arrayPutOrdered(e.allocations, i, UNALLOCATED);
                        } else {
                            casFailures++;
                        }
                    } else {
                        if (deadline == Long.MAX_VALUE) {
                            R r = Unsafe.arrayGet(e.readers, i);
                            if (r != null) {
                                r.goodby();
                                LOG.info().$("shutting down. '").$(r.getTableName()).$("' is left behind").$();
                            }
                        }
                    }
                }
                e = e.next;
            } while (e != null);
        }

        // when we are timing out entries the result is "true" if there was any work done
        // when we closing pool, the result is true when pool is empty
        if (closeReason == PoolConstants.CR_IDLE) {
            return removed;
        } else {
            return casFailures == 0;
        }
    }

    private void closeReader(long thread, Entry entry, int index, short ev, int reason) {
        R r = Unsafe.arrayGet(entry.readers, index);
        if (r != null) {
            r.goodby();
            r.close();
            LOG.info().$("closed '").$(r.getTableName()).$("' [at=").$(entry.index).$(':').$(index).$(", reason=").$(PoolConstants.closeReasonText(reason)).$(']').$();
            notifyListener(thread, r.getTableName(), ev, entry.index, index);
            Unsafe.arrayPut(entry.readers, index, null);
        }
    }

    private void notifyListener(long thread, CharSequence name, short event, int segment, int position) {
        PoolListener listener = getPoolListener();
        if (listener != null) {
            listener.onEvent(PoolListener.SRC_READER, thread, name, event, (short) segment, (short) position);
        }
    }

    private boolean returnToPool(R reader) {
        CharSequence name = reader.getTableName();

        long thread = Thread.currentThread().getId();

        int index = reader.index;

        if (Unsafe.arrayGetVolatile(reader.entry.allocations, index) != UNALLOCATED) {

            if (isClosed()) {
                // keep locked and close
                Unsafe.arrayPutOrdered(reader.entry.readers, index, null);
                notifyListener(thread, name, PoolListener.EV_OUT_OF_POOL_CLOSE, reader.entry.index, index);
                LOG.info().$("allowing '").$(name).$("' to close [thread=").$(thread).$(']').$();
                reader.goodby();
                return false;
            }

            LOG.debug().$('\'').$(name).$("' is back [at=").$(reader.entry.index).$(':').$(index).$(", thread=").$(thread).$(']').$();
            notifyListener(thread, name, PoolListener.EV_RETURN, reader.entry.index, index);

            Unsafe.arrayPut(reader.entry.releaseTimes, index, clock.getTicks());
            Unsafe.arrayPutOrdered(reader.entry.allocations, index, UNALLOCATED);

            return true;
        }
        LOG.error().$('\'').$(name).$("' is available [at=").$(reader.entry.index).$(':').$(index).$(']');
        return true;
    }

    private static class Entry {
        final long[] allocations = new long[ENTRY_SIZE];
        final long[] releaseTimes = new long[ENTRY_SIZE];
        final R[] readers = new R[ENTRY_SIZE];
        final int index;
        volatile long lockOwner = -1L;
        @SuppressWarnings("unused")
        long nextStatus = 0;
        volatile Entry next;

        public Entry(int index, long currentMicros) {
            this.index = index;
            Arrays.fill(allocations, UNALLOCATED);
            Arrays.fill(releaseTimes, currentMicros);
        }
    }

    public static class R extends TableReader {
        private final int index;
        private ReaderPool pool;
        private Entry entry;

        public R(ReaderPool pool, Entry entry, int index, CharSequence name) {
            super(pool.getConfiguration(), name);
            this.pool = pool;
            this.entry = entry;
            this.index = index;
        }

        @Override
        public void close() {
            if (pool != null && entry != null && pool.returnToPool(this)) {
                return;
            }
            super.close();
        }

        private void goodby() {
            entry = null;
            pool = null;
        }
    }
}