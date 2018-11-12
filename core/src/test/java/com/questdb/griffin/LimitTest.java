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

import com.questdb.griffin.engine.functions.rnd.SharedRandom;
import com.questdb.std.Rnd;
import com.questdb.test.tools.TestUtils;
import org.junit.Before;
import org.junit.Test;

public class LimitTest extends AbstractGriffinTest {
    @Before
    public void setUp3() {
        SharedRandom.RANDOM.set(new Rnd());
    }

    @Test
    public void testBottomRange() throws Exception {
        String expected = "i\tsym2\tprice\ttimestamp\tb\tc\td\te\tf\tg\tik\tj\tk\tl\tm\tn\n" +
                "25\tmsft\t0.918000000000\t2018-01-01T00:50:00.000000Z\tfalse\t\t0.326136520120\t0.3394\t176\t2015-02-06T18:42:24.631Z\t\t-8352023907145286323\t1970-01-01T06:40:00.000000Z\t14\t00000000 6a 9b cd bb 2e 74 cd 44 54 13 3f ff\tIGENFEL\n" +
                "26\tibm\t0.330000000000\t2018-01-01T00:52:00.000000Z\tfalse\tM\t0.198236477005\tNaN\t557\t2015-01-30T03:27:34.392Z\t\t5324839128380055812\t1970-01-01T06:56:40.000000Z\t25\t00000000 25 07 db 62 44 33 6e 00 8e 93 bd 27 42 f8 25 2a\n" +
                "00000010 42 71 a3 7a\tDNZNLCNGZTOY\n" +
                "27\tmsft\t0.673000000000\t2018-01-01T00:54:00.000000Z\tfalse\tP\t0.527776712011\t0.0237\t517\t2015-05-20T07:51:29.675Z\tPEHN\t-7667438647671231061\t1970-01-01T07:13:20.000000Z\t22\t00000000 61 99 be 2d f5 30 78 6d 5a 3b\t\n" +
                "28\tgoogl\t0.173000000000\t2018-01-01T00:56:00.000000Z\tfalse\tY\t0.991107083990\t0.6699\t324\t2015-03-27T18:25:29.970Z\t\t-4241819446186560308\t1970-01-01T07:30:00.000000Z\t35\t00000000 34 cd 15 35 bb a4 a3 c8 66 0c 40 71 ea 20\tTHMHZNVZHCNX\n";

        String expected2 = "i\tsym2\tprice\ttimestamp\tb\tc\td\te\tf\tg\tik\tj\tk\tl\tm\tn\n" +
                "55\tibm\t0.213000000000\t2018-01-01T01:50:00.000000Z\tfalse\tKZZ\tNaN\t0.0379\t503\t2015-08-25T16:59:46.151Z\tKKUS\t8510474930626176160\t1970-01-01T06:40:00.000000Z\t13\t\t\n" +
                "56\tmsft\t0.061000000000\t2018-01-01T01:52:00.000000Z\ttrue\tCDE\t0.779251143760\t0.3966\t341\t2015-03-04T08:18:06.265Z\tLVSY\t5320837171213814710\t1970-01-01T06:56:40.000000Z\t16\t\tJCUBBMQSRHLWSX\n" +
                "57\tgoogl\t0.756000000000\t2018-01-01T01:54:00.000000Z\tfalse\tKZZ\t0.892572303318\t0.9925\t416\t2015-11-08T09:45:16.753Z\tLVSY\t7173713836788833462\t1970-01-01T07:13:20.000000Z\t29\t00000000 4d 0d d7 44 2d f1 57 ea aa 41 c5 55 ef 19 d9 0f\n" +
                "00000010 61 2d\tEYDNMIOCCVV\n" +
                "58\tibm\t0.445000000000\t2018-01-01T01:56:00.000000Z\ttrue\tCDE\t0.761311594585\tNaN\t118\t\tHGKR\t-5065534156372441821\t1970-01-01T07:30:00.000000Z\t4\t00000000 cd 98 7d ba 9d 68 2a 79 76 fc\tBGCKOSB\n";

        String query = "select * from y limit -6,-2";
        testLimit(expected, expected2, query);
    }

    @Test
    public void testBottomRangeNotEnoughRows() throws Exception {
        String expected = "i\tsym2\tprice\ttimestamp\tb\tc\td\te\tf\tg\tik\tj\tk\tl\tm\tn\n";

        String expected2 = "i\tsym2\tprice\ttimestamp\tb\tc\td\te\tf\tg\tik\tj\tk\tl\tm\tn\n" +
                "28\tgoogl\t0.173000000000\t2018-01-01T00:56:00.000000Z\tfalse\tY\t0.991107083990\t0.6699\t324\t2015-03-27T18:25:29.970Z\t\t-4241819446186560308\t1970-01-01T07:30:00.000000Z\t35\t00000000 34 cd 15 35 bb a4 a3 c8 66 0c 40 71 ea 20\tTHMHZNVZHCNX\n" +
                "29\tibm\t0.240000000000\t2018-01-01T00:58:00.000000Z\tfalse\t\tNaN\t0.5505\t667\t2015-12-17T04:08:24.080Z\tPEHN\t-1549327479854558367\t1970-01-01T07:46:40.000000Z\t10\t00000000 03 5b 11 44 83 06 63 2b 58 3b 4b b7 e2 7f ab 6e\n" +
                "00000010 23 03 dd\t\n";

        String query = "select * from y limit -33,-31";
        testLimit(expected, expected2, query);
    }

    @Test
    public void testBottomRangePartialRows() throws Exception {
        String expected = "i\tsym2\tprice\ttimestamp\tb\tc\td\te\tf\tg\tik\tj\tk\tl\tm\tn\n" +
                "1\tmsft\t0.509000000000\t2018-01-01T00:02:00.000000Z\tfalse\tU\t0.524372285929\t0.8072\t365\t2015-05-02T19:30:57.935Z\t\t-4485747798769957016\t1970-01-01T00:00:00.000000Z\t19\t00000000 19 c4 95 94 36 53 49 b4 59 7e 3b 08 a1 1e\tYSBEOUOJSHRUEDRQ\n" +
                "2\tgoogl\t0.423000000000\t2018-01-01T00:04:00.000000Z\tfalse\tG\t0.529840594176\tNaN\t493\t2015-04-09T11:42:28.332Z\tHYRX\t-8811278461560712840\t1970-01-01T00:16:40.000000Z\t29\t00000000 53 d0 fb 64 bb 1a d4 f0 2d 40 e2 4b b1 3e e3 f1\t\n";

        String expected2 = "i\tsym2\tprice\ttimestamp\tb\tc\td\te\tf\tg\tik\tj\tk\tl\tm\tn\n" +
                "26\tibm\t0.330000000000\t2018-01-01T00:52:00.000000Z\tfalse\tM\t0.198236477005\tNaN\t557\t2015-01-30T03:27:34.392Z\t\t5324839128380055812\t1970-01-01T06:56:40.000000Z\t25\t00000000 25 07 db 62 44 33 6e 00 8e 93 bd 27 42 f8 25 2a\n" +
                "00000010 42 71 a3 7a\tDNZNLCNGZTOY\n" +
                "27\tmsft\t0.673000000000\t2018-01-01T00:54:00.000000Z\tfalse\tP\t0.527776712011\t0.0237\t517\t2015-05-20T07:51:29.675Z\tPEHN\t-7667438647671231061\t1970-01-01T07:13:20.000000Z\t22\t00000000 61 99 be 2d f5 30 78 6d 5a 3b\t\n" +
                "28\tgoogl\t0.173000000000\t2018-01-01T00:56:00.000000Z\tfalse\tY\t0.991107083990\t0.6699\t324\t2015-03-27T18:25:29.970Z\t\t-4241819446186560308\t1970-01-01T07:30:00.000000Z\t35\t00000000 34 cd 15 35 bb a4 a3 c8 66 0c 40 71 ea 20\tTHMHZNVZHCNX\n" +
                "29\tibm\t0.240000000000\t2018-01-01T00:58:00.000000Z\tfalse\t\tNaN\t0.5505\t667\t2015-12-17T04:08:24.080Z\tPEHN\t-1549327479854558367\t1970-01-01T07:46:40.000000Z\t10\t00000000 03 5b 11 44 83 06 63 2b 58 3b 4b b7 e2 7f ab 6e\n" +
                "00000010 23 03 dd\t\n" +
                "30\tibm\t0.607000000000\t2018-01-01T01:00:00.000000Z\ttrue\tF\t0.478083025671\t0.0109\t998\t2015-04-30T21:40:32.732Z\tCPSW\t4654788096008024367\t1970-01-01T08:03:20.000000Z\t32\t\tJLKTRD\n" +
                "31\tibm\t0.181000000000\t2018-01-01T01:02:00.000000Z\tfalse\tCDE\t0.545517532479\t0.8249\t905\t2015-03-02T13:31:56.918Z\tLVSY\t-4086246124104754347\t1970-01-01T00:00:00.000000Z\t26\t00000000 4b c0 d9 1c 71 cf 5a 8f 21 06 b2 3f\t\n" +
                "32\tibm\t0.473000000000\t2018-01-01T01:04:00.000000Z\tfalse\tABC\t0.039609681243\t0.0783\t42\t2015-05-21T22:28:44.644Z\tKKUS\t2296041148680180183\t1970-01-01T00:16:40.000000Z\t35\t\tSVCLLERSMKRZ\n";

        String query = "select * from y limit -35,-28";
        testLimit(expected, expected2, query);
    }

