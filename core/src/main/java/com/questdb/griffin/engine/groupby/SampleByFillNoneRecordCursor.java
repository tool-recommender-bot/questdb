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

package com.questdb.griffin.engine.groupby;

import com.questdb.cairo.RecordSink;
import com.questdb.cairo.map.Map;
import com.questdb.cairo.map.MapKey;
import com.questdb.cairo.map.MapRecord;
import com.questdb.cairo.map.MapValue;
import com.questdb.cairo.sql.*;
import com.questdb.griffin.engine.functions.TimestampFunction;
import com.questdb.std.IntIntHashMap;
import com.questdb.std.ObjList;

import java.util.Iterator;

class SampleByFillNoneRecordCursor implements RecordCursor {
    private final Map map;
    private final RecordSink keyMapSink;
    private final ObjList<GroupByFunction> groupByFunctions;
    private final int timestampIndex;
    private final TimestampSampler timestampSampler;
    private final Record record;
    private final IntIntHashMap symbolTableIndex;
    private RecordCursor base;
    private Iterator<MapRecord> mapIterator;
    private Record baseRecord;
    private long lastTimestamp;
    private long nextTimestamp;

    public SampleByFillNoneRecordCursor(
            Map map,
            RecordSink keyMapSink,
            ObjList<GroupByFunction> groupByFunctions,
            ObjList<Function> recordFunctions,
            int timestampIndex, // index of timestamp column in base cursor
            TimestampSampler timestampSampler,
            IntIntHashMap symbolTableIndex) {
        this.map = map;
        this.groupByFunctions = groupByFunctions;
        this.timestampIndex = timestampIndex;
        this.keyMapSink = keyMapSink;
        this.timestampSampler = timestampSampler;
        VirtualRecord rec = new VirtualRecord(recordFunctions);
        rec.of(map.getRecord());
        this.record = rec;
        this.symbolTableIndex = symbolTableIndex;
        for (int i = 0, n = recordFunctions.size(); i < n; i++) {
            Function f = recordFunctions.getQuick(i);
            if (f == null) {
                recordFunctions.setQuick(i, new TimestampFunc(0));
            }
        }
        this.mapIterator = map.iterator();
    }

    @Override
    public void close() {
        base.close();
    }

    @Override
    public Record getRecord() {
        return record;
    }

    @Override
    public SymbolTable getSymbolTable(int columnIndex) {
        return base.getSymbolTable(symbolTableIndex.get(columnIndex));
    }

    @Override
    public Record newRecord() {
        return null;
    }

    @Override
    public Record recordAt(long rowId) {
        return null;
    }

    @Override
    public void recordAt(Record record, long atRowId) {
    }

    @Override
    public void toTop() {
        this.base.toTop();
    }

    @Override
    public boolean hasNext() {
        //
        if (mapHasNext()) {
            return true;
        }

        if (baseRecord == null) {
            return false;
        }

        // key map has been flushed
        // before we build another one we need to check
        // for timestamp gaps

        this.lastTimestamp = this.nextTimestamp;
        this.map.clear();

        // looks like we need to populate key map

        int n = groupByFunctions.size();
        while (true) {
            long timestamp = timestampSampler.round(baseRecord.getTimestamp(timestampIndex));
            if (lastTimestamp == timestamp) {
                final MapKey key = map.withKey();
                keyMapSink.copy(baseRecord, key);
                final MapValue value = key.createValue();

                if (value.isNew()) {
                    for (int i = 0; i < n; i++) {
                        groupByFunctions.getQuick(i).computeFirst(value, baseRecord);
                    }
                } else {
                    for (int i = 0; i < n; i++) {
                        groupByFunctions.getQuick(i).computeNext(value, baseRecord);
                    }
                }

                // carry on with the loop if we still have data
                if (base.hasNext()) {
                    base.next();
                    continue;
                }

                // we ran out of data, make sure hasNext() returns false at the next
                // opportunity, after we stream map that is.
                baseRecord = null;
                // reset map iterator
                map.iterator();

                // we do not have any more data, let map take over
                return mapHasNext();
            }

            // timestamp changed, make sure we keep the value of 'lastTimestamp'
            // unchanged. Timestamp columns uses this variable
            // When map is exhausted we would assign 'nextTimestamp' to 'lastTimestamp'
            // and build another map
            this.nextTimestamp = timestamp;
            this.map.iterator();
            if (mapHasNext()) {
                return true;
            }
            // we do not need to clear map, it is empty anyway
            lastTimestamp = nextTimestamp;
        }
    }

    @Override
    public Record next() {
        return record;
    }

    private boolean mapHasNext() {
        if (mapIterator.hasNext()) {
            // scroll down the map iterator
            // next() will return record that uses current map position
            mapIterator.next();
            return true;
        }
        return false;
    }

    void of(RecordCursor base) {
        // factory guarantees that base cursor is not empty
        this.base = base;
        this.baseRecord = base.next();
        this.nextTimestamp = timestampSampler.round(baseRecord.getTimestamp(timestampIndex));
        this.lastTimestamp = this.nextTimestamp;
    }

    private class TimestampFunc extends TimestampFunction {

        public TimestampFunc(int position) {
            super(position);
        }

        @Override
        public long getTimestamp(Record rec) {
            return lastTimestamp;
        }
    }
}
