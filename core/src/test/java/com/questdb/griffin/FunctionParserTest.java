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
import com.questdb.cairo.CairoConfiguration;
import com.questdb.cairo.DefaultCairoConfiguration;
import com.questdb.cairo.TestRecord;
import com.questdb.cairo.sql.Record;
import com.questdb.common.ColumnType;
import com.questdb.common.RecordColumnMetadata;
import com.questdb.common.SymbolTable;
import com.questdb.griffin.common.ExprNode;
import com.questdb.griffin.engine.functions.Parameter;
import com.questdb.griffin.engine.functions.bool.NotVFunctionFactory;
import com.questdb.griffin.engine.functions.bool.OrVVFunctionFactory;
import com.questdb.griffin.engine.functions.date.SysdateFunctionFactory;
import com.questdb.griffin.engine.functions.math.*;
import com.questdb.griffin.engine.functions.str.*;
import com.questdb.griffin.lexer.ExprAstBuilder;
import com.questdb.griffin.lexer.ExprParser;
import com.questdb.griffin.lexer.ParserException;
import com.questdb.ql.CollectionRecordMetadata;
import com.questdb.std.*;
import com.questdb.std.time.DateFormatUtils;
import com.questdb.std.time.MillisecondClock;
import com.questdb.test.tools.TestUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;

public class FunctionParserTest extends AbstractCairoTest {
    private static final ObjectPool<ExprNode> exprNodeObjectPool = new ObjectPool<>(ExprNode.FACTORY, 128);
    private static final Lexer2 lexer = new Lexer2();
    private static final ExprParser parser = new ExprParser(exprNodeObjectPool);
    private static final ExprAstBuilder astBuilder = new ExprAstBuilder();
    private static final CharSequenceObjHashMap<Parameter> params = new CharSequenceObjHashMap<>();
    private static final ArrayList<FunctionFactory> functions = new ArrayList<>();

    @Before
    public void setUp2() {
        params.clear();
        exprNodeObjectPool.clear();
        functions.clear();
        ExprParser.configureLexer(lexer);
    }

    @Test
    public void testAmbiguousFunctionInvocation() {
        functions.add(new AddLongVVFunctionFactory());
        functions.add(new AddIntVVFunctionFactory());
        final CollectionRecordMetadata metadata = new CollectionRecordMetadata();
        metadata.add(new TestColumnMetadata("a", ColumnType.SHORT));
        metadata.add(new TestColumnMetadata("c", ColumnType.SHORT));
        assertFail(2, "ambiguous function call: +(SHORT,SHORT)", "a + c", metadata);
    }

    @Test
    public void testBooleanConstants() throws ParserException {
        functions.add(new NotVFunctionFactory());
        functions.add(new OrVVFunctionFactory());

        final Record record = new Record() {
            @Override
            public boolean getBool(int col) {
                // col0 = false
                return false;
            }
        };

        final CollectionRecordMetadata metadata = new CollectionRecordMetadata();
        metadata.add(new TestColumnMetadata("a", ColumnType.BOOLEAN));
        FunctionParser functionParser = createFunctionParser();
        Function function = parseFunction("a or not false", metadata, functionParser);
        Assert.assertEquals(ColumnType.BOOLEAN, function.getType());
        Assert.assertTrue(function.getBool(record));

        Function function2 = parseFunction("a or true", metadata, functionParser);
        Assert.assertTrue(function2.getBool(record));
    }

    @Test
    public void testBooleanFunctions() throws ParserException {

        functions.add(new NotVFunctionFactory());
        functions.add(new OrVVFunctionFactory());

        final Record record = new Record() {
            @Override
            public boolean getBool(int col) {
                // col0 = false
                // col1 = true
                return col != 0;
            }
        };

        final CollectionRecordMetadata metadata = new CollectionRecordMetadata();
        metadata.add(new TestColumnMetadata("a", ColumnType.BOOLEAN));
        metadata.add(new TestColumnMetadata("b", ColumnType.BOOLEAN));
        FunctionParser functionParser = createFunctionParser();
        Function function = parseFunction("a or not b", metadata, functionParser);
        Assert.assertEquals(ColumnType.BOOLEAN, function.getType());
        Assert.assertFalse(function.getBool(record));
    }

    @Test
    public void testByteAndLongToFloatCast() throws ParserException {
        assertCastToFloat(363, ColumnType.BYTE, ColumnType.LONG, new Record() {

            @Override
            public byte getByte(int col) {
                return 18;
            }

            @Override
            public long getLong(int col) {
                return 345;
            }
        });
    }

