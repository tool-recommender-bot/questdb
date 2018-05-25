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

import com.questdb.cairo.sql.DataFrame;
import com.questdb.cairo.sql.DataFrameCursor;
import com.questdb.cairo.sql.RecordMetadata;
import com.questdb.common.ColumnType;
import com.questdb.common.PartitionBy;
import com.questdb.common.RowCursor;
import com.questdb.common.SymbolTable;
import com.questdb.mp.*;
import com.questdb.std.*;
import com.questdb.std.microtime.DateFormatUtils;
import com.questdb.std.str.LPSZ;
import com.questdb.std.str.StringSink;
import com.questdb.test.tools.TestUtils;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;

public class FullFwdDataFrameCursorTest extends AbstractCairoTest {

    private static final int WORK_STEALING_DONT_TEST = 0;
    private static final int WORK_STEALING_NO_PICKUP = 1;
    private static final int WORK_STEALING_BUSY_QUEUE = 2;
    private static final int WORK_STEALING_HIGH_CONTENTION = 3;
    private static final int WORK_STEALING_CAS_FLAP = 4;

    @Test
    public void testClose() throws Exception {
        TestUtils.assertMemoryLeak(() -> {

            try (TableModel model = new TableModel(configuration, "x", PartitionBy.NONE).
                    col("a", ColumnType.INT).
                    col("b", ColumnType.INT).
                    timestamp()
            ) {
                CairoTestUtils.create(model);
            }

            TableReader reader = new TableReader(configuration, "x");
            FullFwdDataFrameCursor cursor = new FullFwdDataFrameCursor();
            cursor.of(reader);
            cursor.close();
            Assert.assertFalse(reader.isOpen());
            cursor.close();
            Assert.assertFalse(reader.isOpen());
        });
    }

    @Test
    public void testEmptyPartitionSkip() throws Exception {
        TestUtils.assertMemoryLeak(() -> {
            try (TableModel model = new TableModel(configuration, "x", PartitionBy.NONE).
                    col("a", ColumnType.INT).
                    col("b", ColumnType.INT).
                    timestamp()
            ) {
                CairoTestUtils.create(model);
            }

            long timestamp;
            final Rnd rnd = new Rnd();
            try (TableWriter writer = new TableWriter(configuration, "x")) {
                timestamp = DateFormatUtils.parseDateTime("1970-01-03T08:00:00.000Z");

                TableWriter.Row row = writer.newRow(timestamp);
                row.putInt(0, rnd.nextInt());
                row.putInt(1, rnd.nextInt());

                // create partition on disk but not commit neither transaction nor row

                try (TableReader reader = new TableReader(configuration, "x")) {
                    FullFwdDataFrameCursor cursor = new FullFwdDataFrameCursor();

                    int frameCount = 0;
                    cursor.of(reader);
                    while (cursor.hasNext()) {
                        cursor.next();
                        frameCount++;
                    }

                    Assert.assertEquals(0, frameCount);
                }
            }
        });
    }

    @Test
    public void testFailToRemoveDistressFileByDay() throws Exception {
        testFailToRemoveDistressFile(PartitionBy.DAY, 10000000L);
    }

    @Test
    public void testFailToRemoveDistressFileByMonth() throws Exception {
        testFailToRemoveDistressFile(PartitionBy.MONTH, 10000000L * 32);
    }

    @Test
    public void testFailToRemoveDistressFileByNone() throws Exception {
        testFailToRemoveDistressFile(PartitionBy.NONE, 10L);
    }

    @Test
    public void testFailToRemoveDistressFileByYear() throws Exception {
        testFailToRemoveDistressFile(PartitionBy.YEAR, 10000000L * 32 * 12);
    }

    @Test
    @Ignore
    // todo: test key write failure
    // to test this scenario we need large number of keys to overwhelm single memory buffer
    // which is at odds when testing value failure.
    public void testIndexFailAtRuntimeByDay1k() throws Exception {
        testIndexFailureAtRuntime(PartitionBy.DAY, 10L, false, "1970-01-01" + Files.SEPARATOR + "a.k", 1);
    }

    @Test
    public void testIndexFailAtRuntimeByDay1v() throws Exception {
        testIndexFailureAtRuntime(PartitionBy.DAY, 10000000L, false, "1970-01-02" + Files.SEPARATOR + "a.v", 2);
    }

    @Test
    public void testIndexFailAtRuntimeByDay2v() throws Exception {
        testIndexFailureAtRuntime(PartitionBy.DAY, 10000000L, false, "1970-01-02" + Files.SEPARATOR + "b.v", 2);
    }

    @Test
    public void testIndexFailAtRuntimeByDay3v() throws Exception {
        testIndexFailureAtRuntime(PartitionBy.DAY, 10000000L, false, "1970-01-02" + Files.SEPARATOR + "c.v", 2);
    }

    @Test
    public void testIndexFailAtRuntimeByMonth1v() throws Exception {
        testIndexFailureAtRuntime(PartitionBy.MONTH, 10000000L * 32, false, "1970-02" + Files.SEPARATOR + "a.v", 2);
    }

    @Test
    public void testIndexFailAtRuntimeByMonth2v() throws Exception {
        testIndexFailureAtRuntime(PartitionBy.MONTH, 10000000L * 30, false, "1970-02" + Files.SEPARATOR + "b.v", 2);
    }

    @Test
    public void testIndexFailAtRuntimeByMonth3v() throws Exception {
        testIndexFailureAtRuntime(PartitionBy.MONTH, 10000000L * 30, false, "1970-02" + Files.SEPARATOR + "c.v", 2);
    }

    @Test
    public void testIndexFailAtRuntimeByNone1v() throws Exception {
        testIndexFailureAtRuntime(PartitionBy.NONE, 10L, false, TableUtils.DEFAULT_PARTITION_NAME + Files.SEPARATOR + "a.v", 1);
    }

    @Test
    public void testIndexFailAtRuntimeByNone2v() throws Exception {
        testIndexFailureAtRuntime(PartitionBy.NONE, 10L, false, TableUtils.DEFAULT_PARTITION_NAME + Files.SEPARATOR + "b.v", 1);
    }

    @Test
    public void testIndexFailAtRuntimeByNone3v() throws Exception {
        testIndexFailureAtRuntime(PartitionBy.NONE, 10L, false, TableUtils.DEFAULT_PARTITION_NAME + Files.SEPARATOR + "c.v", 1);
    }

    @Test
    public void testIndexFailAtRuntimeByNoneEmpty1v() throws Exception {
        testIndexFailureAtRuntime(PartitionBy.NONE, 10L, true, TableUtils.DEFAULT_PARTITION_NAME + Files.SEPARATOR + "a.v", 0);
    }

    @Test
    public void testIndexFailAtRuntimeByNoneEmpty2v() throws Exception {
        testIndexFailureAtRuntime(PartitionBy.NONE, 10L, true, TableUtils.DEFAULT_PARTITION_NAME + Files.SEPARATOR + "b.v", 0);
    }

    @Test
    public void testIndexFailAtRuntimeByNoneEmpty3v() throws Exception {
        testIndexFailureAtRuntime(PartitionBy.NONE, 10L, true, TableUtils.DEFAULT_PARTITION_NAME + Files.SEPARATOR + "c.v", 0);
    }

    @Test
    public void testIndexFailAtRuntimeByYear1v() throws Exception {
        testIndexFailureAtRuntime(PartitionBy.YEAR, 10000000L * 30 * 12, false, "1972" + Files.SEPARATOR + "a.v", 2);
    }

    @Test
    public void testIndexFailAtRuntimeByYear2v() throws Exception {
        testIndexFailureAtRuntime(PartitionBy.YEAR, 10000000L * 30 * 12, false, "1972" + Files.SEPARATOR + "b.v", 2);
    }

    @Test
    public void testIndexFailAtRuntimeByYear3v() throws Exception {
        testIndexFailureAtRuntime(PartitionBy.YEAR, 10000000L * 30 * 12, false, "1972" + Files.SEPARATOR + "c.v", 2);
    }

    @Test
    public void testIndexFailAtRuntimeByYearEmpty1v() throws Exception {
        testIndexFailureAtRuntime(PartitionBy.YEAR, 10000000L * 30 * 12, true, "1970" + Files.SEPARATOR + "a.v", 0);
    }

    @Test
    public void testIndexFailAtRuntimeByYearEmpty2v() throws Exception {
        testIndexFailureAtRuntime(PartitionBy.YEAR, 10000000L * 30 * 12, true, "1970" + Files.SEPARATOR + "b.v", 0);
    }

    @Test
    public void testIndexFailAtRuntimeByYearEmpty3v() throws Exception {
        testIndexFailureAtRuntime(PartitionBy.YEAR, 10000000L * 30 * 12, true, "1970" + Files.SEPARATOR + "c.v", 0);
    }

    @Test
    public void testIndexFailInConstructorByDay1k() throws Exception {
        testIndexFailureInConstructor(PartitionBy.DAY, 1000000L, false, "1970-01-01" + Files.SEPARATOR + "a.k");
    }

    @Test
    public void testIndexFailInConstructorByDay1v() throws Exception {
        testIndexFailureInConstructor(PartitionBy.DAY, 1000000L, false, "1970-01-01" + Files.SEPARATOR + "a.v");
    }

    @Test
    public void testIndexFailInConstructorByDay2k() throws Exception {
        testIndexFailureInConstructor(PartitionBy.DAY, 1000000L, false, "1970-01-01" + Files.SEPARATOR + "b.k");
    }

    @Test
    public void testIndexFailInConstructorByDay2v() throws Exception {
        testIndexFailureInConstructor(PartitionBy.DAY, 1000000L, false, "1970-01-01" + Files.SEPARATOR + "b.v");
    }

    @Test
    public void testIndexFailInConstructorByNoneEmpty1k() throws Exception {
        testIndexFailureInConstructor(PartitionBy.NONE, 1000L, true, TableUtils.DEFAULT_PARTITION_NAME + Files.SEPARATOR + "a.k");
    }

    @Test
    public void testIndexFailInConstructorByNoneEmpty1v() throws Exception {
        testIndexFailureInConstructor(PartitionBy.NONE, 1000L, true, TableUtils.DEFAULT_PARTITION_NAME + Files.SEPARATOR + "a.v");
    }

