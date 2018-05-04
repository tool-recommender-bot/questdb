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
import com.questdb.cairo.Engine;
import com.questdb.cairo.SymbolMapReader;
import com.questdb.cairo.TableReader;
import com.questdb.cairo.sql.RecordCursorFactory;
import com.questdb.common.ColumnType;
import com.questdb.common.PartitionBy;
import com.questdb.griffin.engine.functions.bind.BindVariableService;
import com.questdb.griffin.engine.functions.rnd.SharedRandom;
import com.questdb.std.Rnd;
import com.questdb.test.tools.TestUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

public class SqlCompilerTest extends AbstractCairoTest {
    private final static Engine engine = new Engine(configuration);
    private final static SqlCompiler compiler = new SqlCompiler(engine, configuration);
    private final static BindVariableService bindVariableService = new BindVariableService();

    @Test
    public void assertCastString() throws SqlException, IOException {
        final String expectedData = "a\n" +
                "JWCPS\n" +
                "\n" +
                "RXPEHNRXG\n" +
                "\n" +
                "\n" +
                "XIBBT\n" +
                "GWFFY\n" +
                "EYYQEHBHFO\n" +
                "PDXYSBEOUO\n" +
                "HRUEDRQQUL\n" +
                "JGETJRSZS\n" +
                "RFBVTMHGO\n" +
                "ZVDZJMY\n" +
                "CXZOUICWEK\n" +
                "VUVSDOTS\n" +
                "YYCTG\n" +
                "LYXWCKYLSU\n" +
                "SWUGSHOLNV\n" +
                "\n" +
                "BZXIOVI\n";

        String expectedMeta = "{\"columnCount\":1,\"columns\":[{\"index\":0,\"name\":\"a\",\"type\":\"SYMBOL\"}],\"timestampIndex\":-1}";

        String sql = "create table y as (" +
                "select * from random_cursor(" +
                " 20," + // record count
                " 'a', rnd_str(5,10,2)" +
                ")), cast(a as SYMBOL)";

        assertCast(expectedData, expectedMeta, sql);
    }

    @Test
    public void assertCastSymbol() throws SqlException, IOException {
        final String expectedData = "a\n" +
                "CPSW\n" +
                "HYRX\n" +
                "\n" +
                "VTJW\n" +
                "PEHN\n" +
                "\n" +
                "VTJW\n" +
                "\n" +
                "CPSW\n" +
                "\n" +
                "PEHN\n" +
                "CPSW\n" +
                "VTJW\n" +
                "\n" +
                "\n" +
                "CPSW\n" +
                "\n" +
                "\n" +
                "\n" +
                "PEHN\n";

        String expectedMeta = "{\"columnCount\":1,\"columns\":[{\"index\":0,\"name\":\"a\",\"type\":\"STRING\"}],\"timestampIndex\":-1}";

        String sql = "create table y as (" +
                "select * from random_cursor(" +
                " 20," + // record count
                " 'a', rnd_symbol(4,4,4,2)" +
                ")), cast(a as STRING)";

        assertCast(expectedData, expectedMeta, sql);
    }


    @Before
    public void setUp2() {
        SharedRandom.RANDOM.set(new Rnd());
    }

    @After
    public void tearDown() {
        engine.releaseAllReaders();
        engine.releaseAllWriters();
    }

    @Test
    public void testCastByteDate() throws SqlException, IOException {
        assertCastByte("a\n" +
                        "1970-01-01T00:00:00.119Z\n" +
                        "1970-01-01T00:00:00.052Z\n" +
                        "1970-01-01T00:00:00.091Z\n" +
                        "1970-01-01T00:00:00.097Z\n" +
                        "1970-01-01T00:00:00.119Z\n" +
                        "1970-01-01T00:00:00.107Z\n" +
                        "1970-01-01T00:00:00.039Z\n" +
                        "1970-01-01T00:00:00.081Z\n" +
                        "1970-01-01T00:00:00.046Z\n" +
                        "1970-01-01T00:00:00.041Z\n" +
                        "1970-01-01T00:00:00.061Z\n" +
                        "1970-01-01T00:00:00.082Z\n" +
                        "1970-01-01T00:00:00.075Z\n" +
                        "1970-01-01T00:00:00.095Z\n" +
                        "1970-01-01T00:00:00.087Z\n" +
                        "1970-01-01T00:00:00.116Z\n" +
                        "1970-01-01T00:00:00.087Z\n" +
                        "1970-01-01T00:00:00.040Z\n" +
                        "1970-01-01T00:00:00.116Z\n" +
                        "1970-01-01T00:00:00.117Z\n",
                ColumnType.DATE);
    }

    @Test
    public void testCastByteDouble() throws SqlException, IOException {
        assertCastByte("a\n" +
                        "119.000000000000\n" +
                        "52.000000000000\n" +
                        "91.000000000000\n" +
                        "97.000000000000\n" +
                        "119.000000000000\n" +
                        "107.000000000000\n" +
                        "39.000000000000\n" +
                        "81.000000000000\n" +
                        "46.000000000000\n" +
                        "41.000000000000\n" +
                        "61.000000000000\n" +
                        "82.000000000000\n" +
                        "75.000000000000\n" +
                        "95.000000000000\n" +
                        "87.000000000000\n" +
                        "116.000000000000\n" +
                        "87.000000000000\n" +
                        "40.000000000000\n" +
                        "116.000000000000\n" +
                        "117.000000000000\n",
                ColumnType.DOUBLE);
    }

    @Test
    public void testCastByteFloat() throws SqlException, IOException {
        assertCastByte("a\n" +
                        "119.0000\n" +
                        "52.0000\n" +
                        "91.0000\n" +
                        "97.0000\n" +
                        "119.0000\n" +
                        "107.0000\n" +
                        "39.0000\n" +
                        "81.0000\n" +
                        "46.0000\n" +
                        "41.0000\n" +
                        "61.0000\n" +
                        "82.0000\n" +
                        "75.0000\n" +
                        "95.0000\n" +
                        "87.0000\n" +
                        "116.0000\n" +
                        "87.0000\n" +
                        "40.0000\n" +
                        "116.0000\n" +
                        "117.0000\n",
                ColumnType.FLOAT);
    }

    @Test
    public void testCastByteInt() throws SqlException, IOException {
        assertCastByte("a\n" +
                        "119\n" +
                        "52\n" +
                        "91\n" +
                        "97\n" +
                        "119\n" +
                        "107\n" +
                        "39\n" +
                        "81\n" +
                        "46\n" +
                        "41\n" +
                        "61\n" +
                        "82\n" +
                        "75\n" +
                        "95\n" +
                        "87\n" +
                        "116\n" +
                        "87\n" +
                        "40\n" +
                        "116\n" +
                        "117\n",
                ColumnType.INT);
    }

    @Test
    public void testCastByteLong() throws SqlException, IOException {
        assertCastByte("a\n" +
                        "119\n" +
                        "52\n" +
                        "91\n" +
                        "97\n" +
                        "119\n" +
                        "107\n" +
                        "39\n" +
                        "81\n" +
                        "46\n" +
                        "41\n" +
                        "61\n" +
                        "82\n" +
                        "75\n" +
                        "95\n" +
                        "87\n" +
                        "116\n" +
                        "87\n" +
                        "40\n" +
                        "116\n" +
                        "117\n",
                ColumnType.LONG);
    }

    @Test
    public void testCastByteShort() throws SqlException, IOException {
        assertCastByte("a\n" +
                        "119\n" +
                        "52\n" +
                        "91\n" +
                        "97\n" +
                        "119\n" +
                        "107\n" +
                        "39\n" +
                        "81\n" +
                        "46\n" +
                        "41\n" +
                        "61\n" +
                        "82\n" +
                        "75\n" +
                        "95\n" +
                        "87\n" +
                        "116\n" +
                        "87\n" +
                        "40\n" +
                        "116\n" +
                        "117\n",
                ColumnType.SHORT);
    }

    @Test
    public void testCastByteTimestamp() throws SqlException, IOException {
        assertCastByte("a\n" +
                        "1970-01-01T00:00:00.000119Z\n" +
                        "1970-01-01T00:00:00.000052Z\n" +
                        "1970-01-01T00:00:00.000091Z\n" +
                        "1970-01-01T00:00:00.000097Z\n" +
                        "1970-01-01T00:00:00.000119Z\n" +
                        "1970-01-01T00:00:00.000107Z\n" +
                        "1970-01-01T00:00:00.000039Z\n" +
                        "1970-01-01T00:00:00.000081Z\n" +
                        "1970-01-01T00:00:00.000046Z\n" +
                        "1970-01-01T00:00:00.000041Z\n" +
                        "1970-01-01T00:00:00.000061Z\n" +
                        "1970-01-01T00:00:00.000082Z\n" +
                        "1970-01-01T00:00:00.000075Z\n" +
                        "1970-01-01T00:00:00.000095Z\n" +
                        "1970-01-01T00:00:00.000087Z\n" +
                        "1970-01-01T00:00:00.000116Z\n" +
                        "1970-01-01T00:00:00.000087Z\n" +
                        "1970-01-01T00:00:00.000040Z\n" +
                        "1970-01-01T00:00:00.000116Z\n" +
                        "1970-01-01T00:00:00.000117Z\n",
                ColumnType.TIMESTAMP);
    }

    @Test
    public void testCastDateByte() throws SqlException, IOException {
        assertCastDate("a\n" +
                        "11\n" +
                        "0\n" +
                        "121\n" +
                        "-2\n" +
                        "0\n" +
                        "-43\n" +
                        "-124\n" +
                        "100\n" +
                        "0\n" +
                        "124\n" +
                        "0\n" +
                        "-45\n" +
                        "0\n" +
                        "24\n" +
                        "-16\n" +
                        "58\n" +
                        "0\n" +
                        "-6\n" +
                        "73\n" +
                        "125\n",
                ColumnType.BYTE);
    }

    @Test
    public void testCastDateDouble() throws SqlException, IOException {
        assertCastDate("a\n" +
                        "1.426297242379E12\n" +
                        "-9.223372036854776E18\n" +
                        "1.446081058169E12\n" +
                        "1.434834113022E12\n" +
                        "-9.223372036854776E18\n" +
                        "1.439739868373E12\n" +
                        "1.443957889668E12\n" +
                        "1.440280260964E12\n" +
                        "-9.223372036854776E18\n" +
                        "1.44318380966E12\n" +
                        "-9.223372036854776E18\n" +
                        "1.435298544851E12\n" +
                        "-9.223372036854776E18\n" +
                        "1.447181628184E12\n" +
                        "1.4423615004E12\n" +
                        "1.428165287226E12\n" +
                        "-9.223372036854776E18\n" +
                        "1.434999533562E12\n" +
                        "1.423736755529E12\n" +
                        "1.426566352765E12\n",
                ColumnType.DOUBLE);
    }

