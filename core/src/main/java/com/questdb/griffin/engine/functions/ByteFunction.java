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

package com.questdb.griffin.engine.functions;

import com.questdb.cairo.sql.Function;
import com.questdb.cairo.sql.Record;
import com.questdb.cairo.sql.RecordCursorFactory;
import com.questdb.cairo.sql.RecordMetadata;
import com.questdb.common.ColumnType;
import com.questdb.std.BinarySequence;
import com.questdb.std.str.CharSink;

public abstract class ByteFunction implements Function {

    private final int position;

    public ByteFunction(int position) {
        this.position = position;
    }

    @Override
    public final BinarySequence getBin(Record rec) {
        throw new UnsupportedOperationException();
    }

    @Override
    public final boolean getBool(Record rec) {
        throw new UnsupportedOperationException();
    }

    @Override
    public final long getDate(Record rec) {
        throw new UnsupportedOperationException();
    }

    @Override
    public double getDouble(Record rec) {
        return getByte(rec);
    }

    @Override
    public float getFloat(Record rec) {
        return getByte(rec);
    }

    @Override
    public int getInt(Record rec) {
        return getByte(rec);
    }

    @Override
    public long getLong(Record rec) {
        return getByte(rec);
    }

    @Override
    public int getPosition() {
        return position;
    }

    @Override
    public RecordMetadata getMetadata() {
        throw new UnsupportedOperationException();
    }

    @Override
    public RecordCursorFactory getRecordCursorFactory() {
        throw new UnsupportedOperationException();
    }

    @Override
    public short getShort(Record rec) {
        return getByte(rec);
    }

    @Override
    public final CharSequence getStr(Record rec) {
        throw new UnsupportedOperationException();
    }

    @Override
    public final void getStr(Record rec, CharSink sink) {
        throw new UnsupportedOperationException();
    }

    @Override
    public final CharSequence getStrB(Record rec) {
        throw new UnsupportedOperationException();
    }

    @Override
    public final int getStrLen(Record rec) {
        throw new UnsupportedOperationException();
    }

    @Override
    public final CharSequence getSymbol(Record rec) {
        throw new UnsupportedOperationException();
    }

    @Override
    public final long getTimestamp(Record rec) {
        throw new UnsupportedOperationException();
    }

    @Override
    public final int getType() {
        return ColumnType.BYTE;
    }
}