    @Test
    public void testByteAndShortToIntCast() throws ParserException {
        functions.add(new AddIntVVFunctionFactory());
        final CollectionRecordMetadata metadata = new CollectionRecordMetadata();
        metadata.add(new TestColumnMetadata("a", ColumnType.BYTE));
        metadata.add(new TestColumnMetadata("b", ColumnType.SHORT));
        FunctionParser functionParser = createFunctionParser();
        Function function = parseFunction("a+b", metadata, functionParser);
        Assert.assertEquals(ColumnType.INT, function.getType());
        Assert.assertEquals(33, function.getInt(new Record() {
            @Override
            public byte getByte(int col) {
                return 12;
            }

            @Override
            public short getShort(int col) {
                return 21;
            }
        }));
    }

    @Test
    public void testByteToDoubleCast() throws ParserException {
        assertCastToDouble(131, ColumnType.BYTE, ColumnType.BYTE, new Record() {
            @Override
            public byte getByte(int col) {
                if (col == 0) {
                    return 41;
                }
                return 90;
            }
        });
    }

    @Test
    public void testByteToLongCast() throws ParserException {
        assertCastToLong(131, ColumnType.BYTE, ColumnType.BYTE, new Record() {
            @Override
            public byte getByte(int col) {
                if (col == 0) {
                    return 41;
                }
                return 90;
            }
        });
    }

    @Test
    public void testByteToShortCast() throws ParserException {
        functions.add(new AddShortVVFunctionFactory());
        final CollectionRecordMetadata metadata = new CollectionRecordMetadata();
        metadata.add(new TestColumnMetadata("a", ColumnType.BYTE));
        metadata.add(new TestColumnMetadata("b", ColumnType.BYTE));
        FunctionParser functionParser = createFunctionParser();
        Function function = parseFunction("a+b", metadata, functionParser);
        Assert.assertEquals(ColumnType.SHORT, function.getType());
        Assert.assertEquals(131, function.getShort(new Record() {
            @Override
            public byte getByte(int col) {
                if (col == 0) {
                    return 41;
                }
                return 90;
            }
        }));
    }

    @Test
    public void testFloatAndLongToDoubleCast() throws ParserException {
        assertCastToDouble(468.3, ColumnType.FLOAT, ColumnType.LONG, new Record() {
            @Override
            public float getFloat(int col) {
                return 123.3f;
            }

            @Override
            public long getLong(int col) {
                return 345;
            }
        });
    }

    @Test
    public void testFunctionDoesNotExist() {
        final CollectionRecordMetadata metadata = new CollectionRecordMetadata();
        metadata.add(new TestColumnMetadata("a", ColumnType.BOOLEAN));
        metadata.add(new TestColumnMetadata("c", ColumnType.SYMBOL));
        assertFail(5, "unknown function name: xyz(BOOLEAN,SYMBOL)", "a or xyz(a,c)", metadata);
    }