    @Test
    public void testCastDateFloat() throws SqlException, IOException {
        assertCastDate("a\n" +
                        "1.42629719E12\n" +
                        "-9.223372E18\n" +
                        "1.44608107E12\n" +
                        "1.43483417E12\n" +
                        "-9.223372E18\n" +
                        "1.43973981E12\n" +
                        "1.44395783E12\n" +
                        "1.44028022E12\n" +
                        "-9.223372E18\n" +
                        "1.44318385E12\n" +
                        "-9.223372E18\n" +
                        "1.43529856E12\n" +
                        "-9.223372E18\n" +
                        "1.44718168E12\n" +
                        "1.44236151E12\n" +
                        "1.42816523E12\n" +
                        "-9.223372E18\n" +
                        "1.43499959E12\n" +
                        "1.4237367E12\n" +
                        "1.42656641E12\n",
                ColumnType.FLOAT);
    }

    @Test
    public void testCastDateInt() throws SqlException, IOException {
        assertCastDate("a\n" +
                        "368100107\n" +
                        "0\n" +
                        "-1322920583\n" +
                        "315036158\n" +
                        "0\n" +
                        "925824213\n" +
                        "848878212\n" +
                        "1466216804\n" +
                        "0\n" +
                        "74798204\n" +
                        "0\n" +
                        "779467987\n" +
                        "0\n" +
                        "-222350568\n" +
                        "-747511056\n" +
                        "-2058822342\n" +
                        "0\n" +
                        "480456698\n" +
                        "2102580553\n" +
                        "637210493\n",
                ColumnType.INT);
    }

    @Test
    public void testCastDateLong() throws SqlException, IOException {
        assertCastDate("a\n" +
                        "1426297242379\n" +
                        "NaN\n" +
                        "1446081058169\n" +
                        "1434834113022\n" +
                        "NaN\n" +
                        "1439739868373\n" +
                        "1443957889668\n" +
                        "1440280260964\n" +
                        "NaN\n" +
                        "1443183809660\n" +
                        "NaN\n" +
                        "1435298544851\n" +
                        "NaN\n" +
                        "1447181628184\n" +
                        "1442361500400\n" +
                        "1428165287226\n" +
                        "NaN\n" +
                        "1434999533562\n" +
                        "1423736755529\n" +
                        "1426566352765\n",
                ColumnType.LONG);
    }

    @Test
    public void testCastDateShort() throws SqlException, IOException {
        assertCastDate("a\n" +
                        "-15605\n" +
                        "0\n" +
                        "-10887\n" +
                        "4606\n" +
                        "0\n" +
                        "-2859\n" +
                        "-9596\n" +
                        "-20124\n" +
                        "0\n" +
                        "21628\n" +
                        "0\n" +
                        "-17197\n" +
                        "0\n" +
                        "13080\n" +
                        "-7440\n" +
                        "-8902\n" +
                        "0\n" +
                        "12282\n" +
                        "-10935\n" +
                        "3965\n",
                ColumnType.SHORT);
    }

    @Test
    public void testCastDateTimestamp() throws SqlException, IOException {
        assertCastDate("a\n" +
                        "1970-01-17T12:11:37.242379Z\n" +
                        "\n" +
                        "1970-01-17T17:41:21.058169Z\n" +
                        "1970-01-17T14:33:54.113022Z\n" +
                        "\n" +
                        "1970-01-17T15:55:39.868373Z\n" +
                        "1970-01-17T17:05:57.889668Z\n" +
                        "1970-01-17T16:04:40.260964Z\n" +
                        "\n" +
                        "1970-01-17T16:53:03.809660Z\n" +
                        "\n" +
                        "1970-01-17T14:41:38.544851Z\n" +
                        "\n" +
                        "1970-01-17T17:59:41.628184Z\n" +
                        "1970-01-17T16:39:21.500400Z\n" +
                        "1970-01-17T12:42:45.287226Z\n" +
                        "\n" +
                        "1970-01-17T14:36:39.533562Z\n" +
                        "1970-01-17T11:28:56.755529Z\n" +
                        "1970-01-17T12:16:06.352765Z\n",
                ColumnType.TIMESTAMP);
    }

    @Test
    public void testCastDoubleByte() throws SqlException, IOException {
        assertCastDouble("a\n" +
                        "80\n" +
                        "8\n" +
                        "8\n" +
                        "65\n" +
                        "79\n" +
                        "22\n" +
                        "34\n" +
                        "76\n" +
                        "42\n" +
                        "0\n" +
                        "72\n" +
                        "42\n" +
                        "70\n" +
                        "38\n" +
                        "0\n" +
                        "32\n" +
                        "0\n" +
                        "97\n" +
                        "24\n" +
                        "63\n",
                ColumnType.BYTE);
    }

    @Test
    public void testCastDoubleDate() throws SqlException, IOException {
        assertCastDouble("a\n" +
                        "1970-01-01T00:00:00.080Z\n" +
                        "1970-01-01T00:00:00.008Z\n" +
                        "1970-01-01T00:00:00.008Z\n" +
                        "1970-01-01T00:00:00.065Z\n" +
                        "1970-01-01T00:00:00.079Z\n" +
                        "1970-01-01T00:00:00.022Z\n" +
                        "1970-01-01T00:00:00.034Z\n" +
                        "1970-01-01T00:00:00.076Z\n" +
                        "1970-01-01T00:00:00.042Z\n" +
                        "1970-01-01T00:00:00.000Z\n" +
                        "1970-01-01T00:00:00.072Z\n" +
                        "1970-01-01T00:00:00.042Z\n" +
                        "1970-01-01T00:00:00.070Z\n" +
                        "1970-01-01T00:00:00.038Z\n" +
                        "1970-01-01T00:00:00.000Z\n" +
                        "1970-01-01T00:00:00.032Z\n" +
                        "1970-01-01T00:00:00.000Z\n" +
                        "1970-01-01T00:00:00.097Z\n" +
                        "1970-01-01T00:00:00.024Z\n" +
                        "1970-01-01T00:00:00.063Z\n",
                ColumnType.DATE);
    }

    @Test
    public void testCastDoubleFloat() throws SqlException, IOException {
        assertCastDouble("a\n" +
                        "80.4322\n" +
                        "8.4870\n" +
                        "8.4383\n" +
                        "65.0859\n" +
                        "79.0568\n" +
                        "22.4523\n" +
                        "34.9107\n" +
                        "76.1103\n" +
                        "42.1777\n" +
                        "NaN\n" +
                        "72.6114\n" +
                        "42.2436\n" +
                        "70.9436\n" +
                        "38.5399\n" +
                        "0.3598\n" +
                        "32.8818\n" +
                        "NaN\n" +
                        "97.7110\n" +
                        "24.8088\n" +
                        "63.8161\n",
                ColumnType.FLOAT);
    }

    @Test
    public void testCastDoubleInt() throws SqlException, IOException {
        assertCastDouble("a\n" +
                        "80\n" +
                        "8\n" +
                        "8\n" +
                        "65\n" +
                        "79\n" +
                        "22\n" +
                        "34\n" +
                        "76\n" +
                        "42\n" +
                        "0\n" +
                        "72\n" +
                        "42\n" +
                        "70\n" +
                        "38\n" +
                        "0\n" +
                        "32\n" +
                        "0\n" +
                        "97\n" +
                        "24\n" +
                        "63\n",
                ColumnType.INT);
    }

    @Test
    public void testCastDoubleLong() throws SqlException, IOException {
        assertCastDouble("a\n" +
                        "80\n" +
                        "8\n" +
                        "8\n" +
                        "65\n" +
                        "79\n" +
                        "22\n" +
                        "34\n" +
                        "76\n" +
                        "42\n" +
                        "0\n" +
                        "72\n" +
                        "42\n" +
                        "70\n" +
                        "38\n" +
                        "0\n" +
                        "32\n" +
                        "0\n" +
                        "97\n" +
                        "24\n" +
                        "63\n",
                ColumnType.LONG);
    }

    @Test
    public void testCastDoubleShort() throws SqlException, IOException {
        assertCastDouble("a\n" +
                        "80\n" +
                        "8\n" +
                        "8\n" +
                        "65\n" +
                        "79\n" +
                        "22\n" +
                        "34\n" +
                        "76\n" +
                        "42\n" +
                        "0\n" +
                        "72\n" +
                        "42\n" +
                        "70\n" +
                        "38\n" +
                        "0\n" +
                        "32\n" +
                        "0\n" +
                        "97\n" +
                        "24\n" +
                        "63\n",
                ColumnType.SHORT);
    }

    @Test
    public void testCastDoubleTimestamp() throws SqlException, IOException {
        assertCastDouble("a\n" +
                        "1970-01-01T00:00:00.000080Z\n" +
                        "1970-01-01T00:00:00.000008Z\n" +
                        "1970-01-01T00:00:00.000008Z\n" +
                        "1970-01-01T00:00:00.000065Z\n" +
                        "1970-01-01T00:00:00.000079Z\n" +
                        "1970-01-01T00:00:00.000022Z\n" +
                        "1970-01-01T00:00:00.000034Z\n" +
                        "1970-01-01T00:00:00.000076Z\n" +
                        "1970-01-01T00:00:00.000042Z\n" +
                        "1970-01-01T00:00:00.000000Z\n" +
                        "1970-01-01T00:00:00.000072Z\n" +
                        "1970-01-01T00:00:00.000042Z\n" +
                        "1970-01-01T00:00:00.000070Z\n" +
                        "1970-01-01T00:00:00.000038Z\n" +
                        "1970-01-01T00:00:00.000000Z\n" +
                        "1970-01-01T00:00:00.000032Z\n" +
                        "1970-01-01T00:00:00.000000Z\n" +
                        "1970-01-01T00:00:00.000097Z\n" +
                        "1970-01-01T00:00:00.000024Z\n" +
                        "1970-01-01T00:00:00.000063Z\n",
                ColumnType.TIMESTAMP);
    }

    @Test
    public void testCastFloatByte() throws SqlException, IOException {
        assertCastFloat("a\n" +
                        "80\n" +
                        "0\n" +
                        "8\n" +
                        "29\n" +
                        "0\n" +
                        "93\n" +
                        "13\n" +
                        "79\n" +
                        "0\n" +
                        "22\n" +
                        "0\n" +
                        "34\n" +
                        "0\n" +
                        "76\n" +
                        "52\n" +
                        "55\n" +
                        "0\n" +
                        "72\n" +
                        "62\n" +
                        "66\n",
                ColumnType.BYTE);
    }

