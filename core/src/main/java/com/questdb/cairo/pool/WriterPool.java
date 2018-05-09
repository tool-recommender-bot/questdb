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

import com.questdb.cairo.*;
import com.questdb.cairo.pool.ex.EntryLockedException;
import com.questdb.cairo.pool.ex.EntryUnavailableException;
import com.questdb.cairo.pool.ex.PoolClosedException;
import com.questdb.common.PoolConstants;
import com.questdb.log.Log;
import com.questdb.log.LogFactory;
import com.questdb.std.ConcurrentHashMap;
import com.questdb.std.Misc;
import com.questdb.std.Unsafe;
import com.questdb.std.microtime.MicrosecondClock;
import com.questdb.std.str.Path;

import java.util.Iterator;

/**
 * This class maintains cache of open writers to avoid OS overhead of
 * opening and closing files. While doing so it abides by the the same
 * rule as non-pooled writers: there can only be one TableWriter instance
 * for any given name.
 * <p>
 * This implementation is thread-safe. Writer allocated by one thread
 * cannot be used by any other threads until it is released. This factory
 * will be returning NULL when writer is already in use and cached
 * instance of writer otherwise. Writers are released back to pool via
 * standard writer.close() call.
 * <p>
 * Writers that have been idle for some time can be expunged from pool
 * by calling Job.run() method asynchronously. Pool implementation is
 * guaranteeing thread-safety of this method at all times.
 * <p>
 * This factory can be closed via close() call. This method is also
 * thread-safe and is guarantying that all open writers will be eventually
 * closed.
 */
public class WriterPool extends AbstractPool implements ResourcePool<TableWriter> {

    private static final Log LOG = LogFactory.getLog(WriterPool.class);

    private final static long ENTRY_OWNER = Unsafe.getFieldOffset(Entry.class, "owner");
    private final ConcurrentHashMap<Entry> entries = new ConcurrentHashMap<>();
    private final CairoConfiguration configuration;
    private final Path path = new Path();
    private final MicrosecondClock clock;
    private final CharSequence root;
    private final CairoWorkScheduler workScheduler;

    /**
     * Pool constructor. WriterPool root directory is passed via configuration.
     *
     * @param configuration configuration parameters.
     */
    public WriterPool(CairoConfiguration configuration, CairoWorkScheduler workScheduler) {
        super(configuration, configuration.getInactiveWriterTTL());
        this.configuration = configuration;
        this.workScheduler = workScheduler;
        this.clock = configuration.getMicrosecondClock();
        this.root = configuration.getRoot();
        notifyListener(Thread.currentThread().getId(), null, PoolListener.EV_POOL_OPEN);
    }

    /**
     * <p>
     * Creates or retrieves existing TableWriter from pool. Because of TableWriter compliance with <b>single
     * writer model</b> pool ensures there is single TableWriter instance for given table name. Table name is unique in
     * context of <b>root</b> and pool instance covers single root.
     * </p>
     * When TableWriter from this pool is used by another thread @{@link EntryUnavailableException} is thrown and
     * when table is locked outside of pool, which includes same or different process, @{@link CairoException} instead.
     * In case of former application can retry getting writer from pool again at any time. When latter occurs application has
     * to call {@link #releaseAll(long)} before retrying for TableWriter.
     *
     * @param tableName name of the table
     * @return cached TableWriter instance.
     */
    @Override
    public TableWriter get(CharSequence tableName) {

        checkClosed();

        long thread = Thread.currentThread().getId();

        Entry e = entries.get(tableName);
        if (e == null) {
            // We are racing to create new writer!
            e = new Entry(clock.getTicks());
            Entry other = entries.putIfAbsent(tableName, e);
            if (other == null) {
                // race won
                return createWriter(tableName, e, thread);
            } else {
                e = other;
            }
        }

        long owner = e.owner;
        // try to change owner
        if (Unsafe.cas(e, ENTRY_OWNER, UNALLOCATED, thread)) {
            // in an extreme race condition it is possible that e.writer will be null
            // in this case behaviour should be identical to entry missing entirely
            if (e.writer == null) {
                return createWriter(tableName, e, thread);
            }

            if (isClosed()) {
                // pool closed but we somehow managed to lock writer
                // make sure that interceptor cleared to allow calling thread close writer normally
                e.writer.goodby();
            }
            return logAndReturn(e, PoolListener.EV_GET);
        } else {
            if (e.owner == thread) {
                if (e.lockFd != -1L) {
                    throw EntryLockedException.INSTANCE;
                }

                if (e.ex != null) {
                    notifyListener(thread, tableName, PoolListener.EV_EX_RESEND);
                    // this writer failed to allocate by this very thread
                    // ensure consistent response
                    throw e.ex;
                }

                if (isClosed()) {
                    LOG.info().$('\'').utf8(tableName).$("' born free").$();
                    e.writer.goodby();
                }
                return logAndReturn(e, PoolListener.EV_GET);
            }
            LOG.error().$('\'').utf8(tableName).$("' is busy [owner=").$(owner).$(']').$();
            throw EntryUnavailableException.INSTANCE;
        }
    }

