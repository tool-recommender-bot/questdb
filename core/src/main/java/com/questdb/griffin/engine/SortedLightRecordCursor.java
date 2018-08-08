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

package com.questdb.griffin.engine;

import com.questdb.cairo.sql.Record;
import com.questdb.cairo.sql.RecordCursor;
import com.questdb.cairo.sql.SymbolTable;

class SortedLightRecordCursor implements RecordCursor {
    private final LongTreeChain chain;
    private final RecordComparator comparator;
    private final LongTreeChain.TreeCursor chainCursor;
    private RecordCursor base;
    private Record record = null;

    public SortedLightRecordCursor(LongTreeChain chain, RecordComparator comparator) {
        this.chain = chain;
        this.comparator = comparator;
        // assign it once, its the same instance anyway
        this.chainCursor = chain.getCursor();
    }

    @Override
    public void close() {
        chain.clear();
        base.close();
    }

    @Override
    public SymbolTable getSymbolTable(int columnIndex) {
        return base.getSymbolTable(columnIndex);
    }

    @Override
    public Record getRecord() {
        return base.getRecord();
    }

    @Override
    public Record newRecord() {
        return base.newRecord();
    }

    @Override
    public Record recordAt(long rowId) {
        return base.recordAt(rowId);
    }

    @Override
    public void recordAt(Record record, long atRowId) {
        base.recordAt(record, atRowId);
    }

    @Override
    public void toTop() {
        chainCursor.toTop();
    }

    @Override
    public boolean hasNext() {
        return chainCursor.hasNext();
    }

    @Override
    public Record next() {
        return base.recordAt(chainCursor.next());
    }

    void of(RecordCursor base) {
        this.base = base;
        if (record == null) {
            record = base.newRecord();
        }

        chain.clear();
        while (base.hasNext()) {
            // Tree chain is liable to re-position record to
            // other rows to do record comparison. We must use our
            // own record instance in case base cursor keeps
            // state in the record it returns.
            chain.put(
                    base.next().getRowId(),
                    base,
                    record,
                    comparator
            );
        }
        chainCursor.toTop();
    }
}