    @Test
    public void testCastFloatDate() throws SqlException, IOException {
        assertCastFloat("a\n" +
                        "1970-01-01T00:00:00.080Z\n" +
                        "1970-01-01T00:00:00.000Z\n" +
                        "1970-01-01T00:00:00.008Z\n" +
                        "1970-01-01T00:00:00.029Z\n" +
                        "1970-01-01T00:00:00.000Z\n" +
                        "1970-01-01T00:00:00.093Z\n" +
                        "1970-01-01T00:00:00.013Z\n" +
                        "1970-01-01T00:00:00.079Z\n" +
                        "1970-01-01T00:00:00.000Z\n" +
                        "1970-01-01T00:00:00.022Z\n" +
                        "1970-01-01T00:00:00.000Z\n" +
                        "1970-01-01T00:00:00.034Z\n" +
                        "1970-01-01T00:00:00.000Z\n" +
                        "1970-01-01T00:00:00.076Z\n" +
                        "1970-01-01T00:00:00.052Z\n" +
                        "1970-01-01T00:00:00.055Z\n" +
                        "1970-01-01T00:00:00.000Z\n" +
                        "1970-01-01T00:00:00.072Z\n" +
                        "1970-01-01T00:00:00.062Z\n" +
                        "1970-01-01T00:00:00.066Z\n",
                ColumnType.DATE);
    }

    @Test
    public void testCastFloatDouble() throws SqlException, IOException {
        assertCastFloat("a\n" +
                        "80.432235717773\n" +
                        "NaN\n" +
                        "8.486962318420\n" +
                        "29.919904708862\n" +
                        "NaN\n" +
                        "93.446044921875\n" +
                        "13.123357772827\n" +
                        "79.056755065918\n" +
                        "NaN\n" +
                        "22.452337265015\n" +
                        "NaN\n" +
                        "34.910701751709\n" +
                        "NaN\n" +
                        "76.110290527344\n" +
                        "52.437229156494\n" +
                        "55.991615295410\n" +
                        "NaN\n" +
                        "72.611358642578\n" +
                        "62.769538879395\n" +
                        "66.938369750977\n",
                ColumnType.DOUBLE);
    }

    @Test
    public void testCastFloatInt() throws SqlException, IOException {
        assertCastFloat("a\n" +
                        "80\n" +
                        "0\n" +
                        "8\n" +
                        "29\n" +
                        "0\n" +
                        "93\n" +
                        "13\n" +
                        "79\n" +
                        "0\n" +
                        "22\n" +
                        "0\n" +
                        "34\n" +
                        "0\n" +
                        "76\n" +
                        "52\n" +
                        "55\n" +
                        "0\n" +
                        "72\n" +
                        "62\n" +
                        "66\n",
                ColumnType.INT);
    }

    @Test
    public void testCastFloatLong() throws SqlException, IOException {
        assertCastFloat("a\n" +
                        "80\n" +
                        "0\n" +
                        "8\n" +
                        "29\n" +
                        "0\n" +
                        "93\n" +
                        "13\n" +
                        "79\n" +
                        "0\n" +
                        "22\n" +
                        "0\n" +
                        "34\n" +
                        "0\n" +
                        "76\n" +
                        "52\n" +
                        "55\n" +
                        "0\n" +
                        "72\n" +
                        "62\n" +
                        "66\n",
                ColumnType.LONG);
    }

    @Test
    public void testCastFloatShort() throws SqlException, IOException {
        assertCastFloat("a\n" +
                        "80\n" +
                        "0\n" +
                        "8\n" +
                        "29\n" +
                        "0\n" +
                        "93\n" +
                        "13\n" +
                        "79\n" +
                        "0\n" +
                        "22\n" +
                        "0\n" +
                        "34\n" +
                        "0\n" +
                        "76\n" +
                        "52\n" +
                        "55\n" +
                        "0\n" +
                        "72\n" +
                        "62\n" +
                        "66\n",
                ColumnType.SHORT);
    }

    @Test
    public void testCastFloatTimestamp() throws SqlException, IOException {
        assertCastFloat("a\n" +
                        "1970-01-01T00:00:00.000080Z\n" +
                        "1970-01-01T00:00:00.000000Z\n" +
                        "1970-01-01T00:00:00.000008Z\n" +
                        "1970-01-01T00:00:00.000029Z\n" +
                        "1970-01-01T00:00:00.000000Z\n" +
                        "1970-01-01T00:00:00.000093Z\n" +
                        "1970-01-01T00:00:00.000013Z\n" +
                        "1970-01-01T00:00:00.000079Z\n" +
                        "1970-01-01T00:00:00.000000Z\n" +
                        "1970-01-01T00:00:00.000022Z\n" +
                        "1970-01-01T00:00:00.000000Z\n" +
                        "1970-01-01T00:00:00.000034Z\n" +
                        "1970-01-01T00:00:00.000000Z\n" +
                        "1970-01-01T00:00:00.000076Z\n" +
                        "1970-01-01T00:00:00.000052Z\n" +
                        "1970-01-01T00:00:00.000055Z\n" +
                        "1970-01-01T00:00:00.000000Z\n" +
                        "1970-01-01T00:00:00.000072Z\n" +
                        "1970-01-01T00:00:00.000062Z\n" +
                        "1970-01-01T00:00:00.000066Z\n",
                ColumnType.TIMESTAMP);
    }

    @Test
    public void testCastIntByte() throws SqlException, IOException {
        assertCastInt("a\n" +
                "1\n" +
                "0\n" +
                "22\n" +
                "22\n" +
                "0\n" +
                "7\n" +
                "26\n" +
                "26\n" +
                "0\n" +
                "13\n" +
                "0\n" +
                "0\n" +
                "0\n" +
                "25\n" +
                "21\n" +
                "23\n" +
                "0\n" +
                "6\n" +
                "19\n" +
                "7\n", ColumnType.BYTE);
    }

    @Test
    public void testCastIntDate() throws SqlException, IOException {
        assertCastInt("a\n" +
                        "1970-01-01T00:00:00.001Z\n" +
                        "1969-12-07T03:28:36.352Z\n" +
                        "1970-01-01T00:00:00.022Z\n" +
                        "1970-01-01T00:00:00.022Z\n" +
                        "1969-12-07T03:28:36.352Z\n" +
                        "1970-01-01T00:00:00.007Z\n" +
                        "1970-01-01T00:00:00.026Z\n" +
                        "1970-01-01T00:00:00.026Z\n" +
                        "1969-12-07T03:28:36.352Z\n" +
                        "1970-01-01T00:00:00.013Z\n" +
                        "1969-12-07T03:28:36.352Z\n" +
                        "1970-01-01T00:00:00.000Z\n" +
                        "1969-12-07T03:28:36.352Z\n" +
                        "1970-01-01T00:00:00.025Z\n" +
                        "1970-01-01T00:00:00.021Z\n" +
                        "1970-01-01T00:00:00.023Z\n" +
                        "1969-12-07T03:28:36.352Z\n" +
                        "1970-01-01T00:00:00.006Z\n" +
                        "1970-01-01T00:00:00.019Z\n" +
                        "1970-01-01T00:00:00.007Z\n",
                ColumnType.DATE);
    }

    @Test
    public void testCastIntDouble() throws SqlException, IOException {
        assertCastInt("a\n" +
                        "1.000000000000\n" +
                        "-2.147483648E9\n" +
                        "22.000000000000\n" +
                        "22.000000000000\n" +
                        "-2.147483648E9\n" +
                        "7.000000000000\n" +
                        "26.000000000000\n" +
                        "26.000000000000\n" +
                        "-2.147483648E9\n" +
                        "13.000000000000\n" +
                        "-2.147483648E9\n" +
                        "0.000000000000\n" +
                        "-2.147483648E9\n" +
                        "25.000000000000\n" +
                        "21.000000000000\n" +
                        "23.000000000000\n" +
                        "-2.147483648E9\n" +
                        "6.000000000000\n" +
                        "19.000000000000\n" +
                        "7.000000000000\n",
                ColumnType.DOUBLE);
    }

    @Test
    public void testCastIntFloat() throws SqlException, IOException {
        assertCastInt("a\n" +
                        "1.0000\n" +
                        "-2.14748365E9\n" +
                        "22.0000\n" +
                        "22.0000\n" +
                        "-2.14748365E9\n" +
                        "7.0000\n" +
                        "26.0000\n" +
                        "26.0000\n" +
                        "-2.14748365E9\n" +
                        "13.0000\n" +
                        "-2.14748365E9\n" +
                        "0.0000\n" +
                        "-2.14748365E9\n" +
                        "25.0000\n" +
                        "21.0000\n" +
                        "23.0000\n" +
                        "-2.14748365E9\n" +
                        "6.0000\n" +
                        "19.0000\n" +
                        "7.0000\n",
                ColumnType.FLOAT);
    }

    @Test
    public void testCastIntLong() throws SqlException, IOException {
        assertCastInt("a\n" +
                "1\n" +
                "-2147483648\n" +
                "22\n" +
                "22\n" +
                "-2147483648\n" +
                "7\n" +
                "26\n" +
                "26\n" +
                "-2147483648\n" +
                "13\n" +
                "-2147483648\n" +
                "0\n" +
                "-2147483648\n" +
                "25\n" +
                "21\n" +
                "23\n" +
                "-2147483648\n" +
                "6\n" +
                "19\n" +
                "7\n", ColumnType.LONG);
    }

    @Test
    public void testCastIntShort() throws SqlException, IOException {
        assertCastInt("a\n" +
                        "1\n" +
                        "0\n" +
                        "22\n" +
                        "22\n" +
                        "0\n" +
                        "7\n" +
                        "26\n" +
                        "26\n" +
                        "0\n" +
                        "13\n" +
                        "0\n" +
                        "0\n" +
                        "0\n" +
                        "25\n" +
                        "21\n" +
                        "23\n" +
                        "0\n" +
                        "6\n" +
                        "19\n" +
                        "7\n",
                ColumnType.SHORT);
    }