    /**
     * Counts busy writers in pool.
     *
     * @return number of busy writer instances.
     */
    public int getBusyCount() {
        int count = 0;
        for (Entry e : entries.values()) {
            if (e.owner != UNALLOCATED) {
                count++;
            }
        }
        return count;
    }

    /**
     * Locks writer. Locking operation is always non-blocking. Lock is usually successful
     * when writer is in pool or owned by calling thread, in which case
     * writer instance is closed. Lock will also succeed when writer does not exist.
     * This will prevent from writer being created before it is unlocked.
     * <p>
     * Lock fails immediately with {@link EntryUnavailableException} when writer is used by another thread and with
     * {@link PoolClosedException} when pool is closed.
     * </p>
     * <p>
     * Lock is beneficial before table directory is renamed or deleted.
     * </p>
     *
     * @param tableName table name
     * @return true if lock was successful, false otherwise
     */
    public boolean lock(CharSequence tableName) {

        checkClosed();

        long thread = Thread.currentThread().getId();

        Entry e = entries.get(tableName);
        if (e == null) {
            // We are racing to create new writer!
            e = new Entry(clock.getTicks());
            Entry other = entries.putIfAbsent(tableName, e);
            if (other == null) {
                return lockAndNotify(thread, e, tableName);
            } else {
                e = other;
            }
        }

        // try to change owner
        if ((Unsafe.cas(e, ENTRY_OWNER, UNALLOCATED, thread) /*|| Unsafe.cas(e, ENTRY_OWNER, thread, thread)*/)) {
            closeWriter(thread, e, PoolListener.EV_LOCK_CLOSE, PoolConstants.CR_NAME_LOCK);
            return lockAndNotify(thread, e, tableName);
        }

        LOG.error().$("cannot lock '").utf8(tableName).$("', busy [owner=").$(e.owner).$(", thread=").$(thread).$();
        notifyListener(thread, tableName, PoolListener.EV_LOCK_BUSY);
        return false;
    }

    public int size() {
        return entries.size();
    }

    public void unlock(CharSequence name) {
        long thread = Thread.currentThread().getId();

        Entry e = entries.get(name);
        if (e == null) {
            notifyListener(thread, name, PoolListener.EV_NOT_LOCKED);
            return;
        }

        // When entry is locked, writer must be null,
        // however if writer is not null, calling thread must be trying to unlock
        // writer that hasn't been locked. This qualifies for "illegal state"
        if (e.owner == thread) {

            if (e.writer != null) {
                notifyListener(thread, name, PoolListener.EV_NOT_LOCKED);
                throw CairoException.instance(0).put("Writer ").put(name).put(" is not locked");
            }
            // unlock must remove entry because pool does not deal with null writer
            entries.remove(name);
        }

        if (e.lockFd != -1) {
            ff.close(e.lockFd);
        }
        notifyListener(thread, name, PoolListener.EV_UNLOCKED);
    }

    private void checkClosed() {
        if (isClosed()) {
            LOG.info().$("is closed").$();
            throw PoolClosedException.INSTANCE;
        }
    }

    /**
     * Closes writer pool. When pool is closed only writers that are in pool are proactively released. Writers that
     * are outside of pool will close when their close() method is invoked.
     * <p>
     * After pool is closed it will notify listener with #EV_POOL_CLOSED event.
     * </p>
     */
    @Override
    protected void closePool() {
        super.closePool();
        Misc.free(path);
        LOG.info().$("closed").$();
    }

    @Override
    protected boolean releaseAll(long deadline) {
        long thread = Thread.currentThread().getId();
        boolean removed = false;
        final int reason;

        if (deadline == Long.MAX_VALUE) {
            reason = PoolConstants.CR_POOL_CLOSE;
        } else {
            reason = PoolConstants.CR_IDLE;
        }

        Iterator<Entry> iterator = entries.values().iterator();
        while (iterator.hasNext()) {
            Entry e = iterator.next();
            // lastReleaseTime is volatile, which makes
            // order of conditions important
            if ((deadline > e.lastReleaseTime && e.owner == UNALLOCATED)) {
                // looks like this one can be released
                // try to lock it
                if (Unsafe.cas(e, ENTRY_OWNER, UNALLOCATED, thread)) {
                    // lock successful
                    closeWriter(thread, e, PoolListener.EV_EXPIRE, reason);
                    iterator.remove();
                    removed = true;
                }
            } else if (e.lockFd != -1L) {
                if (ff.close(e.lockFd)) {
                    e.lockFd = -1L;
                    iterator.remove();
                    removed = true;
                }
            } else if (e.ex != null) {
                LOG.info().$("purging entry for failed to allocate writer").$();
                iterator.remove();
                removed = true;
            }
        }
        return removed;
    }