    @Test
    public void testIndexFailInConstructorByNoneEmpty2k() throws Exception {
        testIndexFailureInConstructor(PartitionBy.NONE, 1000L, true, TableUtils.DEFAULT_PARTITION_NAME + Files.SEPARATOR + "b.k");
    }

    @Test
    public void testIndexFailInConstructorByNoneEmpty2v() throws Exception {
        testIndexFailureInConstructor(PartitionBy.NONE, 1000L, true, TableUtils.DEFAULT_PARTITION_NAME + Files.SEPARATOR + "b.v");
    }

    @Test
    public void testIndexFailInConstructorByNoneEmpty3k() throws Exception {
        testIndexFailureInConstructor(PartitionBy.NONE, 1000L, true, TableUtils.DEFAULT_PARTITION_NAME + Files.SEPARATOR + "c.k");
    }

    @Test
    public void testIndexFailInConstructorByNoneEmpty3v() throws Exception {
        testIndexFailureInConstructor(PartitionBy.NONE, 1000L, true, TableUtils.DEFAULT_PARTITION_NAME + Files.SEPARATOR + "c.v");
    }

    @Test
    public void testIndexFailInConstructorByNoneFull() throws Exception {
        testIndexFailureInConstructor(PartitionBy.NONE, 1000L, false, TableUtils.DEFAULT_PARTITION_NAME + Files.SEPARATOR + "a.v");
    }

    @Test
    public void testParallelIndexByDay() throws Exception {
        testParallelIndex(PartitionBy.DAY, 1000000, 5, WORK_STEALING_DONT_TEST);
    }

    @Test
    public void testParallelIndexByDayBusy() throws Exception {
        testParallelIndex(PartitionBy.DAY, 1000000, 5, WORK_STEALING_BUSY_QUEUE);
    }

    @Test
    public void testParallelIndexByDayCasFlap() throws Exception {
        testParallelIndex(PartitionBy.DAY, 1000000, 5, WORK_STEALING_CAS_FLAP);
    }

    @Test
    public void testParallelIndexByDayContention() throws Exception {
        testParallelIndex(PartitionBy.DAY, 1000000, 5, WORK_STEALING_HIGH_CONTENTION);
    }

    @Test
    public void testParallelIndexByDayNoPickup() throws Exception {
        testParallelIndex(PartitionBy.DAY, 1000000, 5, WORK_STEALING_NO_PICKUP);
    }

    @Test
    public void testParallelIndexByMonth() throws Exception {
        testParallelIndex(PartitionBy.MONTH, 1000000 * 10, 3, WORK_STEALING_DONT_TEST);
    }

    @Test
    public void testParallelIndexByMonthBusy() throws Exception {
        testParallelIndex(PartitionBy.MONTH, 1000000 * 10, 3, WORK_STEALING_BUSY_QUEUE);
    }

    @Test
    public void testParallelIndexByMonthContention() throws Exception {
        testParallelIndex(PartitionBy.MONTH, 1000000 * 10, 3, WORK_STEALING_HIGH_CONTENTION);
    }

    @Test
    public void testParallelIndexByMonthNoPickup() throws Exception {
        testParallelIndex(PartitionBy.MONTH, 1000000 * 10, 3, WORK_STEALING_NO_PICKUP);
    }

    @Test
    public void testParallelIndexByNone() throws Exception {
        testParallelIndex(PartitionBy.NONE, 0, 0, WORK_STEALING_DONT_TEST);
    }

    @Test
    public void testParallelIndexByNoneBusy() throws Exception {
        testParallelIndex(PartitionBy.NONE, 0, 0, WORK_STEALING_BUSY_QUEUE);
    }

    @Test
    public void testParallelIndexByNoneContention() throws Exception {
        testParallelIndex(PartitionBy.NONE, 0, 0, WORK_STEALING_HIGH_CONTENTION);
    }

    @Test
    public void testParallelIndexByNoneNoPickup() throws Exception {
        testParallelIndex(PartitionBy.NONE, 0, 0, WORK_STEALING_NO_PICKUP);
    }

    @Test
    public void testParallelIndexByYear() throws Exception {
        testParallelIndex(PartitionBy.YEAR, 1000000 * 10 * 12, 3, WORK_STEALING_DONT_TEST);
    }

    @Test
    public void testParallelIndexByYearBusy() throws Exception {
        testParallelIndex(PartitionBy.YEAR, 1000000 * 10 * 12, 3, WORK_STEALING_BUSY_QUEUE);
    }

    @Test
    public void testParallelIndexByYearContention() throws Exception {
        testParallelIndex(PartitionBy.YEAR, 1000000 * 10 * 12, 3, WORK_STEALING_HIGH_CONTENTION);
    }

    @Test
    public void testParallelIndexByYearNoPickup() throws Exception {
        testParallelIndex(PartitionBy.YEAR, 1000000 * 10 * 12, 3, WORK_STEALING_NO_PICKUP);
    }

    @Test
    public void testParallelIndexFailAtRuntimeByDay1v() throws Exception {
        testParallelIndexFailureAtRuntime(PartitionBy.DAY, 10000000L, false, "1970-01-02" + Files.SEPARATOR + "a.v", 2);
    }

    @Test
    public void testParallelIndexFailAtRuntimeByDay2v() throws Exception {
        testParallelIndexFailureAtRuntime(PartitionBy.DAY, 10000000L, false, "1970-01-02" + Files.SEPARATOR + "b.v", 2);
    }

    @Test
    public void testParallelIndexFailAtRuntimeByDay3v() throws Exception {
        testParallelIndexFailureAtRuntime(PartitionBy.DAY, 10000000L, false, "1970-01-02" + Files.SEPARATOR + "c.v", 2);
    }

    @Test
    public void testParallelIndexFailAtRuntimeByDayEmpty1v() throws Exception {
        testParallelIndexFailureAtRuntime(PartitionBy.DAY, 10000000L, true, "1970-01-02" + Files.SEPARATOR + "a.v", 0);
    }

    @Test
    public void testParallelIndexFailAtRuntimeByDayEmpty2v() throws Exception {
        testParallelIndexFailureAtRuntime(PartitionBy.DAY, 10000000L, true, "1970-01-02" + Files.SEPARATOR + "b.v", 0);
    }

    @Test
    public void testParallelIndexFailAtRuntimeByDayEmpty3v() throws Exception {
        testParallelIndexFailureAtRuntime(PartitionBy.DAY, 10000000L, true, "1970-01-02" + Files.SEPARATOR + "c.v", 0);
    }

    @Test
    public void testParallelIndexFailAtRuntimeByMonth1v() throws Exception {
        testParallelIndexFailureAtRuntime(PartitionBy.MONTH, 10000000L * 32, false, "1970-02" + Files.SEPARATOR + "a.v", 2);
    }

    @Test
    public void testParallelIndexFailAtRuntimeByMonth2v() throws Exception {
        testParallelIndexFailureAtRuntime(PartitionBy.MONTH, 10000000L * 30, false, "1970-02" + Files.SEPARATOR + "b.v", 2);
    }

    @Test
    public void testParallelIndexFailAtRuntimeByMonth3v() throws Exception {
        testParallelIndexFailureAtRuntime(PartitionBy.MONTH, 10000000L * 30, false, "1970-02" + Files.SEPARATOR + "c.v", 2);
    }

    @Test
    public void testParallelIndexFailAtRuntimeByMonthEmpty1v() throws Exception {
        testParallelIndexFailureAtRuntime(PartitionBy.MONTH, 10000000L * 32, true, "1970-02" + Files.SEPARATOR + "a.v", 0);
    }

    @Test
    public void testParallelIndexFailAtRuntimeByMonthEmpty2v() throws Exception {
        testParallelIndexFailureAtRuntime(PartitionBy.MONTH, 10000000L * 30, true, "1970-02" + Files.SEPARATOR + "b.v", 0);
    }

    @Test
    public void testParallelIndexFailAtRuntimeByMonthEmpty3v() throws Exception {
        testParallelIndexFailureAtRuntime(PartitionBy.MONTH, 10000000L * 30, true, "1970-02" + Files.SEPARATOR + "c.v", 0);
    }

    @Test
    public void testParallelIndexFailAtRuntimeByNone1v() throws Exception {
        testParallelIndexFailureAtRuntime(PartitionBy.NONE, 10L, false, TableUtils.DEFAULT_PARTITION_NAME + Files.SEPARATOR + "a.v", 1);
    }

    @Test
    public void testParallelIndexFailAtRuntimeByNone2v() throws Exception {
        testParallelIndexFailureAtRuntime(PartitionBy.NONE, 10L, false, TableUtils.DEFAULT_PARTITION_NAME + Files.SEPARATOR + "b.v", 1);
    }

    @Test
    public void testParallelIndexFailAtRuntimeByNone3v() throws Exception {
        testParallelIndexFailureAtRuntime(PartitionBy.NONE, 10L, false, TableUtils.DEFAULT_PARTITION_NAME + Files.SEPARATOR + "c.v", 1);
    }

    @Test
    public void testParallelIndexFailAtRuntimeByNoneEmpty1v() throws Exception {
        testParallelIndexFailureAtRuntime(PartitionBy.NONE, 10L, true, TableUtils.DEFAULT_PARTITION_NAME + Files.SEPARATOR + "a.v", 0);
    }

    @Test
    public void testParallelIndexFailAtRuntimeByNoneEmpty2v() throws Exception {
        testParallelIndexFailureAtRuntime(PartitionBy.NONE, 10L, true, TableUtils.DEFAULT_PARTITION_NAME + Files.SEPARATOR + "b.v", 0);
    }

    @Test
    public void testParallelIndexFailAtRuntimeByNoneEmpty3v() throws Exception {
        testParallelIndexFailureAtRuntime(PartitionBy.NONE, 10L, true, TableUtils.DEFAULT_PARTITION_NAME + Files.SEPARATOR + "c.v", 0);
    }

    @Test
    public void testParallelIndexFailAtRuntimeByYear1v() throws Exception {
        testParallelIndexFailureAtRuntime(PartitionBy.YEAR, 10000000L * 30 * 12, false, "1972" + Files.SEPARATOR + "a.v", 2);
    }