    @Test
    public void testCastIntTimestamp() throws SqlException, IOException {
        String expectedData = "a\n" +
                "1970-01-01T00:00:00.000001Z\n" +
                "1969-12-31T23:24:12.516352Z\n" +
                "1970-01-01T00:00:00.000022Z\n" +
                "1970-01-01T00:00:00.000022Z\n" +
                "1969-12-31T23:24:12.516352Z\n" +
                "1970-01-01T00:00:00.000007Z\n" +
                "1970-01-01T00:00:00.000026Z\n" +
                "1970-01-01T00:00:00.000026Z\n" +
                "1969-12-31T23:24:12.516352Z\n" +
                "1970-01-01T00:00:00.000013Z\n" +
                "1969-12-31T23:24:12.516352Z\n" +
                "1970-01-01T00:00:00.000000Z\n" +
                "1969-12-31T23:24:12.516352Z\n" +
                "1970-01-01T00:00:00.000025Z\n" +
                "1970-01-01T00:00:00.000021Z\n" +
                "1970-01-01T00:00:00.000023Z\n" +
                "1969-12-31T23:24:12.516352Z\n" +
                "1970-01-01T00:00:00.000006Z\n" +
                "1970-01-01T00:00:00.000019Z\n" +
                "1970-01-01T00:00:00.000007Z\n";
        assertCastInt(expectedData, ColumnType.TIMESTAMP);
    }

    @Test
    public void testCastLongByte() throws SqlException, IOException {
        assertCastLong("a\n" +
                        "22\n" +
                        "0\n" +
                        "17\n" +
                        "2\n" +
                        "0\n" +
                        "21\n" +
                        "1\n" +
                        "20\n" +
                        "0\n" +
                        "14\n" +
                        "0\n" +
                        "26\n" +
                        "0\n" +
                        "23\n" +
                        "2\n" +
                        "24\n" +
                        "0\n" +
                        "16\n" +
                        "10\n" +
                        "6\n",
                ColumnType.BYTE);
    }

    @Test
    public void testCastLongDate() throws SqlException, IOException {
        assertCastLong("a\n" +
                        "1970-01-01T00:00:00.022Z\n" +
                        "\n" +
                        "1970-01-01T00:00:00.017Z\n" +
                        "1970-01-01T00:00:00.002Z\n" +
                        "\n" +
                        "1970-01-01T00:00:00.021Z\n" +
                        "1970-01-01T00:00:00.001Z\n" +
                        "1970-01-01T00:00:00.020Z\n" +
                        "\n" +
                        "1970-01-01T00:00:00.014Z\n" +
                        "\n" +
                        "1970-01-01T00:00:00.026Z\n" +
                        "\n" +
                        "1970-01-01T00:00:00.023Z\n" +
                        "1970-01-01T00:00:00.002Z\n" +
                        "1970-01-01T00:00:00.024Z\n" +
                        "\n" +
                        "1970-01-01T00:00:00.016Z\n" +
                        "1970-01-01T00:00:00.010Z\n" +
                        "1970-01-01T00:00:00.006Z\n",
                ColumnType.DATE);
    }

    @Test
    public void testCastLongDouble() throws SqlException, IOException {
        assertCastLong("a\n" +
                        "22.000000000000\n" +
                        "-9.223372036854776E18\n" +
                        "17.000000000000\n" +
                        "2.000000000000\n" +
                        "-9.223372036854776E18\n" +
                        "21.000000000000\n" +
                        "1.000000000000\n" +
                        "20.000000000000\n" +
                        "-9.223372036854776E18\n" +
                        "14.000000000000\n" +
                        "-9.223372036854776E18\n" +
                        "26.000000000000\n" +
                        "-9.223372036854776E18\n" +
                        "23.000000000000\n" +
                        "2.000000000000\n" +
                        "24.000000000000\n" +
                        "-9.223372036854776E18\n" +
                        "16.000000000000\n" +
                        "10.000000000000\n" +
                        "6.000000000000\n",
                ColumnType.DOUBLE);
    }

    @Test
    public void testCastLongFloat() throws SqlException, IOException {
        assertCastLong("a\n" +
                        "22.0000\n" +
                        "-9.223372E18\n" +
                        "17.0000\n" +
                        "2.0000\n" +
                        "-9.223372E18\n" +
                        "21.0000\n" +
                        "1.0000\n" +
                        "20.0000\n" +
                        "-9.223372E18\n" +
                        "14.0000\n" +
                        "-9.223372E18\n" +
                        "26.0000\n" +
                        "-9.223372E18\n" +
                        "23.0000\n" +
                        "2.0000\n" +
                        "24.0000\n" +
                        "-9.223372E18\n" +
                        "16.0000\n" +
                        "10.0000\n" +
                        "6.0000\n",
                ColumnType.FLOAT);
    }

    @Test
    public void testCastLongInt() throws SqlException, IOException {
        assertCastLong("a\n" +
                        "22\n" +
                        "0\n" +
                        "17\n" +
                        "2\n" +
                        "0\n" +
                        "21\n" +
                        "1\n" +
                        "20\n" +
                        "0\n" +
                        "14\n" +
                        "0\n" +
                        "26\n" +
                        "0\n" +
                        "23\n" +
                        "2\n" +
                        "24\n" +
                        "0\n" +
                        "16\n" +
                        "10\n" +
                        "6\n",
                ColumnType.INT);
    }

    @Test
    public void testCastLongShort() throws SqlException, IOException {
        assertCastLong("a\n" +
                        "22\n" +
                        "0\n" +
                        "17\n" +
                        "2\n" +
                        "0\n" +
                        "21\n" +
                        "1\n" +
                        "20\n" +
                        "0\n" +
                        "14\n" +
                        "0\n" +
                        "26\n" +
                        "0\n" +
                        "23\n" +
                        "2\n" +
                        "24\n" +
                        "0\n" +
                        "16\n" +
                        "10\n" +
                        "6\n",
                ColumnType.SHORT);
    }

    @Test
    public void testCastLongTimestamp() throws SqlException, IOException {
        assertCastLong("a\n" +
                        "1970-01-01T00:00:00.000022Z\n" +
                        "\n" +
                        "1970-01-01T00:00:00.000017Z\n" +
                        "1970-01-01T00:00:00.000002Z\n" +
                        "\n" +
                        "1970-01-01T00:00:00.000021Z\n" +
                        "1970-01-01T00:00:00.000001Z\n" +
                        "1970-01-01T00:00:00.000020Z\n" +
                        "\n" +
                        "1970-01-01T00:00:00.000014Z\n" +
                        "\n" +
                        "1970-01-01T00:00:00.000026Z\n" +
                        "\n" +
                        "1970-01-01T00:00:00.000023Z\n" +
                        "1970-01-01T00:00:00.000002Z\n" +
                        "1970-01-01T00:00:00.000024Z\n" +
                        "\n" +
                        "1970-01-01T00:00:00.000016Z\n" +
                        "1970-01-01T00:00:00.000010Z\n" +
                        "1970-01-01T00:00:00.000006Z\n",
                ColumnType.TIMESTAMP);
    }

    @Test
    public void testCastNumberFail() {
        assertCastIntFail(ColumnType.BOOLEAN);
        assertCastLongFail(ColumnType.BOOLEAN);
        assertCastByteFail(ColumnType.BOOLEAN);
        assertCastShortFail(ColumnType.BOOLEAN);
        assertCastFloatFail(ColumnType.BOOLEAN);
        assertCastDoubleFail(ColumnType.BOOLEAN);

        assertCastIntFail(ColumnType.STRING);
        assertCastLongFail(ColumnType.STRING);
        assertCastByteFail(ColumnType.STRING);
        assertCastShortFail(ColumnType.STRING);
        assertCastFloatFail(ColumnType.STRING);
        assertCastDoubleFail(ColumnType.STRING);

        assertCastIntFail(ColumnType.SYMBOL);
        assertCastLongFail(ColumnType.SYMBOL);
        assertCastByteFail(ColumnType.SYMBOL);
        assertCastShortFail(ColumnType.SYMBOL);
        assertCastFloatFail(ColumnType.SYMBOL);
        assertCastDoubleFail(ColumnType.SYMBOL);

        assertCastIntFail(ColumnType.BINARY);
        assertCastLongFail(ColumnType.BINARY);
        assertCastByteFail(ColumnType.BINARY);
        assertCastShortFail(ColumnType.BINARY);
        assertCastFloatFail(ColumnType.BINARY);
        assertCastDoubleFail(ColumnType.BINARY);

        assertCastStringFail(ColumnType.BYTE);
        assertCastStringFail(ColumnType.SHORT);
        assertCastStringFail(ColumnType.INT);
        assertCastStringFail(ColumnType.LONG);
        assertCastStringFail(ColumnType.FLOAT);
        assertCastStringFail(ColumnType.DOUBLE);
        assertCastStringFail(ColumnType.DATE);
        assertCastStringFail(ColumnType.TIMESTAMP);

        assertCastSymbolFail(ColumnType.BYTE);
        assertCastSymbolFail(ColumnType.SHORT);
        assertCastSymbolFail(ColumnType.INT);
        assertCastSymbolFail(ColumnType.LONG);
        assertCastSymbolFail(ColumnType.FLOAT);
        assertCastSymbolFail(ColumnType.DOUBLE);
        assertCastSymbolFail(ColumnType.DATE);
        assertCastSymbolFail(ColumnType.TIMESTAMP);
    }

    @Test
    public void testCastShortByte() throws SqlException, IOException {
        assertCastShort("a\n" +
                        "-106\n" +
                        "-42\n" +
                        "-76\n" +
                        "-41\n" +
                        "-41\n" +
                        "-107\n" +
                        "117\n" +
                        "3\n" +
                        "-35\n" +
                        "21\n" +
                        "38\n" +
                        "-25\n" +
                        "46\n" +
                        "-8\n" +
                        "-120\n" +
                        "101\n" +
                        "30\n" +
                        "-122\n" +
                        "52\n" +
                        "91\n",
                ColumnType.BYTE);
    }

    @Test
    public void testCastShortDate() throws SqlException, IOException {
        assertCastShort("a\n" +
                        "1970-01-01T00:00:01.430Z\n" +
                        "1970-01-01T00:00:01.238Z\n" +
                        "1970-01-01T00:00:01.204Z\n" +
                        "1970-01-01T00:00:01.751Z\n" +
                        "1970-01-01T00:00:01.751Z\n" +
                        "1970-01-01T00:00:01.429Z\n" +
                        "1970-01-01T00:00:01.397Z\n" +
                        "1970-01-01T00:00:01.539Z\n" +
                        "1970-01-01T00:00:01.501Z\n" +
                        "1970-01-01T00:00:01.045Z\n" +
                        "1970-01-01T00:00:01.318Z\n" +
                        "1970-01-01T00:00:01.255Z\n" +
                        "1970-01-01T00:00:01.838Z\n" +
                        "1970-01-01T00:00:01.784Z\n" +
                        "1970-01-01T00:00:01.928Z\n" +
                        "1970-01-01T00:00:01.381Z\n" +
                        "1970-01-01T00:00:01.822Z\n" +
                        "1970-01-01T00:00:01.414Z\n" +
                        "1970-01-01T00:00:01.588Z\n" +
                        "1970-01-01T00:00:01.371Z\n",
                ColumnType.DATE);
    }