    @Test
    public void testFunctionOverload() throws ParserException {
        functions.add(new ToCharDateVCFunctionFactory());
        functions.add(new ToCharTimestampVCFunctionFactory());
        functions.add(new ToCharBinVFunctionFactory());

        final CollectionRecordMetadata metadata = new CollectionRecordMetadata();
        metadata.add(new TestColumnMetadata("a", ColumnType.DATE));
        metadata.add(new TestColumnMetadata("b", ColumnType.TIMESTAMP));
        metadata.add(new TestColumnMetadata("c", ColumnType.BINARY));

        FunctionParser functionParser = createFunctionParser();
        Record record = new TestRecord();

        Function function = parseFunction("to_char(a, 'EE, dd-MMM-yyyy hh:mm:ss')", metadata, functionParser);
        Assert.assertEquals(ColumnType.STRING, function.getType());
        TestUtils.assertEquals("Thursday, 03-Apr-150577 03:54:03", function.getStr(record));

        Function function2 = parseFunction("to_char(b, 'EE, dd-MMM-yyyy hh:mm:ss')", metadata, functionParser);
        Assert.assertEquals(ColumnType.STRING, function2.getType());
        TestUtils.assertEquals("Tuesday, 21-Nov-2119 08:50:58", function2.getStr(record));

        Function function3 = parseFunction("to_char(c)", metadata, functionParser);

        String expectedBin = "00000000 1d 15 55 8a 17 fa d8 cc 14 ce f1 59 88 c4 91 3b\n" +
                "00000010 72 db f3 04 1b c7 88 de a0 79 3c 77 15 68 61 26\n" +
                "00000020 af 19 c4 95 94 36 53 49 b4 59 7e 3b 08 a1 1e 38\n" +
                "00000030 8d 1b 9e f4 c8 39 09 fe d8 9d 30 78 36 6a 32 de\n" +
                "00000040 e4 7c d2 35 07 42 fc 31 79 5f 8b 81 2b 93 4d 1a\n" +
                "00000050 8e 78 b5 b9 11 53 d0 fb 64 bb 1a d4 f0 2d 40 e2\n" +
                "00000060 4b b1 3e e3 f1 f1 1e ca 9c 1d 06 ac 37 c8 cd 82\n" +
                "00000070 89 2b 4d 5f f6 46 90 c3 b3 59 8e e5 61 2f 64 0e\n" +
                "00000080 2c 7f d7 6f b8 c9 ae 28 c7 84 47 dc d2 85 7f a5\n" +
                "00000090 b8 7b 4a 9d 46 7c 8d dd 93 e6 d0 b3 2b 07 98 cc\n" +
                "000000a0 76 48 a3 bb 64 d2 ad 49 1c f2 3c ed 39 ac a8 3b\n" +
                "000000b0 a6 dc 3b 7d 2b e3 92 fe 69 38 e1 77 9a e7 0c 89\n" +
                "000000c0 14 58 63 b7 c2 9f 29 8e 29 5e 69 c6 eb ea c3 c9\n" +
                "000000d0 73 93 46 fe c2 d3 68 79 8b 43 1d 57 34 04 23 8d\n" +
                "000000e0 d8 57 91 88 28 a5 18 93 bd 0b 61 f5 5d d0 eb 67\n" +
                "000000f0 44 a7 6a 71 34 e0 b0 e9 98 f7 67 62 28 60 b0 ec\n" +
                "00000100 0b 92 58 7d 24 bc 2e 60 6a 1c 0b 20 a2 86 89 37\n" +
                "00000110 11 2c 14 0c 2d 20 84 52 d9 6f 04 ab 27 47 8f 23\n" +
                "00000120 3f ae 7c 9f 77 04 e9 0c ea 4e ea 8b f5 0f 2d b3\n" +
                "00000130 14 33 80 c9 eb a3 67 7a 1a 79 e4 35 e4 3a dc 5c\n" +
                "00000140 65 ff 27 67 77 12 54 52 d0 29 26 c5 aa da 18 ce\n" +
                "00000150 5f b2 8b 5c 54 90 25 c2 20 ff 70 3a c7 8a b3 14\n" +
                "00000160 cd 47 0b 0c 39 12 f7 05 10 f4 6d f1 e3 ee 58 35\n" +
                "00000170 61 52 8b 0b 93 e5 57 a5 db a1 76 1c 1c 26 fb 2e\n" +
                "00000180 42 fa f5 6e 8f 80 e3 54 b8 07 b1 32 57 ff 9a ef\n" +
                "00000190 88 cb 4b a1 cf cf 41 7d a6 d1 3e b4 48 d4 41 9d\n" +
                "000001a0 fb 49 40 44 49 96 cf 2b b3 71 a7 d5 af 11 96 37\n" +
                "000001b0 08 dd 98 ef 54 88 2a a2 ad e7 d4 62 e1 4e d6 b2\n" +
                "000001c0 57 5b e3 71 3d 20 e2 37 f2 64 43 84 55 a0 dd 44\n" +
                "000001d0 11 e2 a3 24 4e 44 a8 0d fe 27 ec 53 13 5d b2 15\n" +
                "000001e0 e7 b8 35 67 9c 94 b9 8e 28 b6 a9 17 ec 0e 01 c4\n" +
                "000001f0 eb 9f 13 8f bb 2a 4b af 8f 89 df 35 8f da fe 33\n" +
                "00000200 98 80 85 20 53 3b 51 9d 5d 28 ac 02 2e fe 05 3b\n" +
                "00000210 94 5f ec d3 dc f8 43 b2 e3 75 62 60 af 6d 8c d8\n" +
                "00000220 ac c8 46 3b 47 3c e1 72 3b 9d ef c4 4a c9 cf fb\n" +
                "00000230 9d 63 ca 94 00 6b dd 18 fe 71 76 bc 45 24 cd 13\n" +
                "00000240 00 7c fb 01 19 ca f2 bf 84 5a 6f 38 35 15 29 83\n" +
                "00000250 1f c3 2f ed b0 ba 08 e0 2c ee 41 de b6 81 df b7\n" +
                "00000260 6c 4b fb 2d 16 f3 89 a3 83 64 de d6 fd c4 5b c4\n" +
                "00000270 e9 19 47 8d ad 11 bc fe b9 52 dd 4d f3 f9 76 f6\n" +
                "00000280 85 ab a3 ab ee 6d 54 75 10 b3 4c 0e 8f f1 0c c5\n" +
                "00000290 60 b7 d1 5a 0c e9 db 51 13 4d 59 20 c9 37 a1 00\n" +
                "000002a0 f8 42 23 37 03 a1 8c 47 64 59 1a d4 ab be 30 fa\n" +
                "000002b0 8d ac 3d 98 a0 ad 9a 5d df dc 72 d7 97 cb f6 2c\n" +
                "000002c0 23 45 a3 76 60 15 c1 8c d9 11 69 94 3f 7d ef 3b\n" +
                "000002d0 b8 be f8 a1 46 87 28 92 a3 9b e3 cb c2 64 8a b0\n" +
                "000002e0 35 d8 ab 3f a1 f5 4b ea 01 c9 63 b4 fc 92 60 1f\n" +
                "000002f0 df 41 ec 2c 38 88 88 e7 59 40 10 20 81 c6 3d bc\n" +
                "00000300 b5 05 2b 73 51 cf c3 7e c0 1d 6c a9 65 81 ad 79\n" +
                "00000310 87 fc 92 83 fc 88 f3 32 27 70 c8 01 b0 dc c9 3a\n" +
                "00000320 5b 7e 0e 98 0a 8a 0b 1e c4 fd a2 9e b3 77 f8 f6\n" +
                "00000330 78 09 1c 5d 88 f5 52 fd 36 02 50 d9 a0 b5 90 6c\n" +
                "00000340 9c 23 22 89 99 ad f7 fe 9a 9e 1b fd a9 d7 0e 39\n" +
                "00000350 5a 28 ed 97 99 d8 77 33 3f b2 67 da 98 47 47 bf\n" +
                "00000360 4f ea 5f 48 ed f6 bb 28 a2 3c d0 65 5e b7 95 2e\n" +
                "00000370 4a af c6 d0 19 6a de 46 04 d3 81 e7 a2 16 22 35\n" +
                "00000380 3b 1c 9c 1d 5c c1 5d 2d 44 ea 00 81 c4 19 a1 ec\n" +
                "00000390 74 f8 10 fc 6e 23 3d e0 2d 04 86 e7 ca 29 98 07\n" +
                "000003a0 69 ca 5b d6 cf 09 69 01 b1 55 38 ad b2 4a 4e 7d\n" +
                "000003b0 85 f9 39 25 42 67 78 47 b3 80 69 b9 14 d6 fc ee\n" +
                "000003c0 03 22 81 b8 06 c4 06 af 38 71 1f e1 e4 91 7d e9\n" +
                "000003d0 5d 4b 6a cd 4e f9 17 9e cf 6a 34 2c 37 a3 6f 2a\n" +
                "000003e0 12 61 3a 9a ad 98 2e 75 52 ad 62 87 88 45 b9 9d\n" +
                "000003f0 20 13 51 c0 e0 b7 a4 24 40 4d 50 b1 8c 4d 66 e8";

        TestUtils.assertEquals(expectedBin, function3.getStr(record));
    }