    @Test
    public void testParallelIndexFailAtRuntimeByYear2v() throws Exception {
        testParallelIndexFailureAtRuntime(PartitionBy.YEAR, 10000000L * 30 * 12, false, "1972" + Files.SEPARATOR + "b.v", 2);
    }

    @Test
    public void testParallelIndexFailAtRuntimeByYear3v() throws Exception {
        testParallelIndexFailureAtRuntime(PartitionBy.YEAR, 10000000L * 30 * 12, false, "1972" + Files.SEPARATOR + "c.v", 2);
    }

    @Test
    public void testParallelIndexFailAtRuntimeByYearEmpty1v() throws Exception {
        testParallelIndexFailureAtRuntime(PartitionBy.YEAR, 10000000L * 30 * 12, true, "1970" + Files.SEPARATOR + "a.v", 0);
    }

    @Test
    public void testParallelIndexFailAtRuntimeByYearEmpty2v() throws Exception {
        testParallelIndexFailureAtRuntime(PartitionBy.YEAR, 10000000L * 30 * 12, true, "1970" + Files.SEPARATOR + "b.v", 0);
    }

    @Test
    public void testParallelIndexFailAtRuntimeByYearEmpty3v() throws Exception {
        testParallelIndexFailureAtRuntime(PartitionBy.YEAR, 10000000L * 30 * 12, true, "1970" + Files.SEPARATOR + "c.v", 0);
    }

    @Test
    public void testRemoveFirstColByDay() throws Exception {
        testRemoveFirstColumn(PartitionBy.DAY, 1000000 * 60 * 5, 3);
    }

    @Test
    public void testRemoveFirstColByMonth() throws Exception {
        testRemoveFirstColumn(PartitionBy.MONTH, 1000000 * 60 * 5 * 24L, 2);
    }

    @Test
    public void testRemoveFirstColByNone() throws Exception {
        testRemoveFirstColumn(PartitionBy.NONE, 1000000 * 60 * 5, 0);
    }

    @Test
    public void testRemoveFirstColByYear() throws Exception {
        testRemoveFirstColumn(PartitionBy.YEAR, 1000000 * 60 * 5 * 24L * 10L, 2);
    }

    @Test
    public void testRemoveLastColByDay() throws Exception {
        testRemoveLastColumn(PartitionBy.DAY, 1000000 * 60 * 5, 3);
    }

    @Test
    public void testRemoveLastColByMonth() throws Exception {
        testRemoveLastColumn(PartitionBy.MONTH, 1000000 * 60 * 5 * 24L, 2);
    }

    @Test
    public void testRemoveLastColByNone() throws Exception {
        testRemoveFirstColumn(PartitionBy.NONE, 1000000 * 60 * 5, 0);
    }

    @Test
    public void testRemoveLastColByYear() throws Exception {
        testRemoveLastColumn(PartitionBy.YEAR, 1000000 * 60 * 5 * 24L * 10L, 2);
    }

    @Test
    public void testRemoveMidColByDay() throws Exception {
        testRemoveMidColumn(PartitionBy.DAY, 1000000 * 60 * 5, 3);
    }

    @Test
    public void testRemoveMidColByMonth() throws Exception {
        testRemoveMidColumn(PartitionBy.MONTH, 1000000 * 60 * 5 * 24L, 2);
    }

    @Test
    public void testRemoveMidColByNone() throws Exception {
        testRemoveMidColumn(PartitionBy.NONE, 1000000 * 60 * 5, 0);
    }

    @Test
    public void testRemoveMidColByYear() throws Exception {
        testRemoveMidColumn(PartitionBy.YEAR, 1000000 * 60 * 5 * 24L * 10L, 2);
    }

    @Test
    public void testReplaceIndexedWithIndexedByByNone() throws Exception {
        testReplaceIndexedColWithIndexed(PartitionBy.NONE, 1000000 * 60 * 5, 0, false);
    }

    @Test
    public void testReplaceIndexedWithIndexedByByNoneR() throws Exception {
        testReplaceIndexedColWithIndexed(PartitionBy.NONE, 1000000 * 60 * 5, 0, true);
    }

    @Test
    public void testReplaceIndexedWithIndexedByByYear() throws Exception {
        testReplaceIndexedColWithIndexed(PartitionBy.YEAR, 1000000 * 60 * 5 * 24L * 10L, 2, false);
    }

    //

    @Test
    public void testReplaceIndexedWithIndexedByByYearR() throws Exception {
        testReplaceIndexedColWithIndexed(PartitionBy.YEAR, 1000000 * 60 * 5 * 24L * 10L, 2, true);
    }

    @Test
    public void testReplaceIndexedWithIndexedByDay() throws Exception {
        testReplaceIndexedColWithIndexed(PartitionBy.DAY, 1000000 * 60 * 5, 3, false);
    }

    @Test
    public void testReplaceIndexedWithIndexedByDayR() throws Exception {
        testReplaceIndexedColWithIndexed(PartitionBy.DAY, 1000000 * 60 * 5, 3, true);
    }

    @Test
    public void testReplaceIndexedWithIndexedByMonth() throws Exception {
        testReplaceIndexedColWithIndexed(PartitionBy.MONTH, 1000000 * 60 * 5 * 24L, 2, false);
    }

    @Test
    public void testReplaceIndexedWithIndexedByMonthR() throws Exception {
        testReplaceIndexedColWithIndexed(PartitionBy.MONTH, 1000000 * 60 * 5 * 24L, 2, true);
    }

    @Test
    public void testReplaceIndexedWithUnindexedByByDay() throws Exception {
        testReplaceIndexedColWithUnindexed(PartitionBy.DAY, 1000000 * 60 * 5, 3, false);
    }

    @Test
    public void testReplaceIndexedWithUnindexedByByDayR() throws Exception {
        testReplaceIndexedColWithUnindexed(PartitionBy.DAY, 1000000 * 60 * 5, 3, true);
    }

    @Test
    public void testReplaceIndexedWithUnindexedByByNone() throws Exception {
        testReplaceIndexedColWithUnindexed(PartitionBy.NONE, 1000000 * 60 * 5, 0, false);
    }

    ///

    @Test
    public void testReplaceIndexedWithUnindexedByByNoneR() throws Exception {
        testReplaceIndexedColWithUnindexed(PartitionBy.NONE, 1000000 * 60 * 5, 0, true);
    }

    @Test
    public void testReplaceIndexedWithUnindexedByByYear() throws Exception {
        testReplaceIndexedColWithUnindexed(PartitionBy.YEAR, 1000000 * 60 * 5 * 24L * 10L, 2, false);
    }

    @Test
    public void testReplaceIndexedWithUnindexedByByYearR() throws Exception {
        testReplaceIndexedColWithUnindexed(PartitionBy.YEAR, 1000000 * 60 * 5 * 24L * 10L, 2, true);
    }

    @Test
    public void testReplaceIndexedWithUnindexedByMonth() throws Exception {
        testReplaceIndexedColWithUnindexed(PartitionBy.MONTH, 1000000 * 60 * 5 * 24L, 2, false);
    }

    @Test
    public void testReplaceIndexedWithUnindexedByMonthR() throws Exception {
        testReplaceIndexedColWithUnindexed(PartitionBy.MONTH, 1000000 * 60 * 5 * 24L, 2, true);
    }

    @Test
    public void testReplaceUnindexedWithIndexedByDay() throws Exception {
        testReplaceUnindexedColWithIndexed(PartitionBy.DAY, 1000000 * 60 * 5, 3, false);
    }

    @Test
    public void testReplaceUnindexedWithIndexedByDayR() throws Exception {
        testReplaceUnindexedColWithIndexed(PartitionBy.DAY, 1000000 * 60 * 5, 3, true);
    }

    @Test
    public void testReplaceUnindexedWithIndexedByMonth() throws Exception {
        testReplaceUnindexedColWithIndexed(PartitionBy.MONTH, 1000000 * 60 * 5 * 24L, 2, false);
    }

    @Test
    public void testReplaceUnindexedWithIndexedByMonthR() throws Exception {
        testReplaceUnindexedColWithIndexed(PartitionBy.MONTH, 1000000 * 60 * 5 * 24L, 2, true);
    }

    @Test
    public void testReplaceUnindexedWithIndexedByNone() throws Exception {
        testReplaceUnindexedColWithIndexed(PartitionBy.NONE, 1000000 * 60 * 5, 0, false);
    }

    @Test
    public void testReplaceUnindexedWithIndexedByNoneR() throws Exception {
        testReplaceUnindexedColWithIndexed(PartitionBy.NONE, 1000000 * 60 * 5, 0, true);
    }

    @Test
    public void testReplaceUnindexedWithIndexedByYear() throws Exception {
        testReplaceUnindexedColWithIndexed(PartitionBy.YEAR, 1000000 * 60 * 5 * 24L * 10L, 2, false);
    }

    ///

    @Test
    public void testReplaceUnindexedWithIndexedByYearR() throws Exception {
        testReplaceUnindexedColWithIndexed(PartitionBy.YEAR, 1000000 * 60 * 5 * 24L * 10L, 2, true);
    }

    @Test
    public void testRollbackSymbolIndexByDay() throws Exception {
        testSymbolIndexReadAfterRollback(PartitionBy.DAY, 1000000 * 60 * 5, 3);
    }

    @Test
    public void testRollbackSymbolIndexByMonth() throws Exception {
        testSymbolIndexReadAfterRollback(PartitionBy.MONTH, 1000000 * 60 * 5 * 24L, 2);
    }

    @Test
    public void testRollbackSymbolIndexByNone() throws Exception {
        testSymbolIndexReadAfterRollback(PartitionBy.NONE, 1000000 * 60 * 5, 0);
    }

    @Test
    public void testRollbackSymbolIndexByYear() throws Exception {
        testSymbolIndexReadAfterRollback(PartitionBy.YEAR, 1000000 * 60 * 5 * 24L * 10L, 2);
    }