    @Test
    public void testCastShortDouble() throws SqlException, IOException {
        assertCastShort("a\n" +
                        "1430.000000000000\n" +
                        "1238.000000000000\n" +
                        "1204.000000000000\n" +
                        "1751.000000000000\n" +
                        "1751.000000000000\n" +
                        "1429.000000000000\n" +
                        "1397.000000000000\n" +
                        "1539.000000000000\n" +
                        "1501.000000000000\n" +
                        "1045.000000000000\n" +
                        "1318.000000000000\n" +
                        "1255.000000000000\n" +
                        "1838.000000000000\n" +
                        "1784.000000000000\n" +
                        "1928.000000000000\n" +
                        "1381.000000000000\n" +
                        "1822.000000000000\n" +
                        "1414.000000000000\n" +
                        "1588.000000000000\n" +
                        "1371.000000000000\n",
                ColumnType.DOUBLE);
    }

    @Test
    public void testCastShortFloat() throws SqlException, IOException {
        assertCastShort("a\n" +
                        "1430.0000\n" +
                        "1238.0000\n" +
                        "1204.0000\n" +
                        "1751.0000\n" +
                        "1751.0000\n" +
                        "1429.0000\n" +
                        "1397.0000\n" +
                        "1539.0000\n" +
                        "1501.0000\n" +
                        "1045.0000\n" +
                        "1318.0000\n" +
                        "1255.0000\n" +
                        "1838.0000\n" +
                        "1784.0000\n" +
                        "1928.0000\n" +
                        "1381.0000\n" +
                        "1822.0000\n" +
                        "1414.0000\n" +
                        "1588.0000\n" +
                        "1371.0000\n",
                ColumnType.FLOAT);
    }

    @Test
    public void testCastShortInt() throws SqlException, IOException {
        assertCastShort("a\n" +
                        "1430\n" +
                        "1238\n" +
                        "1204\n" +
                        "1751\n" +
                        "1751\n" +
                        "1429\n" +
                        "1397\n" +
                        "1539\n" +
                        "1501\n" +
                        "1045\n" +
                        "1318\n" +
                        "1255\n" +
                        "1838\n" +
                        "1784\n" +
                        "1928\n" +
                        "1381\n" +
                        "1822\n" +
                        "1414\n" +
                        "1588\n" +
                        "1371\n",
                ColumnType.INT);
    }

    @Test
    public void testCastShortLong() throws SqlException, IOException {
        assertCastShort("a\n" +
                        "1430\n" +
                        "1238\n" +
                        "1204\n" +
                        "1751\n" +
                        "1751\n" +
                        "1429\n" +
                        "1397\n" +
                        "1539\n" +
                        "1501\n" +
                        "1045\n" +
                        "1318\n" +
                        "1255\n" +
                        "1838\n" +
                        "1784\n" +
                        "1928\n" +
                        "1381\n" +
                        "1822\n" +
                        "1414\n" +
                        "1588\n" +
                        "1371\n",
                ColumnType.LONG);
    }

    @Test
    public void testCastShortTimestamp() throws SqlException, IOException {
        assertCastShort("a\n" +
                        "1970-01-01T00:00:00.001430Z\n" +
                        "1970-01-01T00:00:00.001238Z\n" +
                        "1970-01-01T00:00:00.001204Z\n" +
                        "1970-01-01T00:00:00.001751Z\n" +
                        "1970-01-01T00:00:00.001751Z\n" +
                        "1970-01-01T00:00:00.001429Z\n" +
                        "1970-01-01T00:00:00.001397Z\n" +
                        "1970-01-01T00:00:00.001539Z\n" +
                        "1970-01-01T00:00:00.001501Z\n" +
                        "1970-01-01T00:00:00.001045Z\n" +
                        "1970-01-01T00:00:00.001318Z\n" +
                        "1970-01-01T00:00:00.001255Z\n" +
                        "1970-01-01T00:00:00.001838Z\n" +
                        "1970-01-01T00:00:00.001784Z\n" +
                        "1970-01-01T00:00:00.001928Z\n" +
                        "1970-01-01T00:00:00.001381Z\n" +
                        "1970-01-01T00:00:00.001822Z\n" +
                        "1970-01-01T00:00:00.001414Z\n" +
                        "1970-01-01T00:00:00.001588Z\n" +
                        "1970-01-01T00:00:00.001371Z\n",
                ColumnType.TIMESTAMP);
    }

    @Test
    public void testCastTimestampByte() throws SqlException, IOException {
        assertCastTimestamp("a\n" +
                        "89\n" +
                        "0\n" +
                        "-19\n" +
                        "-99\n" +
                        "0\n" +
                        "-102\n" +
                        "86\n" +
                        "83\n" +
                        "0\n" +
                        "30\n" +
                        "0\n" +
                        "-128\n" +
                        "0\n" +
                        "-115\n" +
                        "-106\n" +
                        "-76\n" +
                        "0\n" +
                        "25\n" +
                        "30\n" +
                        "-69\n",
                ColumnType.BYTE);
    }

    @Test
    public void testCastTimestampDate() throws SqlException, IOException {
        assertCastTimestamp("a\n" +
                        "47956-10-13T01:43:12.217Z\n" +
                        "\n" +
                        "47830-07-03T01:52:05.101Z\n" +
                        "47946-01-25T16:00:41.629Z\n" +
                        "\n" +
                        "47133-10-04T06:29:09.402Z\n" +
                        "47578-08-06T19:13:17.654Z\n" +
                        "47813-04-30T15:45:24.307Z\n" +
                        "\n" +
                        "47370-09-18T01:29:39.870Z\n" +
                        "\n" +
                        "47817-03-02T05:38:00.192Z\n" +
                        "\n" +
                        "47502-10-03T01:46:21.965Z\n" +
                        "47627-07-07T11:02:25.686Z\n" +
                        "47630-01-25T00:47:44.820Z\n" +
                        "\n" +
                        "47620-04-14T19:43:29.561Z\n" +
                        "47725-11-11T00:22:36.062Z\n" +
                        "47867-11-08T16:30:43.643Z\n",
                ColumnType.DATE);
    }

    @Test
    public void testCastTimestampDouble() throws SqlException, IOException {
        assertCastTimestamp("a\n" +
                        "1.451202658992217E15\n" +
                        "-9.223372036854776E18\n" +
                        "1.447217632325101E15\n" +
                        "1.450864540841629E15\n" +
                        "-9.223372036854776E18\n" +
                        "1.425230490549402E15\n" +
                        "1.439268289997654E15\n" +
                        "1.446675695124307E15\n" +
                        "-9.223372036854776E18\n" +
                        "1.43270813337987E15\n" +
                        "-9.223372036854776E18\n" +
                        "1.446796791480192E15\n" +
                        "-9.223372036854776E18\n" +
                        "1.436874860781965E15\n" +
                        "1.440811969345686E15\n" +
                        "1.44089254366482E15\n" +
                        "-9.223372036854776E18\n" +
                        "1.440583904609561E15\n" +
                        "1.443915505356062E15\n" +
                        "1.448396353843643E15\n",
                ColumnType.DOUBLE);
    }

    @Test
    public void testCastTimestampFloat() throws SqlException, IOException {
        assertCastTimestamp("a\n" +
                        "1.45120261E15\n" +
                        "-9.223372E18\n" +
                        "1.44721768E15\n" +
                        "1.45086451E15\n" +
                        "-9.223372E18\n" +
                        "1.42523054E15\n" +
                        "1.43926824E15\n" +
                        "1.44667571E15\n" +
                        "-9.223372E18\n" +
                        "1.43270808E15\n" +
                        "-9.223372E18\n" +
                        "1.44679678E15\n" +
                        "-9.223372E18\n" +
                        "1.43687487E15\n" +
                        "1.44081201E15\n" +
                        "1.44089254E15\n" +
                        "-9.223372E18\n" +
                        "1.44058384E15\n" +
                        "1.44391553E15\n" +
                        "1.44839638E15\n",
                ColumnType.FLOAT);
    }

    @Test
    public void testCastTimestampInt() throws SqlException, IOException {
        assertCastTimestamp("a\n" +
                        "1929150553\n" +
                        "0\n" +
                        "-1662833171\n" +
                        "-1181550947\n" +
                        "0\n" +
                        "1427946650\n" +
                        "-1020695722\n" +
                        "1860812627\n" +
                        "0\n" +
                        "1532714782\n" +
                        "0\n" +
                        "-1596883072\n" +
                        "0\n" +
                        "2141839757\n" +
                        "765393046\n" +
                        "-264666444\n" +
                        "0\n" +
                        "333923609\n" +
                        "-959951586\n" +
                        "237646267\n",
                ColumnType.INT);
    }

    @Test
    public void testCastTimestampLong() throws SqlException, IOException {
        assertCastTimestamp("a\n" +
                        "1451202658992217\n" +
                        "NaN\n" +
                        "1447217632325101\n" +
                        "1450864540841629\n" +
                        "NaN\n" +
                        "1425230490549402\n" +
                        "1439268289997654\n" +
                        "1446675695124307\n" +
                        "NaN\n" +
                        "1432708133379870\n" +
                        "NaN\n" +
                        "1446796791480192\n" +
                        "NaN\n" +
                        "1436874860781965\n" +
                        "1440811969345686\n" +
                        "1440892543664820\n" +
                        "NaN\n" +
                        "1440583904609561\n" +
                        "1443915505356062\n" +
                        "1448396353843643\n",
                ColumnType.LONG);
    }

    @Test
    public void testCastTimestampShort() throws SqlException, IOException {
        assertCastTimestamp("a\n" +
                        "-32679\n" +
                        "0\n" +
                        "11757\n" +
                        "-2403\n" +
                        "0\n" +
                        "-17254\n" +
                        "27478\n" +
                        "-16557\n" +
                        "0\n" +
                        "24350\n" +
                        "0\n" +
                        "32640\n" +
                        "0\n" +
                        "-7795\n" +
                        "-1898\n" +
                        "-32076\n" +
                        "0\n" +
                        "17689\n" +
                        "19742\n" +
                        "12731\n",
                ColumnType.SHORT);
    }

