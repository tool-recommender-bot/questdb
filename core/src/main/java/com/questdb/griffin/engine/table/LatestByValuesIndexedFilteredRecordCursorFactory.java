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

import com.questdb.cairo.CairoConfiguration;
import com.questdb.cairo.SymbolMapReader;
import com.questdb.cairo.sql.DataFrameCursor;
import com.questdb.cairo.sql.DataFrameCursorFactory;
import com.questdb.cairo.sql.Function;
import com.questdb.cairo.sql.RecordMetadata;
import com.questdb.std.CharSequenceHashSet;
import com.questdb.std.Transient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LatestByValuesIndexedFilteredRecordCursorFactory extends AbstractDeferredTreeSetRecordCursorFactory {

    private final Function filter;

    public LatestByValuesIndexedFilteredRecordCursorFactory(
            @NotNull CairoConfiguration configuration,
            @NotNull RecordMetadata metadata,
            @NotNull DataFrameCursorFactory dataFrameCursorFactory,
            int columnIndex,
            @Transient CharSequenceHashSet keyValues,
            @Transient SymbolMapReader symbolMapReader,
            @Nullable Function filter) {
        super(configuration, metadata, dataFrameCursorFactory, columnIndex, keyValues, symbolMapReader);
        if (filter != null) {
            this.cursor = new LatestByValuesIndexedFilteredRecordCursor(columnIndex, treeSet, symbolKeys, filter);
        } else {
            this.cursor = new LatestByValuesIndexedRecordCursor(columnIndex, treeSet, symbolKeys);
        }
        this.filter = filter;
    }


    @Override
    public void close() {
        super.close();
        if (filter != null) {
            filter.close();
        }
    }

    @Override
    protected AbstractDataFrameRecordCursor getCursorInstance(DataFrameCursor dataFrameCursor) {
        if (filter != null) {
            AbstractDataFrameRecordCursor cursor = super.getCursorInstance(dataFrameCursor);
            filter.withCursor(cursor);
            return cursor;
        }

        return super.getCursorInstance(dataFrameCursor);
    }
}