    @Test
    public void testSimpleSymbolIndex() throws Exception {
        TestUtils.assertMemoryLeak(() -> {
            int N = 1000000;
            int S = 128;
            Rnd rnd = new Rnd();
            SymbolGroup sg = new SymbolGroup(rnd, S, N, PartitionBy.NONE, true);

            final MyWorkScheduler workScheduler = new MyWorkScheduler(new MPSequence(1024) {
                private boolean flap = false;

                @Override
                public long next() {
                    boolean flap = this.flap;
                    this.flap = !this.flap;
                    return flap ? -1 : -2;
                }
            }, null);

            long timestamp = 0;
            try (TableWriter writer = new TableWriter(configuration, "ABC", workScheduler)) {
                for (int i = 0; i < N; i++) {
                    TableWriter.Row r = writer.newRow(timestamp += (long) 0);
                    r.putSym(0, sg.symA[rnd.nextPositiveInt() % S]);
                    r.putSym(1, sg.symB[rnd.nextPositiveInt() % S]);
                    r.putSym(2, sg.symC[rnd.nextPositiveInt() % S]);
                    r.putDouble(3, rnd.nextDouble2());
                    r.append();
                }
                writer.commit();
            }

            try (TableReader reader = new TableReader(configuration, "ABC")) {

                Assert.assertTrue(reader.getPartitionCount() > 0);

                FullFwdDataFrameCursor cursor = new FullFwdDataFrameCursor();
                TableReaderRecord record = new TableReaderRecord();

                cursor.of(reader);
                record.of(reader);

                assertIndexRowsMatchSymbol(cursor, record, 0, N);
                cursor.toTop();
                assertIndexRowsMatchSymbol(cursor, record, 1, N);
                cursor.toTop();
                assertIndexRowsMatchSymbol(cursor, record, 2, N);
            }

        });
    }

    @Test
    public void testSymbolIndexReadByDay() throws Exception {
        testSymbolIndexRead(PartitionBy.DAY, 1000000 * 60 * 5, 3);
    }

    @Test
    public void testSymbolIndexReadByMonth() throws Exception {
        testSymbolIndexRead(PartitionBy.MONTH, 1000000 * 60 * 5 * 24L, 2);
    }

    @Test
    public void testSymbolIndexReadByNone() throws Exception {
        testSymbolIndexRead(PartitionBy.NONE, 1000000 * 60 * 5, 0);
    }

    @Test
    public void testSymbolIndexReadByYear() throws Exception {
        testSymbolIndexRead(PartitionBy.YEAR, 1000000 * 60 * 5 * 24L * 10L, 2);
    }

    static void assertIndexRowsMatchSymbol(DataFrameCursor cursor, TableReaderRecord record, int columnIndex, long expecteRowCount) {
        // SymbolTable is table at table scope, so it will be the same for every
        // data frame here. Get its instance outside of data frame loop.
        SymbolTable symbolTable = cursor.getSymbolTable(columnIndex);

        long rowCount = 0;
        while (cursor.hasNext()) {
            DataFrame frame = cursor.next();
            record.jumpTo(frame.getPartitionIndex(), frame.getRowLo());
            final long limit = frame.getRowHi();

            // BitmapIndex is always at data frame scope, each table can have more than one.
            // we have to get BitmapIndexReader instance once for each frame.
            BitmapIndexReader indexReader = frame.getBitmapIndexReader(columnIndex, BitmapIndexReader.DIR_BACKWARD);

            // because out Symbol column 0 is indexed, frame has to have index.
            Assert.assertNotNull(indexReader);

            int keyCount = indexReader.getKeyCount();
            for (int i = 0; i < keyCount; i++) {
                RowCursor ic = indexReader.getCursor(true, i, 0, limit - 1);
                CharSequence expected = symbolTable.value(i - 1);
                while (ic.hasNext()) {
                    record.setRecordIndex(ic.next());
                    TestUtils.assertEquals(expected, record.getSym(columnIndex));
                    rowCount++;
                }
            }
        }
        Assert.assertEquals(expecteRowCount, rowCount);
    }

    private void assertData(FullFwdDataFrameCursor cursor, TableReaderRecord record, Rnd rnd, SymbolGroup sg, long expecteRowCount) {
        // SymbolTable is table at table scope, so it will be the same for every
        // data frame here. Get its instance outside of data frame loop.

        long rowCount = 0;
        while (cursor.hasNext()) {
            DataFrame frame = cursor.next();
            record.jumpTo(frame.getPartitionIndex(), frame.getRowLo());
            final long limit = frame.getRowHi();
            long recordIndex;
            while ((recordIndex = record.getRecordIndex()) < limit) {
                TestUtils.assertEquals(sg.symA[rnd.nextPositiveInt() % sg.S], record.getSym(0));
                TestUtils.assertEquals(sg.symB[rnd.nextPositiveInt() % sg.S], record.getSym(1));
                TestUtils.assertEquals(sg.symC[rnd.nextPositiveInt() % sg.S], record.getSym(2));
                Assert.assertEquals(rnd.nextDouble2(), record.getDouble(3), 0.0000001d);
                record.setRecordIndex(recordIndex + 1);
                rowCount++;
            }
        }

        Assert.assertEquals(expecteRowCount, rowCount);
    }

    private long assertIndex(TableReaderRecord record, int columnIndex, SymbolTable symbolTable, long count, DataFrame frame, int direction) {

        BitmapIndexReader indexReader = frame.getBitmapIndexReader(columnIndex, direction);

        // because out Symbol column 0 is indexed, frame has to have index.
        Assert.assertNotNull(indexReader);

        final long hi = frame.getRowHi();
        record.jumpTo(frame.getPartitionIndex(), frame.getRowLo());
        // Iterate data frame and advance record by incrementing "recordIndex"
        long recordIndex;
        while ((recordIndex = record.getRecordIndex()) < hi) {
            CharSequence sym = record.getSym(columnIndex);

            // Assert that index cursor contains offset of current row
            boolean offsetFound = false;
            long target = record.getRecordIndex();
/*

            if (*/
            /*direction == BitmapIndexReader.DIR_FORWARD &&*//*
 sym == null && recordIndex == 0) {
                System.out.println("ok");
            }
*/

            // Get index cursor for each symbol in data frame
            RowCursor ic = indexReader.getCursor(true, symbolTable.getQuick(sym) + 1, frame.getRowLo(), hi - 1);

            while (ic.hasNext()) {
                if (ic.next() == target) {
                    offsetFound = true;
                    break;
                }
            }
            if (!offsetFound) {
                Assert.fail("not found, target=" + target + ", sym=" + sym);
            }
            record.setRecordIndex(recordIndex + 1);
            count++;
        }
        return count;
    }

    private void assertMetadataEquals(RecordMetadata a, RecordMetadata b) {
        StringSink sinkA = new StringSink();
        StringSink sinkB = new StringSink();
        a.toJson(sinkA);
        b.toJson(sinkB);
        TestUtils.assertEquals(sinkA, sinkB);
    }

    private void assertNoIndex(FullFwdDataFrameCursor cursor) {
        while (cursor.hasNext()) {
            DataFrame frame = cursor.next();
            try {
                frame.getBitmapIndexReader(4, BitmapIndexReader.DIR_BACKWARD);
                Assert.fail();
            } catch (CairoException e) {
                Assert.assertTrue(Chars.contains(e.getMessage(), "Not indexed"));
            }
        }
    }

    private void assertSymbolFoundInIndex(FullFwdDataFrameCursor cursor, TableReaderRecord record, int columnIndex, int M) {
        // SymbolTable is table at table scope, so it will be the same for every
        // data frame here. Get its instance outside of data frame loop.
        SymbolTable symbolTable = cursor.getSymbolTable(columnIndex);

        long count = 0;
        while (cursor.hasNext()) {
            DataFrame frame = cursor.next();

            // BitmapIndex is always at data frame scope, each table can have more than one.
            // we have to get BitmapIndexReader instance once for each frame.
            count = assertIndex(
                    record,
                    columnIndex,
                    symbolTable,
                    count,
                    frame,
                    BitmapIndexReader.DIR_BACKWARD
            );

            count = assertIndex(
                    record,
                    columnIndex,
                    symbolTable,
                    count,
                    frame,
                    BitmapIndexReader.DIR_FORWARD
            );

        }

        // assert that we read entire table
        Assert.assertEquals(M * 2, count);
    }

    private long populateTable(TableWriter writer, String[] symbols, Rnd rnd, long ts, long increment) {
        long timestamp = ts;
        for (int i = 0; i < 1000; i++) {
            TableWriter.Row row = writer.newRow(timestamp += increment);
            row.putSym(0, symbols[rnd.nextPositiveInt() % 100]);
            row.append();
        }
        return timestamp;
    }

    private void testFailToRemoveDistressFile(int partitionBy, long increment) throws Exception {
        TestUtils.assertMemoryLeak(() -> {
            int N = 10000;
            int S = 512;
            Rnd rnd = new Rnd();
            Rnd eRnd = new Rnd();

            TestFilesFacade ff = new TestFilesFacade() {
                boolean invoked = false;

                @Override
                public boolean wasCalled() {
                    return invoked;
                }

                @Override
                public boolean remove(LPSZ name) {
                    if (Chars.endsWith(name, ".lock")) {
                        invoked = true;
                        return false;
                    }
                    return super.remove(name);
                }


            };

            CairoConfiguration configuration = new DefaultCairoConfiguration(root) {
                @Override
                public FilesFacade getFilesFacade() {
                    return ff;
                }
            };

            SymbolGroup sg = new SymbolGroup(rnd, S, N, partitionBy, false);

            // align pseudo-random generators
            // we have to do this because asserting code will not be re-populating symbol group
            eRnd.syncWith(rnd);

            long timestamp = 0;
            try (TableWriter writer = new TableWriter(configuration, "ABC")) {
                for (int i = 0; i < (long) N; i++) {
                    TableWriter.Row r = writer.newRow(timestamp += increment);
                    r.putSym(0, sg.symA[rnd.nextPositiveInt() % sg.S]);
                    r.putSym(1, sg.symB[rnd.nextPositiveInt() % sg.S]);
                    r.putSym(2, sg.symC[rnd.nextPositiveInt() % sg.S]);
                    r.putDouble(3, rnd.nextDouble2());
                    r.append();
                }
                writer.commit();
                // closing should fail
            } catch (CairoException e) {
                TestUtils.assertContains(e.getMessage(), "remove");
            }

            new TableWriter(AbstractCairoTest.configuration, "ABC").close();

            Assert.assertTrue(ff.wasCalled());

            // lets see what we can read after this catastrophe
            try (TableReader reader = new TableReader(AbstractCairoTest.configuration, "ABC")) {
                FullFwdDataFrameCursor cursor = new FullFwdDataFrameCursor();
                TableReaderRecord record = new TableReaderRecord();

                cursor.of(reader);
                record.of(reader);

                assertSymbolFoundInIndex(cursor, record, 0, N);
                cursor.toTop();
                assertSymbolFoundInIndex(cursor, record, 1, N);
                cursor.toTop();
                assertSymbolFoundInIndex(cursor, record, 2, N);
                cursor.toTop();
                assertIndexRowsMatchSymbol(cursor, record, 0, N);
                cursor.toTop();
                assertIndexRowsMatchSymbol(cursor, record, 1, N);
                cursor.toTop();
                assertIndexRowsMatchSymbol(cursor, record, 2, N);
                cursor.toTop();
                assertData(cursor, record, eRnd, sg, N);
            }
        });
    }