    @Test
    public void testCreateEmptyTableNoPartition() throws SqlException {
        compiler.execute("create table x (" +
                        "a INT, " +
                        "b BYTE, " +
                        "c SHORT, " +
                        "d LONG, " +
                        "e FLOAT, " +
                        "f DOUBLE, " +
                        "g DATE, " +
                        "h BINARY, " +
                        "t TIMESTAMP, " +
                        "x SYMBOL capacity 16 cache, " +
                        "z STRING, " +
                        "y BOOLEAN) " +
                        "timestamp(t)",
                bindVariableService
        );

        try (TableReader reader = engine.getReader("x")) {
            sink.clear();
            reader.getMetadata().toJson(sink);
            TestUtils.assertEquals(
                    "{\"columnCount\":12,\"columns\":[{\"index\":0,\"name\":\"a\",\"type\":\"INT\"},{\"index\":1,\"name\":\"b\",\"type\":\"BYTE\"},{\"index\":2,\"name\":\"c\",\"type\":\"SHORT\"},{\"index\":3,\"name\":\"d\",\"type\":\"LONG\"},{\"index\":4,\"name\":\"e\",\"type\":\"FLOAT\"},{\"index\":5,\"name\":\"f\",\"type\":\"DOUBLE\"},{\"index\":6,\"name\":\"g\",\"type\":\"DATE\"},{\"index\":7,\"name\":\"h\",\"type\":\"BINARY\"},{\"index\":8,\"name\":\"t\",\"type\":\"TIMESTAMP\"},{\"index\":9,\"name\":\"x\",\"type\":\"SYMBOL\"},{\"index\":10,\"name\":\"z\",\"type\":\"STRING\"},{\"index\":11,\"name\":\"y\",\"type\":\"BOOLEAN\"}],\"timestampIndex\":8}",
                    sink);

            Assert.assertEquals(PartitionBy.NONE, reader.getPartitionedBy());
            Assert.assertEquals(0L, reader.size());

            int symbolIndex = reader.getMetadata().getColumnIndex("x");
            SymbolMapReader symbolMapReader = reader.getSymbolMapReader(symbolIndex);
            Assert.assertNotNull(symbolMapReader);
            Assert.assertEquals(16, symbolMapReader.getSymbolCapacity());
            Assert.assertTrue(symbolMapReader.isCached());
        }
    }

    @Test
    public void testCreateEmptyTableNoTimestamp() throws SqlException {
        compiler.execute("create table x (" +
                        "a INT, " +
                        "b BYTE, " +
                        "c SHORT, " +
                        "d LONG, " +
                        "e FLOAT, " +
                        "f DOUBLE, " +
                        "g DATE, " +
                        "h BINARY, " +
                        "t TIMESTAMP, " +
                        "x SYMBOL capacity 16 cache, " +
                        "z STRING, " +
                        "y BOOLEAN) " +
                        "partition by MONTH",
                bindVariableService
        );

        try (TableReader reader = engine.getReader("x")) {
            sink.clear();
            reader.getMetadata().toJson(sink);
            TestUtils.assertEquals(
                    "{\"columnCount\":12,\"columns\":[{\"index\":0,\"name\":\"a\",\"type\":\"INT\"},{\"index\":1,\"name\":\"b\",\"type\":\"BYTE\"},{\"index\":2,\"name\":\"c\",\"type\":\"SHORT\"},{\"index\":3,\"name\":\"d\",\"type\":\"LONG\"},{\"index\":4,\"name\":\"e\",\"type\":\"FLOAT\"},{\"index\":5,\"name\":\"f\",\"type\":\"DOUBLE\"},{\"index\":6,\"name\":\"g\",\"type\":\"DATE\"},{\"index\":7,\"name\":\"h\",\"type\":\"BINARY\"},{\"index\":8,\"name\":\"t\",\"type\":\"TIMESTAMP\"},{\"index\":9,\"name\":\"x\",\"type\":\"SYMBOL\"},{\"index\":10,\"name\":\"z\",\"type\":\"STRING\"},{\"index\":11,\"name\":\"y\",\"type\":\"BOOLEAN\"}],\"timestampIndex\":-1}",
                    sink);

            Assert.assertEquals(PartitionBy.MONTH, reader.getPartitionedBy());
            Assert.assertEquals(0L, reader.size());

            int symbolIndex = reader.getMetadata().getColumnIndex("x");
            SymbolMapReader symbolMapReader = reader.getSymbolMapReader(symbolIndex);
            Assert.assertNotNull(symbolMapReader);
            Assert.assertEquals(16, symbolMapReader.getSymbolCapacity());
            Assert.assertTrue(symbolMapReader.isCached());
        }
    }

    @Test
    public void testCreateEmptyTableSymbolCache() throws SqlException {
        compiler.execute("create table x (" +
                        "a INT, " +
                        "b BYTE, " +
                        "c SHORT, " +
                        "d LONG, " +
                        "e FLOAT, " +
                        "f DOUBLE, " +
                        "g DATE, " +
                        "h BINARY, " +
                        "t TIMESTAMP, " +
                        "x SYMBOL capacity 16 cache, " +
                        "z STRING, " +
                        "y BOOLEAN) " +
                        "timestamp(t) " +
                        "partition by MONTH",
                bindVariableService
        );

        try (TableReader reader = engine.getReader("x")) {
            sink.clear();
            reader.getMetadata().toJson(sink);
            TestUtils.assertEquals(
                    "{\"columnCount\":12,\"columns\":[{\"index\":0,\"name\":\"a\",\"type\":\"INT\"},{\"index\":1,\"name\":\"b\",\"type\":\"BYTE\"},{\"index\":2,\"name\":\"c\",\"type\":\"SHORT\"},{\"index\":3,\"name\":\"d\",\"type\":\"LONG\"},{\"index\":4,\"name\":\"e\",\"type\":\"FLOAT\"},{\"index\":5,\"name\":\"f\",\"type\":\"DOUBLE\"},{\"index\":6,\"name\":\"g\",\"type\":\"DATE\"},{\"index\":7,\"name\":\"h\",\"type\":\"BINARY\"},{\"index\":8,\"name\":\"t\",\"type\":\"TIMESTAMP\"},{\"index\":9,\"name\":\"x\",\"type\":\"SYMBOL\"},{\"index\":10,\"name\":\"z\",\"type\":\"STRING\"},{\"index\":11,\"name\":\"y\",\"type\":\"BOOLEAN\"}],\"timestampIndex\":8}",
                    sink);

            Assert.assertEquals(PartitionBy.MONTH, reader.getPartitionedBy());
            Assert.assertEquals(0L, reader.size());

            int symbolIndex = reader.getMetadata().getColumnIndex("x");
            SymbolMapReader symbolMapReader = reader.getSymbolMapReader(symbolIndex);
            Assert.assertNotNull(symbolMapReader);
            Assert.assertEquals(16, symbolMapReader.getSymbolCapacity());
            Assert.assertTrue(symbolMapReader.isCached());
        }
    }

    @Test
    public void testCreateEmptyTableSymbolNoCache() throws SqlException {
        compiler.execute("create table x (" +
                        "a INT, " +
                        "b BYTE, " +
                        "c SHORT, " +
                        "d LONG, " +
                        "e FLOAT, " +
                        "f DOUBLE, " +
                        "g DATE, " +
                        "h BINARY, " +
                        "t TIMESTAMP, " +
                        "x SYMBOL capacity 16 nocache, " +
                        "z STRING, " +
                        "y BOOLEAN) " +
                        "timestamp(t) " +
                        "partition by MONTH",
                bindVariableService
        );

        try (TableReader reader = engine.getReader("x")) {
            sink.clear();
            reader.getMetadata().toJson(sink);
            TestUtils.assertEquals(
                    "{\"columnCount\":12,\"columns\":[{\"index\":0,\"name\":\"a\",\"type\":\"INT\"},{\"index\":1,\"name\":\"b\",\"type\":\"BYTE\"},{\"index\":2,\"name\":\"c\",\"type\":\"SHORT\"},{\"index\":3,\"name\":\"d\",\"type\":\"LONG\"},{\"index\":4,\"name\":\"e\",\"type\":\"FLOAT\"},{\"index\":5,\"name\":\"f\",\"type\":\"DOUBLE\"},{\"index\":6,\"name\":\"g\",\"type\":\"DATE\"},{\"index\":7,\"name\":\"h\",\"type\":\"BINARY\"},{\"index\":8,\"name\":\"t\",\"type\":\"TIMESTAMP\"},{\"index\":9,\"name\":\"x\",\"type\":\"SYMBOL\"},{\"index\":10,\"name\":\"z\",\"type\":\"STRING\"},{\"index\":11,\"name\":\"y\",\"type\":\"BOOLEAN\"}],\"timestampIndex\":8}",
                    sink);

            Assert.assertEquals(PartitionBy.MONTH, reader.getPartitionedBy());
            Assert.assertEquals(0L, reader.size());

            int symbolIndex = reader.getMetadata().getColumnIndex("x");
            SymbolMapReader symbolMapReader = reader.getSymbolMapReader(symbolIndex);
            Assert.assertNotNull(symbolMapReader);
            Assert.assertEquals(16, symbolMapReader.getSymbolCapacity());
            Assert.assertFalse(symbolMapReader.isCached());
        }
    }

    @Test
    public void testCreateEmptyTableWithIndex() throws SqlException {
        compiler.execute("create table x (" +
                        "a INT, " +
                        "b BYTE, " +
                        "c SHORT, " +
                        "d LONG, " +
                        "e FLOAT, " +
                        "f DOUBLE, " +
                        "g DATE, " +
                        "h BINARY, " +
                        "t TIMESTAMP, " +
                        "x SYMBOL capacity 16 cache index capacity 2048, " +
                        "z STRING, " +
                        "y BOOLEAN) " +
                        "timestamp(t) " +
                        "partition by MONTH",
                bindVariableService
        );

        try (TableReader reader = engine.getReader("x")) {
            sink.clear();
            reader.getMetadata().toJson(sink);
            TestUtils.assertEquals(
                    "{\"columnCount\":12,\"columns\":[{\"index\":0,\"name\":\"a\",\"type\":\"INT\"},{\"index\":1,\"name\":\"b\",\"type\":\"BYTE\"},{\"index\":2,\"name\":\"c\",\"type\":\"SHORT\"},{\"index\":3,\"name\":\"d\",\"type\":\"LONG\"},{\"index\":4,\"name\":\"e\",\"type\":\"FLOAT\"},{\"index\":5,\"name\":\"f\",\"type\":\"DOUBLE\"},{\"index\":6,\"name\":\"g\",\"type\":\"DATE\"},{\"index\":7,\"name\":\"h\",\"type\":\"BINARY\"},{\"index\":8,\"name\":\"t\",\"type\":\"TIMESTAMP\"},{\"index\":9,\"name\":\"x\",\"type\":\"SYMBOL\",\"indexed\":true,\"indexValueBlockCapacity\":2048},{\"index\":10,\"name\":\"z\",\"type\":\"STRING\"},{\"index\":11,\"name\":\"y\",\"type\":\"BOOLEAN\"}],\"timestampIndex\":8}",
                    sink);

            Assert.assertEquals(PartitionBy.MONTH, reader.getPartitionedBy());
            Assert.assertEquals(0L, reader.size());

            int symbolIndex = reader.getMetadata().getColumnIndex("x");
            SymbolMapReader symbolMapReader = reader.getSymbolMapReader(symbolIndex);
            Assert.assertNotNull(symbolMapReader);
            Assert.assertEquals(16, symbolMapReader.getSymbolCapacity());
            Assert.assertTrue(symbolMapReader.isCached());
        }
    }

