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

package com.questdb.griffin.engine.table;

import com.questdb.cairo.sql.Function;
import com.questdb.cairo.sql.Record;
import com.questdb.cairo.sql.RecordCursor;
import com.questdb.std.ObjList;

class VirtualRecordCursor implements RecordCursor {
    private final VirtualRecord record;
    private RecordCursor baseCursor;

    public VirtualRecordCursor(ObjList<Function> functions) {
        this.record = new VirtualRecord(functions);
    }

    void of(RecordCursor cursor) {
        this.baseCursor = cursor;
        record.of(baseCursor.getRecord());
    }

    @Override
    public Record getRecord() {
        return record;
    }

    @Override
    public Record newRecord() {
        final VirtualRecord record = new VirtualRecord(this.record.getFunctions());
        record.of(baseCursor.newRecord());
        return record;
    }

    @Override
    public Record recordAt(long rowId) {
        baseCursor.recordAt(rowId);
        return record;
    }

    @Override
    public void recordAt(Record record, long atRowId) {
        baseCursor.recordAt(((VirtualRecord) record).getBaseRecord(), atRowId);
    }

    @Override
    public void toTop() {
        baseCursor.toTop();
    }

    @Override
    public void close() {
        baseCursor.close();
    }

    @Override
    public boolean hasNext() {
        return baseCursor.hasNext();
    }

    @Override
    public Record next() {
        baseCursor.next();
        return record;
    }
}