    private void testIndexFailureAtRuntime(int partitionBy, long increment, boolean empty, String fileUnderAttack, int expectedPartitionCount) throws Exception {
        TestUtils.assertMemoryLeak(() -> {
            int N = 10000;
            int S = 512;
            Rnd rnd = new Rnd();
            Rnd eRnd = new Rnd();

            FilesFacade ff = new FilesFacadeImpl() {
                private long fd = -1;
                private int mapCount = 0;

                @Override
                public long getMapPageSize() {
                    return 65535;
                }

                @Override
                public long mmap(long fd, long len, long offset, int mode) {
                    // mess with the target FD
                    if (fd == this.fd) {
                        if (mapCount == 1) {
                            return -1;
                        }
                        mapCount++;
                    }
                    return super.mmap(fd, len, offset, mode);
                }

                @Override
                public long openRW(LPSZ name) {
                    // remember FD of the file we are targeting
                    if (Chars.endsWith(name, fileUnderAttack)) {
                        return fd = super.openRW(name);
                    }
                    return super.openRW(name);
                }
            };

            CairoConfiguration configuration = new DefaultCairoConfiguration(root) {
                @Override
                public FilesFacade getFilesFacade() {
                    return ff;
                }
            };

            SymbolGroup sg = new SymbolGroup(rnd, S, N, partitionBy, false);

            // align pseudo-random generators
            // we have to do this because asserting code will not be re-populating symbol group
            eRnd.syncWith(rnd);

            long timestamp = 0;
            if (!empty) {
                timestamp = sg.appendABC(AbstractCairoTest.configuration, rnd, N, timestamp, increment);
            }
            try (TableWriter writer = new TableWriter(configuration, "ABC")) {
                // first batch without problems
                try {
                    for (int i = 0; i < (long) N; i++) {
                        TableWriter.Row r = writer.newRow(timestamp += increment);
                        r.putSym(0, sg.symA[rnd.nextPositiveInt() % sg.S]);
                        r.putSym(1, sg.symB[rnd.nextPositiveInt() % sg.S]);
                        r.putSym(2, sg.symC[rnd.nextPositiveInt() % sg.S]);
                        r.putDouble(3, rnd.nextDouble2());
                        r.append();
                    }
                    writer.commit();
                    Assert.fail();
                } catch (CairoError ignored) {
                }
                // writer must be closed, we must not interact with writer anymore

                // test that we cannot commit
                try {
                    writer.commit();
                    Assert.fail();
                } catch (CairoError e) {
                    TestUtils.assertContains(e.getMessage(), "distressed");
                }

                // test that we cannot rollback
                try {
                    writer.rollback();
                    Assert.fail();
                } catch (CairoError e) {
                    TestUtils.assertContains(e.getMessage(), "distressed");
                }
            }


            // open another writer that would fail recovery
            // ft table is empty constructor should only attempt to recover non-partitioned ones
            if (empty && partitionBy == PartitionBy.NONE) {
                try {
                    new TableWriter(configuration, "ABC");
                    Assert.fail();
                } catch (CairoException ignore) {
                }
            }

            // lets see what we can read after this catastrophe
            try (TableReader reader = new TableReader(AbstractCairoTest.configuration, "ABC")) {
                FullFwdDataFrameCursor cursor = new FullFwdDataFrameCursor();
                TableReaderRecord record = new TableReaderRecord();

                Assert.assertEquals(expectedPartitionCount, reader.getPartitionCount());

                cursor.of(reader);
                record.of(reader);

                assertSymbolFoundInIndex(cursor, record, 0, empty ? 0 : N);
                cursor.toTop();
                assertSymbolFoundInIndex(cursor, record, 1, empty ? 0 : N);
                cursor.toTop();
                assertSymbolFoundInIndex(cursor, record, 2, empty ? 0 : N);
                cursor.toTop();
                assertIndexRowsMatchSymbol(cursor, record, 0, empty ? 0 : N);
                cursor.toTop();
                assertIndexRowsMatchSymbol(cursor, record, 1, empty ? 0 : N);
                cursor.toTop();
                assertIndexRowsMatchSymbol(cursor, record, 2, empty ? 0 : N);
                cursor.toTop();
                assertData(cursor, record, eRnd, sg, empty ? 0 : N);

                // we should be able to append more rows to new writer instance once the
                // original problem is resolved, e.g. system can mmap again

                sg.appendABC(AbstractCairoTest.configuration, rnd, N, timestamp, increment);

                Assert.assertTrue(cursor.reload());
                assertSymbolFoundInIndex(cursor, record, 0, empty ? N : N * 2);
                cursor.toTop();
                assertSymbolFoundInIndex(cursor, record, 1, empty ? N : N * 2);
                cursor.toTop();
                assertSymbolFoundInIndex(cursor, record, 2, empty ? N : N * 2);
                cursor.toTop();
                assertIndexRowsMatchSymbol(cursor, record, 0, empty ? N : N * 2);
                cursor.toTop();
                assertIndexRowsMatchSymbol(cursor, record, 1, empty ? N : N * 2);
                cursor.toTop();
                assertIndexRowsMatchSymbol(cursor, record, 2, empty ? N : N * 2);
            }
        });
    }

    private void testIndexFailureInConstructor(int partitionBy, long increment, boolean empty, String fileUnderAttack) throws Exception {
        TestUtils.assertMemoryLeak(() -> {
            int N = 10000;
            int S = 512;
            Rnd rnd = new Rnd();

            FilesFacade ff = new FilesFacadeImpl() {
                private long fd = -1;

                @Override
                public long getMapPageSize() {
                    return 65535;
                }

                @Override
                public long mmap(long fd, long len, long offset, int mode) {
                    // mess with the target FD
                    if (fd == this.fd) {
                        return -1;
                    }
                    return super.mmap(fd, len, offset, mode);
                }

                @Override
                public long openRW(LPSZ name) {
                    // remember FD of the file we are targeting
                    if (Chars.endsWith(name, fileUnderAttack)) {
                        return fd = super.openRW(name);
                    }
                    return super.openRW(name);
                }

                @Override
                public boolean remove(LPSZ name) {
                    // fail to remove file for good measure
                    return !Chars.endsWith(name, fileUnderAttack) && super.remove(name);
                }
            };

            CairoConfiguration configuration = new DefaultCairoConfiguration(root) {
                @Override
                public FilesFacade getFilesFacade() {
                    return ff;
                }
            };

            SymbolGroup sg = new SymbolGroup(rnd, S, N, partitionBy, false);

            long timestamp = 0;
            if (!empty) {
                timestamp = sg.appendABC(AbstractCairoTest.configuration, rnd, N, timestamp, increment);
            }

            try {
                new TableWriter(configuration, "ABC");
                Assert.fail();
            } catch (CairoException ignore) {
            }

            sg.appendABC(AbstractCairoTest.configuration, rnd, N, timestamp, increment);
        });
    }

    private void testParallelIndex(int partitionBy, long increment, int expectedPartitionMin, int testWorkStealing) throws Exception {
        TestUtils.assertMemoryLeak(() -> {
            int N = 1000000;
            int S = 128;
            Rnd rnd = new Rnd();

            SymbolGroup sg = new SymbolGroup(rnd, S, N, partitionBy, false);
            MPSequence pubSeq;
            MCSequence subSeq = null;

            switch (testWorkStealing) {
                case WORK_STEALING_BUSY_QUEUE:
                    pubSeq = new MPSequence(1024) {
                        @Override
                        public long next() {
                            return -1;
                        }
                    };
                    break;
                case WORK_STEALING_HIGH_CONTENTION:
                    pubSeq = new MPSequence(1024) {
                        private boolean flap = false;

                        @Override
                        public long next() {
                            boolean flap = this.flap;
                            this.flap = !this.flap;
                            return flap ? -1 : -2;
                        }
                    };
                    break;
                case WORK_STEALING_DONT_TEST:
                    pubSeq = new MPSequence(1024);
                    subSeq = new MCSequence(1024);
                    break;
                case WORK_STEALING_CAS_FLAP:
                    pubSeq = new MPSequence(1024) {
                        private boolean flap = true;

                        @Override
                        public long next() {
                            boolean flap = this.flap;
                            this.flap = !this.flap;
                            return flap ? -2 : super.next();
                        }
                    };
                    subSeq = new MCSequence(1024);
                    break;
                case WORK_STEALING_NO_PICKUP:
                    pubSeq = new MPSequence(1024);
                    break;
                default:
                    throw new RuntimeException("Unsupported test");
            }

            CairoConfiguration configuration = new DefaultCairoConfiguration(root) {
                @Override
                public int getParallelIndexThreshold() {
                    return 1;
                }
            };

            MyWorkScheduler workScheduler = new MyWorkScheduler(pubSeq, subSeq);
            if (subSeq != null) {
                workScheduler.addJob(new ColumnIndexerJob(workScheduler));
                workScheduler.start();
            }

            long timestamp = 0;
            try (TableWriter writer = new TableWriter(configuration, "ABC", workScheduler)) {
                for (int i = 0; i < N; i++) {
                    TableWriter.Row r = writer.newRow(timestamp += increment);
                    r.putSym(0, sg.symA[rnd.nextPositiveInt() % S]);
                    r.putSym(1, sg.symB[rnd.nextPositiveInt() % S]);
                    r.putSym(2, sg.symC[rnd.nextPositiveInt() % S]);
                    r.putDouble(3, rnd.nextDouble2());
                    r.append();
                }
                writer.commit();
            }

            workScheduler.halt();

            try (TableReader reader = new TableReader(configuration, "ABC")) {

                Assert.assertTrue(reader.getPartitionCount() > expectedPartitionMin);

                FullFwdDataFrameCursor cursor = new FullFwdDataFrameCursor();
                TableReaderRecord record = new TableReaderRecord();

                cursor.of(reader);
                record.of(reader);

                assertIndexRowsMatchSymbol(cursor, record, 0, N);
                cursor.toTop();
                assertIndexRowsMatchSymbol(cursor, record, 1, N);
                cursor.toTop();
                assertIndexRowsMatchSymbol(cursor, record, 2, N);
            }
        });
    }