    @Test
    public void testCreateTableAsSelect() throws SqlException, IOException {
        String expectedData = "a1\ta\tb\tc\td\te\tf\tf1\tg\th\ti\tj\tj1\tk\tl\tm\n" +
                "1569490116\tNaN\tfalse\t\tNaN\t0.7611\t428\t-1593\t2015-04-04T16:34:47.226Z\t\t\t185\t7039584373105579285\t1970-01-01T00:00:00.000000Z\t4\t\n" +
                "1253890363\t10\tfalse\tXYS\t0.191123461757\t0.5793\t881\t-1379\t\t2015-03-04T23:08:35.722465Z\tHYRX\t188\t-4986232506486815364\t1970-01-01T00:16:40.000000Z\t50\t\n" +
                "-1819240775\t27\ttrue\tGOO\t0.041428124702\t0.9205\t97\t-9039\t2015-08-25T03:15:07.653Z\t2015-12-06T09:41:30.297134Z\tHYRX\t109\t571924429013198086\t1970-01-01T00:33:20.000000Z\t21\t\n" +
                "-1201923128\t18\ttrue\tUVS\t0.758817540345\t0.5779\t480\t-4379\t2015-12-16T09:15:02.086Z\t2015-05-31T18:12:45.686366Z\tCPSW\tNaN\t-6161552193869048721\t1970-01-01T00:50:00.000000Z\t27\t\n" +
                "865832060\tNaN\ttrue\t\t0.148305523358\t0.9442\t95\t2508\t\t2015-10-20T09:33:20.502524Z\t\tNaN\t-3289070757475856942\t1970-01-01T01:06:40.000000Z\t40\t\n" +
                "1100812407\t22\tfalse\tOVL\tNaN\t0.7633\t698\t-17778\t2015-09-13T09:55:17.815Z\t\tCPSW\t182\t-8757007522346766135\t1970-01-01T01:23:20.000000Z\t23\t\n" +
                "1677463366\t18\tfalse\tMNZ\t0.337470756550\t0.1179\t533\t18904\t2015-05-13T23:13:05.262Z\t2015-05-10T00:20:17.926993Z\t\t175\t6351664568801157821\t1970-01-01T01:40:00.000000Z\t29\t\n" +
                "39497392\t4\tfalse\tUOH\t0.029227696943\t0.1718\t652\t14242\t\t2015-05-24T22:09:55.175991Z\tVTJW\t141\t3527911398466283309\t1970-01-01T01:56:40.000000Z\t9\t\n" +
                "1545963509\t10\tfalse\tNWI\t0.113718418361\t0.0620\t356\t-29980\t2015-09-12T14:33:11.105Z\t2015-08-06T04:51:01.526782Z\t\t168\t6380499796471875623\t1970-01-01T02:13:20.000000Z\t13\t\n" +
                "53462821\t4\tfalse\tGOO\t0.055149337562\t0.1195\t115\t-6087\t2015-08-09T19:28:14.249Z\t2015-09-20T01:50:37.694867Z\tCPSW\t145\t-7212878484370155026\t1970-01-01T02:30:00.000000Z\t46\t\n" +
                "-2139296159\t30\tfalse\t\t0.185864355816\t0.5638\t299\t21020\t2015-12-30T22:10:50.759Z\t2015-01-19T15:54:44.696040Z\tHYRX\t105\t-3463832009795858033\t1970-01-01T02:46:40.000000Z\t38\t\n" +
                "-406528351\t21\tfalse\tNLE\tNaN\tNaN\t968\t21057\t2015-10-17T07:20:26.881Z\t2015-06-02T13:00:45.180827Z\tPEHN\t102\t5360746485515325739\t1970-01-01T03:03:20.000000Z\t43\t\n" +
                "415709351\t17\tfalse\tGQZ\t0.491990017163\t0.6292\t581\t18605\t2015-03-04T06:48:42.194Z\t2015-08-14T15:51:23.307152Z\tHYRX\t185\t-5611837907908424613\t1970-01-01T03:20:00.000000Z\t19\t\n" +
                "-1387693529\t19\ttrue\tMCG\t0.848083900630\t0.4699\t119\t24206\t2015-03-01T23:54:10.204Z\t2015-10-01T12:02:08.698373Z\t\t175\t3669882909701240516\t1970-01-01T03:36:40.000000Z\t12\t\n" +
                "346891421\t21\tfalse\t\t0.933609514583\t0.6380\t405\t15084\t2015-10-12T05:36:54.066Z\t2015-11-16T05:48:57.958190Z\tPEHN\t196\t-9200716729349404576\t1970-01-01T03:53:20.000000Z\t43\t\n" +
                "263487884\t27\ttrue\tHZQ\t0.703978540803\t0.8461\t834\t31562\t2015-08-04T00:55:25.323Z\t2015-07-25T18:26:42.499255Z\tHYRX\t128\t8196544381931602027\t1970-01-01T04:10:00.000000Z\t15\t\n" +
                "-1034870849\t9\tfalse\tLSV\t0.650660460171\t0.7020\t110\t-838\t2015-08-17T23:50:39.534Z\t2015-03-17T03:23:26.126568Z\tHYRX\tNaN\t-6929866925584807039\t1970-01-01T04:26:40.000000Z\t4\t\n" +
                "1848218326\t26\ttrue\tSUW\t0.803404910559\t0.0440\t854\t-3502\t2015-04-04T20:55:02.116Z\t2015-11-23T07:46:10.570856Z\t\t145\t4290477379978201771\t1970-01-01T04:43:20.000000Z\t35\t\n" +
                "-1496904948\t5\ttrue\tDBZ\t0.286271736488\tNaN\t764\t5698\t2015-02-06T02:49:54.147Z\t\t\tNaN\t-3058745577013275321\t1970-01-01T05:00:00.000000Z\t19\t\n" +
                "856634079\t20\ttrue\tRJU\t0.108206023861\t0.4565\t669\t13505\t2015-11-14T15:19:19.390Z\t\tVTJW\t134\t-3700177025310488849\t1970-01-01T05:16:40.000000Z\t3\t\n";

        String expectedMeta = "{\"columnCount\":16,\"columns\":[{\"index\":0,\"name\":\"a1\",\"type\":\"INT\"},{\"index\":1,\"name\":\"a\",\"type\":\"INT\"},{\"index\":2,\"name\":\"b\",\"type\":\"BOOLEAN\"},{\"index\":3,\"name\":\"c\",\"type\":\"STRING\"},{\"index\":4,\"name\":\"d\",\"type\":\"DOUBLE\"},{\"index\":5,\"name\":\"e\",\"type\":\"FLOAT\"},{\"index\":6,\"name\":\"f\",\"type\":\"SHORT\"},{\"index\":7,\"name\":\"f1\",\"type\":\"SHORT\"},{\"index\":8,\"name\":\"g\",\"type\":\"DATE\"},{\"index\":9,\"name\":\"h\",\"type\":\"TIMESTAMP\"},{\"index\":10,\"name\":\"i\",\"type\":\"SYMBOL\"},{\"index\":11,\"name\":\"j\",\"type\":\"LONG\"},{\"index\":12,\"name\":\"j1\",\"type\":\"LONG\"},{\"index\":13,\"name\":\"k\",\"type\":\"TIMESTAMP\"},{\"index\":14,\"name\":\"l\",\"type\":\"BYTE\"},{\"index\":15,\"name\":\"m\",\"type\":\"BINARY\"}],\"timestampIndex\":13}";

        assertCast(expectedData, expectedMeta, "create table y as (" +
                "select * from random_cursor(" +
                " 20," + // record count
                " 'a1', rnd_int()," +
                " 'a', rnd_int(0, 30, 2)," +
                " 'b', rnd_boolean()," +
                " 'c', rnd_str(3,3,2)," +
                " 'd', rnd_double(2)," +
                " 'e', rnd_float(2)," +
                " 'f', rnd_short(10,1024)," +
                " 'f1', rnd_short()," +
                " 'g', rnd_date(to_date('2015', 'yyyy'), to_date('2016', 'yyyy'), 2)," +
                " 'h', rnd_timestamp(to_timestamp('2015', 'yyyy'), to_timestamp('2016', 'yyyy'), 2)," +
                " 'i', rnd_symbol(4,4,4,2)," +
                " 'j', rnd_long(100,200,2)," +
                " 'j1', rnd_long()," +
                " 'k', timestamp_sequence(to_timestamp(0), 1000000000)," +
                " 'l', rnd_byte(2,50)," +
                " 'm', rnd_bin(10, 20, 2)" +
                "))  timestamp(k) partition by DAY");
    }

    @Test
    public void testCreateTableAsSelectInvalidTimestamp() {
        assertFailure(88, "TIMESTAMP column expected",
                "create table y as (" +
                        "select * from random_cursor(" +
                        " 20," + // record count
                        " 'a', rnd_int(0, 30, 2)" +
                        "))  timestamp(a) partition by DAY");
    }

    @Test
    public void testDuplicateTableName() throws SqlException {
        compiler.execute("create table x (" +
                        "a INT, " +
                        "b BYTE, " +
                        "t TIMESTAMP, " +
                        "y BOOLEAN) " +
                        "timestamp(t) " +
                        "partition by MONTH",
                bindVariableService
        );

        assertFailure(13, "table already exists",
                "create table x (" +
                        "t TIMESTAMP, " +
                        "y BOOLEAN) " +
                        "timestamp(t) " +
                        "partition by MONTH");
    }