    @Test
    public void testDefaultHi() throws Exception {
        String expected = "i\tsym2\tprice\ttimestamp\tb\tc\td\te\tf\tg\tik\tj\tk\tl\tm\tn\n" +
                "1\tmsft\t0.509000000000\t2018-01-01T00:02:00.000000Z\tfalse\tU\t0.524372285929\t0.8072\t365\t2015-05-02T19:30:57.935Z\t\t-4485747798769957016\t1970-01-01T00:00:00.000000Z\t19\t00000000 19 c4 95 94 36 53 49 b4 59 7e 3b 08 a1 1e\tYSBEOUOJSHRUEDRQ\n" +
                "2\tgoogl\t0.423000000000\t2018-01-01T00:04:00.000000Z\tfalse\tG\t0.529840594176\tNaN\t493\t2015-04-09T11:42:28.332Z\tHYRX\t-8811278461560712840\t1970-01-01T00:16:40.000000Z\t29\t00000000 53 d0 fb 64 bb 1a d4 f0 2d 40 e2 4b b1 3e e3 f1\t\n" +
                "3\tgoogl\t0.174000000000\t2018-01-01T00:06:00.000000Z\tfalse\tW\t0.882822836670\t0.7230\t845\t2015-08-26T10:57:26.275Z\tVTJW\t9029468389542245059\t1970-01-01T00:33:20.000000Z\t46\t00000000 e5 61 2f 64 0e 2c 7f d7 6f b8 c9 ae 28 c7 84 47\tDSWUGSHOLNV\n" +
                "4\tibm\t0.148000000000\t2018-01-01T00:08:00.000000Z\ttrue\tI\t0.345689799154\t0.2401\t775\t2015-08-03T15:58:03.335Z\tVTJW\t-8910603140262731534\t1970-01-01T00:50:00.000000Z\t24\t00000000 ac a8 3b a6 dc 3b 7d 2b e3 92 fe 69 38 e1 77 9a\n" +
                "00000010 e7 0c 89\tLJUMLGLHMLLEO\n" +
                "5\tgoogl\t0.868000000000\t2018-01-01T00:10:00.000000Z\ttrue\tZ\t0.427470428635\t0.0212\t179\t\t\t5746626297238459939\t1970-01-01T01:06:40.000000Z\t35\t00000000 91 88 28 a5 18 93 bd 0b 61 f5 5d d0 eb\tRGIIH\n";

        String query = "select * from y limit 5,";
        testLimit(expected, expected, query);
    }

    @Test
    public void testDefaultLo() throws Exception {
        String expected = "i\tsym2\tprice\ttimestamp\tb\tc\td\te\tf\tg\tik\tj\tk\tl\tm\tn\n" +
                "1\tmsft\t0.509000000000\t2018-01-01T00:02:00.000000Z\tfalse\tU\t0.524372285929\t0.8072\t365\t2015-05-02T19:30:57.935Z\t\t-4485747798769957016\t1970-01-01T00:00:00.000000Z\t19\t00000000 19 c4 95 94 36 53 49 b4 59 7e 3b 08 a1 1e\tYSBEOUOJSHRUEDRQ\n" +
                "2\tgoogl\t0.423000000000\t2018-01-01T00:04:00.000000Z\tfalse\tG\t0.529840594176\tNaN\t493\t2015-04-09T11:42:28.332Z\tHYRX\t-8811278461560712840\t1970-01-01T00:16:40.000000Z\t29\t00000000 53 d0 fb 64 bb 1a d4 f0 2d 40 e2 4b b1 3e e3 f1\t\n" +
                "3\tgoogl\t0.174000000000\t2018-01-01T00:06:00.000000Z\tfalse\tW\t0.882822836670\t0.7230\t845\t2015-08-26T10:57:26.275Z\tVTJW\t9029468389542245059\t1970-01-01T00:33:20.000000Z\t46\t00000000 e5 61 2f 64 0e 2c 7f d7 6f b8 c9 ae 28 c7 84 47\tDSWUGSHOLNV\n" +
                "4\tibm\t0.148000000000\t2018-01-01T00:08:00.000000Z\ttrue\tI\t0.345689799154\t0.2401\t775\t2015-08-03T15:58:03.335Z\tVTJW\t-8910603140262731534\t1970-01-01T00:50:00.000000Z\t24\t00000000 ac a8 3b a6 dc 3b 7d 2b e3 92 fe 69 38 e1 77 9a\n" +
                "00000010 e7 0c 89\tLJUMLGLHMLLEO\n" +
                "5\tgoogl\t0.868000000000\t2018-01-01T00:10:00.000000Z\ttrue\tZ\t0.427470428635\t0.0212\t179\t\t\t5746626297238459939\t1970-01-01T01:06:40.000000Z\t35\t00000000 91 88 28 a5 18 93 bd 0b 61 f5 5d d0 eb\tRGIIH\n";

        String query = "select * from y limit ,5";
        testLimit(expected, expected, query);
    }

    @Test
    public void testInvalidBottomRange() throws Exception {
        String expected = "i\tsym2\tprice\ttimestamp\tb\tc\td\te\tf\tg\tik\tj\tk\tl\tm\tn\n";

        String query = "select * from y limit -3,-10";
        testLimit(expected, expected, query);
    }

    @Test
    public void testInvalidHiType() throws Exception {
        assertFailure(
                "select * from y limit 5,'a'",
                "create table y as (" +
                        "select" +
                        " to_int(x) i," +
                        " rnd_symbol('msft','ibm', 'googl') sym2," +
                        " round(rnd_double(0), 3) price," +
                        " to_timestamp('2018-01', 'yyyy-MM') + x * 120000000 timestamp," +
                        " rnd_boolean() b," +
                        " rnd_str(1,1,2) c," +
                        " rnd_double(2) d," +
                        " rnd_float(2) e," +
                        " rnd_short(10,1024) f," +
                        " rnd_date(to_date('2015', 'yyyy'), to_date('2016', 'yyyy'), 2) g," +
                        " rnd_symbol(4,4,4,2) ik," +
                        " rnd_long() j," +
                        " timestamp_sequence(to_timestamp(0), 1000000000) k," +
                        " rnd_byte(2,50) l," +
                        " rnd_bin(10, 20, 2) m," +
                        " rnd_str(5,16,2) n" +
                        " from long_sequence(30)" +
                        ") timestamp(timestamp)",
                24,
                "invalid type: STRING"
        );
    }

