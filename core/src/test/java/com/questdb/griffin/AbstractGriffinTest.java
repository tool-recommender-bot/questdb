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

package com.questdb.griffin;

import com.questdb.cairo.AbstractCairoTest;
import com.questdb.cairo.ColumnType;
import com.questdb.cairo.Engine;
import com.questdb.cairo.TableUtils;
import com.questdb.cairo.sql.*;
import com.questdb.griffin.engine.functions.bind.BindVariableService;
import com.questdb.std.BinarySequence;
import com.questdb.std.IntList;
import com.questdb.std.LongList;
import com.questdb.test.tools.TestUtils;
import org.jetbrains.annotations.Nullable;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;

import java.io.IOException;

public class AbstractGriffinTest extends AbstractCairoTest {
    protected static final BindVariableService bindVariableService = new BindVariableService();
    private static final LongList rows = new LongList();
    protected static Engine engine;
    protected static SqlCompiler compiler;
    protected static TestExecutionContext sqlExecutionContext;

    @BeforeClass
    public static void setUp2() {
        engine = new Engine(configuration);
        compiler = new SqlCompiler(engine, configuration);
        sqlExecutionContext = new TestExecutionContext(compiler.getCodeGenerator());
    }

    @AfterClass
    public static void tearDown() {
        engine.close();
        compiler.close();
    }

    protected static void assertCursor(CharSequence expected, RecordCursorFactory factory, boolean supportsRandomAccess) throws IOException {
        try (RecordCursor cursor = factory.getCursor(bindVariableService)) {
            sink.clear();
            rows.clear();
            printer.print(cursor, factory.getMetadata(), true);

            if (expected == null) {
                return;
            }

            TestUtils.assertEquals(expected, sink);

            final RecordMetadata metadata = factory.getMetadata();

            testSymbolAPI(metadata, cursor);

            if (supportsRandomAccess) {

                Assert.assertTrue(factory.isRandomAccessCursor());

                cursor.toTop();

                sink.clear();
                while (cursor.hasNext()) {
                    rows.add(cursor.next().getRowId());
                }


                // test external record
                Record record = cursor.getRecord();

                printer.printHeader(metadata);
                for (int i = 0, n = rows.size(); i < n; i++) {
                    cursor.recordAt(record, rows.getQuick(i));
                    printer.print(record, metadata);
                }

                TestUtils.assertEquals(expected, sink);

                // test internal record
                sink.clear();
                printer.printHeader(metadata);
                for (int i = 0, n = rows.size(); i < n; i++) {
                    printer.print(cursor.recordAt(rows.getQuick(i)), metadata);
                }

                TestUtils.assertEquals(expected, sink);

                // test _new_ record
                sink.clear();
                record = cursor.newRecord();
                printer.printHeader(metadata);
                for (int i = 0, n = rows.size(); i < n; i++) {
                    cursor.recordAt(record, rows.getQuick(i));
                    printer.print(record, metadata);
                }

                TestUtils.assertEquals(expected, sink);
            } else {
                Assert.assertFalse(factory.isRandomAccessCursor());
            }
        }
    }

    protected static void testSymbolAPI(RecordMetadata metadata, RecordCursor cursor) {
        IntList symbolIndexes = null;
        for (int i = 0, n = metadata.getColumnCount(); i < n; i++) {
            if (metadata.getColumnType(i) == ColumnType.SYMBOL) {
                if (symbolIndexes == null) {
                    symbolIndexes = new IntList();
                }
                symbolIndexes.add(i);
            }
        }

        if (symbolIndexes != null) {
            cursor.toTop();
            while (cursor.hasNext()) {
                Record record = cursor.next();
                for (int i = 0, n = symbolIndexes.size(); i < n; i++) {
                    int column = symbolIndexes.getQuick(i);
                    SymbolTable symbolTable = cursor.getSymbolTable(column);
                    CharSequence sym = record.getSym(column);
                    int value = record.getInt(column);
                    Assert.assertEquals(value, symbolTable.getQuick(sym));
                    TestUtils.assertEquals(sym, symbolTable.value(value));
                }
            }
        }
    }

    private static void assertTimestampColumnValues(RecordCursorFactory factory) {
        int index = factory.getMetadata().getTimestampIndex();
        long timestamp = Long.MIN_VALUE;
        try (RecordCursor cursor = factory.getCursor(bindVariableService)) {
            while (cursor.hasNext()) {
                long ts = cursor.next().getTimestamp(index);
                Assert.assertTrue(timestamp <= ts);
                timestamp = ts;
            }
        }
    }

