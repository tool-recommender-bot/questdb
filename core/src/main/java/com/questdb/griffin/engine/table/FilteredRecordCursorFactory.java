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
import com.questdb.cairo.sql.RecordCursor;
import com.questdb.cairo.sql.RecordCursorFactory;
import com.questdb.cairo.sql.RecordMetadata;
import com.questdb.griffin.engine.functions.bind.BindVariableService;

public class FilteredRecordCursorFactory implements RecordCursorFactory {
    private final RecordCursorFactory base;
    private final FilteredRecordCursor cursor;
    private final Function filter;

    public FilteredRecordCursorFactory(RecordCursorFactory base, Function filter) {
        this.base = base;
        this.cursor = new FilteredRecordCursor(filter);
        this.filter = filter;
    }

    @Override
    public void close() {
        base.close();
        filter.close();
    }

    @Override
    public RecordCursor getCursor(BindVariableService bindVariableService) {
        RecordCursor cursor = base.getCursor(bindVariableService);
        this.cursor.of(cursor, bindVariableService);
        return this.cursor;
    }

    @Override
    public RecordMetadata getMetadata() {
        return base.getMetadata();
    }

    @Override
    public boolean isRandomAccessCursor() {
        return base.isRandomAccessCursor();
    }
}