    @Test
    public void testInvalidLoType() throws Exception {
        assertFailure(
                "select * from y limit 5 + 0.3",
                "create table y as (" +
                        "select" +
                        " to_int(x) i," +
                        " rnd_symbol('msft','ibm', 'googl') sym2," +
                        " round(rnd_double(0), 3) price," +
                        " to_timestamp('2018-01', 'yyyy-MM') + x * 120000000 timestamp," +
                        " rnd_boolean() b," +
                        " rnd_str(1,1,2) c," +
                        " rnd_double(2) d," +
                        " rnd_float(2) e," +
                        " rnd_short(10,1024) f," +
                        " rnd_date(to_date('2015', 'yyyy'), to_date('2016', 'yyyy'), 2) g," +
                        " rnd_symbol(4,4,4,2) ik," +
                        " rnd_long() j," +
                        " timestamp_sequence(to_timestamp(0), 1000000000) k," +
                        " rnd_byte(2,50) l," +
                        " rnd_bin(10, 20, 2) m," +
                        " rnd_str(5,16,2) n" +
                        " from long_sequence(30)" +
                        ") timestamp(timestamp)",
                24,
                "invalid type: DOUBLE"
        );
    }

    @Test
    public void testInvalidTopRange() throws Exception {
        String expected = "i\tsym2\tprice\ttimestamp\tb\tc\td\te\tf\tg\tik\tj\tk\tl\tm\tn\n";

        String query = "select * from y limit 6,5";
        testLimit(expected, expected, query);
    }

    @Test
    public void testLastN() throws Exception {
        String expected = "i\tsym2\tprice\ttimestamp\tb\tc\td\te\tf\tg\tik\tj\tk\tl\tm\tn\n" +
                "26\tibm\t0.330000000000\t2018-01-01T00:52:00.000000Z\tfalse\tM\t0.198236477005\tNaN\t557\t2015-01-30T03:27:34.392Z\t\t5324839128380055812\t1970-01-01T06:56:40.000000Z\t25\t00000000 25 07 db 62 44 33 6e 00 8e 93 bd 27 42 f8 25 2a\n" +
                "00000010 42 71 a3 7a\tDNZNLCNGZTOY\n" +
                "27\tmsft\t0.673000000000\t2018-01-01T00:54:00.000000Z\tfalse\tP\t0.527776712011\t0.0237\t517\t2015-05-20T07:51:29.675Z\tPEHN\t-7667438647671231061\t1970-01-01T07:13:20.000000Z\t22\t00000000 61 99 be 2d f5 30 78 6d 5a 3b\t\n" +
                "28\tgoogl\t0.173000000000\t2018-01-01T00:56:00.000000Z\tfalse\tY\t0.991107083990\t0.6699\t324\t2015-03-27T18:25:29.970Z\t\t-4241819446186560308\t1970-01-01T07:30:00.000000Z\t35\t00000000 34 cd 15 35 bb a4 a3 c8 66 0c 40 71 ea 20\tTHMHZNVZHCNX\n" +
                "29\tibm\t0.240000000000\t2018-01-01T00:58:00.000000Z\tfalse\t\tNaN\t0.5505\t667\t2015-12-17T04:08:24.080Z\tPEHN\t-1549327479854558367\t1970-01-01T07:46:40.000000Z\t10\t00000000 03 5b 11 44 83 06 63 2b 58 3b 4b b7 e2 7f ab 6e\n" +
                "00000010 23 03 dd\t\n" +
                "30\tibm\t0.607000000000\t2018-01-01T01:00:00.000000Z\ttrue\tF\t0.478083025671\t0.0109\t998\t2015-04-30T21:40:32.732Z\tCPSW\t4654788096008024367\t1970-01-01T08:03:20.000000Z\t32\t\tJLKTRD\n";

        String expected2 = "i\tsym2\tprice\ttimestamp\tb\tc\td\te\tf\tg\tik\tj\tk\tl\tm\tn\n" +
                "56\tmsft\t0.061000000000\t2018-01-01T01:52:00.000000Z\ttrue\tCDE\t0.779251143760\t0.3966\t341\t2015-03-04T08:18:06.265Z\tLVSY\t5320837171213814710\t1970-01-01T06:56:40.000000Z\t16\t\tJCUBBMQSRHLWSX\n" +
                "57\tgoogl\t0.756000000000\t2018-01-01T01:54:00.000000Z\tfalse\tKZZ\t0.892572303318\t0.9925\t416\t2015-11-08T09:45:16.753Z\tLVSY\t7173713836788833462\t1970-01-01T07:13:20.000000Z\t29\t00000000 4d 0d d7 44 2d f1 57 ea aa 41 c5 55 ef 19 d9 0f\n" +
                "00000010 61 2d\tEYDNMIOCCVV\n" +
                "58\tibm\t0.445000000000\t2018-01-01T01:56:00.000000Z\ttrue\tCDE\t0.761311594585\tNaN\t118\t\tHGKR\t-5065534156372441821\t1970-01-01T07:30:00.000000Z\t4\t00000000 cd 98 7d ba 9d 68 2a 79 76 fc\tBGCKOSB\n" +
                "59\tgoogl\t0.778000000000\t2018-01-01T01:58:00.000000Z\tfalse\tKZZ\t0.774180142253\t0.1870\t586\t2015-05-27T15:12:16.295Z\t\t-7715437488835448247\t1970-01-01T07:46:40.000000Z\t10\t\tEPLWDUWIWJTLCP\n" +
                "60\tgoogl\t0.852000000000\t2018-01-01T02:00:00.000000Z\ttrue\tKZZ\tNaN\tNaN\t834\t2015-07-15T04:34:51.645Z\tLMSR\t-4834150290387342806\t1970-01-01T08:03:20.000000Z\t23\t00000000 dd 02 98 ad a8 82 73 a6 7f db d6 20\tFDRPHNGTNJJPT\n";

        String query = "select * from y limit -5";
        testLimit(expected, expected2, query);
    }