    private void testParallelIndexFailureAtRuntime(int partitionBy, long increment, boolean empty, String fileUnderAttack, int expectedPartitionCount) throws Exception {
        TestUtils.assertMemoryLeak(() -> {
            int N = 10000;
            int S = 512;
            Rnd rnd = new Rnd();
            Rnd eRnd = new Rnd();

            FilesFacade ff = new FilesFacadeImpl() {
                private long fd = -1;
                private int mapCount = 0;

                @Override
                public long getMapPageSize() {
                    return 65535;
                }

                @Override
                public long mmap(long fd, long len, long offset, int mode) {
                    // mess with the target FD
                    if (fd == this.fd) {
                        if (mapCount == 1) {
                            return -1;
                        }
                        mapCount++;
                    }
                    return super.mmap(fd, len, offset, mode);
                }

                @Override
                public long openRW(LPSZ name) {
                    // remember FD of the file we are targeting
                    if (Chars.endsWith(name, fileUnderAttack)) {
                        return fd = super.openRW(name);
                    }
                    return super.openRW(name);
                }
            };

            CairoConfiguration configuration = new DefaultCairoConfiguration(root) {
                @Override
                public FilesFacade getFilesFacade() {
                    return ff;
                }

                @Override
                public int getParallelIndexThreshold() {
                    return 1;
                }
            };

            SymbolGroup sg = new SymbolGroup(rnd, S, N, partitionBy, false);

            // align pseudo-random generators
            // we have to do this because asserting code will not be re-populating symbol group
            eRnd.syncWith(rnd);

            long timestamp = 0;
            if (!empty) {
                timestamp = sg.appendABC(AbstractCairoTest.configuration, rnd, N, timestamp, increment);
            }

            final MyWorkScheduler workScheduler = new MyWorkScheduler();
            workScheduler.addJob(new ColumnIndexerJob(workScheduler));
            workScheduler.start();

            try (TableWriter writer = new TableWriter(configuration, "ABC", workScheduler)) {
                try {
                    for (int i = 0; i < (long) N; i++) {
                        TableWriter.Row r = writer.newRow(timestamp += increment);
                        r.putSym(0, sg.symA[rnd.nextPositiveInt() % sg.S]);
                        r.putSym(1, sg.symB[rnd.nextPositiveInt() % sg.S]);
                        r.putSym(2, sg.symC[rnd.nextPositiveInt() % sg.S]);
                        r.putDouble(3, rnd.nextDouble2());
                        r.append();
                    }
                    writer.commit();
                    Assert.fail();
                } catch (CairoError ignored) {
                }
                // writer must be closed, we must not interact with writer anymore

                // test that we cannot commit
                try {
                    writer.commit();
                    Assert.fail();
                } catch (CairoError e) {
                    TestUtils.assertContains(e.getMessage(), "distressed");
                }

                // test that we cannot rollback
                try {
                    writer.rollback();
                    Assert.fail();
                } catch (CairoError e) {
                    TestUtils.assertContains(e.getMessage(), "distressed");
                }
            }

            // open another writer that would fail recovery
            // constructor must attempt to recover non-partitioned empty table
            if (empty && partitionBy == PartitionBy.NONE) {
                try {
                    new TableWriter(configuration, "ABC");
                    Assert.fail();
                } catch (CairoException ignore) {
                }
            }

            workScheduler.halt();

            // lets see what we can read after this catastrophe
            try (TableReader reader = new TableReader(AbstractCairoTest.configuration, "ABC")) {
                FullFwdDataFrameCursor cursor = new FullFwdDataFrameCursor();
                TableReaderRecord record = new TableReaderRecord();

                Assert.assertEquals(expectedPartitionCount, reader.getPartitionCount());

                cursor.of(reader);
                record.of(reader);

                assertSymbolFoundInIndex(cursor, record, 0, empty ? 0 : N);
                cursor.toTop();
                assertSymbolFoundInIndex(cursor, record, 1, empty ? 0 : N);
                cursor.toTop();
                assertSymbolFoundInIndex(cursor, record, 2, empty ? 0 : N);
                cursor.toTop();
                assertIndexRowsMatchSymbol(cursor, record, 0, empty ? 0 : N);
                cursor.toTop();
                assertIndexRowsMatchSymbol(cursor, record, 1, empty ? 0 : N);
                cursor.toTop();
                assertIndexRowsMatchSymbol(cursor, record, 2, empty ? 0 : N);
                cursor.toTop();
                assertData(cursor, record, eRnd, sg, empty ? 0 : N);
                assertMetadataEquals(reader.getMetadata(), cursor.getTableReader().getMetadata());

                // we should be able to append more rows to new writer instance once the
                // original problem is resolved, e.g. system can mmap again

                sg.appendABC(AbstractCairoTest.configuration, rnd, N, timestamp, increment);

                Assert.assertTrue(cursor.reload());
                assertSymbolFoundInIndex(cursor, record, 0, empty ? N : N * 2);
                cursor.toTop();
                assertSymbolFoundInIndex(cursor, record, 1, empty ? N : N * 2);
                cursor.toTop();
                assertSymbolFoundInIndex(cursor, record, 2, empty ? N : N * 2);
                cursor.toTop();
                assertIndexRowsMatchSymbol(cursor, record, 0, empty ? N : N * 2);
                cursor.toTop();
                assertIndexRowsMatchSymbol(cursor, record, 1, empty ? N : N * 2);
                cursor.toTop();
                assertIndexRowsMatchSymbol(cursor, record, 2, empty ? N : N * 2);
            }
        });
    }

    private void testRemoveFirstColumn(int partitionBy, long increment, int expectedPartitionMin) throws Exception {
        TestUtils.assertMemoryLeak(() -> {
            final int N = 100;
            // separate two symbol columns with primitive. It will make problems apparent if index does not shift correctly
            try (TableModel model = new TableModel(configuration, "x", partitionBy).
                    col("a", ColumnType.STRING).
                    col("b", ColumnType.SYMBOL).indexed(true, N / 4).
                    col("i", ColumnType.INT).
                    col("c", ColumnType.SYMBOL).indexed(true, N / 4).
                    timestamp()
            ) {
                CairoTestUtils.create(model);
            }

            final Rnd rnd = new Rnd();
            final String symbols[] = new String[N];
            final int M = 1000;

            for (int i = 0; i < N; i++) {
                symbols[i] = rnd.nextChars(8).toString();
            }

            // prepare the data
            long timestamp = 0;
            try (TableWriter writer = new TableWriter(configuration, "x")) {
                for (int i = 0; i < M; i++) {
                    TableWriter.Row row = writer.newRow(timestamp += increment);
                    row.putStr(0, rnd.nextChars(20));
                    row.putSym(1, symbols[rnd.nextPositiveInt() % N]);
                    row.putInt(2, rnd.nextInt());
                    row.putSym(3, symbols[rnd.nextPositiveInt() % N]);
                    row.append();
                }
                writer.commit();

                try (TableReader reader = new TableReader(configuration, "x")) {
                    TableReaderRecord record = new TableReaderRecord();

                    Assert.assertTrue(reader.getPartitionCount() > expectedPartitionMin);

                    FullFwdDataFrameCursor cursor = new FullFwdDataFrameCursor();

                    // assert baseline
                    cursor.of(reader);
                    record.of(reader);

                    assertSymbolFoundInIndex(cursor, record, 1, M);
                    cursor.toTop();
                    assertSymbolFoundInIndex(cursor, record, 3, M);

                    writer.removeColumn("a");

                    // Indexes should shift left for both writer and reader
                    // To make sure writer is ok we add more rows
                    for (int i = 0; i < M; i++) {
                        TableWriter.Row row = writer.newRow(timestamp += increment);
                        row.putSym(0, symbols[rnd.nextPositiveInt() % N]);
                        row.putInt(1, rnd.nextInt());
                        row.putSym(2, symbols[rnd.nextPositiveInt() % N]);
                        row.append();
                    }
                    writer.commit();

                    cursor.reload();
                    assertSymbolFoundInIndex(cursor, record, 0, M * 2);
                    cursor.toTop();
                    assertSymbolFoundInIndex(cursor, record, 2, M * 2);
                    cursor.toTop();
                    assertIndexRowsMatchSymbol(cursor, record, 0, M * 2);
                    cursor.toTop();
                    assertIndexRowsMatchSymbol(cursor, record, 2, M * 2);
                }
            }
        });
    }