    @Test
    public void testIntAndShortToDoubleCast() throws ParserException {
        assertCastToDouble(33, ColumnType.INT, ColumnType.SHORT, new Record() {
            @Override
            public int getInt(int col) {
                return 12;
            }

            @Override
            public short getShort(int col) {
                return 21;
            }
        });
    }

    @Test
    public void testIntAndShortToFloatCast() throws ParserException {
        assertCastToFloat(33, ColumnType.INT, ColumnType.SHORT, new Record() {
            @Override
            public int getInt(int col) {
                return 12;
            }

            @Override
            public short getShort(int col) {
                return 21;
            }
        });
    }

    @Test
    public void testIntAndShortToLongCast() throws ParserException {
        assertCastToLong(33, ColumnType.INT, ColumnType.SHORT, new Record() {
            @Override
            public int getInt(int col) {
                return 12;
            }

            @Override
            public short getShort(int col) {
                return 21;
            }
        });
    }

    @Test
    public void testInvalidColumn() {
        final CollectionRecordMetadata metadata = new CollectionRecordMetadata();
        metadata.add(new TestColumnMetadata("a", ColumnType.SHORT));
        metadata.add(new TestColumnMetadata("c", ColumnType.SHORT));
        assertFail(4, "Invalid column: d", "a + d", metadata);
    }