    private static void assertVariableColumns(RecordCursorFactory factory) {
        try (RecordCursor cursor = factory.getCursor(bindVariableService)) {
            RecordMetadata metadata = factory.getMetadata();
            final int columnCount = metadata.getColumnCount();
            while (cursor.hasNext()) {
                Record record = cursor.next();
                for (int i = 0; i < columnCount; i++) {
                    switch (metadata.getColumnType(i)) {
                        case ColumnType.STRING:
                            CharSequence a = record.getStr(i);
                            CharSequence b = record.getStrB(i);
                            if (a == null) {
                                Assert.assertNull(b);
                                Assert.assertEquals(TableUtils.NULL_LEN, record.getStrLen(i));
                            } else {
                                Assert.assertNotSame(a, b);
                                TestUtils.assertEquals(a, b);
                                Assert.assertEquals(a.length(), record.getStrLen(i));
                            }
                            break;
                        case ColumnType.BINARY:
                            BinarySequence s = record.getBin(i);
                            if (s == null) {
                                Assert.assertEquals(TableUtils.NULL_LEN, record.getBinLen(i));
                            } else {
                                Assert.assertEquals(s.length(), record.getBinLen(i));
                            }
                            break;
                        default:
                            break;
                    }
                }
            }
        }
    }

    protected static void printSqlResult(
            CharSequence expected,
            CharSequence query,
            CharSequence expectedTimestamp,
            CharSequence ddl2,
            CharSequence expected2,
            boolean supportsRandomAccess
    ) throws IOException, SqlException {
        try (final RecordCursorFactory factory = compiler.compile(query, bindVariableService)) {
            if (expectedTimestamp == null) {
                Assert.assertEquals(-1, factory.getMetadata().getTimestampIndex());
            } else {
                int index = factory.getMetadata().getColumnIndex(expectedTimestamp);
                Assert.assertNotEquals(-1, index);
                Assert.assertEquals(index, factory.getMetadata().getTimestampIndex());
                assertTimestampColumnValues(factory);
            }
            assertCursor(expected, factory, supportsRandomAccess);
            // make sure we get the same outcome when we get factory to create new cursor
            assertCursor(expected, factory, supportsRandomAccess);
            // make sure strings, binary fields and symbols are compliant with expected record behaviour
            assertVariableColumns(factory);

            if (ddl2 != null) {
                compiler.compile(ddl2, bindVariableService);
                assertCursor(expected2, factory, supportsRandomAccess);
                // and again
                assertCursor(expected2, factory, supportsRandomAccess);
            }
        }
    }

    private static void assertQuery(
            CharSequence expected,
            CharSequence query,
            @Nullable CharSequence ddl,
            @Nullable CharSequence verify,
            @Nullable CharSequence expectedTimestamp,
            @Nullable CharSequence ddl2,
            @Nullable CharSequence expected2,
            boolean supportsRandomAccess
    ) throws Exception {
        TestUtils.assertMemoryLeak(() -> {
            try {
                if (ddl != null) {
                    compiler.compile(ddl, bindVariableService);
                }
                if (verify != null) {
                    printSqlResult(null, verify, expectedTimestamp, ddl2, expected2, supportsRandomAccess);
                }
                printSqlResult(expected, query, expectedTimestamp, ddl2, expected2, supportsRandomAccess);
                Assert.assertEquals(0, engine.getBusyReaderCount());
                Assert.assertEquals(0, engine.getBusyWriterCount());
            } finally {
                engine.releaseAllWriters();
                engine.releaseAllReaders();
            }
        });
    }

    protected static void assertQuery(
            CharSequence expected,
            CharSequence query,
            CharSequence ddl,
            @Nullable CharSequence expectedTimestamp) throws Exception {
        assertQuery(expected, query, ddl, null, expectedTimestamp, null, null, true);
    }

    protected static void assertQuery(
            CharSequence expected,
            CharSequence query,
            CharSequence ddl,
            @Nullable CharSequence expectedTimestamp,
            boolean supportsRandomAccess) throws Exception {
        assertQuery(expected, query, ddl, null, expectedTimestamp, null, null, supportsRandomAccess);
    }

    protected static void assertQuery(
            CharSequence expected,
            CharSequence query,
            CharSequence ddl,
            @Nullable CharSequence expectedTimestamp,
            @Nullable CharSequence ddl2,
            @Nullable CharSequence expected2) throws Exception {
        assertQuery(expected, query, ddl, null, expectedTimestamp, ddl2, expected2, true);
    }

    protected static void assertQuery(
            CharSequence expected,
            CharSequence query,
            CharSequence ddl,
            @Nullable CharSequence expectedTimestamp,
            @Nullable CharSequence ddl2,
            @Nullable CharSequence expected2,
            boolean supportsRandomAccess) throws Exception {
        assertQuery(expected, query, ddl, null, expectedTimestamp, ddl2, expected2, supportsRandomAccess);
    }
}