    @Test
    public void testTopBottomRange() throws Exception {
        String expected = "i\tsym2\tprice\ttimestamp\tb\tc\td\te\tf\tg\tik\tj\tk\tl\tm\tn\n" +
                "5\tgoogl\t0.868000000000\t2018-01-01T00:10:00.000000Z\ttrue\tZ\t0.427470428635\t0.0212\t179\t\t\t5746626297238459939\t1970-01-01T01:06:40.000000Z\t35\t00000000 91 88 28 a5 18 93 bd 0b 61 f5 5d d0 eb\tRGIIH\n" +
                "6\tmsft\t0.297000000000\t2018-01-01T00:12:00.000000Z\tfalse\tY\t0.267212048922\t0.1326\t215\t\t\t-8534688874718947140\t1970-01-01T01:23:20.000000Z\t34\t00000000 1c 0b 20 a2 86 89 37 11 2c 14\tUSZMZVQE\n" +
                "7\tgoogl\t0.076000000000\t2018-01-01T00:14:00.000000Z\ttrue\tE\t0.760625263412\t0.0658\t1018\t2015-02-23T07:09:35.550Z\tPEHN\t7797019568426198829\t1970-01-01T01:40:00.000000Z\t10\t00000000 80 c9 eb a3 67 7a 1a 79 e4 35 e4 3a dc 5c 65 ff\tIGYVFZ\n" +
                "8\tibm\t0.543000000000\t2018-01-01T00:16:00.000000Z\ttrue\tO\t0.483525620204\t0.8688\t355\t2015-09-06T20:21:06.672Z\t\t-9219078548506735248\t1970-01-01T01:56:40.000000Z\t33\t00000000 b3 14 cd 47 0b 0c 39 12 f7 05 10 f4 6d f1\tXUKLGMXSLUQ\n" +
                "9\tmsft\t0.623000000000\t2018-01-01T00:18:00.000000Z\tfalse\tI\t0.878611111254\t0.9966\t403\t2015-08-19T00:36:24.375Z\tCPSW\t-8506266080452644687\t1970-01-01T02:13:20.000000Z\t6\t00000000 9a ef 88 cb 4b a1 cf cf 41 7d a6\t\n" +
                "10\tmsft\t0.509000000000\t2018-01-01T00:20:00.000000Z\ttrue\tI\t0.491532681548\t0.0024\t195\t2015-10-15T17:45:21.025Z\t\t3987576220753016999\t1970-01-01T02:30:00.000000Z\t20\t00000000 96 37 08 dd 98 ef 54 88 2a a2\t\n" +
                "11\tmsft\t0.578000000000\t2018-01-01T00:22:00.000000Z\ttrue\tP\t0.746701366813\t0.5795\t122\t2015-11-25T07:36:56.937Z\t\t2004830221820243556\t1970-01-01T02:46:40.000000Z\t45\t00000000 a0 dd 44 11 e2 a3 24 4e 44 a8 0d fe 27 ec 53 13\n" +
                "00000010 5d b2 15 e7\tWGRMDGGIJYDVRV\n" +
                "12\tmsft\t0.661000000000\t2018-01-01T00:24:00.000000Z\ttrue\tO\t0.013960795460\t0.8136\t345\t2015-08-18T10:31:42.373Z\tVTJW\t5045825384817367685\t1970-01-01T03:03:20.000000Z\t23\t00000000 51 9d 5d 28 ac 02 2e fe 05 3b 94 5f ec d3 dc f8\n" +
                "00000010 43\tJCTIZKYFLUHZ\n" +
                "13\tibm\t0.704000000000\t2018-01-01T00:26:00.000000Z\ttrue\tK\t0.036735155240\t0.8406\t742\t2015-05-03T18:49:03.996Z\tPEHN\t2568830294369411037\t1970-01-01T03:20:00.000000Z\t24\t00000000 76 bc 45 24 cd 13 00 7c fb 01 19 ca f2 bf 84 5a\n" +
                "00000010 6f 38 35\t\n" +
                "14\tgoogl\t0.651000000000\t2018-01-01T00:28:00.000000Z\ttrue\tL\tNaN\t0.1389\t984\t2015-04-30T08:35:52.508Z\tHYRX\t-6929866925584807039\t1970-01-01T03:36:40.000000Z\t4\t00000000 4b fb 2d 16 f3 89 a3 83 64 de\t\n" +
                "15\tmsft\t0.409000000000\t2018-01-01T00:30:00.000000Z\tfalse\tW\t0.803404910559\t0.0440\t854\t2015-04-10T01:03:15.469Z\t\t-9050844408181442061\t1970-01-01T03:53:20.000000Z\t24\t\tPFYXPV\n" +
                "16\tgoogl\t0.839000000000\t2018-01-01T00:32:00.000000Z\tfalse\tN\t0.110480003996\t0.7096\t379\t2015-04-14T08:49:20.021Z\t\t-5749151825415257775\t1970-01-01T04:10:00.000000Z\t16\t\t\n" +
                "17\tibm\t0.286000000000\t2018-01-01T00:34:00.000000Z\tfalse\t\t0.135352967461\tNaN\t595\t\tVTJW\t-6237826420165615015\t1970-01-01T04:26:40.000000Z\t33\t\tBEGMITI\n" +
                "18\tibm\t0.932000000000\t2018-01-01T00:36:00.000000Z\ttrue\tH\t0.889822641116\t0.7074\t130\t2015-04-09T21:18:23.066Z\tCPSW\t-5708280760166173503\t1970-01-01T04:43:20.000000Z\t27\t00000000 69 94 3f 7d ef 3b b8 be f8 a1 46 87 28 92 a3 9b\n" +
                "00000010 e3 cb\tNIZOSBOSE\n" +
                "19\tibm\t0.151000000000\t2018-01-01T00:38:00.000000Z\ttrue\tH\t0.392012963507\t0.5700\t748\t\t\t-8060696376111078264\t1970-01-01T05:00:00.000000Z\t11\t00000000 10 20 81 c6 3d bc b5 05 2b 73 51 cf c3 7e c0 1d\n" +
                "00000010 6c a9\tTDCEBYWXBBZV\n" +
                "20\tgoogl\t0.624000000000\t2018-01-01T00:40:00.000000Z\tfalse\tY\t0.586393781337\t0.2103\t834\t2015-10-15T00:48:00.413Z\tHYRX\t7913225810447636126\t1970-01-01T05:16:40.000000Z\t12\t00000000 f6 78 09 1c 5d 88 f5 52 fd 36 02 50 d9 a0 b5 90\n" +
                "00000010 6c 9c 23\tILLEYMIWTCWLFORG\n" +
                "21\tmsft\t0.597000000000\t2018-01-01T00:42:00.000000Z\ttrue\tP\t0.669079054612\t0.6591\t974\t\tVTJW\t8843532011989881581\t1970-01-01T05:33:20.000000Z\t17\t00000000 a2 3c d0 65 5e b7 95 2e 4a af c6 d0 19 6a de 46\n" +
                "00000010 04 d3\tZZBBUKOJSOLDYR\n" +
                "22\tgoogl\t0.702000000000\t2018-01-01T00:44:00.000000Z\ttrue\tR\t0.713456851675\t0.0921\t879\t2015-03-07T18:51:10.265Z\tHYRX\t5447530387277886439\t1970-01-01T05:50:00.000000Z\t2\t\tQSQJGDIHHNSS\n" +
                "23\tibm\t0.573000000000\t2018-01-01T00:46:00.000000Z\ttrue\tV\t0.322820281743\tNaN\t791\t2015-10-07T21:38:49.138Z\tPEHN\t4430387718690044436\t1970-01-01T06:06:40.000000Z\t34\t\tZVQQHSQSPZPB\n" +
                "24\tmsft\t0.238000000000\t2018-01-01T00:48:00.000000Z\ttrue\t\t0.479307307187\t0.6937\t635\t2015-10-11T00:49:46.817Z\tCPSW\t3860877990849202595\t1970-01-01T06:23:20.000000Z\t14\t\tPZNYVLTPKBBQ\n";

        String expected2 = "i\tsym2\tprice\ttimestamp\tb\tc\td\te\tf\tg\tik\tj\tk\tl\tm\tn\n" +
                "5\tgoogl\t0.868000000000\t2018-01-01T00:10:00.000000Z\ttrue\tZ\t0.427470428635\t0.0212\t179\t\t\t5746626297238459939\t1970-01-01T01:06:40.000000Z\t35\t00000000 91 88 28 a5 18 93 bd 0b 61 f5 5d d0 eb\tRGIIH\n" +
                "6\tmsft\t0.297000000000\t2018-01-01T00:12:00.000000Z\tfalse\tY\t0.267212048922\t0.1326\t215\t\t\t-8534688874718947140\t1970-01-01T01:23:20.000000Z\t34\t00000000 1c 0b 20 a2 86 89 37 11 2c 14\tUSZMZVQE\n" +
                "7\tgoogl\t0.076000000000\t2018-01-01T00:14:00.000000Z\ttrue\tE\t0.760625263412\t0.0658\t1018\t2015-02-23T07:09:35.550Z\tPEHN\t7797019568426198829\t1970-01-01T01:40:00.000000Z\t10\t00000000 80 c9 eb a3 67 7a 1a 79 e4 35 e4 3a dc 5c 65 ff\tIGYVFZ\n" +
                "8\tibm\t0.543000000000\t2018-01-01T00:16:00.000000Z\ttrue\tO\t0.483525620204\t0.8688\t355\t2015-09-06T20:21:06.672Z\t\t-9219078548506735248\t1970-01-01T01:56:40.000000Z\t33\t00000000 b3 14 cd 47 0b 0c 39 12 f7 05 10 f4 6d f1\tXUKLGMXSLUQ\n" +
                "9\tmsft\t0.623000000000\t2018-01-01T00:18:00.000000Z\tfalse\tI\t0.878611111254\t0.9966\t403\t2015-08-19T00:36:24.375Z\tCPSW\t-8506266080452644687\t1970-01-01T02:13:20.000000Z\t6\t00000000 9a ef 88 cb 4b a1 cf cf 41 7d a6\t\n" +
                "10\tmsft\t0.509000000000\t2018-01-01T00:20:00.000000Z\ttrue\tI\t0.491532681548\t0.0024\t195\t2015-10-15T17:45:21.025Z\t\t3987576220753016999\t1970-01-01T02:30:00.000000Z\t20\t00000000 96 37 08 dd 98 ef 54 88 2a a2\t\n" +
                "11\tmsft\t0.578000000000\t2018-01-01T00:22:00.000000Z\ttrue\tP\t0.746701366813\t0.5795\t122\t2015-11-25T07:36:56.937Z\t\t2004830221820243556\t1970-01-01T02:46:40.000000Z\t45\t00000000 a0 dd 44 11 e2 a3 24 4e 44 a8 0d fe 27 ec 53 13\n" +
                "00000010 5d b2 15 e7\tWGRMDGGIJYDVRV\n" +
                "12\tmsft\t0.661000000000\t2018-01-01T00:24:00.000000Z\ttrue\tO\t0.013960795460\t0.8136\t345\t2015-08-18T10:31:42.373Z\tVTJW\t5045825384817367685\t1970-01-01T03:03:20.000000Z\t23\t00000000 51 9d 5d 28 ac 02 2e fe 05 3b 94 5f ec d3 dc f8\n" +
                "00000010 43\tJCTIZKYFLUHZ\n" +
                "13\tibm\t0.704000000000\t2018-01-01T00:26:00.000000Z\ttrue\tK\t0.036735155240\t0.8406\t742\t2015-05-03T18:49:03.996Z\tPEHN\t2568830294369411037\t1970-01-01T03:20:00.000000Z\t24\t00000000 76 bc 45 24 cd 13 00 7c fb 01 19 ca f2 bf 84 5a\n" +
                "00000010 6f 38 35\t\n" +
                "14\tgoogl\t0.651000000000\t2018-01-01T00:28:00.000000Z\ttrue\tL\tNaN\t0.1389\t984\t2015-04-30T08:35:52.508Z\tHYRX\t-6929866925584807039\t1970-01-01T03:36:40.000000Z\t4\t00000000 4b fb 2d 16 f3 89 a3 83 64 de\t\n" +
                "15\tmsft\t0.409000000000\t2018-01-01T00:30:00.000000Z\tfalse\tW\t0.803404910559\t0.0440\t854\t2015-04-10T01:03:15.469Z\t\t-9050844408181442061\t1970-01-01T03:53:20.000000Z\t24\t\tPFYXPV\n" +
                "16\tgoogl\t0.839000000000\t2018-01-01T00:32:00.000000Z\tfalse\tN\t0.110480003996\t0.7096\t379\t2015-04-14T08:49:20.021Z\t\t-5749151825415257775\t1970-01-01T04:10:00.000000Z\t16\t\t\n" +
                "17\tibm\t0.286000000000\t2018-01-01T00:34:00.000000Z\tfalse\t\t0.135352967461\tNaN\t595\t\tVTJW\t-6237826420165615015\t1970-01-01T04:26:40.000000Z\t33\t\tBEGMITI\n" +
                "18\tibm\t0.932000000000\t2018-01-01T00:36:00.000000Z\ttrue\tH\t0.889822641116\t0.7074\t130\t2015-04-09T21:18:23.066Z\tCPSW\t-5708280760166173503\t1970-01-01T04:43:20.000000Z\t27\t00000000 69 94 3f 7d ef 3b b8 be f8 a1 46 87 28 92 a3 9b\n" +
                "00000010 e3 cb\tNIZOSBOSE\n" +
                "19\tibm\t0.151000000000\t2018-01-01T00:38:00.000000Z\ttrue\tH\t0.392012963507\t0.5700\t748\t\t\t-8060696376111078264\t1970-01-01T05:00:00.000000Z\t11\t00000000 10 20 81 c6 3d bc b5 05 2b 73 51 cf c3 7e c0 1d\n" +
                "00000010 6c a9\tTDCEBYWXBBZV\n" +
                "20\tgoogl\t0.624000000000\t2018-01-01T00:40:00.000000Z\tfalse\tY\t0.586393781337\t0.2103\t834\t2015-10-15T00:48:00.413Z\tHYRX\t7913225810447636126\t1970-01-01T05:16:40.000000Z\t12\t00000000 f6 78 09 1c 5d 88 f5 52 fd 36 02 50 d9 a0 b5 90\n" +
                "00000010 6c 9c 23\tILLEYMIWTCWLFORG\n" +
                "21\tmsft\t0.597000000000\t2018-01-01T00:42:00.000000Z\ttrue\tP\t0.669079054612\t0.6591\t974\t\tVTJW\t8843532011989881581\t1970-01-01T05:33:20.000000Z\t17\t00000000 a2 3c d0 65 5e b7 95 2e 4a af c6 d0 19 6a de 46\n" +
                "00000010 04 d3\tZZBBUKOJSOLDYR\n" +
                "22\tgoogl\t0.702000000000\t2018-01-01T00:44:00.000000Z\ttrue\tR\t0.713456851675\t0.0921\t879\t2015-03-07T18:51:10.265Z\tHYRX\t5447530387277886439\t1970-01-01T05:50:00.000000Z\t2\t\tQSQJGDIHHNSS\n" +
                "23\tibm\t0.573000000000\t2018-01-01T00:46:00.000000Z\ttrue\tV\t0.322820281743\tNaN\t791\t2015-10-07T21:38:49.138Z\tPEHN\t4430387718690044436\t1970-01-01T06:06:40.000000Z\t34\t\tZVQQHSQSPZPB\n" +
                "24\tmsft\t0.238000000000\t2018-01-01T00:48:00.000000Z\ttrue\t\t0.479307307187\t0.6937\t635\t2015-10-11T00:49:46.817Z\tCPSW\t3860877990849202595\t1970-01-01T06:23:20.000000Z\t14\t\tPZNYVLTPKBBQ\n" +
                "25\tmsft\t0.918000000000\t2018-01-01T00:50:00.000000Z\tfalse\t\t0.326136520120\t0.3394\t176\t2015-02-06T18:42:24.631Z\t\t-8352023907145286323\t1970-01-01T06:40:00.000000Z\t14\t00000000 6a 9b cd bb 2e 74 cd 44 54 13 3f ff\tIGENFEL\n" +
                "26\tibm\t0.330000000000\t2018-01-01T00:52:00.000000Z\tfalse\tM\t0.198236477005\tNaN\t557\t2015-01-30T03:27:34.392Z\t\t5324839128380055812\t1970-01-01T06:56:40.000000Z\t25\t00000000 25 07 db 62 44 33 6e 00 8e 93 bd 27 42 f8 25 2a\n" +
                "00000010 42 71 a3 7a\tDNZNLCNGZTOY\n" +
                "27\tmsft\t0.673000000000\t2018-01-01T00:54:00.000000Z\tfalse\tP\t0.527776712011\t0.0237\t517\t2015-05-20T07:51:29.675Z\tPEHN\t-7667438647671231061\t1970-01-01T07:13:20.000000Z\t22\t00000000 61 99 be 2d f5 30 78 6d 5a 3b\t\n" +
                "28\tgoogl\t0.173000000000\t2018-01-01T00:56:00.000000Z\tfalse\tY\t0.991107083990\t0.6699\t324\t2015-03-27T18:25:29.970Z\t\t-4241819446186560308\t1970-01-01T07:30:00.000000Z\t35\t00000000 34 cd 15 35 bb a4 a3 c8 66 0c 40 71 ea 20\tTHMHZNVZHCNX\n" +
                "29\tibm\t0.240000000000\t2018-01-01T00:58:00.000000Z\tfalse\t\tNaN\t0.5505\t667\t2015-12-17T04:08:24.080Z\tPEHN\t-1549327479854558367\t1970-01-01T07:46:40.000000Z\t10\t00000000 03 5b 11 44 83 06 63 2b 58 3b 4b b7 e2 7f ab 6e\n" +
                "00000010 23 03 dd\t\n" +
                "30\tibm\t0.607000000000\t2018-01-01T01:00:00.000000Z\ttrue\tF\t0.478083025671\t0.0109\t998\t2015-04-30T21:40:32.732Z\tCPSW\t4654788096008024367\t1970-01-01T08:03:20.000000Z\t32\t\tJLKTRD\n" +
                "31\tibm\t0.181000000000\t2018-01-01T01:02:00.000000Z\tfalse\tCDE\t0.545517532479\t0.8249\t905\t2015-03-02T13:31:56.918Z\tLVSY\t-4086246124104754347\t1970-01-01T00:00:00.000000Z\t26\t00000000 4b c0 d9 1c 71 cf 5a 8f 21 06 b2 3f\t\n" +
                "32\tibm\t0.473000000000\t2018-01-01T01:04:00.000000Z\tfalse\tABC\t0.039609681243\t0.0783\t42\t2015-05-21T22:28:44.644Z\tKKUS\t2296041148680180183\t1970-01-01T00:16:40.000000Z\t35\t\tSVCLLERSMKRZ\n" +
                "33\tibm\t0.600000000000\t2018-01-01T01:06:00.000000Z\tfalse\tCDE\t0.114236183457\t0.9925\t288\t2015-11-17T19:46:21.874Z\tLVSY\t-5669452046744991419\t1970-01-01T00:33:20.000000Z\t43\t00000000 ef 2d 99 66 3d db c1 cc b8 82 3d ec f3 66 5e 70\n" +
                "00000010 38 5e\tBWWXFQDCQSCMO\n" +
                "34\tmsft\t0.867000000000\t2018-01-01T01:08:00.000000Z\tfalse\tKZZ\t0.759386845801\tNaN\t177\t2015-01-12T08:29:36.861Z\t\t7660755785254391246\t1970-01-01T00:50:00.000000Z\t22\t00000000 a0 ba a5 d1 63 ca 32 e5 0d 68 52 c6 94 c3 18 c9\n" +
                "00000010 7c\t\n" +
                "35\tgoogl\t0.110000000000\t2018-01-01T01:10:00.000000Z\tfalse\t\t0.361900664543\t0.1061\t510\t2015-09-11T19:36:06.058Z\t\t-8165131599894925271\t1970-01-01T01:06:40.000000Z\t4\t\tGZGKCGBZDMGYDEQ\n" +
                "36\tibm\t0.072000000000\t2018-01-01T01:12:00.000000Z\ttrue\tABC\t0.120493929019\t0.7400\t65\t2015-06-24T11:48:21.316Z\t\t7391407846465678094\t1970-01-01T01:23:20.000000Z\t39\t00000000 bb c3 ec 4b 97 27 df cd 7a 14 07 92 01 f5 6a\t\n" +
                "37\tmsft\t0.730000000000\t2018-01-01T01:14:00.000000Z\ttrue\tABC\t0.271767050564\t0.2094\t941\t2015-02-13T23:00:15.193Z\tLVSY\t-4554200170857361590\t1970-01-01T01:40:00.000000Z\t11\t00000000 3f 4e 27 42 f2 f8 5e 29 d3 b9 67 75 95 fa 1f 92\n" +
                "00000010 24 b1\tWYUHNBCCPM\n" +
                "38\tgoogl\t0.590000000000\t2018-01-01T01:16:00.000000Z\ttrue\tABC\t0.129651074121\t0.4552\t350\t2015-05-27T12:47:41.516Z\tHGKR\t7992851382615210277\t1970-01-01T01:56:40.000000Z\t26\t\tKUNRDCWNPQYTEW\n" +
                "39\tibm\t0.477000000000\t2018-01-01T01:18:00.000000Z\ttrue\t\t0.110401374980\t0.4204\t701\t2015-02-06T16:19:37.448Z\tHGKR\t7258963507745426614\t1970-01-01T02:13:20.000000Z\t13\t\tFCSRED\n" +
                "40\tmsft\t0.009000000000\t2018-01-01T01:20:00.000000Z\ttrue\tCDE\t0.102884322639\t0.9336\t626\t2015-07-16T10:03:42.421Z\tKKUS\t5578476878779881545\t1970-01-01T02:30:00.000000Z\t14\t00000000 3f d6 88 3a 93 ef 24 a5 e2 bc 86 f9 92 a3 f1 92\n" +
                "00000010 08 f1 96 7f\tWUVMBPSVEZDY\n" +
                "41\tibm\t0.255000000000\t2018-01-01T01:22:00.000000Z\tfalse\tCDE\t0.007868356217\t0.0238\t610\t2015-08-16T21:13:37.471Z\t\t-1995707592048806500\t1970-01-01T02:46:40.000000Z\t49\t\tXGYTEQCHND\n" +
                "42\tmsft\t0.949000000000\t2018-01-01T01:24:00.000000Z\ttrue\tCDE\t0.095941136525\tNaN\t521\t2015-12-25T22:48:17.395Z\tHGKR\t-7773905708214258099\t1970-01-01T03:03:20.000000Z\t31\t00000000 b8 17 f7 41 ff c1 a7 5c c3 31 17 dd 8d\tSXQSTVSTYSWHLSWP\n" +
                "43\tgoogl\t0.056000000000\t2018-01-01T01:26:00.000000Z\tfalse\tABC\t0.922772279885\t0.6153\t497\t2015-04-20T22:03:28.690Z\tHGKR\t-3371567840873310066\t1970-01-01T03:20:00.000000Z\t28\t00000000 b2 31 9c 69 be 74 9a ad cc cf b8 e4 d1 7a 4f\tEODDBHEVGXY\n" +
                "44\tgoogl\t0.552000000000\t2018-01-01T01:28:00.000000Z\tfalse\tABC\t0.176033573712\t0.1548\t707\t\tLVSY\t3865339280024677042\t1970-01-01T03:36:40.000000Z\t15\t\tBIDSTDTFB\n" +
                "45\tgoogl\t0.032000000000\t2018-01-01T01:30:00.000000Z\ttrue\tKZZ\tNaN\t0.4632\t309\t2015-09-22T19:28:39.051Z\tLVSY\t8710436463015317840\t1970-01-01T03:53:20.000000Z\t9\t00000000 66 ca 85 56 e2 44 db b8 e9 93 fc d9 cb 99\tCIYIXGHRQQTKO\n" +
                "46\tgoogl\t0.064000000000\t2018-01-01T01:32:00.000000Z\tfalse\t\tNaN\t0.3006\t272\t2015-11-11T13:18:22.490Z\tLMSR\t8918536674918169108\t1970-01-01T04:10:00.000000Z\t34\t00000000 27 c7 97 9b 8b f8 04 6f d6 af 3f 2f 84 d5 12 fb\n" +
                "00000010 71 99 34\tQUJJFGQIZKM\n" +
                "47\tgoogl\t0.183000000000\t2018-01-01T01:34:00.000000Z\ttrue\tCDE\t0.515511062899\t0.8308\t1002\t2015-06-19T13:11:21.018Z\tHGKR\t-4579557415183386099\t1970-01-01T04:26:40.000000Z\t34\t00000000 92 ff 37 63 be 5f b7 70 a0 07 8f 33\tIHCTIVYIVCHUC\n" +
                "48\tgoogl\t0.164000000000\t2018-01-01T01:36:00.000000Z\ttrue\tABC\t0.181000422866\t0.5756\t415\t2015-04-28T21:13:18.568Z\t\t7970442953226983551\t1970-01-01T04:43:20.000000Z\t19\t\t\n" +
                "49\tibm\t0.048000000000\t2018-01-01T01:38:00.000000Z\ttrue\t\t0.374466137193\t0.1264\t28\t2015-09-06T14:09:17.223Z\tHGKR\t-7172806426401245043\t1970-01-01T05:00:00.000000Z\t29\t00000000 42 9e 8a 86 17 89 6b c0 cd a4 21 12 b7 e3\tRPYKHPMBMDR\n" +
                "50\tibm\t0.706000000000\t2018-01-01T01:40:00.000000Z\tfalse\tKZZ\t0.474347929050\t0.5190\t864\t2015-04-26T09:59:33.624Z\tKKUS\t2808899229016932370\t1970-01-01T05:16:40.000000Z\t5\t00000000 39 dc 8c 6c 6b ac 60 aa bc f4 27 61 78\tPGHPS\n" +
                "51\tgoogl\t0.761000000000\t2018-01-01T01:42:00.000000Z\ttrue\tABC\t0.252512889184\tNaN\t719\t2015-05-22T06:14:06.815Z\tHGKR\t7822359916932392178\t1970-01-01T05:33:20.000000Z\t2\t00000000 26 4f e4 51 37 85 e1 e4 6e 75 fc f4 57 0e 7b 09\n" +
                "00000010 de 09 5e d7\tWCDVPWCYCTDDNJ\n" +
                "52\tgoogl\t0.512000000000\t2018-01-01T01:44:00.000000Z\tfalse\tABC\t0.411220836986\t0.2756\t740\t2015-02-23T09:03:19.389Z\tHGKR\t1930705357282501293\t1970-01-01T05:50:00.000000Z\t19\t\t\n" +
                "53\tgoogl\t0.106000000000\t2018-01-01T01:46:00.000000Z\ttrue\tABC\t0.586984299235\t0.4215\t762\t2015-03-07T03:16:06.453Z\tHGKR\t-7872707389967331757\t1970-01-01T06:06:40.000000Z\t3\t00000000 8d ca 1d d0 b2 eb 54 3f 32 82\tQQDOZFIDQTYO\n" +
                "54\tmsft\t0.266000000000\t2018-01-01T01:48:00.000000Z\tfalse\t\t0.266520042530\t0.0911\t937\t\t\t-7761587678997431446\t1970-01-01T06:23:20.000000Z\t39\t00000000 53 28 c0 93 b2 7b c7 55 0c dd fd c1\t\n";


        String query = "select * from y limit 4,-6";
        testLimit(expected, expected2, query);
    }