    private void closeWriter(long thread, Entry e, short ev, int reason) {
        PooledTableWriter w = e.writer;
        if (w != null) {
            CharSequence name = e.writer.getName();
            w.goodby();
            w.close();
            e.writer = null;
            LOG.info().$("closed '").utf8(name).$("' [reason=").$(PoolConstants.closeReasonText(reason)).$(", by=").$(thread).$(']').$();
            notifyListener(thread, name, ev);
        }
    }

    int countFreeWriters() {
        int count = 0;
        for (Entry e : entries.values()) {
            if (e.owner == UNALLOCATED) {
                count++;
            } else {
                LOG.info().$("'").utf8(e.writer.getName()).$("' is still busy [owner=").$(e.owner).$(']').$();
            }
        }

        return count;
    }

    private PooledTableWriter createWriter(CharSequence name, Entry e, long thread) {
        try {
            checkClosed();
            LOG.info().$("open '").utf8(name).$("' [thread=").$(thread).$(']').$();
            e.writer = new PooledTableWriter(this, e, name);
            return logAndReturn(e, PoolListener.EV_CREATE);
        } catch (CairoException ex) {
            LOG.error().$("failed to allocate writer '").utf8(name).$("' [thread=").$(e.owner).$(']').$();
            e.ex = ex;
            notifyListener(e.owner, name, PoolListener.EV_CREATE_EX);
            throw ex;
        }
    }

    private boolean lockAndNotify(long thread, Entry e, CharSequence tableName) {
        TableUtils.lockName(path.of(root).concat(tableName));
        e.lockFd = TableUtils.lock(ff, path);
        if (e.lockFd == -1L) {
            LOG.error().$("cannot lock '").utf8(tableName).$("' [thread=").$(thread).$(']').$();
            e.owner = UNALLOCATED;
            return false;
        }
        LOG.info().$('\'').utf8(tableName).$("' locked [thread=").$(thread).$(']').$();
        notifyListener(thread, tableName, PoolListener.EV_LOCK_SUCCESS);
        return true;
    }

    private PooledTableWriter logAndReturn(Entry e, short event) {
        LOG.info().$('\'').utf8(e.writer.getName()).$("' is assigned [thread=").$(e.owner).$(']').$();
        notifyListener(e.owner, e.writer.getName(), event);
        return e.writer;
    }

    private boolean returnToPool(Entry e) {
        CharSequence name = e.writer.getName();
        long thread = Thread.currentThread().getId();
        if (e.owner != UNALLOCATED) {
            LOG.info().$('\'').utf8(name).$(" is back [thread=").$(thread).$(']').$();
            if (isClosed()) {
                LOG.info().$("allowing '").utf8(name).$("' to close [thread=").$(e.owner).$(']').$();
                e.writer.goodby();
                e.writer = null;
                entries.remove(name);
                notifyListener(thread, name, PoolListener.EV_OUT_OF_POOL_CLOSE);
                return false;
            }

            e.owner = UNALLOCATED;
            e.lastReleaseTime = configuration.getMicrosecondClock().getTicks();
            notifyListener(thread, name, PoolListener.EV_RETURN);
        } else {
            LOG.error().$('\'').utf8(name).$("' has no owner").$();
            notifyListener(thread, name, PoolListener.EV_UNEXPECTED_CLOSE);
        }
        return true;
    }

    private static class Entry {
        // owner thread id or -1 if writer is available for hire
        private long owner = Thread.currentThread().getId();
        private PooledTableWriter writer;
        // time writer was last released
        private volatile long lastReleaseTime;
        private CairoException ex = null;
        private volatile long lockFd = -1L;

        public Entry(long lastReleaseTime) {
            this.lastReleaseTime = lastReleaseTime;
        }
    }

    private static class PooledTableWriter extends TableWriter {
        private final WriterPool pool;
        private Entry entry;

        public PooledTableWriter(WriterPool pool, Entry e, CharSequence name) {
            super(pool.configuration, name, pool.workScheduler);
            this.pool = pool;
            this.entry = e;
        }

        @Override
        public void close() {
            if (entry != null && pool != null && pool.returnToPool(entry)) {
                return;
            }
            super.close();
        }

        @Override
        public String toString() {
            return "PooledTableWriter{" +
                    "name=" + (entry.writer != null ? entry.writer.getName() : "<unassigned>") +
                    '}';
        }

        private void goodby() {
            this.entry = null;
        }
    }
}