    @Test
    public void testInvalidConstant() {
        final CollectionRecordMetadata metadata = new CollectionRecordMetadata();
        metadata.add(new TestColumnMetadata("a", ColumnType.BOOLEAN));
        metadata.add(new TestColumnMetadata("c", ColumnType.SYMBOL));
        assertFail(4, "invalid constant: 1c", "a + 1c", metadata);
    }

    @Test
    public void testNoArgFunction() throws ParserException {
        functions.add(new SysdateFunctionFactory());
        functions.add(new ToCharDateVCFunctionFactory());
        FunctionParser functionParser = new FunctionParser(
                new DefaultCairoConfiguration(root) {
                    @Override
                    public MillisecondClock getMillisecondClock() {
                        return () -> {
                            try {
                                return DateFormatUtils.parseDateTime("2018-03-04T21:40:00.000Z");
                            } catch (NumericException e) {
                                Assert.fail();
                            }
                            return 0;
                        };
                    }
                },
                functions);

        Function function = parseFunction("to_char(sysdate(), 'EE, dd-MMM-yyyy HH:mm:ss')",
                new CollectionRecordMetadata(),
                functionParser
        );
        TestUtils.assertEquals("Sunday, 04-Mar-2018 21:40:00", function.getStr(null));
    }

    @Test
    public void testNoArgFunctionDoesNotExist() {
        final CollectionRecordMetadata metadata = new CollectionRecordMetadata();
        metadata.add(new TestColumnMetadata("a", ColumnType.BOOLEAN));
        assertFail(5, "unknown function name", "a or xyz()", metadata);
    }

    @Test
    public void testNoArgFunctionWrongSignature() {
        functions.add(new SysdateFunctionFactory());
        final CollectionRecordMetadata metadata = new CollectionRecordMetadata();
        metadata.add(new TestColumnMetadata("a", ColumnType.BOOLEAN));
        assertFail(7, "no signature match", "a or   sysdate(a)", metadata);
    }

    @Test
    public void testSignatureBeginsWithDigit() throws ParserException {
        assertSignatureFailure("1x()");
    }

    @Test
    public void testSignatureEmptyFunctionName() throws ParserException {
        assertSignatureFailure("(B)");
    }

    @Test
    public void testSignatureIllegalArgumentType() throws ParserException {
        assertSignatureFailure("x(Bz)");
    }

    @Test
    public void testSignatureIllegalCharacter() throws ParserException {
        assertSignatureFailure("x'x()");
    }

    @Test
    public void testSignatureMissingCloseBrace() throws ParserException {
        assertSignatureFailure("a(");
    }

    @Test
    public void testSignatureMissingOpenBrace() throws ParserException {
        assertSignatureFailure("x");
    }

    @Test
    public void testSimpleFunction() throws ParserException {
        assertCastToDouble(13.1, ColumnType.DOUBLE, ColumnType.DOUBLE, new Record() {
            @Override
            public double getDouble(int col) {
                if (col == 0) {
                    return 5.3;
                }
                return 7.8;
            }
        });
    }

    @Test
    public void testSymbolFunction() throws ParserException {
        functions.add(new LengthStrVFunctionFactory());
        functions.add(new LengthSymbolVFunctionFactory());
        functions.add(new SubtractIntVVFunctionFactory());

        FunctionParser functionParser = createFunctionParser();
        final CollectionRecordMetadata metadata = new CollectionRecordMetadata();
        metadata.add(new TestColumnMetadata("a", ColumnType.STRING));
        metadata.add(new TestColumnMetadata("b", ColumnType.SYMBOL));

        Function function = parseFunction("length(b) - length(a)",
                metadata,
                functionParser
        );

        Record record = new Record() {
            @Override
            public CharSequence getFlyweightStr(int col) {
                return "ABC";
            }

            @Override
            public int getStrLen(int col) {
                return getFlyweightStr(col).length();
            }

            @Override
            public CharSequence getSym(int col) {
                return "EFGHT";
            }
        };

        Assert.assertEquals(2, function.getInt(record));

        Function function1 = parseFunction("length(null)", metadata, functionParser);
        Assert.assertEquals(-1, function1.getInt(record));

        Function function2 = parseFunction("length(NULL)", metadata, functionParser);
        Assert.assertEquals(-1, function2.getInt(record));
    }