    @Test
    public void testTopN() throws Exception {
        String expected = "i\tsym2\tprice\ttimestamp\tb\tc\td\te\tf\tg\tik\tj\tk\tl\tm\tn\n" +
                "1\tmsft\t0.509000000000\t2018-01-01T00:02:00.000000Z\tfalse\tU\t0.524372285929\t0.8072\t365\t2015-05-02T19:30:57.935Z\t\t-4485747798769957016\t1970-01-01T00:00:00.000000Z\t19\t00000000 19 c4 95 94 36 53 49 b4 59 7e 3b 08 a1 1e\tYSBEOUOJSHRUEDRQ\n" +
                "2\tgoogl\t0.423000000000\t2018-01-01T00:04:00.000000Z\tfalse\tG\t0.529840594176\tNaN\t493\t2015-04-09T11:42:28.332Z\tHYRX\t-8811278461560712840\t1970-01-01T00:16:40.000000Z\t29\t00000000 53 d0 fb 64 bb 1a d4 f0 2d 40 e2 4b b1 3e e3 f1\t\n" +
                "3\tgoogl\t0.174000000000\t2018-01-01T00:06:00.000000Z\tfalse\tW\t0.882822836670\t0.7230\t845\t2015-08-26T10:57:26.275Z\tVTJW\t9029468389542245059\t1970-01-01T00:33:20.000000Z\t46\t00000000 e5 61 2f 64 0e 2c 7f d7 6f b8 c9 ae 28 c7 84 47\tDSWUGSHOLNV\n" +
                "4\tibm\t0.148000000000\t2018-01-01T00:08:00.000000Z\ttrue\tI\t0.345689799154\t0.2401\t775\t2015-08-03T15:58:03.335Z\tVTJW\t-8910603140262731534\t1970-01-01T00:50:00.000000Z\t24\t00000000 ac a8 3b a6 dc 3b 7d 2b e3 92 fe 69 38 e1 77 9a\n" +
                "00000010 e7 0c 89\tLJUMLGLHMLLEO\n" +
                "5\tgoogl\t0.868000000000\t2018-01-01T00:10:00.000000Z\ttrue\tZ\t0.427470428635\t0.0212\t179\t\t\t5746626297238459939\t1970-01-01T01:06:40.000000Z\t35\t00000000 91 88 28 a5 18 93 bd 0b 61 f5 5d d0 eb\tRGIIH\n";

        testLimit(expected, expected, "select * from y limit 5");
    }