    private void testRemoveLastColumn(int partitionBy, long increment, int expectedPartitionMin) throws Exception {
        TestUtils.assertMemoryLeak(() -> {
            final int N = 100;
            // separate two symbol columns with primitive. It will make problems apparent if index does not shift correctly
            try (TableModel model = new TableModel(configuration, "x", partitionBy).
                    col("a", ColumnType.STRING).
                    col("b", ColumnType.SYMBOL).indexed(true, N / 4).
                    col("i", ColumnType.INT).indexed(false, 0).
                    col("c", ColumnType.SYMBOL).indexed(true, N / 4).
                    timestamp()
            ) {
                CairoTestUtils.create(model);
            }

            final Rnd rnd = new Rnd();
            final String symbols[] = new String[N];
            final int M = 1000;

            for (int i = 0; i < N; i++) {
                symbols[i] = rnd.nextChars(8).toString();
            }

            // prepare the data
            long timestamp = 0;
            try (TableWriter writer = new TableWriter(configuration, "x")) {
                for (int i = 0; i < M; i++) {
                    TableWriter.Row row = writer.newRow(timestamp += increment);
                    row.putStr(0, rnd.nextChars(20));
                    row.putSym(1, symbols[rnd.nextPositiveInt() % N]);
                    row.putInt(2, rnd.nextInt());
                    row.putSym(3, symbols[rnd.nextPositiveInt() % N]);
                    row.append();
                }
                writer.commit();

                try (TableReader reader = new TableReader(configuration, "x")) {
                    FullFwdDataFrameCursor cursor = new FullFwdDataFrameCursor();
                    TableReaderRecord record = new TableReaderRecord();

                    Assert.assertTrue(reader.getPartitionCount() > expectedPartitionMin);

                    // assert baseline
                    cursor.of(reader);
                    record.of(reader);

                    assertSymbolFoundInIndex(cursor, record, 1, M);
                    cursor.toTop();
                    assertSymbolFoundInIndex(cursor, record, 3, M);

                    writer.removeColumn("c");

                    // Indexes should shift left for both writer and reader
                    // To make sure writer is ok we add more rows
                    for (int i = 0; i < M; i++) {
                        TableWriter.Row row = writer.newRow(timestamp += increment);
                        row.putStr(0, rnd.nextChars(20));
                        row.putSym(1, symbols[rnd.nextPositiveInt() % N]);
                        row.putInt(2, rnd.nextInt());
                        row.append();
                    }
                    writer.commit();

                    cursor.reload();
                    assertSymbolFoundInIndex(cursor, record, 1, M * 2);
                    cursor.toTop();
                    assertIndexRowsMatchSymbol(cursor, record, 1, M * 2);
                }
            }
        });
    }

    private void testRemoveMidColumn(int partitionBy, long increment, int expectedPartitionMin) throws Exception {
        TestUtils.assertMemoryLeak(() -> {
            final int N = 100;
            // separate two symbol columns with primitive. It will make problems apparent if index does not shift correctly
            try (TableModel model = new TableModel(configuration, "x", partitionBy).
                    col("a", ColumnType.STRING).
                    col("b", ColumnType.SYMBOL).indexed(true, N / 4).
                    col("i", ColumnType.INT).
                    col("c", ColumnType.SYMBOL).indexed(true, N / 4).
                    timestamp()
            ) {
                CairoTestUtils.create(model);
            }

            final Rnd rnd = new Rnd();
            final String symbols[] = new String[N];
            final int M = 1000;

            for (int i = 0; i < N; i++) {
                symbols[i] = rnd.nextChars(8).toString();
            }

            // prepare the data
            long timestamp = 0;
            try (TableWriter writer = new TableWriter(configuration, "x")) {
                for (int i = 0; i < M; i++) {
                    TableWriter.Row row = writer.newRow(timestamp += increment);
                    row.putStr(0, rnd.nextChars(20));
                    row.putSym(1, symbols[rnd.nextPositiveInt() % N]);
                    row.putInt(2, rnd.nextInt());
                    row.putSym(3, symbols[rnd.nextPositiveInt() % N]);
                    row.append();
                }
                writer.commit();

                try (TableReader reader = new TableReader(configuration, "x")) {
                    FullFwdDataFrameCursor cursor = new FullFwdDataFrameCursor();
                    TableReaderRecord record = new TableReaderRecord();

                    Assert.assertTrue(reader.getPartitionCount() > expectedPartitionMin);


                    // assert baseline
                    cursor.of(reader);
                    record.of(reader);

                    assertSymbolFoundInIndex(cursor, record, 1, M);
                    cursor.toTop();
                    assertSymbolFoundInIndex(cursor, record, 3, M);

                    writer.removeColumn("i");

                    // Indexes should shift left for both writer and reader
                    // To make sure writer is ok we add more rows
                    for (int i = 0; i < M; i++) {
                        TableWriter.Row row = writer.newRow(timestamp += increment);
                        row.putStr(0, rnd.nextChars(20));
                        row.putSym(1, symbols[rnd.nextPositiveInt() % N]);
                        row.putSym(2, symbols[rnd.nextPositiveInt() % N]);
                        row.append();
                    }
                    writer.commit();

                    cursor.reload();
                    assertSymbolFoundInIndex(cursor, record, 1, M * 2);
                    cursor.toTop();
                    assertSymbolFoundInIndex(cursor, record, 2, M * 2);
                    cursor.toTop();
                    assertIndexRowsMatchSymbol(cursor, record, 1, M * 2);
                    cursor.toTop();
                    assertIndexRowsMatchSymbol(cursor, record, 2, M * 2);
                }
            }
        });
    }

    private void testReplaceIndexedColWithIndexed(int partitionBy, long increment, int expectedPartitionMin, boolean testRestricted) throws Exception {
        TestUtils.assertMemoryLeak(() -> {
            final int M = 1000;
            final int N = 100;

            // separate two symbol columns with primitive. It will make problems apparent if index does not shift correctly
            try (TableModel model = new TableModel(configuration, "x", partitionBy).
                    col("a", ColumnType.STRING).
                    col("b", ColumnType.SYMBOL).indexed(true, N / 4).
                    col("i", ColumnType.INT).
                    timestamp().
                    col("c", ColumnType.SYMBOL).indexed(true, N / 4)
            ) {
                CairoTestUtils.create(model);
            }

            final Rnd rnd = new Rnd();
            final String symbols[] = new String[N];

            for (int i = 0; i < N; i++) {
                symbols[i] = rnd.nextChars(8).toString();
            }

            // prepare the data
            long timestamp = 0;
            try (TableWriter writer = new TableWriter(configuration, "x")) {
                for (int i = 0; i < M; i++) {
                    TableWriter.Row row = writer.newRow(timestamp += increment);
                    row.putStr(0, rnd.nextChars(20));
                    row.putSym(1, symbols[rnd.nextPositiveInt() % N]);
                    row.putInt(2, rnd.nextInt());
                    row.putSym(4, symbols[rnd.nextPositiveInt() % N]);
                    row.append();
                }
                writer.commit();

                try (TableReader reader = new TableReader(configuration, "x")) {

                    final FullFwdDataFrameCursor cursor = new FullFwdDataFrameCursor();
                    final TableReaderRecord record = new TableReaderRecord();

                    Assert.assertTrue(reader.getPartitionCount() > expectedPartitionMin);


                    cursor.of(reader);
                    record.of(reader);

                    assertSymbolFoundInIndex(cursor, record, 1, M);
                    cursor.toTop();
                    assertSymbolFoundInIndex(cursor, record, 4, M);

                    if (testRestricted || configuration.getFilesFacade().isRestrictedFileSystem()) {
                        reader.closeColumnForRemove("c");
                    }

                    writer.removeColumn("c");
                    writer.addColumn("c", ColumnType.SYMBOL, N, true, true, 8);

                    for (int i = 0; i < M; i++) {
                        TableWriter.Row row = writer.newRow(timestamp += increment);
                        row.putStr(0, rnd.nextChars(20));
                        row.putSym(1, symbols[rnd.nextPositiveInt() % N]);
                        row.putInt(2, rnd.nextInt());
                        row.putSym(4, symbols[rnd.nextPositiveInt() % N]);
                        row.append();
                    }
                    writer.commit();

                    Assert.assertTrue(reader.reload());
                    cursor.reload();
                    assertSymbolFoundInIndex(cursor, record, 1, M * 2);
                    cursor.toTop();
                    assertSymbolFoundInIndex(cursor, record, 4, M * 2);
                }
            }
        });
    }

    private void testReplaceIndexedColWithUnindexed(int partitionBy, long increment, int expectedPartitionMin, boolean testRestricted) throws Exception {
        TestUtils.assertMemoryLeak(() -> {
            final int M = 1000;
            final int N = 100;

            // separate two symbol columns with primitive. It will make problems apparent if index does not shift correctly
            try (TableModel model = new TableModel(configuration, "x", partitionBy).
                    col("a", ColumnType.STRING).
                    col("b", ColumnType.SYMBOL).indexed(true, N / 4).
                    col("i", ColumnType.INT).
                    timestamp().
                    col("c", ColumnType.SYMBOL).indexed(true, N / 4)
            ) {
                CairoTestUtils.create(model);
            }

            final Rnd rnd = new Rnd();
            final String symbols[] = new String[N];

            for (int i = 0; i < N; i++) {
                symbols[i] = rnd.nextChars(8).toString();
            }

            // prepare the data
            long timestamp = 0;
            try (TableWriter writer = new TableWriter(configuration, "x")) {
                for (int i = 0; i < M; i++) {
                    TableWriter.Row row = writer.newRow(timestamp += increment);
                    row.putStr(0, rnd.nextChars(20));
                    row.putSym(1, symbols[rnd.nextPositiveInt() % N]);
                    row.putInt(2, rnd.nextInt());
                    row.putSym(4, symbols[rnd.nextPositiveInt() % N]);
                    row.append();
                }
                writer.commit();

                try (TableReader reader = new TableReader(configuration, "x")) {

                    final FullFwdDataFrameCursor cursor = new FullFwdDataFrameCursor();
                    final TableReaderRecord record = new TableReaderRecord();

                    Assert.assertTrue(reader.getPartitionCount() > expectedPartitionMin);


                    cursor.of(reader);
                    record.of(reader);

                    assertSymbolFoundInIndex(cursor, record, 1, M);
                    cursor.toTop();
                    assertSymbolFoundInIndex(cursor, record, 4, M);

                    if (testRestricted || configuration.getFilesFacade().isRestrictedFileSystem()) {
                        reader.closeColumnForRemove("c");
                    }

                    writer.removeColumn("c");
                    writer.addColumn("c", ColumnType.SYMBOL);

                    for (int i = 0; i < M; i++) {
                        TableWriter.Row row = writer.newRow(timestamp += increment);
                        row.putStr(0, rnd.nextChars(20));
                        row.putSym(1, symbols[rnd.nextPositiveInt() % N]);
                        row.putInt(2, rnd.nextInt());
                        row.putSym(4, symbols[rnd.nextPositiveInt() % N]);
                        row.append();
                    }
                    writer.commit();

                    Assert.assertTrue(reader.reload());
                    cursor.reload();
                    assertSymbolFoundInIndex(cursor, record, 1, M * 2);
                    cursor.toTop();
                    assertNoIndex(cursor);
                }
            }
        });
    }

