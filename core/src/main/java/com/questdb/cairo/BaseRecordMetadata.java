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


import com.questdb.cairo.sql.RecordMetadata;
import com.questdb.std.CharSequenceIntHashMap;
import com.questdb.std.ObjList;

public class BaseRecordMetadata implements RecordMetadata {
    protected ObjList<TableColumnMetadata> columnMetadata;
    protected CharSequenceIntHashMap columnNameIndexMap;
    protected int timestampIndex;
    protected int columnCount;

    @Override
    public int getColumnCount() {
        return columnCount;
    }

    @Override
    public int getColumnType(int columnIndex) {
        return getColumnQuick(columnIndex).getType();
    }

    @Override
    public int getColumnIndexQuiet(CharSequence columnName) {
        return columnNameIndexMap.get(columnName);
    }

    @Override
    public CharSequence getColumnName(int columnIndex) {
        return getColumnQuick(columnIndex).getName();
    }

    @Override
    public int getIndexValueBlockCapacity(int columnIndex) {
        return getColumnQuick(columnIndex).getIndexValueBlockCapacity();
    }

    @Override
    public int getTimestampIndex() {
        return timestampIndex;
    }

    @Override
    public boolean isColumnIndexed(int columnIndex) {
        return getColumnQuick(columnIndex).isIndexed();
    }

    public TableColumnMetadata getColumnQuick(int index) {
        return columnMetadata.getQuick(index);
    }
}