    @Test
    public void testTopN2() throws Exception {
        String expected = "i\tsym2\tprice\ttimestamp\tb\tc\td\te\tf\tg\tik\tj\tk\tl\tm\tn\n" +
                "1\tmsft\t0.509000000000\t2018-01-01T00:02:00.000000Z\tfalse\tU\t0.524372285929\t0.8072\t365\t2015-05-02T19:30:57.935Z\t\t-4485747798769957016\t1970-01-01T00:00:00.000000Z\t19\t00000000 19 c4 95 94 36 53 49 b4 59 7e 3b 08 a1 1e\tYSBEOUOJSHRUEDRQ\n" +
                "2\tgoogl\t0.423000000000\t2018-01-01T00:04:00.000000Z\tfalse\tG\t0.529840594176\tNaN\t493\t2015-04-09T11:42:28.332Z\tHYRX\t-8811278461560712840\t1970-01-01T00:16:40.000000Z\t29\t00000000 53 d0 fb 64 bb 1a d4 f0 2d 40 e2 4b b1 3e e3 f1\t\n" +
                "3\tgoogl\t0.174000000000\t2018-01-01T00:06:00.000000Z\tfalse\tW\t0.882822836670\t0.7230\t845\t2015-08-26T10:57:26.275Z\tVTJW\t9029468389542245059\t1970-01-01T00:33:20.000000Z\t46\t00000000 e5 61 2f 64 0e 2c 7f d7 6f b8 c9 ae 28 c7 84 47\tDSWUGSHOLNV\n" +
                "4\tibm\t0.148000000000\t2018-01-01T00:08:00.000000Z\ttrue\tI\t0.345689799154\t0.2401\t775\t2015-08-03T15:58:03.335Z\tVTJW\t-8910603140262731534\t1970-01-01T00:50:00.000000Z\t24\t00000000 ac a8 3b a6 dc 3b 7d 2b e3 92 fe 69 38 e1 77 9a\n" +
                "00000010 e7 0c 89\tLJUMLGLHMLLEO\n" +
                "5\tgoogl\t0.868000000000\t2018-01-01T00:10:00.000000Z\ttrue\tZ\t0.427470428635\t0.0212\t179\t\t\t5746626297238459939\t1970-01-01T01:06:40.000000Z\t35\t00000000 91 88 28 a5 18 93 bd 0b 61 f5 5d d0 eb\tRGIIH\n";

        testLimit(expected, expected, "select * from y limit 0,5");
    }