    @Test
    public void testTooFewArguments() {
        final CollectionRecordMetadata metadata = new CollectionRecordMetadata();
        metadata.add(new TestColumnMetadata("a", ColumnType.SHORT));
        assertFail(2, "too few arguments [found=1,expected=2]", "a + ", metadata);
    }

    private void assertCastToDouble(double expected, int type1, int type2, Record record) throws ParserException {
        functions.add(new AddDoubleVVFunctionFactory());
        final CollectionRecordMetadata metadata = new CollectionRecordMetadata();
        metadata.add(new TestColumnMetadata("a", type1));
        metadata.add(new TestColumnMetadata("b", type2));
        FunctionParser functionParser = createFunctionParser();
        Function function = parseFunction("a+b", metadata, functionParser);
        Assert.assertEquals(ColumnType.DOUBLE, function.getType());
        Assert.assertEquals(expected, function.getDouble(record), 0.00001);
    }

    private void assertCastToFloat(float expected, int type1, int type2, Record record) throws ParserException {
        functions.add(new AddFloatVVFunctionFactory());
        final CollectionRecordMetadata metadata = new CollectionRecordMetadata();
        metadata.add(new TestColumnMetadata("a", type1));
        metadata.add(new TestColumnMetadata("b", type2));
        FunctionParser functionParser = createFunctionParser();
        Function function = parseFunction("a+b", metadata, functionParser);
        Assert.assertEquals(ColumnType.FLOAT, function.getType());
        Assert.assertEquals(expected, function.getFloat(record), 0.00001);
    }

    private void assertCastToLong(long expected, int type1, int type2, Record record) throws ParserException {
        functions.add(new AddLongVVFunctionFactory());
        final CollectionRecordMetadata metadata = new CollectionRecordMetadata();
        metadata.add(new TestColumnMetadata("a", type1));
        metadata.add(new TestColumnMetadata("b", type2));
        FunctionParser functionParser = createFunctionParser();
        Function function = parseFunction("a+b", metadata, functionParser);
        Assert.assertEquals(ColumnType.LONG, function.getType());
        Assert.assertEquals(expected, function.getLong(record));
    }

    private void assertFail(int expectedPos, String expectedMessage, String expression, CollectionRecordMetadata metadata) {
        FunctionParser functionParser = createFunctionParser();
        try {
            parseFunction(expression, metadata, functionParser);
            Assert.fail();
        } catch (ParserException e) {
            Assert.assertEquals(expectedPos, e.getPosition());
            TestUtils.assertContains(e.getMessage(), expectedMessage);
        }
    }

    private void assertSignatureFailure(String signature) throws ParserException {
        functions.add(new OrVVFunctionFactory());
        functions.add(new FunctionFactory() {
            @Override
            public String getSignature() {
                return signature;
            }

            @Override
            public Function newInstance(ObjList<Function> args, int position, CairoConfiguration configuration) {
                return null;
            }
        });
        functions.add(new NotVFunctionFactory());
        final CollectionRecordMetadata metadata = new CollectionRecordMetadata();
        metadata.add(new TestColumnMetadata("a", ColumnType.BOOLEAN));
        metadata.add(new TestColumnMetadata("b", ColumnType.BOOLEAN));
        FunctionParser functionParser = createFunctionParser();
        Assert.assertNotNull(parseFunction("a or not b", metadata, functionParser));
        Assert.assertEquals(2, functionParser.getFunctionCount());
    }

    @NotNull
    private FunctionParser createFunctionParser() {
        return new FunctionParser(configuration, functions);
    }

    private ExprNode expr(CharSequence expression) throws ParserException {
        lexer.setContent(expression);
        astBuilder.reset();
        parser.parseExpr(lexer, astBuilder);
        return astBuilder.poll();
    }

    private Function parseFunction(CharSequence expression, CollectionRecordMetadata metadata, FunctionParser functionParser) throws ParserException {
        return functionParser.parseFunction(expr(expression), metadata, params);
    }

    private class TestColumnMetadata implements RecordColumnMetadata {
        private final String name;
        private final int type;

        public TestColumnMetadata(String name, int type) {
            this.name = name;
            this.type = type;
        }

        @Override
        public int getBucketCount() {
            return 0;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public SymbolTable getSymbolTable() {
            return null;
        }

        @Override
        public int getType() {
            return type;
        }

        @Override
        public boolean isIndexed() {
            return false;
        }
    }
}