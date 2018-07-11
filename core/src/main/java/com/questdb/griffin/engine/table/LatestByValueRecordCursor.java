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

import com.questdb.cairo.sql.DataFrame;
import com.questdb.cairo.sql.DataFrameCursor;
import com.questdb.cairo.sql.Record;

class LatestByValueRecordCursor extends AbstractDataFrameRecordCursor {

    private final int columnIndex;
    private final int symbolKey;
    private boolean empty;
    private boolean hasNext;

    public LatestByValueRecordCursor(int columnIndex, int symbolKey) {
        this.columnIndex = columnIndex;
        this.symbolKey = symbolKey;
    }

    @Override
    public void close() {
        dataFrameCursor.close();
    }

    @Override
    public boolean hasNext() {
        return hasNext;
    }

    @Override
    public Record next() {
        hasNext = false;
        return record;
    }

    @Override
    public void toTop() {
        hasNext = !empty;
    }

    private void findRecord() {
        empty = true;
        OUT:
        while (this.dataFrameCursor.hasNext()) {
            final DataFrame frame = this.dataFrameCursor.next();
            final long rowLo = frame.getRowLo();
            final long rowHi = frame.getRowHi() - 1;

            record.jumpTo(frame.getPartitionIndex(), rowHi);
            for (long row = rowHi; row >= rowLo; row--) {
                record.setRecordIndex(row);
                int key = record.getInt(columnIndex);
                if (key == symbolKey) {
                    empty = false;
                    break OUT;
                }
            }
        }
    }

    void of(DataFrameCursor dataFrameCursor) {
        this.dataFrameCursor = dataFrameCursor;
        this.record.of(dataFrameCursor.getTableReader());
        findRecord();
        toTop();
    }
}