    @Test
    public void testTopNVariable() throws Exception {
        String query = "select * from y limit :lim";
        TestUtils.assertMemoryLeak(() -> {
            try {
                String expected1 = "i\tsym2\tprice\ttimestamp\tb\tc\td\te\tf\tg\tik\tj\tk\tl\tm\tn\n" +
                        "1\tmsft\t0.509000000000\t2018-01-01T00:02:00.000000Z\tfalse\tU\t0.524372285929\t0.8072\t365\t2015-05-02T19:30:57.935Z\t\t-4485747798769957016\t1970-01-01T00:00:00.000000Z\t19\t00000000 19 c4 95 94 36 53 49 b4 59 7e 3b 08 a1 1e\tYSBEOUOJSHRUEDRQ\n" +
                        "2\tgoogl\t0.423000000000\t2018-01-01T00:04:00.000000Z\tfalse\tG\t0.529840594176\tNaN\t493\t2015-04-09T11:42:28.332Z\tHYRX\t-8811278461560712840\t1970-01-01T00:16:40.000000Z\t29\t00000000 53 d0 fb 64 bb 1a d4 f0 2d 40 e2 4b b1 3e e3 f1\t\n" +
                        "3\tgoogl\t0.174000000000\t2018-01-01T00:06:00.000000Z\tfalse\tW\t0.882822836670\t0.7230\t845\t2015-08-26T10:57:26.275Z\tVTJW\t9029468389542245059\t1970-01-01T00:33:20.000000Z\t46\t00000000 e5 61 2f 64 0e 2c 7f d7 6f b8 c9 ae 28 c7 84 47\tDSWUGSHOLNV\n" +
                        "4\tibm\t0.148000000000\t2018-01-01T00:08:00.000000Z\ttrue\tI\t0.345689799154\t0.2401\t775\t2015-08-03T15:58:03.335Z\tVTJW\t-8910603140262731534\t1970-01-01T00:50:00.000000Z\t24\t00000000 ac a8 3b a6 dc 3b 7d 2b e3 92 fe 69 38 e1 77 9a\n" +
                        "00000010 e7 0c 89\tLJUMLGLHMLLEO\n";

                String expected2 = "i\tsym2\tprice\ttimestamp\tb\tc\td\te\tf\tg\tik\tj\tk\tl\tm\tn\n" +
                        "1\tmsft\t0.509000000000\t2018-01-01T00:02:00.000000Z\tfalse\tU\t0.524372285929\t0.8072\t365\t2015-05-02T19:30:57.935Z\t\t-4485747798769957016\t1970-01-01T00:00:00.000000Z\t19\t00000000 19 c4 95 94 36 53 49 b4 59 7e 3b 08 a1 1e\tYSBEOUOJSHRUEDRQ\n" +
                        "2\tgoogl\t0.423000000000\t2018-01-01T00:04:00.000000Z\tfalse\tG\t0.529840594176\tNaN\t493\t2015-04-09T11:42:28.332Z\tHYRX\t-8811278461560712840\t1970-01-01T00:16:40.000000Z\t29\t00000000 53 d0 fb 64 bb 1a d4 f0 2d 40 e2 4b b1 3e e3 f1\t\n" +
                        "3\tgoogl\t0.174000000000\t2018-01-01T00:06:00.000000Z\tfalse\tW\t0.882822836670\t0.7230\t845\t2015-08-26T10:57:26.275Z\tVTJW\t9029468389542245059\t1970-01-01T00:33:20.000000Z\t46\t00000000 e5 61 2f 64 0e 2c 7f d7 6f b8 c9 ae 28 c7 84 47\tDSWUGSHOLNV\n" +
                        "4\tibm\t0.148000000000\t2018-01-01T00:08:00.000000Z\ttrue\tI\t0.345689799154\t0.2401\t775\t2015-08-03T15:58:03.335Z\tVTJW\t-8910603140262731534\t1970-01-01T00:50:00.000000Z\t24\t00000000 ac a8 3b a6 dc 3b 7d 2b e3 92 fe 69 38 e1 77 9a\n" +
                        "00000010 e7 0c 89\tLJUMLGLHMLLEO\n" +
                        "5\tgoogl\t0.868000000000\t2018-01-01T00:10:00.000000Z\ttrue\tZ\t0.427470428635\t0.0212\t179\t\t\t5746626297238459939\t1970-01-01T01:06:40.000000Z\t35\t00000000 91 88 28 a5 18 93 bd 0b 61 f5 5d d0 eb\tRGIIH\n" +
                        "6\tmsft\t0.297000000000\t2018-01-01T00:12:00.000000Z\tfalse\tY\t0.267212048922\t0.1326\t215\t\t\t-8534688874718947140\t1970-01-01T01:23:20.000000Z\t34\t00000000 1c 0b 20 a2 86 89 37 11 2c 14\tUSZMZVQE\n";

                compiler.compile(
                        "create table y as (" +
                                "select" +
                                " to_int(x) i," +
                                " rnd_symbol('msft','ibm', 'googl') sym2," +
                                " round(rnd_double(0), 3) price," +
                                " to_timestamp('2018-01', 'yyyy-MM') + x * 120000000 timestamp," +
                                " rnd_boolean() b," +
                                " rnd_str(1,1,2) c," +
                                " rnd_double(2) d," +
                                " rnd_float(2) e," +
                                " rnd_short(10,1024) f," +
                                " rnd_date(to_date('2015', 'yyyy'), to_date('2016', 'yyyy'), 2) g," +
                                " rnd_symbol(4,4,4,2) ik," +
                                " rnd_long() j," +
                                " timestamp_sequence(to_timestamp(0), 1000000000) k," +
                                " rnd_byte(2,50) l," +
                                " rnd_bin(10, 20, 2) m," +
                                " rnd_str(5,16,2) n" +
                                " from long_sequence(30)" +
                                ") timestamp(timestamp)"
                        , bindVariableService
                );

                bindVariableService.setLong("lim", 4);
                assertQueryAndCache(expected1, query, "timestamp", true);
                bindVariableService.setLong("lim", 6);
                assertQueryAndCache(expected2, query, "timestamp", true);
            } finally {
                engine.releaseAllWriters();
                engine.releaseAllReaders();
            }
        });
    }