    private void testReplaceUnindexedColWithIndexed(int partitionBy, long increment, int expectedPartitionMin, boolean testRestricted) throws Exception {
        TestUtils.assertMemoryLeak(() -> {
            final int M = 1000;
            final int N = 100;

            // separate two symbol columns with primitive. It will make problems apparent if index does not shift correctly
            try (TableModel model = new TableModel(configuration, "x", partitionBy).
                    col("a", ColumnType.STRING).
                    col("b", ColumnType.SYMBOL).indexed(true, N / 4).
                    col("i", ColumnType.INT).
                    timestamp().
                    col("c", ColumnType.SYMBOL)
            ) {
                CairoTestUtils.create(model);
            }

            final Rnd rnd = new Rnd();
            final String symbols[] = new String[N];

            for (int i = 0; i < N; i++) {
                symbols[i] = rnd.nextChars(8).toString();
            }

            // prepare the data
            long timestamp = 0;
            try (TableWriter writer = new TableWriter(configuration, "x")) {
                for (int i = 0; i < M; i++) {
                    TableWriter.Row row = writer.newRow(timestamp += increment);
                    row.putStr(0, rnd.nextChars(20));
                    row.putSym(1, symbols[rnd.nextPositiveInt() % N]);
                    row.putInt(2, rnd.nextInt());
                    row.putSym(4, symbols[rnd.nextPositiveInt() % N]);
                    row.append();
                }
                writer.commit();

                try (TableReader reader = new TableReader(configuration, "x")) {

                    final FullFwdDataFrameCursor cursor = new FullFwdDataFrameCursor();
                    final TableReaderRecord record = new TableReaderRecord();

                    Assert.assertTrue(reader.getPartitionCount() > expectedPartitionMin);


                    cursor.of(reader);
                    record.of(reader);

                    assertSymbolFoundInIndex(cursor, record, 1, M);
                    cursor.toTop();
                    assertNoIndex(cursor);

                    if (testRestricted || configuration.getFilesFacade().isRestrictedFileSystem()) {
                        reader.closeColumnForRemove("c");
                    }

                    writer.removeColumn("c");
                    writer.addColumn("c", ColumnType.SYMBOL, N, true, true, 8);

                    for (int i = 0; i < M; i++) {
                        TableWriter.Row row = writer.newRow(timestamp += increment);
                        row.putStr(0, rnd.nextChars(20));
                        row.putSym(1, symbols[rnd.nextPositiveInt() % N]);
                        row.putInt(2, rnd.nextInt());
                        row.putSym(4, rnd.nextPositiveInt() % 16 == 0 ? null : symbols[rnd.nextPositiveInt() % N]);
                        row.append();
                    }
                    writer.commit();

                    Assert.assertTrue(reader.reload());
                    cursor.reload();
                    assertSymbolFoundInIndex(cursor, record, 1, M * 2);
                    cursor.toTop();
                    assertSymbolFoundInIndex(cursor, record, 4, M * 2);
                    cursor.toTop();
                    assertIndexRowsMatchSymbol(cursor, record, 4, M * 2);
                }
            }
        });
    }

    private void testSymbolIndexRead(int partitionBy, long increment, int expectedPartitionMin) throws Exception {
        TestUtils.assertMemoryLeak(() -> {
            final int N = 100;
            try (TableModel model = new TableModel(configuration, "x", partitionBy).
                    col("a", ColumnType.SYMBOL).indexed(true, N / 4).
                    timestamp()
            ) {
                CairoTestUtils.create(model);
            }

            final Rnd rnd = new Rnd();
            final String symbols[] = new String[N];
            final int M = 1000;

            for (int i = 0; i < N; i++) {
                symbols[i] = rnd.nextChars(8).toString();
            }

            // prepare the data
            long timestamp = 0;
            try (TableWriter writer = new TableWriter(configuration, "x")) {
                populateTable(writer, symbols, rnd, timestamp, increment);
                writer.commit();
            }

            // check that each symbol in table exists in index as well
            // and current row is collection of index rows
            try (TableReader reader = new TableReader(configuration, "x")) {

                // Open data frame cursor. This one will frame table as collection of
                // partitions, each partition is a frame.
                FullFwdDataFrameCursor cursor = new FullFwdDataFrameCursor();
                // TableRecord will help us read the table. We need to position this record using
                // "recordIndex" and "columnBase".
                TableReaderRecord record = new TableReaderRecord();

                Assert.assertTrue(reader.getPartitionCount() > expectedPartitionMin);

                cursor.of(reader);
                record.of(reader);

                assertSymbolFoundInIndex(cursor, record, 0, M);
                cursor.toTop();
                assertSymbolFoundInIndex(cursor, record, 0, M);
                cursor.toTop();
                assertIndexRowsMatchSymbol(cursor, record, 0, M);
            }
        });
    }

    private void testSymbolIndexReadAfterRollback(int partitionBy, long increment, int expectedPartitionMin) throws Exception {
        TestUtils.assertMemoryLeak(() -> {
            final int N = 100;
            try (TableModel model = new TableModel(configuration, "x", partitionBy).
                    col("a", ColumnType.SYMBOL).indexed(true, N / 4).
                    timestamp()
            ) {
                CairoTestUtils.create(model);
            }

            final Rnd rnd = new Rnd();
            final String symbols[] = new String[N];
            final int M = 1000;

            for (int i = 0; i < N; i++) {
                symbols[i] = rnd.nextChars(8).toString();
            }

            // prepare the data, make sure rollback does the job
            long timestamp = 0;

            try (TableWriter writer = new TableWriter(configuration, "x")) {
                timestamp = populateTable(writer, symbols, rnd, timestamp, increment);
                writer.commit();
                timestamp = populateTable(writer, symbols, rnd, timestamp, increment);
                writer.rollback();
                populateTable(writer, symbols, rnd, timestamp, increment);
                writer.commit();
            }

            // check that each symbol in table exists in index as well
            // and current row is collection of index rows
            try (TableReader reader = new TableReader(configuration, "x")) {

                // Open data frame cursor. This one will frame table as collection of
                // partitions, each partition is a frame.
                FullFwdDataFrameCursor cursor = new FullFwdDataFrameCursor();
                // TableRecord will help us read the table. We need to position this record using
                // "recordIndex" and "columnBase".
                TableReaderRecord record = new TableReaderRecord();

                Assert.assertTrue(reader.getPartitionCount() > expectedPartitionMin);

                cursor.of(reader);
                record.of(reader);

                assertSymbolFoundInIndex(cursor, record, 0, M * 2);
                cursor.toTop();
                assertSymbolFoundInIndex(cursor, record, 0, M * 2);
                cursor.toTop();
                assertIndexRowsMatchSymbol(cursor, record, 0, M * 2);
            }
        });
    }

    private static class SymbolGroup {

        final String[] symA;
        final String[] symB;
        final String[] symC;
        final int S;

        public SymbolGroup(Rnd rnd, int S, int N, int partitionBy, boolean useDefaultBlockSize) {
            this.S = S;
            symA = new String[S];
            symB = new String[S];
            symC = new String[S];

            for (int i = 0; i < S; i++) {
                symA[i] = rnd.nextChars(10).toString();
                symB[i] = rnd.nextChars(8).toString();
                symC[i] = rnd.nextChars(10).toString();
            }

            int indexBlockSize;
            if (useDefaultBlockSize) {
                indexBlockSize = configuration.getIndexValueBlockSize();
            } else {
                indexBlockSize = N / S;
            }

            try (TableModel model = new TableModel(configuration, "ABC", partitionBy)
                    .col("a", ColumnType.SYMBOL).indexed(true, indexBlockSize)
                    .col("b", ColumnType.SYMBOL).indexed(true, indexBlockSize)
                    .col("c", ColumnType.SYMBOL).indexed(true, indexBlockSize)
                    .col("d", ColumnType.DOUBLE)
                    .timestamp()) {
                CairoTestUtils.create(model);
            }
        }

        long appendABC(CairoConfiguration configuration, Rnd rnd, long N, long timestamp, long increment) {
            try (TableWriter writer = new TableWriter(configuration, "ABC")) {
                // first batch without problems
                for (int i = 0; i < N; i++) {
                    TableWriter.Row r = writer.newRow(timestamp += increment);
                    r.putSym(0, symA[rnd.nextPositiveInt() % S]);
                    r.putSym(1, symB[rnd.nextPositiveInt() % S]);
                    r.putSym(2, symC[rnd.nextPositiveInt() % S]);
                    r.putDouble(3, rnd.nextDouble2());
                    r.append();
                }
                writer.commit();
            }
            return timestamp;
        }
    }

    final static class MyWorkScheduler implements CairoWorkScheduler {
        private final int nWorkers = 2;
        private final CountDownLatch workerHaltLatch = new CountDownLatch(nWorkers);
        private final Worker workers[] = new Worker[nWorkers];
        private final RingQueue<ColumnIndexerEntry> queue = new RingQueue<>(ColumnIndexerEntry::new, 1024);
        private final Sequence pubSeq;
        private final Sequence subSeq;
        private final ObjHashSet<Job> jobs = new ObjHashSet<>();
        private boolean active = false;

        public MyWorkScheduler(Sequence pubSequence, Sequence subSequence) {
            this.pubSeq = pubSequence;
            this.subSeq = subSequence;
        }

        public MyWorkScheduler() {
            this(new MPSequence(1024), new MCSequence(1024));
        }

        @Override
        public void addJob(Job job) {
            jobs.add(job);
        }

        @Override
        public Sequence getIndexerPubSequence() {
            return pubSeq;
        }

        @Override
        public RingQueue<ColumnIndexerEntry> getIndexerQueue() {
            return queue;
        }

        @Override
        public Sequence getIndexerSubSequence() {
            return subSeq;
        }

        void halt() throws InterruptedException {
            if (active) {
                for (int i = 0; i < nWorkers; i++) {
                    workers[i].halt();
                }
                workerHaltLatch.await();
            }
        }

        void start() {
            pubSeq.then(subSeq).then(pubSeq);
            for (int i = 0; i < nWorkers; i++) {
                workers[i] = new Worker(jobs, workerHaltLatch);
                workers[i].start();
            }
            active = true;
        }
    }
}