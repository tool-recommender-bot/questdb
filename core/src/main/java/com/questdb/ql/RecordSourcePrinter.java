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

package com.questdb.ql;

import com.questdb.std.Numbers;
import com.questdb.std.str.CharSink;
import com.questdb.std.time.DateFormatUtils;
import com.questdb.store.ColumnType;
import com.questdb.store.Record;
import com.questdb.store.RecordCursor;
import com.questdb.store.RecordMetadata;
import com.questdb.store.factory.ReaderFactory;

import java.io.IOException;

public class RecordSourcePrinter {
    private final CharSink sink;
    private final char delimiter;

    public RecordSourcePrinter(CharSink sink) {
        this.sink = sink;
        this.delimiter = '\t';
    }

    public RecordSourcePrinter(CharSink sink, char delimiter) {
        this.sink = sink;
        this.delimiter = delimiter;
    }

    public void print(RecordSource src, ReaderFactory factory) throws IOException {
        print(src, factory, false);
    }

    public void print(RecordSource src, ReaderFactory factory, boolean header) throws IOException {
        RecordCursor cursor = src.prepareCursor(factory);
        try {
            print(cursor, header, src.getMetadata());
        } finally {
            cursor.releaseCursor();
        }
    }

    public void print(RecordCursor cursor, boolean header, RecordMetadata metadata) throws IOException {
        if (header) {
            printHeader(metadata);
        }

        while (cursor.hasNext()) {
            print(cursor.next(), metadata);
        }
    }

    private void print(Record r, RecordMetadata m) throws IOException {
        for (int i = 0, sz = m.getColumnCount(); i < sz; i++) {
            if (i > 0) {
                sink.put(delimiter);
            }
            printColumn(r, m, i);
        }
        sink.put("\n");
        sink.flush();
    }

    private void printColumn(Record r, RecordMetadata m, int i) {
        switch (m.getColumnQuick(i).getType()) {
            case ColumnType.DATE:
                DateFormatUtils.appendDateTime(sink, r.getDate(i));
                break;
            case ColumnType.TIMESTAMP:
                com.questdb.std.microtime.DateFormatUtils.appendDateTime(sink, r.getDate(i));
                break;
            case ColumnType.DOUBLE:
                Numbers.append(sink, r.getDouble(i), 12);
                break;
            case ColumnType.FLOAT:
                Numbers.append(sink, r.getFloat(i), 4);
                break;
            case ColumnType.INT:
                Numbers.append(sink, r.getInt(i));
                break;
            case ColumnType.STRING:
                r.getStr(i, sink);
                break;
            case ColumnType.SYMBOL:
                sink.put(r.getSym(i));
                break;
            case ColumnType.SHORT:
                Numbers.append(sink, r.getShort(i));
                break;
            case ColumnType.LONG:
                Numbers.append(sink, r.getLong(i));
                break;
            case ColumnType.BYTE:
                Numbers.append(sink, r.getByte(i));
                break;
            case ColumnType.BOOLEAN:
                sink.put(r.getBool(i) ? "true" : "false");
                break;
            default:
                break;
        }
    }

    private void printHeader(RecordMetadata metadata) {
        for (int i = 0, n = metadata.getColumnCount(); i < n; i++) {
            if (i > 0) {
                sink.put(delimiter);
            }
            sink.put(metadata.getColumnName(i));
        }
        sink.put('\n');
    }
}