    @Test
    public void testTopRange() throws Exception {
        String expected = "i\tsym2\tprice\ttimestamp\tb\tc\td\te\tf\tg\tik\tj\tk\tl\tm\tn\n" +
                "5\tgoogl\t0.868000000000\t2018-01-01T00:10:00.000000Z\ttrue\tZ\t0.427470428635\t0.0212\t179\t\t\t5746626297238459939\t1970-01-01T01:06:40.000000Z\t35\t00000000 91 88 28 a5 18 93 bd 0b 61 f5 5d d0 eb\tRGIIH\n" +
                "6\tmsft\t0.297000000000\t2018-01-01T00:12:00.000000Z\tfalse\tY\t0.267212048922\t0.1326\t215\t\t\t-8534688874718947140\t1970-01-01T01:23:20.000000Z\t34\t00000000 1c 0b 20 a2 86 89 37 11 2c 14\tUSZMZVQE\n";

        String query = "select * from y limit 4,6";
        testLimit(expected, expected, query);
    }

    private void testLimit(String expected1, String expected2, String query) throws Exception {
        TestUtils.assertMemoryLeak(() -> {
            try {
                compiler.compile(
                        "create table y as (" +
                                "select" +
                                " to_int(x) i," +
                                " rnd_symbol('msft','ibm', 'googl') sym2," +
                                " round(rnd_double(0), 3) price," +
                                " to_timestamp('2018-01', 'yyyy-MM') + x * 120000000 timestamp," +
                                " rnd_boolean() b," +
                                " rnd_str(1,1,2) c," +
                                " rnd_double(2) d," +
                                " rnd_float(2) e," +
                                " rnd_short(10,1024) f," +
                                " rnd_date(to_date('2015', 'yyyy'), to_date('2016', 'yyyy'), 2) g," +
                                " rnd_symbol(4,4,4,2) ik," +
                                " rnd_long() j," +
                                " timestamp_sequence(to_timestamp(0), 1000000000) k," +
                                " rnd_byte(2,50) l," +
                                " rnd_bin(10, 20, 2) m," +
                                " rnd_str(5,16,2) n" +
                                " from long_sequence(30)" +
                                ") timestamp(timestamp)"
                        , bindVariableService
                );
                assertQueryAndCache(expected1, query, "timestamp", true);

                compiler.compile(
                        "insert into y select * from " +
                                "(select" +
                                " to_int(x + 30) i," +
                                " rnd_symbol('msft','ibm', 'googl') sym2," +
                                " round(rnd_double(0), 3) price," +
                                " to_timestamp('2018-01', 'yyyy-MM') + (x + 30) * 120000000 timestamp," +
                                " rnd_boolean() b," +
                                " rnd_str('ABC', 'CDE', null, 'KZZ') c," +
                                " rnd_double(2) d," +
                                " rnd_float(2) e," +
                                " rnd_short(10,1024) f," +
                                " rnd_date(to_date('2015', 'yyyy'), to_date('2016', 'yyyy'), 2) g," +
                                " rnd_symbol(4,4,4,2) ik," +
                                " rnd_long() j," +
                                " timestamp_sequence(to_timestamp(0), 1000000000) k," +
                                " rnd_byte(2,50) l," +
                                " rnd_bin(10, 20, 2) m," +
                                " rnd_str(5,16,2) n" +
                                " from long_sequence(30)" +
                                ") timestamp(timestamp)"
                        , bindVariableService
                );

                assertQuery(expected2, query, "timestamp", true);

            } finally {
                engine.releaseAllWriters();
                engine.releaseAllReaders();
            }
        });
    }
}