    @Test
    public void testExecuteQuery() throws SqlException {
        // there is no explicit assert here because when SQL contains semantic error
        // timestamp refers to INT column, which is picked up by both optimiser and compiler
        compiler.execute("select * from random_cursor(20, 'x', rnd_int()) timestamp(x)", bindVariableService);
    }

    @Test
    public void testWithFunction() throws SqlException, IOException {
        String sql = "with x as (select * from random_cursor(10, 'a', rnd_int(), 's', rnd_symbol(4,4,4,2))) " +
                "select * from x x1 join x x2 on (s)";
        RecordCursorFactory factory = compiler.compile(sql, bindVariableService);
        sink.clear();
        printer.print(factory.getCursor(), true);
        System.out.println(sink);
    }

    private void assertCast(String expectedData, String expectedMeta, String sql) throws SqlException, IOException {
        compiler.execute(sql, bindVariableService);
        try (TableReader reader = engine.getReader("y")) {
            sink.clear();
            reader.getMetadata().toJson(sink);
            TestUtils.assertEquals(expectedMeta, sink);

            sink.clear();
            printer.print(reader.getCursor(), true);
            TestUtils.assertEquals(expectedData, sink);
        }
    }

    private void assertCastByte(String expectedData, int castTo) throws SqlException, IOException {
        String expectedMeta = "{\"columnCount\":1,\"columns\":[{\"index\":0,\"name\":\"a\",\"type\":\"" + ColumnType.nameOf(castTo) + "\"}],\"timestampIndex\":-1}";

        String sql = "create table y as (" +
                "select * from random_cursor(" +
                " 20," + // record count
                " 'a', rnd_byte(33, 119)" +
                ")), cast(a as " + ColumnType.nameOf(castTo) + ")";

        assertCast(expectedData, expectedMeta, sql);
    }

    private void assertCastByteFail(int castTo) {
        try {
            compiler.execute("create table y as (" +
                            "select * from random_cursor(" +
                            " 20," + // record count
                            " 'a', rnd_byte(2,50)" +
                            ")), cast(a as " + ColumnType.nameOf(castTo) + ")",
                    bindVariableService);
            Assert.fail();
        } catch (SqlException e) {
            Assert.assertEquals(85, e.getPosition());
            TestUtils.assertContains(e.getMessage(), "unsupported cast");
        }
    }

    private void assertCastDate(String expectedData, int castTo) throws SqlException, IOException {
        String expectedMeta = "{\"columnCount\":1,\"columns\":[{\"index\":0,\"name\":\"a\",\"type\":\"" + ColumnType.nameOf(castTo) + "\"}],\"timestampIndex\":-1}";

        String sql = "create table y as (" +
                "select * from random_cursor(" +
                " 20," + // record count
                " 'a', rnd_date(to_date('2015', 'yyyy'), to_date('2016', 'yyyy'), 2)" +
                ")), cast(a as " + ColumnType.nameOf(castTo) + ")";

        assertCast(expectedData, expectedMeta, sql);
    }

    private void assertCastDouble(String expectedData, int castTo) throws SqlException, IOException {
        String expectedMeta = "{\"columnCount\":1,\"columns\":[{\"index\":0,\"name\":\"a\",\"type\":\"" + ColumnType.nameOf(castTo) + "\"}],\"timestampIndex\":-1}";

        String sql = "create table y as (" +
                "select * from random_cursor(" +
                " 20," + // record count
                " 'a', 100 * rnd_double(2)" +
                ")), cast(a as " + ColumnType.nameOf(castTo) + ")";

        assertCast(expectedData, expectedMeta, sql);
    }

    private void assertCastDoubleFail(int castTo) {
        try {
            compiler.execute("create table y as (" +
                            "select * from random_cursor(" +
                            " 20," + // record count
                            " 'a', rnd_double(2)" +
                            ")), cast(a as " + ColumnType.nameOf(castTo) + ")",
                    bindVariableService);
            Assert.fail();
        } catch (SqlException e) {
            Assert.assertEquals(84, e.getPosition());
            TestUtils.assertContains(e.getMessage(), "unsupported cast");
        }
    }

    private void assertCastFloat(String expectedData, int castTo) throws SqlException, IOException {
        String expectedMeta = "{\"columnCount\":1,\"columns\":[{\"index\":0,\"name\":\"a\",\"type\":\"" + ColumnType.nameOf(castTo) + "\"}],\"timestampIndex\":-1}";

        String sql = "create table y as (" +
                "select * from random_cursor(" +
                " 20," + // record count
                " 'a', 100 * rnd_float(2)" +
                ")), cast(a as " + ColumnType.nameOf(castTo) + ")";

        assertCast(expectedData, expectedMeta, sql);
    }

    private void assertCastFloatFail(int castTo) {
        try {
            compiler.execute("create table y as (" +
                            "select * from random_cursor(" +
                            " 20," + // record count
                            " 'a', rnd_float(2)" +
                            ")), cast(a as " + ColumnType.nameOf(castTo) + ")",
                    bindVariableService);
            Assert.fail();
        } catch (SqlException e) {
            Assert.assertEquals(83, e.getPosition());
            TestUtils.assertContains(e.getMessage(), "unsupported cast");
        }
    }

    private void assertCastInt(String expectedData, int castTo) throws SqlException, IOException {
        String expectedMeta = "{\"columnCount\":1,\"columns\":[{\"index\":0,\"name\":\"a\",\"type\":\"" + ColumnType.nameOf(castTo) + "\"}],\"timestampIndex\":-1}";

        String sql = "create table y as (" +
                "select * from random_cursor(" +
                " 20," + // record count
                " 'a', rnd_int(0, 30, 2)" +
                ")), cast(a as " + ColumnType.nameOf(castTo) + ")";

        assertCast(expectedData, expectedMeta, sql);
    }

    private void assertCastIntFail(int castTo) {
        try {
            compiler.execute("create table y as (" +
                            "select * from random_cursor(" +
                            " 20," + // record count
                            " 'a', rnd_int(0, 30, 2)" +
                            ")), cast(a as " + ColumnType.nameOf(castTo) + ")",
                    bindVariableService);
            Assert.fail();
        } catch (SqlException e) {
            Assert.assertEquals(88, e.getPosition());
            TestUtils.assertContains(e.getMessage(), "unsupported cast");
        }
    }

    private void assertCastLong(String expectedData, int castTo) throws SqlException, IOException {
        String expectedMeta = "{\"columnCount\":1,\"columns\":[{\"index\":0,\"name\":\"a\",\"type\":\"" + ColumnType.nameOf(castTo) + "\"}],\"timestampIndex\":-1}";

        String sql = "create table y as (" +
                "select * from random_cursor(" +
                " 20," + // record count
                " 'a', rnd_long(0, 30, 2)" +
                ")), cast(a as " + ColumnType.nameOf(castTo) + ")";

        assertCast(expectedData, expectedMeta, sql);
    }

    private void assertCastLongFail(int castTo) {
        try {
            compiler.execute("create table y as (" +
                            "select * from random_cursor(" +
                            " 20," + // record count
                            " 'a', rnd_long(0, 30, 2)" +
                            ")), cast(a as " + ColumnType.nameOf(castTo) + ")",
                    bindVariableService);
            Assert.fail();
        } catch (SqlException e) {
            Assert.assertEquals(89, e.getPosition());
            TestUtils.assertContains(e.getMessage(), "unsupported cast");
        }
    }

    private void assertCastShort(String expectedData, int castTo) throws SqlException, IOException {
        String expectedMeta = "{\"columnCount\":1,\"columns\":[{\"index\":0,\"name\":\"a\",\"type\":\"" + ColumnType.nameOf(castTo) + "\"}],\"timestampIndex\":-1}";

        String sql = "create table y as (" +
                "select * from random_cursor(" +
                " 20," + // record count
                " 'a', rnd_short(1024, 2048)" +
                ")), cast(a as " + ColumnType.nameOf(castTo) + ")";

        assertCast(expectedData, expectedMeta, sql);
    }

    private void assertCastShortFail(int castTo) {
        try {
            compiler.execute("create table y as (" +
                            "select * from random_cursor(" +
                            " 20," + // record count
                            " 'a', rnd_short(2,10)" +
                            ")), cast(a as " + ColumnType.nameOf(castTo) + ")",
                    bindVariableService);
            Assert.fail();
        } catch (SqlException e) {
            Assert.assertEquals(86, e.getPosition());
            TestUtils.assertContains(e.getMessage(), "unsupported cast");
        }
    }

    private void assertCastStringFail(int castTo) {
        try {
            compiler.execute("create table y as (" +
                            "select * from random_cursor(" +
                            " 20," + // record count
                            " 'a', rnd_str(5,10,2)" +
                            ")), cast(a as " + ColumnType.nameOf(castTo) + ")",
                    bindVariableService);
            Assert.fail();
        } catch (SqlException e) {
            Assert.assertEquals(86, e.getPosition());
            TestUtils.assertContains(e.getMessage(), "unsupported cast");
        }
    }

    private void assertCastSymbolFail(int castTo) {
        try {
            compiler.execute("create table y as (" +
                            "select * from random_cursor(" +
                            " 20," + // record count
                            " 'a', rnd_symbol(4,6,10,2)" +
                            ")), cast(a as " + ColumnType.nameOf(castTo) + ")",
                    bindVariableService);
            Assert.fail();
        } catch (SqlException e) {
            Assert.assertEquals(91, e.getPosition());
            TestUtils.assertContains(e.getMessage(), "unsupported cast");
        }
    }

    private void assertCastTimestamp(String expectedData, int castTo) throws SqlException, IOException {
        String expectedMeta = "{\"columnCount\":1,\"columns\":[{\"index\":0,\"name\":\"a\",\"type\":\"" + ColumnType.nameOf(castTo) + "\"}],\"timestampIndex\":-1}";

        String sql = "create table y as (" +
                "select * from random_cursor(" +
                " 20," + // record count
                " 'a', rnd_timestamp(to_timestamp('2015', 'yyyy'), to_timestamp('2016', 'yyyy'), 2)" +
                ")), cast(a as " + ColumnType.nameOf(castTo) + ")";

        assertCast(expectedData, expectedMeta, sql);
    }

    private void assertFailure(int position, CharSequence expectedMessage, CharSequence sql) {
        try {
            compiler.compile(sql, bindVariableService);
            Assert.fail();
        } catch (SqlException e) {
            Assert.assertEquals(position, e.getPosition());
            TestUtils.assertContains(e.getMessage(), expectedMessage);
        }
    }
}