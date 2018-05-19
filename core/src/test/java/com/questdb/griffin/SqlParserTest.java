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

import com.questdb.cairo.*;
import com.questdb.cairo.sql.CairoEngine;
import com.questdb.common.ColumnType;
import com.questdb.common.PartitionBy;
import com.questdb.griffin.engine.functions.bind.BindVariableService;
import com.questdb.griffin.model.CreateTableModel;
import com.questdb.griffin.model.ExecutionModel;
import com.questdb.griffin.model.QueryModel;
import com.questdb.std.Chars;
import com.questdb.std.Files;
import com.questdb.std.FilesFacade;
import com.questdb.std.FilesFacadeImpl;
import com.questdb.std.str.LPSZ;
import com.questdb.std.str.Path;
import com.questdb.test.tools.TestUtils;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

public class SqlParserTest extends AbstractCairoTest {
    private final static CairoEngine engine = new Engine(configuration);
    private final static SqlCompiler sqlCompiler = new SqlCompiler(engine, configuration);
    private final static BindVariableService bindVariableService = new BindVariableService();

    @AfterClass
    public static void tearDown() throws IOException {
        engine.close();
    }

    @Test
    public void testAliasWithSpace() throws Exception {
        assertQuery("select-choose x from (x 'b a' where x > 1)",
                "x 'b a' where x > 1",
                modelOf("x").col("x", ColumnType.INT));
    }

    @Test
    public void testAliasWithSpaceX() {
        assertSyntaxError("from x 'a b' where x > 1", 7, "unexpected");
    }

    @Test
    public void testAmbiguousColumn() {
        assertSyntaxError("orders join customers on customerId = customerId", 25, "Ambiguous",
                modelOf("orders").col("customerId", ColumnType.INT),
                modelOf("customers").col("customerId", ColumnType.INT)
        );
    }

    @Test
    public void testAnalyticOrderDirection() throws Exception {
        assertQuery(
                "select-analytic a, b, f(c) my over (partition by b order by ts desc, x, y) from (xyz)",
                "select a,b, f(c) my over (partition by b order by ts desc, x asc, y) from xyz",
                modelOf("xyz")
                        .col("a", ColumnType.INT)
                        .col("b", ColumnType.INT)
                        .col("c", ColumnType.INT)
                        .col("x", ColumnType.INT)
                        .col("y", ColumnType.INT)
                        .col("z", ColumnType.INT)
        );
    }

    @Test
    public void testAnalyticPartitionByMultiple() throws Exception {
        assertQuery(
                "select-analytic a, b, f(c) my over (partition by b, a order by ts), d(c) d over () from (xyz)",
                "select a,b, f(c) my over (partition by b, a order by ts), d(c) over() from xyz",
                modelOf("xyz").col("c", ColumnType.INT).col("b", ColumnType.INT).col("a", ColumnType.INT)
        );
    }

    @Test
    public void testAsOfJoin() throws SqlException {
        assertQuery(
                "select-choose" +
                        " t.timestamp timestamp," +
                        " t.tag tag," +
                        " q.timestamp timestamp1" +
                        " from (" +
                        "trades t timestamp (timestamp) asof join quotes q timestamp (timestamp) post-join-where tag = null)",
                "trades t asof join quotes q where tag = null",
                modelOf("trades").timestamp().col("tag", ColumnType.SYMBOL),
                modelOf("quotes").timestamp()
        );
    }

    @Test
    public void testAsOfJoinOrder() throws Exception {
        assertQuery(
                "select-choose" +
                        " c.customerId customerId," +
                        " e.employeeId employeeId," +
                        " o.customerId customerId1" +
                        " from (" +
                        "customers c" +
                        " asof join employees e on e.employeeId = c.customerId" +
                        " join orders o on o.customerId = c.customerId" +
                        ")",
                "customers c" +
                        " asof join employees e on c.customerId = e.employeeId" +
                        " join orders o on c.customerId = o.customerId",
                modelOf("customers").col("customerId", ColumnType.SYMBOL),
                modelOf("employees").col("employeeId", ColumnType.STRING),
                modelOf("orders").col("customerId", ColumnType.SYMBOL));
    }

    @Test
    public void testAsOfJoinSubQuery() throws Exception {
        // execution order must be (src: SQL Server)
        //        1. FROM
        //        2. ON
        //        3. JOIN
        //        4. WHERE
        //        5. GROUP BY
        //        6. WITH CUBE or WITH ROLLUP
        //        7. HAVING
        //        8. SELECT
        //        9. DISTINCT
        //        10. ORDER BY
        //        11. TOP
        //
        // which means "where" clause for "e" table has to be explicitly as post-join-where
        assertQuery(
                "select-choose" +
                        " c.customerId customerId," +
                        " e.lastName lastName," +
                        " e.employeeId employeeId," +
                        " e.blah blah," +
                        " e.timestamp timestamp," +
                        " o.customerId customerId1" +
                        " from (" +
                        "customers c" +
                        " asof join" +
                        " (select-virtual" +
                        " '1' blah," +
                        " lastName," +
                        " employeeId," +
                        " timestamp" +
                        " from (employees)" +
                        " order by lastName) e on e.employeeId = c.customerId post-join-where e.lastName = 'x' and e.blah = 'y'" +
                        " join orders o on o.customerId = c.customerId" +
                        ")",
                "customers c" +
                        " asof join (select '1' blah, lastName, employeeId, timestamp from employees order by lastName) e on c.customerId = e.employeeId" +
                        " join orders o on c.customerId = o.customerId where e.lastName = 'x' and e.blah = 'y'",
                modelOf("customers")
                        .col("customerId", ColumnType.SYMBOL),
                modelOf("employees")
                        .col("employeeId", ColumnType.STRING)
                        .col("lastName", ColumnType.STRING)
                        .col("timestamp", ColumnType.TIMESTAMP),
                modelOf("orders")
                        .col("customerId", ColumnType.SYMBOL)
        );
    }

    @Test
    public void testAsOfJoinSubQuerySimpleAlias() throws Exception {
        assertQuery(
                "select-choose" +
                        " c.customerId customerId," +
                        " a.lastName lastName," +
                        " a.blah blah," +
                        " a.timestamp timestamp," +
                        " a.customerId customerId1" +
                        " from " +
                        "(" +
                        "customers c" +
                        " asof join (" +
                        "select-virtual" +
                        " '1' blah," +
                        " lastName," +
                        " customerId," +
                        " timestamp" +
                        " from (" +
                        "select-choose" +
                        " lastName," +
                        " employeeId customerId," +
                        " timestamp from (employees)) order by lastName) a on a.customerId = c.customerId)",
                "customers c" +
                        " asof join (select '1' blah, lastName, employeeId customerId, timestamp from employees order by lastName) a on (customerId)",
                modelOf("customers")
                        .col("customerId", ColumnType.SYMBOL),
                modelOf("employees")
                        .col("employeeId", ColumnType.STRING)
                        .col("lastName", ColumnType.STRING)
                        .col("timestamp", ColumnType.TIMESTAMP)
        );
    }

    @Test
    public void testAsOfJoinSubQuerySimpleNoAlias() throws Exception {
        assertQuery(
                "select-choose" +
                        " c.customerId customerId," +
                        " _xQdbA1.lastName lastName," +
                        " _xQdbA1.blah blah," +
                        " _xQdbA1.timestamp timestamp," +
                        " _xQdbA1.customerId customerId1" +
                        " from (" +
                        "customers c" +
                        " asof join (select-virtual '1' blah, lastName, customerId, timestamp" +
                        " from (select-choose lastName, employeeId customerId, timestamp" +
                        " from (employees)) order by lastName) _xQdbA1 on _xQdbA1.customerId = c.customerId)",
                "customers c" +
                        " asof join (select '1' blah, lastName, employeeId customerId, timestamp from employees order by lastName) on (customerId)",
                modelOf("customers").col("customerId", ColumnType.SYMBOL),
                modelOf("employees")
                        .col("employeeId", ColumnType.STRING)
                        .col("lastName", ColumnType.STRING)
                        .col("timestamp", ColumnType.TIMESTAMP)
        );
    }

    @Test
    public void testBlockCommentAtMiddle() throws Exception {
        assertQuery(
                "select-choose" +
                        " a, x" +
                        " from (" +
                        "(x where a > 1 and x > 1) 'b a')",
                "(x where /*this is a random comment */a > 1) 'b a' where x > 1",
                modelOf("x")
                        .col("x", ColumnType.INT)
                        .col("a", ColumnType.INT));
    }

    @Test
    public void testBlockCommentNested() throws Exception {
        assertQuery(
                "select-choose" +
                        " a, x" +
                        " from (" +
                        "(x where a > 1 and x > 1) 'b a')",
                "(x where a > 1) /* comment /* ok */  whatever */'b a' where x > 1",
                modelOf("x")
                        .col("x", ColumnType.INT)
                        .col("a", ColumnType.INT));
    }

    @Test
    public void testBlockCommentUnclosed() throws Exception {
        assertQuery(
                "select-choose" +
                        " a, x" +
                        " from (" +
                        "(x where a > 1 and x > 1) 'b a')",
                "(x where a > 1) 'b a' where x > 1 /* this block comment",
                modelOf("x")
                        .col("x", ColumnType.INT)
                        .col("a", ColumnType.INT));
    }

    @Test
    public void testCaseImpossibleRewrite1() throws SqlException {
        // referenced columns in 'when' clauses are different
        assertQuery(
                "select-virtual case('C','B',2 = b,'A',a = 1) + 1 column, b from (tab)",
                "select case when a = 1 then 'A' when 2 = b then 'B' else 'C' end+1, b from tab",
                modelOf("tab").col("a", ColumnType.INT).col("b", ColumnType.INT)
        );
    }

    @Test
    public void testCaseImpossibleRewrite2() throws SqlException {
        // 'when' is non-constant
        assertQuery(
                "select-virtual case('C','B',2 + b = a,'A',a = 1) + 1 column, b from (tab)",
                "select case when a = 1 then 'A' when 2 + b = a then 'B' else 'C' end+1, b from tab",
                modelOf("tab").col("a", ColumnType.INT).col("b", ColumnType.INT)
        );
    }

    @Test
    public void testCaseNoElseClause() throws SqlException {
        // referenced columns in 'when' clauses are different
        assertQuery(
                "select-virtual case('B',2 = b,'A',a = 1) + 1 column, b from (tab)",
                "select case when a = 1 then 'A' when 2 = b then 'B' end+1, b from tab",
                modelOf("tab").col("a", ColumnType.INT).col("b", ColumnType.INT)
        );
    }

    @Test
    public void testCaseToSwitchExpression() throws SqlException {
        assertQuery(
                "select-virtual switch(a,'C',1,'A',2,'B') + 1 column, b from (tab)",
                "select case when a = 1 then 'A' when a = 2 then 'B' else 'C' end+1, b from tab",
                modelOf("tab").col("a", ColumnType.INT).col("b", ColumnType.INT)
        );
    }

    @Test
    public void testCaseToSwitchExpression2() throws SqlException {
        // this test has inverted '=' arguments but should still be rewritten to 'switch'
        assertQuery(
                "select-virtual switch(a,'C',1,'A',2,'B') + 1 column, b from (tab)",
                "select case when a = 1 then 'A' when 2 = a then 'B' else 'C' end+1, b from tab",
                modelOf("tab").col("a", ColumnType.INT).col("b", ColumnType.INT)
        );
    }

    @Test
    public void testConstantFunctionAsArg() throws Exception {
        assertQuery(
                "select-choose customerId from (customers where f(1.2) > 1)",
                "select * from customers where f(1.2) > 1",
                modelOf("customers").col("customerId", ColumnType.INT)
        );
    }

    @Test
    public void testCount() throws Exception {
        assertQuery(
                "select-group-by" +
                        " customerId," +
                        " count() count " +
                        "from" +
                        " (select-choose c.customerId customerId from (customers c outer join orders o on o.customerId = c.customerId post-join-where o.customerId = NaN))",
                "select c.customerId, count() from customers c" +
                        " outer join orders o on c.customerId = o.customerId " +
                        " where o.customerId = NaN",
                modelOf("customers").col("customerId", ColumnType.INT).col("customerName", ColumnType.STRING),
                modelOf("orders").col("customerId", ColumnType.INT).col("product", ColumnType.STRING)
        );
    }

    @Test
    public void testCreateAsSelectInvalidIndex() {
        assertSyntaxError(
                "create table X as ( select a, b, c from tab ), index(x)",
                53,
                "Invalid column",
                modelOf("tab")
                        .col("a", ColumnType.INT)
                        .col("b", ColumnType.DOUBLE)
                        .col("c", ColumnType.STRING)
        );
    }

    @Test
    public void testCreateNameWithDot() {
        assertSyntaxError(
                "create table X.y as ( select a, b, c from tab )",
                13,
                "'.' is not allowed here",
                modelOf("tab")
                        .col("a", ColumnType.INT)
                        .col("b", ColumnType.DOUBLE)
                        .col("c", ColumnType.STRING)
        );
    }

    @Test
    public void testCreateTable() throws SqlException {
        assertCreateTable(
                "create table x (" +
                        "a INT," +
                        " b BYTE," +
                        " c SHORT," +
                        " d LONG," +
                        " e FLOAT," +
                        " f DOUBLE," +
                        " g DATE," +
                        " h BINARY," +
                        " t TIMESTAMP," +
                        " x SYMBOL capacity 128 cache," +
                        " z STRING," +
                        " y BOOLEAN) timestamp(t) partition by MONTH",
                "create table x (" +
                        "a INT, " +
                        "b BYTE, " +
                        "c SHORT, " +
                        "d LONG, " +
                        "e FLOAT, " +
                        "f DOUBLE, " +
                        "g DATE, " +
                        "h BINARY, " +
                        "t TIMESTAMP, " +
                        "x SYMBOL, " +
                        "z STRING, " +
                        "y BOOLEAN) " +
                        "timestamp(t) " +
                        "partition by MONTH");
    }

    @Test
    public void testCreateTableAsSelect() throws SqlException {
        assertCreateTable(
                "create table X as (select-choose a, b, c from (tab))",
                "create table X as ( select a, b, c from tab )",
                modelOf("tab")
                        .col("a", ColumnType.INT)
                        .col("b", ColumnType.DOUBLE)
                        .col("c", ColumnType.STRING)
        );
    }

    @Test
    public void testCreateTableAsSelectIndex() throws SqlException {
        assertCreateTable(
                "create table X as (select-choose a, b, c from (tab)), index(b capacity 256)",
                "create table X as ( select a, b, c from tab ), index(b)",
                modelOf("tab")
                        .col("a", ColumnType.INT)
                        .col("b", ColumnType.DOUBLE)
                        .col("c", ColumnType.STRING)

        );
    }

    @Test
    public void testCreateTableAsSelectIndexCapacity() throws SqlException {
        assertCreateTable(
                "create table X as (select-choose a, b, c from (tab)), index(b capacity 64)",
                "create table X as ( select a, b, c from tab ), index(b capacity 64)",
                modelOf("tab")
                        .col("a", ColumnType.INT)
                        .col("b", ColumnType.DOUBLE)
                        .col("c", ColumnType.STRING)

        );
    }

    @Test
    public void testCreateTableAsSelectTimestamp() throws SqlException {
        assertCreateTable(
                "create table X as (select-choose a, b, c from (tab)) timestamp(b)",
                "create table X as ( select a, b, c from tab ) timestamp(b)",
                modelOf("tab")
                        .col("a", ColumnType.INT)
                        .col("b", ColumnType.DOUBLE)
                        .col("c", ColumnType.STRING)

        );
    }

    @Test
    public void testCreateTableBadColumnDef() {
        assertSyntaxError(
                "create table x (" +
                        "a INT, " +
                        "b BYTE, " +
                        "g DATE, " +
                        "h BINARY, " +
                        "t TIMESTAMP blah, " +
                        "x SYMBOL index, " +
                        "z STRING, " +
                        "bool BOOLEAN) " +
                        "timestamp(t) " +
                        "partition by YEAR index",
                61,
                "',' or ')' expected"
        );
    }

    @Test
    public void testCreateTableCacheCapacity() throws SqlException {
        assertCreateTable("create table x (" +
                        "a INT," +
                        " b BYTE," +
                        " c SHORT," +
                        " d LONG," +
                        " e FLOAT," +
                        " f DOUBLE," +
                        " g DATE," +
                        " h BINARY," +
                        " t TIMESTAMP," +
                        " x SYMBOL capacity 64 cache," +
                        " z STRING," +
                        " y BOOLEAN)" +
                        " timestamp(t)" +
                        " partition by YEAR",
                "create table x (" +
                        "a INT, " +
                        "b BYTE, " +
                        "c SHORT, " +
                        "d LONG, " +
                        "e FLOAT, " +
                        "f DOUBLE, " +
                        "g DATE, " +
                        "h BINARY, " +
                        "t TIMESTAMP, " +
                        "x SYMBOL capacity 64 cache, " +
                        "z STRING, " +
                        "y BOOLEAN) " +
                        "timestamp(t) " +
                        "partition by YEAR");
    }

    @Test
    public void testCreateTableCastCapacityDef() throws SqlException {
        assertCreateTable(
                "create table x as (select-choose a, b, c from (tab)), cast(a as DOUBLE:35), cast(c as SYMBOL:54 capacity 16)",
                "create table x as (tab), cast(a as double), cast(c as symbol capacity 16)",
                modelOf("tab")
                        .col("a", ColumnType.INT)
                        .col("b", ColumnType.LONG)
                        .col("c", ColumnType.STRING)

        );
    }

    @Test
    public void testCreateTableCastDef() throws SqlException {
        assertCreateTable(
                "create table x as (select-choose a, b, c from (tab)), cast(a as DOUBLE:35), cast(c as SYMBOL:54 capacity 128)",
                "create table x as (tab), cast(a as double), cast(c as symbol)",
                modelOf("tab")
                        .col("a", ColumnType.INT)
                        .col("b", ColumnType.LONG)
                        .col("c", ColumnType.STRING)

        );
    }

    @Test
    public void testCreateTableCastUnsupportedType() {
        assertSyntaxError(
                "create table x as (tab), cast(b as integer)",
                35,
                "unsupported column type",
                modelOf("tab")
                        .col("a", ColumnType.INT)
                        .col("b", ColumnType.LONG)
                        .col("c", ColumnType.STRING)
        );
    }

    @Test
    public void testCreateTableDuplicateCast() {
        assertSyntaxError(
                "create table x as (tab), cast(b as double), cast(b as long)",
                49,
                "duplicate cast",
                modelOf("tab")
                        .col("a", ColumnType.INT)
                        .col("b", ColumnType.LONG)
                        .col("c", ColumnType.STRING)
        );
    }

    @Test
    public void testCreateTableDuplicateColumn() {
        assertSyntaxError(
                "create table x (" +
                        "a INT, " +
                        "b BYTE, " +
                        "c SHORT, " +
                        "d LONG, " +
                        "e FLOAT, " +
                        "f DOUBLE, " +
                        "g DATE, " +
                        "h BINARY, " +
                        "t TIMESTAMP, " +
                        "x SYMBOL index, " +
                        "z STRING, " +
                        "t BOOLEAN) " +
                        "timestamp(t) " +
                        "partition by YEAR",
                122,
                "Duplicate column"
        );
    }

    @Test
    public void testCreateTableInPlaceIndex() throws SqlException {
        assertCreateTable("create table x (" +
                        "a INT," +
                        " b BYTE," +
                        " c SHORT," +
                        " d LONG," +
                        " e FLOAT," +
                        " f DOUBLE," +
                        " g DATE," +
                        " h BINARY," +
                        " t TIMESTAMP," +
                        " x SYMBOL capacity 128 cache index capacity 256," +
                        " z STRING," +
                        " y BOOLEAN)" +
                        " timestamp(t)" +
                        " partition by YEAR",
                "create table x (" +
                        "a INT, " +
                        "b BYTE, " +
                        "c SHORT, " +
                        "d LONG, " +
                        "e FLOAT, " +
                        "f DOUBLE, " +
                        "g DATE, " +
                        "h BINARY, " +
                        "t TIMESTAMP, " +
                        "x SYMBOL index, " + // <-- index here
                        "z STRING, " +
                        "y BOOLEAN) " +
                        "timestamp(t) " +
                        "partition by YEAR");
    }

    @Test
    public void testCreateTableInPlaceIndexAndBlockSize() throws SqlException {
        assertCreateTable(
                "create table x (" +
                        "a INT," +
                        " b BYTE," +
                        " c SHORT," +
                        " t TIMESTAMP," +
                        " d LONG," +
                        " e FLOAT," +
                        " f DOUBLE," +
                        " g DATE," +
                        " h BINARY," +
                        " x SYMBOL capacity 128 cache index capacity 128," +
                        " z STRING," +
                        " y BOOLEAN) timestamp(t) partition by MONTH",
                "create table x (" +
                        "a INT, " +
                        "b BYTE, " +
                        "c SHORT, " +
                        "t TIMESTAMP, " +
                        "d LONG, " +
                        "e FLOAT, " +
                        "f DOUBLE, " +
                        "g DATE, " +
                        "h BINARY, " +
                        "x SYMBOL index capacity 128, " +
                        "z STRING, " +
                        "y BOOLEAN) " +
                        "timestamp(t) " +
                        "partition by MONTH");
    }

    @Test
    public void testCreateTableInvalidCapacity() {
        assertSyntaxError(
                "create table x (a symbol capacity z)",
                34,
                "bad integer"
        );
    }

    @Test
    public void testCreateTableInvalidColumnInIndex() {
        assertSyntaxError(
                "create table x (" +
                        "a INT, " +
                        "b BYTE, " +
                        "g DATE, " +
                        "h BINARY, " +
                        "t TIMESTAMP, " +
                        "x SYMBOL index, " +
                        "z STRING, " +
                        "bool BOOLEAN), " +
                        "index(k) " +
                        "timestamp(t) " +
                        "partition by YEAR",
                109,
                "Invalid column"
        );
    }

    @Test
    public void testCreateTableInvalidColumnType() {
        assertSyntaxError(
                "create table tab (a int, b integer)",
                27,
                "unsupported column type"
        );
    }

    @Test
    public void testCreateTableInvalidPartitionBy() {
        assertSyntaxError(
                "create table x (" +
                        "a INT, " +
                        "b BYTE, " +
                        "g DATE, " +
                        "h BINARY, " +
                        "t TIMESTAMP, " +
                        "x SYMBOL index, " +
                        "z STRING, " +
                        "bool BOOLEAN) " +
                        "timestamp(t) " +
                        "partition by EPOCH",
                128,
                "'NONE', 'DAY', 'MONTH' or 'YEAR' expected"
        );
    }

    @Test
    public void testCreateTableInvalidTimestampColumn() {
        assertSyntaxError(
                "create table x (" +
                        "a INT, " +
                        "b BYTE, " +
                        "g DATE, " +
                        "h BINARY, " +
                        "t TIMESTAMP, " +
                        "x SYMBOL index, " +
                        "z STRING, " +
                        "bool BOOLEAN) " +
                        "timestamp(zyz) " +
                        "partition by YEAR",
                112,
                "Invalid column"
        );
    }

    @Test
    public void testCreateTableMisplacedCastCapacity() {
        assertSyntaxError(
                "create table x as (tab), cast(a as double capacity 16)",
                42,
                "')' expected",
                modelOf("tab")
                        .col("a", ColumnType.INT)
                        .col("b", ColumnType.LONG)
                        .col("c", ColumnType.STRING)
        );
    }

    @Test
    public void testCreateTableMisplacedCastDef() {
        assertSyntaxError(
                "create table tab (a int, b long), cast (a as double)",
                34,
                "cast is only supported"
        );
    }

    @Test
    public void testCreateTableMissingColumnDef() {
        assertSyntaxError(
                "create table x (" +
                        "a INT, " +
                        "b BYTE, " +
                        "g DATE, " +
                        "h BINARY, " +
                        "t TIMESTAMP, " +
                        "x SYMBOL index, " +
                        "z STRING, " +
                        "bool BOOLEAN, ) " +
                        "timestamp(t) " +
                        "partition by YEAR index",
                102,
                "missing column definition"
        );
    }

    @Test
    public void testCreateTableMissingDef() {
        assertSyntaxError("create table xyx", 13, "'(' or 'as' expected");
    }

    @Test
    public void testCreateTableMissingName() {
        assertSyntaxError("create table ", 12, "table name expected");
    }

    @Test
    public void testCreateTableNoCache() throws SqlException {
        assertCreateTable("create table x (" +
                        "a INT," +
                        " b BYTE," +
                        " c SHORT," +
                        " d LONG," +
                        " e FLOAT," +
                        " f DOUBLE," +
                        " g DATE," +
                        " h BINARY," +
                        " t TIMESTAMP," +
                        " x SYMBOL capacity 128 nocache," +
                        " z STRING," +
                        " y BOOLEAN)" +
                        " timestamp(t)" +
                        " partition by YEAR",
                "create table x (" +
                        "a INT, " +
                        "b BYTE, " +
                        "c SHORT, " +
                        "d LONG, " +
                        "e FLOAT, " +
                        "f DOUBLE, " +
                        "g DATE, " +
                        "h BINARY, " +
                        "t TIMESTAMP, " +
                        "x SYMBOL nocache, " +
                        "z STRING, " +
                        "y BOOLEAN) " +
                        "timestamp(t) " +
                        "partition by YEAR");
    }

    @Test
    public void testCreateTableNoCacheIndex() throws SqlException {
        assertCreateTable("create table x (" +
                        "a INT," +
                        " b BYTE," +
                        " c SHORT," +
                        " d LONG," +
                        " e FLOAT," +
                        " f DOUBLE," +
                        " g DATE," +
                        " h BINARY," +
                        " t TIMESTAMP," +
                        " x SYMBOL capacity 128 nocache index capacity 256," +
                        " z STRING," +
                        " y BOOLEAN)" +
                        " timestamp(t)" +
                        " partition by YEAR",
                "create table x (" +
                        "a INT, " +
                        "b BYTE, " +
                        "c SHORT, " +
                        "d LONG, " +
                        "e FLOAT, " +
                        "f DOUBLE, " +
                        "g DATE, " +
                        "h BINARY, " +
                        "t TIMESTAMP, " +
                        "x SYMBOL nocache index, " +
                        "z STRING, " +
                        "y BOOLEAN) " +
                        "timestamp(t) " +
                        "partition by YEAR");
    }

    @Test
    public void testCreateTableOutOfPlaceIndex() throws SqlException {
        assertCreateTable(
                "create table x (" +
                        "a INT index capacity 256," +
                        " b BYTE," +
                        " c SHORT," +
                        " t TIMESTAMP," +
                        " d LONG," +
                        " e FLOAT," +
                        " f DOUBLE," +
                        " g DATE," +
                        " h BINARY," +
                        " x SYMBOL capacity 128 cache index capacity 256," +
                        " z STRING," +
                        " y BOOLEAN)" +
                        " timestamp(t)" +
                        " partition by MONTH",
                "create table x (" +
                        "a INT, " +
                        "b BYTE, " +
                        "c SHORT, " +
                        "t TIMESTAMP, " +
                        "d LONG, " +
                        "e FLOAT, " +
                        "f DOUBLE, " +
                        "g DATE, " +
                        "h BINARY, " +
                        "x SYMBOL, " +
                        "z STRING, " +
                        "y BOOLEAN) " +
                        ", index (a) " +
                        ", index (x) " +
                        "timestamp(t) " +
                        "partition by MONTH");
    }

    @Test
    public void testCreateTableOutOfPlaceIndexAndCapacity() throws SqlException {
        assertCreateTable(
                "create table x (" +
                        "a INT index capacity 16," +
                        " b BYTE," +
                        " c SHORT," +
                        " t TIMESTAMP," +
                        " d LONG," +
                        " e FLOAT," +
                        " f DOUBLE," +
                        " g DATE," +
                        " h BINARY," +
                        " x SYMBOL capacity 128 cache index capacity 32," +
                        " z STRING," +
                        " y BOOLEAN)" +
                        " timestamp(t)" +
                        " partition by MONTH",
                "create table x (" +
                        "a INT, " +
                        "b BYTE, " +
                        "c SHORT, " +
                        "t TIMESTAMP, " +
                        "d LONG, " +
                        "e FLOAT, " +
                        "f DOUBLE, " +
                        "g DATE, " +
                        "h BINARY, " +
                        "x SYMBOL, " +
                        "z STRING, " +
                        "y BOOLEAN) " +
                        ", index (a capacity 16) " +
                        ", index (x capacity 24) " +
                        "timestamp(t) " +
                        "partition by MONTH");
    }

    @Test
    public void testCreateTableUnexpectedToken() {
        assertSyntaxError(
                "create table x blah",
                15,
                "unexpected token"
        );
    }

    @Test
    public void testCreateTableUnexpectedToken2() {
        assertSyntaxError(
                "create table x (a int, b double), xyz",
                34,
                "unexpected token"
        );
    }

    @Test
    public void testCreateTableUnexpectedTrailingToken() {
        assertSyntaxError(
                "create table x (" +
                        "a INT, " +
                        "b BYTE, " +
                        "g DATE, " +
                        "h BINARY, " +
                        "t TIMESTAMP, " +
                        "x SYMBOL index, " +
                        "z STRING, " +
                        "bool BOOLEAN) " +
                        "timestamp(t) " +
                        "partition by YEAR index",
                133,
                "unexpected token"
        );
    }

    @Test
    public void testCreateTableUnexpectedTrailingToken2() {
        assertSyntaxError(
                "create table x (" +
                        "a INT, " +
                        "b BYTE, " +
                        "g DATE, " +
                        "h BINARY, " +
                        "t TIMESTAMP, " +
                        "x SYMBOL index, " +
                        "z STRING, " +
                        "bool BOOLEAN) " +
                        "timestamp(t) " +
                        " index",
                116,
                "unexpected token"
        );
    }

    @Test
    public void testCreateUnsupported() {
        assertSyntaxError("create object x", 7, "table");
    }

    @Test
    public void testCrossJoin() {
        assertSyntaxError("select x from a a cross join b on b.x = a.x", 31, "cannot");
    }

    @Test
    public void testCrossJoin2() throws Exception {
        assertQuery(
                "select-choose a.x x from (a a cross join b z)",
                "select a.x from a a cross join b z",
                modelOf("a").col("x", ColumnType.INT),
                modelOf("b").col("x", ColumnType.INT));
    }

    @Test
    public void testCrossJoin3() throws Exception {
        assertQuery(
                "select-choose a.x x from (a a join c on c.x = a.x cross join b z)",
                "select a.x from a a " +
                        "cross join b z " +
                        "join c on a.x = c.x",
                modelOf("a").col("x", ColumnType.INT),
                modelOf("b").col("x", ColumnType.INT),
                modelOf("c").col("x", ColumnType.INT)
        );
    }

    @Test
    public void testCrossJoinNoAlias() throws Exception {
        assertQuery("select-choose a.x x from (a a join c on c.x = a.x cross join b)",
                "select a.x from a a " +
                        "cross join b " +
                        "join c on a.x = c.x",
                modelOf("a").col("x", ColumnType.INT),
                modelOf("b").col("x", ColumnType.INT),
                modelOf("c").col("x", ColumnType.INT));
    }

    @Test
    public void testCrossJoinWithClause() throws SqlException {
        assertQuery(
                "select-choose" +
                        " c.name name," +
                        " c.customerId customerId," +
                        " c.age age," +
                        " c1.name name1," +
                        " c1.customerId customerId1," +
                        " c1.age age1" +
                        " from (" +
                        "(customers where name ~ 'X') c" +
                        " cross join (customers where name ~ 'X' and age = 30) c1" +
                        " limit 10" +
                        ")",
                "with" +
                        " cust as (customers where name ~ 'X')" +
                        " cust c cross join cust c1 where c1.age = 30 " +
                        " limit 10",
                modelOf("customers")
                        .col("customerId", ColumnType.INT)
                        .col("name", ColumnType.STRING)
                        .col("age", ColumnType.BYTE)
        );
    }

    @Test
    public void testDisallowDotInColumnAlias() {
        assertSyntaxError("select x x.y, y from tab order by x", 9, "not allowed");
    }

    @Test
    public void testDisallowedColumnAliases() throws SqlException {
        assertQuery(
                "select-virtual x + z column, x - z column1, x * z column2, x / z column3, x % z column4, x ^ z column5 from (tab1)",
                "select x+z, x-z, x*z, x/z, x%z, x^z from tab1",
                modelOf("tab1")
                        .col("x", ColumnType.INT)
                        .col("z", ColumnType.INT)
        );
    }

    @Test
    public void testDuplicateAlias() {
        assertSyntaxError("customers a" +
                        " cross join orders a", 30, "duplicate",
                modelOf("customers").col("customerId", ColumnType.INT).col("customerName", ColumnType.STRING),
                modelOf("orders").col("customerId", ColumnType.INT).col("product", ColumnType.STRING)
        );
    }

    @Test
    public void testDuplicateTables() throws Exception {
        assertQuery(
                "select-choose" +
                        " customers.customerId customerId," +
                        " customers.customerName customerName," +
                        " cust.customerId customerId1," +
                        " cust.customerName customerName1" +
                        " from (customers cross join customers cust)",
                "customers cross join customers cust",
                modelOf("customers").col("customerId", ColumnType.INT).col("customerName", ColumnType.STRING),
                modelOf("orders").col("customerId", ColumnType.INT).col("product", ColumnType.STRING)
        );
    }

    @Test
    public void testEmptyOrderBy() {
        assertSyntaxError("select x, y from tab order by", 27, "literal expected");
    }

    @Test
    public void testEmptySampleBy() {
        assertSyntaxError("select x, y from tab sample by", 28, "literal expected");
    }

    @Test
    public void testEqualsConstantTransitivityLhs() throws Exception {
        assertQuery(
                "select-choose" +
                        " c.customerId customerId," +
                        " o.customerId customerId1" +
                        " from (" +
                        "customers c" +
                        " outer join (orders o where customerId = 100) o on o.customerId = c.customerId where 100 = customerId)",
                "customers c" +
                        " outer join orders o on c.customerId = o.customerId" +
                        " where 100 = c.customerId",
                modelOf("customers").col("customerId", ColumnType.INT),
                modelOf("orders").col("customerId", ColumnType.INT)
        );
    }

    @Test
    public void testEqualsConstantTransitivityRhs() throws Exception {
        assertQuery(
                "select-choose" +
                        " c.customerId customerId," +
                        " o.customerId customerId1" +
                        " from (" +
                        "customers c outer join (orders o where customerId = 100) o on o.customerId = c.customerId where customerId = 100)",
                "customers c" +
                        " outer join orders o on c.customerId = o.customerId" +
                        " where c.customerId = 100",
                modelOf("customers").col("customerId", ColumnType.INT),
                modelOf("orders").col("customerId", ColumnType.INT)
        );
    }

    @Test
    public void testEraseColumnPrefix() throws SqlException {
        assertQuery(
                "select-choose name from (cust where name ~ 'x')",
                "cust where cust.name ~ 'x'",
                modelOf("cust").col("name", ColumnType.STRING)
        );
    }

    @Test
    public void testEraseColumnPrefixInJoin() throws Exception {
        assertQuery(
                "select-choose" +
                        " c.customerId customerId," +
                        " o.x x," +
                        " o.customerId customerId1" +
                        " from " +
                        "(" +
                        "customers c" +
                        " outer join (orders o where x = 10 and customerId = 100) o on customerId = c.customerId" +
                        " where customerId = 100" +
                        ")",
                "customers c" +
                        " outer join (orders o where o.x = 10) o on c.customerId = o.customerId" +
                        " where c.customerId = 100",
                modelOf("customers").col("customerId", ColumnType.INT),
                modelOf("orders")
                        .col("customerId", ColumnType.INT)
                        .col("x", ColumnType.INT)
        );
    }


    @Test
    public void testExpressionSyntaxError() {
        assertSyntaxError("select x from a where a + b(c,) > 10", 30, "missing argument");

        // when AST cache is not cleared below query will pickup "garbage" and will misrepresent error
        assertSyntaxError("orders join customers on orders.customerId = c.customerId", 45, "alias",
                modelOf("customers").col("customerId", ColumnType.INT),
                modelOf("orders").col("customerId", ColumnType.INT).col("productName", ColumnType.STRING).col("productId", ColumnType.INT)
        );
    }

    @Test
    public void testExtraComma2OrderByInAnalyticFunction() {
        assertSyntaxError("select a,b, f(c) my over (partition by b order by ts,) from xyz", 53, "literal expected");
    }

    @Test
    public void testExtraCommaOrderByInAnalyticFunction() {
        assertSyntaxError("select a,b, f(c) my over (partition by b order by ,ts) from xyz", 50, "literal");
    }

    @Test
    public void testExtraCommaPartitionByInAnalyticFunction() {
        assertSyntaxError("select a,b, f(c) my over (partition by b, order by ts) from xyz", 48, "')' expected");
    }

    @Test
    public void testFilterOnSubQuery() throws Exception {
        assertQuery(
                "select-choose" +
                        " c.customerName customerName," +
                        " c.count count," +
                        " c.customerId customerId," +
                        " o.orderId orderId," +
                        " o.customerId customerId1" +
                        " from (" +
                        "(select-group-by" +
                        " customerId," +
                        " customerName," +
                        " count() count" +
                        " from (customers where customerId > 400 and customerId < 1200)) c" +
                        " outer join orders o on o.customerId = c.customerId" +
                        " post-join-where o.orderId = NaN where count > 1)" +
                        " order by customerId",
                "(select customerId, customerName, count() count from customers) c" +
                        " outer join orders o on c.customerId = o.customerId " +
                        " where o.orderId = NaN and c.customerId > 400 and c.customerId < 1200 and count > 1 order by c.customerId",
                modelOf("customers").col("customerId", ColumnType.INT).col("customerName", ColumnType.STRING),
                modelOf("orders").col("orderId", ColumnType.INT).col("customerId", ColumnType.INT)
        );
    }

    @Test
    public void testGenericPreFilterPlacement() throws Exception {
        assertQuery(
                "select-choose customerName, orderId, productId" +
                        " from (" +
                        "customers" +
                        " join (orders where product = 'X') orders on orders.customerId = customers.customerId where customerName ~ 'WTBHZVPVZZ')",
                "select customerName, orderId, productId " +
                        "from customers join orders on customers.customerId = orders.customerId where customerName ~ 'WTBHZVPVZZ' and product = 'X'",
                modelOf("customers").col("customerId", ColumnType.INT).col("customerName", ColumnType.STRING),
                modelOf("orders").col("customerId", ColumnType.INT).col("product", ColumnType.STRING).col("orderId", ColumnType.INT).col("productId", ColumnType.INT)
        );
    }

    @Test
    public void testInnerJoin() throws Exception {
        assertQuery(
                "select-choose a.x x from (a a join b on b.x = a.x)",
                "select a.x from a a inner join b on b.x = a.x",
                modelOf("a").col("x", ColumnType.INT),
                modelOf("b").col("x", ColumnType.INT)
        );
    }

    @Test
    public void testInnerJoin2() throws Exception {
        assertQuery(
                "select-choose" +
                        " customers.customerId customerId," +
                        " customers.customerName customerName," +
                        " orders.customerId customerId1" +
                        " from (" +
                        "customers join orders on orders.customerId = customers.customerId where customerName ~ 'WTBHZVPVZZ'" +
                        ")",
                "customers join orders on customers.customerId = orders.customerId where customerName ~ 'WTBHZVPVZZ'",
                modelOf("customers").col("customerId", ColumnType.INT).col("customerName", ColumnType.STRING),
                modelOf("orders").col("customerId", ColumnType.INT)
        );
    }

    @Test
    public void testInnerJoinEqualsConstant() throws Exception {
        assertQuery(
                "select-choose" +
                        " customers.customerId customerId," +
                        " orders.customerId customerId1," +
                        " orders.productName productName" +
                        " from (" +
                        "customers" +
                        " join (orders where productName = 'WTBHZVPVZZ') orders on orders.customerId = customers.customerId)",
                "customers join orders on customers.customerId = orders.customerId where productName = 'WTBHZVPVZZ'",
                modelOf("customers").col("customerId", ColumnType.INT),
                modelOf("orders").col("customerId", ColumnType.INT).col("productName", ColumnType.STRING));
    }

    @Test
    public void testInnerJoinEqualsConstantLhs() throws Exception {
        assertQuery(
                "select-choose" +
                        " customers.customerId customerId," +
                        " orders.customerId customerId1," +
                        " orders.productName productName" +
                        " from (" +
                        "customers" +
                        " join (orders where 'WTBHZVPVZZ' = productName) orders on orders.customerId = customers.customerId)",
                "customers join orders on customers.customerId = orders.customerId where 'WTBHZVPVZZ' = productName",
                modelOf("customers").col("customerId", ColumnType.INT),
                modelOf("orders").col("customerId", ColumnType.INT).col("productName", ColumnType.STRING));
    }

    @Test
    public void testInnerJoinSubQuery() throws Exception {
        assertQuery(
                "select-choose customerName, productName, orderId" +
                        " from (" +
                        "(select-choose customerName, orderId, productId, productName from (" +
                        "customers" +
                        " join (orders where productName ~ 'WTBHZVPVZZ') orders on orders.customerId = customers.customerId)" +
                        ") x" +
                        " join products p on p.productId = x.productId)",
                "select customerName, productName, orderId from (" +
                        "select customerName, orderId, productId, productName " +
                        "from customers join orders on customers.customerId = orders.customerId where productName ~ 'WTBHZVPVZZ'" +
                        ") x" +
                        " join products p on p.productId = x.productId",
                modelOf("customers").col("customerId", ColumnType.INT).col("customerName", ColumnType.STRING),
                modelOf("orders").col("customerId", ColumnType.INT).col("productName", ColumnType.STRING).col("productId", ColumnType.INT).col("orderId", ColumnType.INT),
                modelOf("products").col("productId", ColumnType.INT)
        );

        assertQuery(
                "select-choose customerName, productName, orderId from (customers join (orders o where productName ~ 'WTBHZVPVZZ') o on o.customerId = customers.customerId join products p on p.productId = o.productId)",
                "select customerName, productName, orderId " +
                        " from customers join orders o on customers.customerId = o.customerId " +
                        " join products p on p.productId = o.productId" +
                        " where productName ~ 'WTBHZVPVZZ'",
                modelOf("customers").col("customerId", ColumnType.INT).col("customerName", ColumnType.STRING),
                modelOf("orders").col("customerId", ColumnType.INT).col("productName", ColumnType.STRING).col("productId", ColumnType.INT).col("orderId", ColumnType.INT),
                modelOf("products").col("productId", ColumnType.INT)
        );
    }

    @Test
    public void testInvalidAlias() {
        assertSyntaxError("orders join customers on orders.customerId = c.customerId", 45, "alias",
                modelOf("customers").col("customerId", ColumnType.INT),
                modelOf("orders").col("customerId", ColumnType.INT).col("productName", ColumnType.STRING).col("productId", ColumnType.INT)
        );
    }

    @Test
    public void testInvalidColumn() {
        assertSyntaxError("orders join customers on customerIdx = customerId", 25, "Invalid column",
                modelOf("customers").col("customerId", ColumnType.INT),
                modelOf("orders").col("customerId", ColumnType.INT).col("productName", ColumnType.STRING).col("productId", ColumnType.INT)
        );
    }

    @Test
    public void testInvalidColumnInExpression() {
        assertSyntaxError(
                "select a + b x from tab",
                11,
                "Invalid column",
                modelOf("tab").col("a", ColumnType.INT));
    }

    @Test
    public void testInvalidGroupBy1() {
        assertSyntaxError("select x, y from tab sample by x,", 32, "unexpected");
    }

    @Test
    public void testInvalidGroupBy2() {
        assertSyntaxError("select x, y from (tab sample by x,)", 33, "')' expected");
    }

    @Test
    public void testInvalidGroupBy3() {
        assertSyntaxError("select x, y from tab sample by x, order by y", 32, "unexpected token: ,");
    }

    @Test
    public void testInvalidInnerJoin1() {
        assertSyntaxError("select x from a a inner join b z", 31, "'on'");
    }

    @Test
    public void testInvalidInnerJoin2() {
        assertSyntaxError("select x from a a inner join b z on", 33, "Expression");
    }

    @Test
    public void testInvalidOrderBy1() {
        assertSyntaxError("select x, y from tab order by x,", 31, "literal expected");
    }

    @Test
    public void testInvalidOrderBy2() {
        assertSyntaxError("select x, y from (tab order by x,)", 33, "literal expected");
    }

    @Test
    public void testInvalidOuterJoin1() {
        assertSyntaxError("select x from a a outer join b z", 31, "'on'");
    }

    @Test
    public void testInvalidOuterJoin2() {
        assertSyntaxError("select x from a a outer join b z on", 33, "Expression");
    }

    @Test
    public void testInvalidSelectColumn() {
        assertSyntaxError("select c.customerId, orderIdx, o.productId from " +
                        "customers c " +
                        "join (" +
                        "orders latest by customerId where customerId in (`customers where customerName ~ 'PJFSREKEUNMKWOF'`)" +
                        ") o on c.customerId = o.customerId", 21, "Invalid column",
                modelOf("customers").col("customerName", ColumnType.STRING).col("customerId", ColumnType.INT),
                modelOf("orders").col("orderId", ColumnType.INT).col("customerId", ColumnType.INT)
        );

        assertSyntaxError("select c.customerId, orderId, o.productId2 from " +
                        "customers c " +
                        "join (" +
                        "orders latest by customerId where customerId in (`customers where customerName ~ 'PJFSREKEUNMKWOF'`)" +
                        ") o on c.customerId = o.customerId", 30, "Invalid column",
                modelOf("customers").col("customerName", ColumnType.STRING).col("customerId", ColumnType.INT),
                modelOf("orders").col("orderId", ColumnType.INT).col("customerId", ColumnType.INT)
        );

        assertSyntaxError("select c.customerId, orderId, o2.productId from " +
                        "customers c " +
                        "join (" +
                        "orders latest by customerId where customerId in (`customers where customerName ~ 'PJFSREKEUNMKWOF'`)" +
                        ") o on c.customerId = o.customerId", 30, "Invalid table name",
                modelOf("customers").col("customerName", ColumnType.STRING).col("customerId", ColumnType.INT),
                modelOf("orders").col("orderId", ColumnType.INT).col("customerId", ColumnType.INT)
        );
    }

    @Test
    public void testInvalidSubQuery() {
        assertSyntaxError("select x,y from (tab where x = 100) latest by x", 36, "latest");
    }

    @Test
    public void testInvalidTableName() {
        assertSyntaxError("orders join customer on customerId = customerId", 12, "does not exist",
                modelOf("orders").col("customerId", ColumnType.INT));
    }

    @Test
    public void testJoin1() throws Exception {
        assertQuery(
                "select-choose t1.x x, y from " +
                        "(" +
                        "(select-choose x from " +
                        "(" +
                        "tab t2 latest by x where x > 100)) t1 " +
                        "join tab2 xx2 on xx2.x = t1.x " +
                        "join (select-choose x, y from (tab4 latest by z where a > b and y > 0)) x4 on x4.x = t1.x " +
                        "cross join tab3 post-join-where xx2.x > tab3.b" +
                        ")",
                "select t1.x, y from (select x from tab t2 latest by x where x > 100) t1 " +
                        "join tab2 xx2 on xx2.x = t1.x " +
                        "join tab3 on xx2.x > tab3.b " +
                        "join (select x,y from tab4 latest by z where a > b) x4 on x4.x = t1.x " +
                        "where y > 0",
                modelOf("tab").col("x", ColumnType.INT),
                modelOf("tab2").col("x", ColumnType.INT),
                modelOf("tab3").col("b", ColumnType.INT),
                modelOf("tab4").col("x", ColumnType.INT).col("y", ColumnType.INT).col("z", ColumnType.INT).col("a", ColumnType.INT).col("b", ColumnType.INT));
    }

    @Test
    public void testJoin2() throws Exception {
        assertQuery(
                "select-choose x from (((" +
                        "select-choose" +
                        " tab2.x x" +
                        " from (tab join tab2 on tab2.x = tab.x)) t" +
                        " join tab3 on tab3.x = t.x) _xQdbA1)",
                "select x from ((select tab2.x from tab join tab2 on tab.x=tab2.x) t join tab3 on tab3.x = t.x)",
                modelOf("tab").col("x", ColumnType.INT),
                modelOf("tab2").col("x", ColumnType.INT),
                modelOf("tab3").col("x", ColumnType.INT)
        );
    }

    @Test
    public void testJoin3() throws Exception {
        assertQuery(
                "select-choose x from ((select-choose tab2.x x from (tab join tab2 on tab2.x = tab.x cross join tab3 post-join-where tab3.x f tab2.x = tab.x)) _xQdbA1)",
                "select x from (select tab2.x from tab join tab2 on tab.x=tab2.x join tab3 on f(tab3.x,tab2.x) = tab.x)",
                modelOf("tab").col("x", ColumnType.INT),
                modelOf("tab2").col("x", ColumnType.INT),
                modelOf("tab3").col("x", ColumnType.INT)
        );
    }

    @Test
    public void testJoinColumnAlias() {
        assertSyntaxError(
                "(select c.customerId, o.customerId kk, count() from customers c" +
                        " outer join orders o on c.customerId = o.customerId) " +
                        " where kk = NaN limit 10",
                123,
                "Invalid column",
                modelOf("customers").col("customerId", ColumnType.INT),
                modelOf("orders").col("customerId", ColumnType.INT)
        );
    }

    @Test
    public void testJoinColumnResolutionOnSubQuery() throws SqlException {
        assertQuery(
                "select-group-by sum(timestamp) sum from ((y) _xQdbA1 cross join (x) _xQdbA2)",
                "select sum(timestamp) from (y) cross join (x)",
                modelOf("x").col("ccy", ColumnType.SYMBOL),
                modelOf("y").col("ccy", ColumnType.SYMBOL).col("timestamp", ColumnType.TIMESTAMP)
        );
    }

    @Test
    public void testJoinColumnResolutionOnSubQuery2() throws SqlException {
        assertQuery(
                "select-group-by sum(timestamp) sum from ((y) _xQdbA1 join (x) _xQdbA2 on _xQdbA2.ccy = _xQdbA1.ccy and _xQdbA2.sym = _xQdbA1.sym)",
                "select sum(timestamp) from (y) join (x) on (ccy, sym)",
                modelOf("x").col("ccy", ColumnType.SYMBOL).col("sym", ColumnType.INT),
                modelOf("y").col("ccy", ColumnType.SYMBOL).col("timestamp", ColumnType.TIMESTAMP).col("sym", ColumnType.INT)
        );
    }

    @Test
    public void testJoinColumnResolutionOnSubQuery3() throws SqlException {
        assertQuery(
                "select-group-by sum(timestamp) sum from ((y) _xQdbA1 cross join x)",
                "select sum(timestamp) from (y) cross join x",
                modelOf("x").col("ccy", ColumnType.SYMBOL),
                modelOf("y").col("ccy", ColumnType.SYMBOL).col("timestamp", ColumnType.TIMESTAMP)
        );
    }

    @Test
    public void testJoinCycle() throws Exception {
        assertQuery(
                "select-choose" +
                        " orders.customerId customerId," +
                        " orders.orderId orderId," +
                        " customers.customerId customerId1," +
                        " d.orderId orderId1," +
                        " d.productId productId," +
                        " suppliers.supplier supplier," +
                        " products.productId productId1," +
                        " products.supplier supplier1" +
                        " from (" +
                        "orders" +
                        " join customers on customers.customerId = orders.customerId" +
                        " join (orderDetails d where orderId = productId) d on d.productId = orders.orderId" +
                        " join suppliers on suppliers.supplier = orders.orderId" +
                        " join products on products.productId = orders.orderId and products.supplier = suppliers.supplier)",
                "orders" +
                        " join customers on orders.customerId = customers.customerId" +
                        " join orderDetails d on d.orderId = orders.orderId and orders.orderId = products.productId" +
                        " join suppliers on products.supplier = suppliers.supplier" +
                        " join products on d.productId = products.productId and orders.orderId = products.productId" +
                        " where orders.orderId = suppliers.supplier",
                modelOf("orders").col("customerId", ColumnType.INT).col("orderId", ColumnType.INT),
                modelOf("customers").col("customerId", ColumnType.INT),
                modelOf("orderDetails").col("orderId", ColumnType.INT).col("productId", ColumnType.INT),
                modelOf("products").col("productId", ColumnType.INT).col("supplier", ColumnType.SYMBOL),
                modelOf("suppliers").col("supplier", ColumnType.SYMBOL)
        );
    }

    @Test
    public void testJoinCycle2() throws Exception {
        assertQuery(
                "select-choose" +
                        " orders.customerId" +
                        " customerId," +
                        " orders.orderId orderId," +
                        " customers.customerId customerId1," +
                        " d.orderId orderId1," +
                        " d.productId productId," +
                        " suppliers.supplier supplier," +
                        " suppliers.x x," +
                        " products.productId productId1," +
                        " products.supplier supplier1" +
                        " from (" +
                        "orders" +
                        " join customers on customers.customerId = orders.customerId" +
                        " join orderDetails d on d.productId = orders.orderId" +
                        " join suppliers on suppliers.x = d.orderId and suppliers.supplier = orders.orderId" +
                        " join products on products.productId = orders.orderId and products.supplier = suppliers.supplier" +
                        ")",
                "orders" +
                        " join customers on orders.orderId = products.productId" +
                        " join orderDetails d on products.supplier = suppliers.supplier" +
                        " join suppliers on orders.customerId = customers.customerId" +
                        " join products on d.productId = products.productId and orders.orderId = products.productId" +
                        " where orders.orderId = suppliers.supplier and d.orderId = suppliers.x",
                modelOf("orders").col("customerId", ColumnType.INT).col("orderId", ColumnType.INT),
                modelOf("customers").col("customerId", ColumnType.INT),
                modelOf("orderDetails").col("orderId", ColumnType.INT).col("productId", ColumnType.INT),
                modelOf("products").col("productId", ColumnType.INT).col("supplier", ColumnType.SYMBOL),
                modelOf("suppliers").col("supplier", ColumnType.SYMBOL).col("x", ColumnType.INT)
        );
    }

    @Test
    public void testJoinDuplicateTables() {
        assertSyntaxError(
                "select * from tab cross join tab",
                29,
                "duplicate",
                modelOf("tab").col("y", ColumnType.INT)
        );
    }

    @Test
    public void testJoinFunction() throws SqlException {
        assertQuery(
                "select-choose" +
                        " tab.x x," +
                        " t.y y," +
                        " t1.z z" +
                        " from (" +
                        "tab" +
                        " join t on f(y) = f(x)" +
                        " join t1 on z = f(x)" +
                        " const-where 1 = 1" +
                        ")",
                "select * from tab join t on f(x)=f(y) join t1 on 1=1 where z=f(x)",
                modelOf("tab").col("x", ColumnType.INT),
                modelOf("t").col("y", ColumnType.INT),
                modelOf("t1").col("z", ColumnType.INT)
        );
    }

    @Test
    public void testJoinGroupBy() throws Exception {
        assertQuery("select-group-by" +
                        " country," +
                        " avg(quantity) avg " +
                        "from (orders o join (customers c where country ~ '^Z') c on c.customerId = o.customerId join orderDetails d on d.orderId = o.orderId)",
                "select country, avg(quantity) from orders o " +
                        "join customers c on c.customerId = o.customerId " +
                        "join orderDetails d on o.orderId = d.orderId" +
                        " where country ~ '^Z'",
                modelOf("orders").col("customerId", ColumnType.INT).col("orderId", ColumnType.INT),
                modelOf("customers").col("customerId", ColumnType.INT).col("country", ColumnType.SYMBOL),
                modelOf("orderDetails").col("orderId", ColumnType.INT).col("quantity", ColumnType.DOUBLE)
        );
    }

    @Test
    public void testJoinGroupByFilter() throws Exception {
        assertQuery(
                "select-choose" +
                        " avg," +
                        " country " +
                        "from" +
                        " ((select-group-by country," +
                        " avg(quantity) avg" +
                        " from (orders o" +
                        " join (customers c where country ~ '^Z') c on c.customerId = o.customerId" +
                        " join orderDetails d on d.orderId = o.orderId)" +
                        ") _xQdbA1 where avg > 2" +
                        ")",
                "(select country, avg(quantity) avg from orders o " +
                        "join customers c on c.customerId = o.customerId " +
                        "join orderDetails d on o.orderId = d.orderId" +
                        " where country ~ '^Z') where avg > 2",
                modelOf("orders").col("customerId", ColumnType.INT).col("orderId", ColumnType.INT).col("quantity", ColumnType.DOUBLE),
                modelOf("customers").col("customerId", ColumnType.INT).col("country", ColumnType.SYMBOL),
                modelOf("orderDetails").col("orderId", ColumnType.INT)
        );
    }

    @Test
    public void testJoinImpliedCrosses() throws Exception {
        assertQuery(
                "select-choose" +
                        " orders.customerId customerId," +
                        " orders.orderId orderId," +
                        " customers.customerId customerId1," +
                        " d.orderId orderId1," +
                        " d.productId productId," +
                        " products.productId productId1," +
                        " products.supplier supplier," +
                        " suppliers.supplier supplier1" +
                        " from (" +
                        "orders" +
                        " cross join products join suppliers on suppliers.supplier = products.supplier" +
                        " cross join customers" +
                        " cross join orderDetails d" +
                        " const-where 1 = 1 and 2 = 2 and 3 = 3)",
                "orders" +
                        " join customers on 1=1" +
                        " join orderDetails d on 2=2" +
                        " join products on 3=3" +
                        " join suppliers on products.supplier = suppliers.supplier",
                modelOf("orders").col("customerId", ColumnType.INT).col("orderId", ColumnType.INT),
                modelOf("customers").col("customerId", ColumnType.INT),
                modelOf("orderDetails").col("orderId", ColumnType.INT).col("productId", ColumnType.INT),
                modelOf("products").col("productId", ColumnType.INT).col("supplier", ColumnType.SYMBOL),
                modelOf("suppliers").col("supplier", ColumnType.SYMBOL)
        );
    }

    @Test
    public void testJoinMultipleFields() throws Exception {
        assertQuery(
                "select-choose" +
                        " orders.customerId customerId," +
                        " orders.orderId orderId," +
                        " customers.customerId customerId1," +
                        " d.orderId orderId1," +
                        " d.productId productId," +
                        " products.productId productId1," +
                        " products.supplier supplier," +
                        " suppliers.supplier supplier1" +
                        " from (" +
                        "orders" +
                        " join customers on customers.customerId = orders.customerId" +
                        " join (orderDetails d where productId = orderId) d on d.productId = customers.customerId and d.orderId = orders.orderId" +
                        " join products on products.productId = d.productId" +
                        " join suppliers on suppliers.supplier = products.supplier)",
                "orders" +
                        " join customers on orders.customerId = customers.customerId" +
                        " join orderDetails d on d.orderId = orders.orderId and d.productId = customers.customerId" +
                        " join products on d.productId = products.productId" +
                        " join suppliers on products.supplier = suppliers.supplier" +
                        " where d.productId = d.orderId",
                modelOf("orders").col("customerId", ColumnType.INT).col("orderId", ColumnType.INT),
                modelOf("customers").col("customerId", ColumnType.INT),
                modelOf("orderDetails").col("orderId", ColumnType.INT).col("productId", ColumnType.INT),
                modelOf("products").col("productId", ColumnType.INT).col("supplier", ColumnType.SYMBOL),
                modelOf("suppliers").col("supplier", ColumnType.SYMBOL)
        );
    }

    @Test
    public void testJoinOfJoin() throws SqlException {
        assertQuery(
                "select-choose" +
                        " tt.x x," +
                        " tt.y y," +
                        " tt.x1 x1," +
                        " tt.z z," +
                        " tab2.z z1," +
                        " tab2.k k" +
                        " from ((" +
                        "select-choose" +
                        " tab.x x," +
                        " tab.y y," +
                        " tab1.x x1," +
                        " tab1.z z" +
                        " from (tab join tab1 on tab1.x = tab.x)" +
                        ") tt" +
                        " join tab2 on tab2.z = tt.z" +
                        ")",
                "select * from (select * from tab join tab1 on (x)) tt join tab2 on(z)",
                modelOf("tab")
                        .col("x", ColumnType.INT)
                        .col("y", ColumnType.INT),
                modelOf("tab1")
                        .col("x", ColumnType.INT)
                        .col("z", ColumnType.INT),
                modelOf("tab2")
                        .col("z", ColumnType.INT)
                        .col("k", ColumnType.INT)
        );
    }

    @Test
    public void testJoinOnCase() throws Exception {
        assertQuery(
                "select-choose a.x x from (a a cross join b where switch(x,15,1,10))",
                "select a.x from a a join b on (case when a.x = 1 then 10 else 15 end)",
                modelOf("a").col("x", ColumnType.INT),
                modelOf("b").col("x", ColumnType.INT));
    }

    @Test
    public void testJoinOnColumns() throws SqlException {
        assertQuery(
                "select-choose a.x x, b.y y from (tab1 a join tab2 b on b.z = a.z)",
                "select a.x, b.y from tab1 a join tab2 b on (z)",
                modelOf("tab1")
                        .col("x", ColumnType.INT)
                        .col("z", ColumnType.INT),
                modelOf("tab2")
                        .col("x", ColumnType.INT)
                        .col("y", ColumnType.INT)
                        .col("z", ColumnType.INT)
                        .col("s", ColumnType.INT)
        );
    }

    @Test
    public void testJoinOnExpression() {
        assertSyntaxError(
                "a join b on (x,x+1)",
                18,
                "Column name expected",
                modelOf("a").col("x", ColumnType.INT),
                modelOf("b").col("x", ColumnType.INT)
        );
    }

    @Test
    public void testJoinOnExpression2() throws SqlException {
        assertQuery("select-choose" +
                        " a.x x," +
                        " b.x x1" +
                        " from (" +
                        "a cross join (b where x) b where x + 1" +
                        ")",
                "a join b on a.x+1 and b.x",
                modelOf("a").col("x", ColumnType.INT),
                modelOf("b").col("x", ColumnType.INT)
        );
    }

    @Test
    public void testJoinOneFieldToTwoAcross2() throws Exception {
        assertQuery(
                "select-choose" +
                        " orders.orderId orderId," +
                        " orders.customerId customerId," +
                        " customers.customerId customerId1," +
                        " d.orderId orderId1," +
                        " d.productId productId," +
                        " products.productId productId1," +
                        " products.supplier supplier," +
                        " suppliers.supplier supplier1" +
                        " from (" +
                        "orders" +
                        " join customers on customers.customerId = orders.orderId" +
                        " join (orderDetails d where productId = orderId) d on d.orderId = orders.orderId" +
                        " join products on products.productId = d.productId" +
                        " join suppliers on suppliers.supplier = products.supplier" +
                        " where customerId = orderId)",
                "orders" +
                        " join customers on orders.customerId = customers.customerId" +
                        " join orderDetails d on d.orderId = customers.customerId and orders.orderId = d.orderId" +
                        " join products on d.productId = products.productId" +
                        " join suppliers on products.supplier = suppliers.supplier" +
                        " where d.productId = d.orderId",
                modelOf("orders").col("orderId", ColumnType.INT).col("customerId", ColumnType.INT),
                modelOf("customers").col("customerId", ColumnType.INT),
                modelOf("orderDetails").col("orderId", ColumnType.INT).col("productId", ColumnType.INT),
                modelOf("products").col("productId", ColumnType.INT).col("supplier", ColumnType.INT),
                modelOf("suppliers").col("supplier", ColumnType.INT)
        );
    }

    @Test
    public void testJoinOneFieldToTwoReorder() throws Exception {
        assertQuery(
                "select-choose" +
                        " orders.orderId orderId," +
                        " orders.customerId customerId," +
                        " d.orderId orderId1," +
                        " d.productId productId," +
                        " customers.customerId customerId1," +
                        " products.productId productId1," +
                        " products.supplier supplier," +
                        " suppliers.supplier supplier1" +
                        " from (" +
                        "orders" +
                        " join (orderDetails d where productId = orderId) d on d.orderId = orders.customerId" +
                        " join customers on customers.customerId = orders.customerId" +
                        " join products on products.productId = d.productId" +
                        " join suppliers on suppliers.supplier = products.supplier" +
                        " where orderId = customerId)",
                "orders" +
                        " join orderDetails d on d.orderId = orders.orderId and d.orderId = customers.customerId" +
                        " join customers on orders.customerId = customers.customerId" +
                        " join products on d.productId = products.productId" +
                        " join suppliers on products.supplier = suppliers.supplier" +
                        " where d.productId = d.orderId",
                modelOf("orders").col("orderId", ColumnType.INT).col("customerId", ColumnType.INT),
                modelOf("customers").col("customerId", ColumnType.INT),
                modelOf("orderDetails").col("orderId", ColumnType.INT).col("productId", ColumnType.INT),
                modelOf("products").col("productId", ColumnType.INT).col("supplier", ColumnType.INT),
                modelOf("suppliers").col("supplier", ColumnType.INT)
        );
    }

    @Test
    public void testJoinOrder4() throws SqlException {
        assertQuery(
                "select-choose" +
                        " b.id id," +
                        " e.id id1" +
                        " from (" +
                        "a" +
                        " cross join b" +
                        " asof join d" +
                        " join e on e.id = b.id" +
                        " cross join c" +
                        ")",
                "a" +
                        " cross join b cross join c" +
                        " asof join d inner join e on b.id = e.id",
                modelOf("a"),
                modelOf("b").col("id", ColumnType.INT),
                modelOf("c"),
                modelOf("d"),
                modelOf("e").col("id", ColumnType.INT)
        );
    }

    @Test
    public void testJoinReorder() throws Exception {
        assertQuery(
                "select-choose" +
                        " orders.orderId orderId," +
                        " customers.customerId customerId," +
                        " d.orderId orderId1," +
                        " d.productId productId," +
                        " products.productId productId1," +
                        " products.supplier supplier," +
                        " suppliers.supplier supplier1" +
                        " from (" +
                        "orders" +
                        " join (orderDetails d where productId = orderId) d on d.orderId = orders.orderId" +
                        " join customers on customers.customerId = d.productId" +
                        " join products on products.productId = d.productId" +
                        " join suppliers on suppliers.supplier = products.supplier const-where 1 = 1" +
                        ")",
                "orders" +
                        " join customers on 1=1" +
                        " join orderDetails d on d.orderId = orders.orderId and d.productId = customers.customerId" +
                        " join products on d.productId = products.productId" +
                        " join suppliers on products.supplier = suppliers.supplier" +
                        " where d.productId = d.orderId",
                modelOf("orders").col("orderId", ColumnType.INT),
                modelOf("customers").col("customerId", ColumnType.INT),
                modelOf("orderDetails").col("orderId", ColumnType.INT).col("productId", ColumnType.INT),
                modelOf("products").col("productId", ColumnType.INT).col("supplier", ColumnType.INT),
                modelOf("suppliers").col("supplier", ColumnType.INT)
        );
    }

    @Test
    public void testJoinReorder3() throws Exception {
        assertQuery(
                "select-choose" +
                        " orders.orderId orderId," +
                        " customers.customerId customerId," +
                        " shippers.shipper shipper," +
                        " d.orderId orderId1," +
                        " d.productId productId," +
                        " suppliers.supplier supplier," +
                        " products.productId productId1," +
                        " products.supplier supplier1" +
                        " from (" +
                        "orders" +
                        " join shippers on shippers.shipper = orders.orderId" +
                        " join (orderDetails d where productId = orderId) d on d.productId = shippers.shipper" +
                        " join products on products.productId = d.productId" +
                        " join suppliers on suppliers.supplier = products.supplier" +
                        " cross join customers" +
                        " const-where 1 = 1)",
                "orders" +
                        " outer join customers on 1=1" +
                        " join shippers on shippers.shipper = orders.orderId" +
                        " join orderDetails d on d.orderId = orders.orderId and d.productId = shippers.shipper" +
                        " join suppliers on products.supplier = suppliers.supplier" +
                        " join products on d.productId = products.productId" +
                        " where d.productId = d.orderId",
                modelOf("orders").col("orderId", ColumnType.INT),
                modelOf("customers").col("customerId", ColumnType.INT),
                modelOf("orderDetails").col("orderId", ColumnType.INT).col("productId", ColumnType.INT),
                modelOf("products").col("productId", ColumnType.INT).col("supplier", ColumnType.INT),
                modelOf("suppliers").col("supplier", ColumnType.INT),
                modelOf("shippers").col("shipper", ColumnType.INT)
        );
    }

    @Test
    public void testJoinReorderRoot() throws Exception {
        assertQuery(
                "select-choose" +
                        " customers.customerId customerId," +
                        " orders.orderId orderId," +
                        " d.orderId orderId1," +
                        " d.productId productId," +
                        " products.productId productId1," +
                        " products.supplier supplier," +
                        " suppliers.supplier supplier1" +
                        " from (" +
                        "customers" +
                        " join (orderDetails d where productId = orderId) d on d.productId = customers.customerId" +
                        " join orders on orders.orderId = d.orderId" +
                        " join products on products.productId = d.productId" +
                        " join suppliers on suppliers.supplier = products.supplier" +
                        ")",
                "customers" +
                        " cross join orders" +
                        " join orderDetails d on d.orderId = orders.orderId and d.productId = customers.customerId" +
                        " join products on d.productId = products.productId" +
                        " join suppliers on products.supplier = suppliers.supplier" +
                        " where d.productId = d.orderId",

                modelOf("orders").col("orderId", ColumnType.INT),
                modelOf("customers").col("customerId", ColumnType.INT),
                modelOf("orderDetails").col("orderId", ColumnType.INT).col("productId", ColumnType.INT),
                modelOf("products").col("productId", ColumnType.INT).col("supplier", ColumnType.INT),
                modelOf("suppliers").col("supplier", ColumnType.INT)
        );
    }

    @Test
    public void testJoinReorderRoot2() throws Exception {
        assertQuery(
                "select-choose" +
                        " orders.orderId orderId," +
                        " customers.customerId customerId," +
                        " shippers.shipper shipper," +
                        " d.orderId orderId1," +
                        " d.productId productId," +
                        " products.productId productId1," +
                        " products.supplier supplier," +
                        " suppliers.supplier supplier1" +
                        " from (" +
                        "orders" +
                        " join shippers on shippers.shipper = orders.orderId" +
                        // joining on productId = shipper is sufficient because:
                        // 1. shipper = orders.orderId
                        // 2. d.orderId = orders.orderId
                        // 3. d.productId = shipper
                        " join (orderDetails d where productId = orderId) d on d.productId = shippers.shipper" +
                        " join products on products.productId = d.productId" +
                        " join suppliers on suppliers.supplier = products.supplier" +
                        " cross join customers const-where 1 = 1" +
                        ")",
                "orders" +
                        " outer join customers on 1=1" +
                        " join shippers on shippers.shipper = orders.orderId" +
                        " join orderDetails d on d.orderId = orders.orderId and d.productId = shippers.shipper" +
                        " join products on d.productId = products.productId" +
                        " join suppliers on products.supplier = suppliers.supplier" +
                        " where d.productId = d.orderId",
                modelOf("orders").col("orderId", ColumnType.INT),
                modelOf("customers").col("customerId", ColumnType.INT),
                modelOf("orderDetails").col("orderId", ColumnType.INT).col("productId", ColumnType.INT),
                modelOf("products").col("productId", ColumnType.INT).col("supplier", ColumnType.INT),
                modelOf("suppliers").col("supplier", ColumnType.INT),
                modelOf("shippers").col("shipper", ColumnType.INT)
        );
    }

    @Test
    public void testJoinSubQuery() throws Exception {
        assertQuery(
                "select-choose" +
                        " orders.orderId orderId," +
                        " _xQdbA1.customerName customerName," +
                        " _xQdbA1.customerId customerId" +
                        " from (" +
                        "orders" +
                        " join (select-choose customerId, customerName from (customers where customerName ~ 'X')) _xQdbA1 on customerName = orderId)",
                "orders" +
                        " cross join (select customerId, customerName from customers where customerName ~ 'X')" +
                        " where orderId = customerName",
                modelOf("orders").col("orderId", ColumnType.INT),
                modelOf("customers").col("customerId", ColumnType.INT).col("customerName", ColumnType.STRING)

        );
    }

    @Test
    public void testJoinSubQueryConstantWhere() throws Exception {
        assertQuery(
                "select-choose o.customerId customerId" +
                        " from ((select-choose customerId cid from (customers where 100 = customerId)) c" +
                        " outer join (orders o where customerId = 100) o on o.customerId = c.cid" +
                        " const-where 10 = 9)",
                "select o.customerId from (select customerId cid from customers) c" +
                        " outer join orders o on c.cid = o.customerId" +
                        " where 100 = c.cid and 10=9",
                modelOf("customers").col("customerId", ColumnType.INT),
                modelOf("orders").col("customerId", ColumnType.INT)
        );
    }

    @Test
    public void testJoinSubQueryWherePosition() throws Exception {
        assertQuery(
                "select-choose" +
                        " o.customerId customerId " +
                        "from " +
                        "((select-choose" +
                        " customerId cid " +
                        "from (customers where 100 = customerId)) c " +
                        "outer join (orders o where customerId = 100) o on o.customerId = c.cid)",
                "select o.customerId from (select customerId cid from customers) c" +
                        " outer join orders o on c.cid = o.customerId" +
                        " where 100 = c.cid",
                modelOf("customers").col("customerId", ColumnType.INT),
                modelOf("orders").col("customerId", ColumnType.INT)
        );
    }

    @Test
    public void testJoinSyntaxError() {
        assertSyntaxError(
                "select a.x from a a join b on (a + case when a.x = 1 then 10 else end)",
                66,
                "missing argument",
                modelOf("a").col("x", ColumnType.INT),
                modelOf("b").col("x", ColumnType.INT));
    }

    @Test
    public void testJoinTableMissing() {
        assertSyntaxError(
                "select a from tab join",
                18,
                "table name or sub-query expected"
        );
    }

    @Test
    public void testJoinWith() throws SqlException {
        assertQuery(
                "select-choose" +
                        " x.y y," +
                        " x1.y y1," +
                        " x2.y y2" +
                        " from" +
                        " ((select-choose y from (tab)) x" +
                        " cross join (select-choose y from (tab)) x1" +
                        " cross join (select-choose y from (tab)) x2)",
                "with x as (select * from tab) x cross join x x1 cross join x x2",
                modelOf("tab").col("y", ColumnType.INT)
        );
    }

    @Test
    public void testJoinWithClausesDefaultAlias() throws SqlException {
        assertQuery(
                "select-choose" +
                        " cust.customerId customerId," +
                        " cust.name name," +
                        " ord.customerId customerId1" +
                        " from (" +
                        "(customers where name ~ 'X') cust" +
                        " outer join (select-choose customerId from (orders where amount > 100)) ord on ord.customerId = cust.customerId" +
                        " post-join-where ord.customerId != null limit 10)",
                "with" +
                        " cust as (customers where name ~ 'X')," +
                        " ord as (select customerId from orders where amount > 100)" +
                        " cust outer join ord on (customerId) " +
                        " where ord.customerId != null" +
                        " limit 10",
                modelOf("customers").col("customerId", ColumnType.INT).col("name", ColumnType.STRING),
                modelOf("orders").col("customerId", ColumnType.INT).col("amount", ColumnType.DOUBLE)
        );
    }

    @Test
    public void testJoinWithClausesExplicitAlias() throws SqlException {
        assertQuery(
                "select-choose" +
                        " c.name name," +
                        " c.customerId customerId," +
                        " o.customerId customerId1" +
                        " from ((customers where name ~ 'X') c" +
                        " outer join (select-choose customerId from (orders where amount > 100)) o on o.customerId = c.customerId" +
                        " post-join-where o.customerId != null" +
                        " limit 10)",
                "with" +
                        " cust as (customers where name ~ 'X')," +
                        " ord as (select customerId from orders where amount > 100)" +
                        " cust c outer join ord o on (customerId) " +
                        " where o.customerId != null" +
                        " limit 10",
                modelOf("customers").col("customerId", ColumnType.INT).col("name", ColumnType.STRING),
                modelOf("orders").col("customerId", ColumnType.INT).col("amount", ColumnType.DOUBLE)
        );
    }

    @Test
    public void testJoinWithFilter() throws Exception {
        assertQuery(
                "select-choose" +
                        " customers.customerId customerId," +
                        " orders.orderId orderId," +
                        " d.orderId orderId1," +
                        " d.productId productId," +
                        " d.quantity quantity," +
                        " products.productId productId1," +
                        " products.supplier supplier," +
                        " products.price price," +
                        " suppliers.supplier supplier1" +
                        " from (" +
                        "customers" +
                        " join (orderDetails d where productId = orderId) d on d.productId = customers.customerId" +
                        " join orders on orders.orderId = d.orderId post-join-where d.quantity < orders.orderId" +
                        " join products on products.productId = d.productId post-join-where products.price > d.quantity or d.orderId = orders.orderId" +
                        " join suppliers on suppliers.supplier = products.supplier" +
                        ")",
                "customers" +
                        " cross join orders" +
                        " join orderDetails d on d.orderId = orders.orderId and d.productId = customers.customerId" +
                        " join products on d.productId = products.productId" +
                        " join suppliers on products.supplier = suppliers.supplier" +
                        " where d.productId = d.orderId" +
                        " and (products.price > d.quantity or d.orderId = orders.orderId) and d.quantity < orders.orderId",

                modelOf("orders").col("orderId", ColumnType.INT),
                modelOf("customers").col("customerId", ColumnType.INT),
                modelOf("orderDetails")
                        .col("orderId", ColumnType.INT)
                        .col("productId", ColumnType.INT)
                        .col("quantity", ColumnType.DOUBLE),
                modelOf("products").col("productId", ColumnType.INT)
                        .col("supplier", ColumnType.INT)
                        .col("price", ColumnType.DOUBLE),
                modelOf("suppliers").col("supplier", ColumnType.INT)
        );
    }

    @Test
    public void testJoinWithFunction() throws SqlException {
        assertQuery("select-choose x1.a a, x1.s s, x2.a a1, x2.s s1 from ((select-choose a, s from (random_cursor(rnd_symbol(2,4,4,4),'s',rnd_int(),'a',10))) x1 join (select-choose a, s from (random_cursor(rnd_symbol(2,4,4,4),'s',rnd_int(),'a',10))) x2 on x2.s = x1.s)",
                "with x as (select * from random_cursor(10, 'a', rnd_int(), 's', rnd_symbol(4,4,4,2))) " +
                        "select * from x x1 join x x2 on (s)");
    }

    @Test
    public void testLatestBySyntax() {
        assertSyntaxError(
                "select * from tab latest",
                18,
                "'by' expected"
        );
    }

    @Test
    public void testLexerReset() {
        for (int i = 0; i < 10; i++) {
            try {
                sqlCompiler.compileExecutionModel("select \n" +
                                "-- ltod(Date)\n" +
                                "count() \n" +
                                "-- from acc\n" +
                                "from acc(Date) sample by 1d\n" +
                                "-- where x = 10\n",
                        bindVariableService, true);
                Assert.fail();
            } catch (SqlException e) {
                TestUtils.assertEquals("Invalid column: Date", e.getFlyweightMessage());
            }
        }
    }

    @Test
    public void testLineCommentAtEnd() throws Exception {
        assertQuery(
                "select-choose" +
                        " a, x" +
                        " from (" +
                        "(x where a > 1 and x > 1) 'b a')",
                "(x where a > 1) 'b a' where x > 1\n--this is comment",
                modelOf("x")
                        .col("x", ColumnType.INT)
                        .col("a", ColumnType.INT));
    }

    @Test
    public void testLineCommentAtMiddle() throws Exception {
        assertQuery(
                "select-choose" +
                        " a, x" +
                        " from (" +
                        "(x where a > 1 and x > 1) 'b a')",
                "(x where a > 1) \n" +
                        " -- this is a comment \n" +
                        "'b a' where x > 1",
                modelOf("x")
                        .col("x", ColumnType.INT)
                        .col("a", ColumnType.INT));
    }

    @Test
    public void testLineCommentAtStart() throws Exception {
        assertQuery(
                "select-choose" +
                        " a, x" +
                        " from (" +
                        "(x where a > 1 and x > 1) 'b a')",
                "-- hello, this is a comment\n (x where a > 1) 'b a' where x > 1",
                modelOf("x")
                        .col("x", ColumnType.INT)
                        .col("a", ColumnType.INT));
    }

    @Test
    public void testMissingArgument() {
        assertSyntaxError(
                "select x from tab where not (x != 1 and)",
                36,
                "Missing right argument",
                modelOf("tab").col("x", ColumnType.INT)
        );
    }

    @Test
    public void testMissingTable() {
        assertSyntaxError(
                "select a from",
                9,
                "table name or sub-query expected"
        );
    }

    @Test
    public void testMissingTableInSubQuery() {
        assertSyntaxError(
                "with x as (select a from) x",
                24,
                "table name or sub-query expected",
                modelOf("tab").col("b", ColumnType.INT)
        );
    }

    @Test
    public void testMissingWhere() {
        try {
            sqlCompiler.compileExecutionModel("select id, x + 10, x from tab id ~ 'HBRO'", bindVariableService, true);
            Assert.fail("Exception expected");
        } catch (SqlException e) {
            Assert.assertEquals(33, e.getPosition());
        }
    }

    @Test
    public void testMixedFieldsSubQuery() throws Exception {
        assertQuery(
                "select-choose x, y from ((select-virtual x, z + x y from (tab t2 latest by x where x > 100)) t1 where y > 0)",
                "select x, y from (select x,z + x y from tab t2 latest by x where x > 100) t1 where y > 0",
                modelOf("tab").col("x", ColumnType.INT).col("z", ColumnType.INT));
    }

    @Test
    public void testMostRecentWhereClause() throws Exception {
        assertQuery(
                "select-virtual x, sum + 25 ohoh from (select-group-by x, sum(z) sum from (select-virtual a + b * c x, z from (zyzy latest by x where in(y,x,a) and b = 10)))",
                "select a+b*c x, sum(z)+25 ohoh from zyzy latest by x where a in (x,y) and b = 10",
                modelOf("zyzy")
                        .col("a", ColumnType.INT)
                        .col("b", ColumnType.INT)
                        .col("c", ColumnType.INT)
                        .col("x", ColumnType.INT)
                        .col("y", ColumnType.INT)
                        .col("z", ColumnType.INT)
        );
    }

    @Test
    public void testMultipleExpressions() throws Exception {
        assertQuery(
                "select-virtual x, sum + 25 ohoh from (select-group-by x, sum(z) sum from (select-virtual a + b * c x, z from (zyzy)))",
                "select a+b*c x, sum(z)+25 ohoh from zyzy",
                modelOf("zyzy")
                        .col("a", ColumnType.INT)
                        .col("b", ColumnType.INT)
                        .col("c", ColumnType.INT)
                        .col("x", ColumnType.INT)
                        .col("y", ColumnType.INT)
                        .col("z", ColumnType.INT)
        );
    }

    @Test
    public void testNestedJoinReorder() throws Exception {
        assertQuery(
                "select-choose" +
                        " x.orderId orderId," +
                        " x.productId productId," +
                        " y.orderId orderId1," +
                        " y.customerId customerId" +
                        " from " +
                        "(" +
                        "(" +
                        "select-choose orders.orderId orderId, products.productId productId" +
                        " from " +
                        "(" +
                        "orders" +
                        " join (orderDetails d where productId = orderId) d on d.orderId = orders.customerId" +
                        " join customers on customers.customerId = orders.customerId" +
                        " join products on products.productId = d.productId" +
                        " join suppliers on suppliers.supplier = products.supplier" +
                        " where orderId = customerId" +
                        ")" +
                        ") x cross join (orders" +
                        " join customers on customers.customerId = orders.customerId" +
                        " join (orderDetails d where orderId = productId) d on d.productId = orders.orderId" +
                        " join suppliers on suppliers.supplier = orders.orderId" +
                        " join products on products.productId = orders.orderId and products.supplier = suppliers.supplier) y)",
                "with x as (select orders.orderId, products.productId from " +
                        "orders" +
                        " join orderDetails d on d.orderId = orders.orderId and d.orderId = customers.customerId" +
                        " join customers on orders.customerId = customers.customerId" +
                        " join products on d.productId = products.productId" +
                        " join suppliers on products.supplier = suppliers.supplier" +
                        " where d.productId = d.orderId), " +
                        " y as (" +
                        "orders" +
                        " join customers on orders.customerId = customers.customerId" +
                        " join orderDetails d on d.orderId = orders.orderId and orders.orderId = products.productId" +
                        " join suppliers on products.supplier = suppliers.supplier" +
                        " join products on d.productId = products.productId and orders.orderId = products.productId" +
                        " where orders.orderId = suppliers.supplier)" +
                        " x cross join y",
                modelOf("orders").col("orderId", ColumnType.INT).col("customerId", ColumnType.INT),
                modelOf("customers").col("customerId", ColumnType.INT),
                modelOf("orderDetails").col("orderId", ColumnType.INT).col("productId", ColumnType.INT),
                modelOf("products").col("productId", ColumnType.INT).col("supplier", ColumnType.INT),
                modelOf("suppliers").col("supplier", ColumnType.INT),
                modelOf("shippers").col("shipper", ColumnType.INT)
        );
    }

    @Test
    public void testOneAnalyticColumn() throws Exception {
        assertQuery(
                "select-analytic a, b, f(c) f over (partition by b order by ts) from (xyz)",
                "select a,b, f(c) over (partition by b order by ts) from xyz",
                modelOf("xyz")
                        .col("a", ColumnType.INT)
                        .col("b", ColumnType.INT)
                        .col("c", ColumnType.INT)
        );
    }

    @Test
    public void testOptimiseNotAnd() throws SqlException {
        assertQuery(
                "select-choose a, b from (tab where a != b or b != a)",
                "select a, b from tab where not (a = b and b = a)",
                modelOf("tab")
                        .col("a", ColumnType.INT)
                        .col("b", ColumnType.INT));
    }

    @Test
    public void testOptimiseNotEqual() throws SqlException {
        assertQuery(
                "select-choose a, b from (tab where a != b)",
                "select a, b from tab where not (a = b)",
                modelOf("tab")
                        .col("a", ColumnType.INT)
                        .col("b", ColumnType.INT));
    }

    @Test
    public void testOptimiseNotGreater() throws SqlException {
        assertQuery(
                "select-choose a, b from (tab where a <= b)",
                "select a, b from tab where not (a > b)",
                modelOf("tab")
                        .col("a", ColumnType.INT)
                        .col("b", ColumnType.INT));
    }

    @Test
    public void testOptimiseNotGreaterOrEqual() throws SqlException {
        assertQuery(
                "select-choose a, b from (tab where a < b)",
                "select a, b from tab where not (a >= b)",
                modelOf("tab")
                        .col("a", ColumnType.INT)
                        .col("b", ColumnType.INT));
    }

    @Test
    public void testOptimiseNotLess() throws SqlException {
        assertQuery(
                "select-choose a, b from (tab where a >= b)",
                "select a, b from tab where not (a < b)",
                modelOf("tab")
                        .col("a", ColumnType.INT)
                        .col("b", ColumnType.INT));
    }

    @Test
    public void testOptimiseNotLessOrEqual() throws SqlException {
        assertQuery(
                "select-choose a, b from (tab where a > b)",
                "select a, b from tab where not (a <= b)",
                modelOf("tab")
                        .col("a", ColumnType.INT)
                        .col("b", ColumnType.INT));
    }

    @Test
    public void testOptimiseNotLiteral() throws SqlException {
        assertQuery(
                "select-choose a, b from (tab where not(a))",
                "select a, b from tab where not (a)",
                modelOf("tab")
                        .col("a", ColumnType.INT)
                        .col("b", ColumnType.INT));
    }

    @Test
    public void testOptimiseNotLiteralOr() throws SqlException {
        assertQuery(
                "select-choose a, b from (tab where not(a) and b != a)",
                "select a, b from tab where not (a or b = a)",
                modelOf("tab")
                        .col("a", ColumnType.INT)
                        .col("b", ColumnType.INT));
    }

    @Test
    public void testOptimiseNotNotEqual() throws SqlException {
        assertQuery(
                "select-choose a, b from (tab where a = b)",
                "select a, b from tab where not (a != b)",
                modelOf("tab")
                        .col("a", ColumnType.INT)
                        .col("b", ColumnType.INT));
    }

    @Test
    public void testOptimiseNotNotNotEqual() throws SqlException {
        assertQuery(
                "select-choose a, b from (tab where a != b)",
                "select a, b from tab where not(not (a != b))",
                modelOf("tab")
                        .col("a", ColumnType.INT)
                        .col("b", ColumnType.INT));
    }

    @Test
    public void testOptimiseNotOr() throws SqlException {
        assertQuery(
                "select-choose a, b from (tab where a != b and b != a)",
                "select a, b from tab where not (a = b or b = a)",
                modelOf("tab")
                        .col("a", ColumnType.INT)
                        .col("b", ColumnType.INT));
    }

    @Test
    public void testOptimiseNotOrLiterals() throws SqlException {
        assertQuery(
                "select-choose a, b from (tab where not(a) and not(b))",
                "select a, b from tab where not (a or b)",
                modelOf("tab")
                        .col("a", ColumnType.INT)
                        .col("b", ColumnType.INT));
    }

    @Test
    public void testOptionalSelect() throws Exception {
        assertQuery(
                "select-choose x from (tab t2 latest by x where x > 100)",
                "tab t2 latest by x where x > 100",
                modelOf("tab").col("x", ColumnType.INT));
    }

    @Test
    public void testOrderBy1() throws Exception {
        assertQuery(
                "select-choose x, y from (select-choose x, y, z from (tab) order by x, y, z)",
                "select x,y from tab order by x,y,z",
                modelOf("tab")
                        .col("x", ColumnType.INT)
                        .col("y", ColumnType.INT)
                        .col("z", ColumnType.INT)
        );

    }

    @Test
    public void testOrderByAmbiguousColumn() {
        assertSyntaxError(
                "select tab1.x from tab1 join tab2 on (x) order by y",
                50,
                "Ambiguous",
                modelOf("tab1").col("x", ColumnType.INT).col("y", ColumnType.INT),
                modelOf("tab2").col("x", ColumnType.INT).col("y", ColumnType.INT)
        );
    }

    @Test
    public void testOrderByExpression() {
        assertSyntaxError("select x, y from tab order by x+y", 31, "unexpected");
    }

    @Test
    public void testOrderByGroupByCol() throws SqlException {
        assertQuery(
                "select-group-by a, sum(b) b from (tab) order by b",
                "select a, sum(b) b from tab order by b",
                modelOf("tab").col("a", ColumnType.INT).col("b", ColumnType.INT)
        );
    }

    @Test
    public void testOrderByGroupByColPrefixed() throws SqlException {
        assertQuery(
                "select-group-by a, sum(b) b from (tab)",
                "select a, sum(b) b from tab order by tab.b, a",
                modelOf("tab").col("a", ColumnType.INT).col("b", ColumnType.INT)
        );
    }

    @Test
    public void testOrderByGroupByColPrefixed2() throws SqlException {
        assertQuery(
                "select-group-by a, sum(b) b from (tab) order by a",
                "select a, sum(b) b from tab order by a, tab.b",
                modelOf("tab").col("a", ColumnType.INT).col("b", ColumnType.INT)
        );
    }

    @Test
    public void testOrderByGroupByColPrefixed3() throws SqlException {
        assertQuery(
                "select-group-by a, sum(b) b from (tab) order by a",
                "select a, sum(b) b from tab order by tab.a, tab.b",
                modelOf("tab").col("a", ColumnType.INT).col("b", ColumnType.INT)
        );
    }

    @Test
    public void testOrderByOnAliasedColumn() throws SqlException {
        assertQuery(
                "select-choose y from (select-choose y, tab.x x from (tab) order by x)",
                "select y from tab order by tab.x",
                modelOf("tab")
                        .col("x", ColumnType.DOUBLE)
                        .col("y", ColumnType.INT)
        );
    }

    @Test
    public void testOrderByOnExpression() throws SqlException {
        assertQuery(
                "select-virtual y + x z from (tab) order by z",
                "select y+x z from tab order by z",
                modelOf("tab")
                        .col("x", ColumnType.DOUBLE)
                        .col("y", ColumnType.INT)
        );
    }

    @Test
    public void testOrderByOnJoinSubQuery() throws SqlException {
        assertQuery(
                "select-choose x, y from (select-choose a.x x, b.y y, b.s s from ((select-choose x, z from (tab1 where x = 'Z')) a join (tab2 where s ~ 'K') b on b.z = a.z) order by s)",
                "select a.x, b.y from (select x,z from tab1 where x = 'Z' order by x) a join (tab2 where s ~ 'K') b on a.z=b.z order by b.s",
                modelOf("tab1")
                        .col("x", ColumnType.INT)
                        .col("z", ColumnType.INT),
                modelOf("tab2")
                        .col("x", ColumnType.INT)
                        .col("y", ColumnType.INT)
                        .col("z", ColumnType.INT)
                        .col("s", ColumnType.INT)
        );
    }

    @Test
    public void testOrderByOnJoinSubQuery2() throws SqlException {
        assertQuery(
                "select-choose a.x x, b.y y from ((select-choose x, z from (select-choose x, z, p from (tab1 where x = 'Z') order by p)) a join (tab2 where s ~ 'K') b on b.z = a.z)",
                "select a.x, b.y from (select x,z from tab1 where x = 'Z' order by p) a join (tab2 where s ~ 'K') b on a.z=b.z",
                modelOf("tab1")
                        .col("x", ColumnType.INT)
                        .col("z", ColumnType.INT)
                        .col("p", ColumnType.INT),
                modelOf("tab2")
                        .col("x", ColumnType.INT)
                        .col("y", ColumnType.INT)
                        .col("z", ColumnType.INT)
                        .col("s", ColumnType.INT)
        );
    }

    @Test
    public void testOrderByOnJoinSubQuery3() throws SqlException {
        assertQuery(
                "select-choose a.x x, b.y y from ((select-choose x from (select-choose x, z from (tab1 where x = 'Z') order by z)) a asof join (select-choose y, z from (select-choose y, z, s from (tab2 where s ~ 'K') order by s)) b on b.z = a.x)",
                "select a.x, b.y from (select x from tab1 where x = 'Z' order by z) a asof join (select y,z from tab2 where s ~ 'K' order by s) b where a.x = b.z",
                modelOf("tab1")
                        .col("x", ColumnType.INT)
                        .col("z", ColumnType.INT),
                modelOf("tab2")
                        .col("x", ColumnType.INT)
                        .col("y", ColumnType.INT)
                        .col("z", ColumnType.INT)
                        .col("s", ColumnType.INT)
        );
    }

    @Test
    public void testOrderByOnJoinTableReference() throws SqlException {
        assertQuery(
                "select-choose x, y from (select-choose a.x x, b.y y, b.s s from (tab1 a join tab2 b on b.z = a.z) order by s)",
                "select a.x, b.y from tab1 a join tab2 b on a.z = b.z order by b.s",
                modelOf("tab1")
                        .col("x", ColumnType.INT)
                        .col("z", ColumnType.INT),
                modelOf("tab2")
                        .col("x", ColumnType.INT)
                        .col("y", ColumnType.INT)
                        .col("z", ColumnType.INT)
                        .col("s", ColumnType.INT)
        );
    }

    @Test
    public void testOrderByOnMultipleColumns() throws SqlException {
        assertQuery(
                "select-choose z from (select-choose y z, x from (tab) order by z desc, x)",
                "select y z from tab order by z desc, x",
                modelOf("tab")
                        .col("x", ColumnType.DOUBLE)
                        .col("y", ColumnType.INT)
        );
    }

    @Test
    public void testOrderByOnNonSelectedColumn() throws SqlException {
        assertQuery(
                "select-choose y from (select-choose y, x from (tab) order by x)",
                "select y from tab order by x",
                modelOf("tab")
                        .col("x", ColumnType.DOUBLE)
                        .col("y", ColumnType.INT)
        );
    }

    @Test
    public void testOrderByOnNonSelectedColumn2() throws SqlException {
        assertQuery(
                "select-choose column from (select-virtual 2 * y + x column, x from (select-choose 2 * y + x column, x from (tab)) order by x)",
                "select 2*y+x from tab order by x",
                modelOf("tab")
                        .col("x", ColumnType.DOUBLE)
                        .col("y", ColumnType.INT)
        );
    }

    @Test
    public void testOrderByOnNonSelectedColumn3() throws SqlException {
        assertQuery(
                "select-choose" +
                        " column," +
                        " column1" +
                        " from (" +
                        "select-virtual" +
                        " 2 * y + x column," +
                        " 3 / x column1, x" +
                        " from (" +
                        "select-choose" +
                        " 2 * y + x column," +
                        " 3 / x column1," +
                        " x from (tab)) order by x)",
                "select 2*y+x, 3/x from tab order by x",
                modelOf("tab")
                        .col("x", ColumnType.DOUBLE)
                        .col("y", ColumnType.INT)
        );
    }

    @Test
    public void testOrderByOnOuterResult() throws SqlException {
        assertQuery(
                "select-virtual x, sum1 + sum z from (select-group-by x, sum(3 / x) sum, sum(2 * y + x) sum1 from (tab)) order by z",
                "select x, sum(2*y+x) + sum(3/x) z from tab order by z asc, tab.y desc",
                modelOf("tab")
                        .col("x", ColumnType.DOUBLE)
                        .col("y", ColumnType.INT)
        );
    }

    @Test
    public void testOrderByOnSelectedAlias() throws SqlException {
        assertQuery(
                "select-choose y z from (tab) order by z",
                "select y z from tab order by z",
                modelOf("tab")
                        .col("x", ColumnType.DOUBLE)
                        .col("y", ColumnType.INT)
        );
    }

    @Test
    public void testOrderByWithSampleBy() throws SqlException {
        assertQuery(
                "select-group-by t, a, sum(b) sum from ((tab order by t) _xQdbA1) timestamp (t) sample by 2m order by a",
                "select a, sum(b) from (tab order by t) timestamp(t) sample by 2m order by a",
                modelOf("tab")
                        .col("a", ColumnType.INT)
                        .col("b", ColumnType.INT)
                        .col("t", ColumnType.TIMESTAMP)
        );
    }

    @Test
    public void testOrderByWithSampleBy2() throws SqlException {
        assertQuery(
                "select-group-by a, sum(b) sum from ((select-group-by t, a, sum(b) b from ((tab order by t) _xQdbA3) timestamp (t) sample by 10m) _xQdbA1) order by a",
                "select a, sum(b) from (select a,sum(b) b from (tab order by t) timestamp(t) sample by 10m order by t) order by a",
                modelOf("tab")
                        .col("a", ColumnType.INT)
                        .col("b", ColumnType.INT)
                        .col("t", ColumnType.TIMESTAMP)
        );
    }

    @Test
    public void testOuterJoin() throws Exception {
        assertQuery(
                "select-choose a.x x from (a a outer join b on b.x = a.x)",
                "select a.x from a a outer join b on b.x = a.x",
                modelOf("a").col("x", ColumnType.INT),
                modelOf("b").col("x", ColumnType.INT)
        );
    }

    @Test
    public void testSampleBy() throws Exception {
        assertQuery(
                "select-group-by timestamp, x, avg(y) avg from (tab) timestamp (timestamp) sample by 2m",
                "select x,avg(y) from tab sample by 2m",
                modelOf("tab")
                        .col("x", ColumnType.INT)
                        .col("y", ColumnType.INT)
                        .timestamp()
        );
    }

    @Test
    public void testSampleByAlreadySelected() throws Exception {
        assertQuery(
                "select-group-by x, x1, avg(y) avg from (select-choose x, x x1, y from (tab)) timestamp (x) sample by 2m",
                "select x,avg(y) from tab timestamp(x) sample by 2m",
                modelOf("tab")
                        .col("x", ColumnType.TIMESTAMP)
                        .col("y", ColumnType.INT)
        );
    }

    @Test
    public void testSampleByAltTimestamp() throws Exception {
        assertQuery(
                "select-group-by t, x, avg(y) avg from (tab) timestamp (t) sample by 2m",
                "select x,avg(y) from tab timestamp(t) sample by 2m",
                modelOf("tab")
                        .col("x", ColumnType.INT)
                        .col("y", ColumnType.INT)
                        .col("t", ColumnType.TIMESTAMP)
        );
    }

    @Test
    public void testSampleByIncorrectPlacement() {
        assertSyntaxError(
                "select a, sum(b) from ((tab order by t) timestamp(t) sample by 10m order by t) order by a",
                63,
                "'sample by' must be used with 'select'",
                modelOf("tab")
                        .col("a", ColumnType.INT)
                        .col("b", ColumnType.INT)
                        .col("t", ColumnType.TIMESTAMP)
        );
    }

    @Test
    public void testSampleByInvalidColumn() {
        assertSyntaxError("select x,sum(y) from tab timestamp(z) sample by 2m",
                35,
                "Invalid column",
                modelOf("tab")
                        .col("x", ColumnType.INT)
                        .col("y", ColumnType.INT)
                        .timestamp()
        );
    }

    @Test
    public void testSampleByInvalidType() {
        assertSyntaxError("select x,sum(y) from tab timestamp(x) sample by 2m",
                35,
                "not a TIMESTAMP",
                modelOf("tab")
                        .col("x", ColumnType.INT)
                        .col("y", ColumnType.INT)
                        .timestamp()
        );
    }

    @Test
    public void testSampleByNoAggregate() {
        assertSyntaxError("select x,y from tab sample by 2m", 30, "at least one",
                modelOf("tab")
                        .col("x", ColumnType.INT)
                        .col("y", ColumnType.INT)
                        .timestamp()
        );
    }

    @Test
    public void testSampleByUndefinedTimestamp() {
        assertSyntaxError("select x,sum(y) from tab sample by 2m",
                35,
                "TIMESTAMP column is not defined",
                modelOf("tab")
                        .col("x", ColumnType.INT)
                        .col("y", ColumnType.INT)
        );
    }

    @Test
    public void testSelectAliasAsFunction() {
        assertSyntaxError(
                "select sum(x) x() from tab",
                15,
                "',' or 'from' expected",
                modelOf("tab").col("x", ColumnType.INT)
        );
    }

    @Test
    public void testSelectAnalyticOperator() {
        assertSyntaxError(
                "select sum(x), 2*x over() from tab",
                16,
                "Analytic function expected",
                modelOf("tab").col("x", ColumnType.INT)
        );
    }

    @Test
    public void testSelectFromNonCursorFunction() {
        assertSyntaxError("select * from length('')", 14, "function must return CURSOR");
    }

    @Test
    public void testSelectFromSubQuery() throws SqlException {
        assertQuery(
                "select-choose a.x x from ((tab where y > 10) a)",
                "select a.x from (tab where y > 10) a",
                modelOf("tab")
                        .col("x", ColumnType.INT)
                        .col("y", ColumnType.INT)
        );
    }

    @Test
    public void testSelectGroupByAndAnalytic() {
        assertSyntaxError(
                "select sum(x), count() over() from tab",
                0,
                "Analytic function is not allowed",
                modelOf("tab").col("x", ColumnType.INT)
        );
    }

    @Test
    public void testSelectMissingExpression() {
        assertSyntaxError(
                "select ,a from tab",
                7,
                "missing expression"
        );
    }

    @Test
    public void testSelectMissingExpression2() {
        assertSyntaxError(
                "select a, from tab",
                15,
                "',', 'from' or 'over' expected"
        );
    }

    @Test
    public void testSelectOnItsOwn() {
        assertSyntaxError("select ", 6, "column expected");
    }

    @Test
    public void testSelectPlainColumns() throws Exception {
        assertQuery(
                "select-choose a, b, c from (t)",
                "select a,b,c from t",
                modelOf("t").col("a", ColumnType.INT).col("b", ColumnType.INT).col("c", ColumnType.INT)
        );
    }

    @Test
    public void testSelectSingleExpression() throws Exception {
        assertQuery(
                "select-virtual a + b * c x from (t)",
                "select a+b*c x from t",
                modelOf("t").col("a", ColumnType.INT).col("b", ColumnType.INT).col("c", ColumnType.INT));
    }

    @Test
    public void testSelectWildcard() throws SqlException {
        assertQuery(
                "select-choose" +
                        " tab1.x x," +
                        " tab1.y y," +
                        " tab2.x x1," +
                        " tab2.z z" +
                        " from (tab1 join tab2 on tab2.x = tab1.x)",
                "select * from tab1 join tab2 on (x)",
                modelOf("tab1").col("x", ColumnType.INT).col("y", ColumnType.INT),
                modelOf("tab2").col("x", ColumnType.INT).col("z", ColumnType.INT)
        );
    }

    @Test
    public void testSelectWildcardAndExpr() throws SqlException {
        // todo: Y column is selected twice, code should be able to tell that y and tab1.y is the same column
        assertQuery(
                "select-virtual" +
                        " x," +
                        " y," +
                        " x1," +
                        " z," +
                        " x + y1 column1" +
                        " from (" +
                        "select-choose" +
                        " tab1.x x," +
                        " tab1.y y," +
                        " tab2.x x1," +
                        " tab2.z z," +
                        " y y1" +
                        " from (tab1 join tab2 on tab2.x = tab1.x))",
                "select *, tab1.x + y from tab1 join tab2 on (x)",
                modelOf("tab1").col("x", ColumnType.INT).col("y", ColumnType.INT),
                modelOf("tab2").col("x", ColumnType.INT).col("z", ColumnType.INT)
        );
    }

    @Test
    public void testSelectWildcardDetachedStar() {
        assertSyntaxError(
                "select tab2.*, bxx.  * from tab1 a join tab2 on (x)",
                19,
                "whitespace is not allowed",
                modelOf("tab1").col("x", ColumnType.INT).col("y", ColumnType.INT),
                modelOf("tab2").col("x", ColumnType.INT).col("z", ColumnType.INT)
        );
    }

    @Test
    public void testSelectWildcardInvalidTableAlias() {
        assertSyntaxError(
                "select tab2.*, b.* from tab1 a join tab2 on (x)",
                17,
                "invalid table alias",
                modelOf("tab1").col("x", ColumnType.INT).col("y", ColumnType.INT),
                modelOf("tab2").col("x", ColumnType.INT).col("z", ColumnType.INT)
        );
    }

    @Test
    public void testSelectWildcardMissingStar() {
        assertSyntaxError(
                "select tab2.*, bxx. from tab1 a join tab2 on (x)",
                19,
                "'*' expected",
                modelOf("tab1").col("x", ColumnType.INT).col("y", ColumnType.INT),
                modelOf("tab2").col("x", ColumnType.INT).col("z", ColumnType.INT)
        );
    }

    @Test
    public void testSelectWildcardPrefixed() throws SqlException {
        assertQuery(
                "select-choose" +
                        " tab2.x x," +
                        " tab2.z z," +
                        " tab1.x x1," +
                        " tab1.y y" +
                        " from (tab1 join tab2 on tab2.x = tab1.x)",
                "select tab2.*, tab1.* from tab1 join tab2 on (x)",
                modelOf("tab1").col("x", ColumnType.INT).col("y", ColumnType.INT),
                modelOf("tab2").col("x", ColumnType.INT).col("z", ColumnType.INT)
        );
    }

    @Test
    public void testSelectWildcardPrefixed2() throws SqlException {
        assertQuery(
                "select-choose" +
                        " tab2.x x," +
                        " tab2.z z," +
                        " a.x x1," +
                        " a.y y" +
                        " from (tab1 a join tab2 on tab2.x = a.x)",
                "select tab2.*, a.* from tab1 a join tab2 on (x)",
                modelOf("tab1").col("x", ColumnType.INT).col("y", ColumnType.INT),
                modelOf("tab2").col("x", ColumnType.INT).col("z", ColumnType.INT)
        );
    }

    @Test
    public void testSimpleSubQuery() throws Exception {
        assertQuery(
                "select-choose y from ((x where y > 1) _xQdbA1)",
                "(x) where y > 1",
                modelOf("x").col("y", ColumnType.INT)
        );
    }

    @Test
    public void testSingleTableLimit() throws Exception {
        assertQuery(
                "select-choose x, y from (tab where x > z limit 100)",
                "select x x, y y from tab where x > z limit 100",
                modelOf("tab")
                        .col("x", ColumnType.INT)
                        .col("y", ColumnType.INT)
                        .col("z", ColumnType.INT)
        );
    }

    @Test
    public void testSingleTableLimitLoHi() throws Exception {
        assertQuery(
                "select-choose x, y from (tab where x > z limit 100,200)",
                "select x x, y y from tab where x > z limit 100,200",
                modelOf("tab").col("x", ColumnType.INT).col("y", ColumnType.INT).col("z", ColumnType.INT)
        );
    }

    @Test
    public void testSingleTableLimitLoHiExtraToken() {
        assertSyntaxError("select x x, y y from tab where x > z limit 100,200 b", 51, "unexpected");
    }

    @Test
    public void testSingleTableNoWhereLimit() throws Exception {
        assertQuery(
                "select-choose x, y from (tab limit 100)",
                "select x x, y y from tab limit 100",
                modelOf("tab").col("x", ColumnType.INT).col("y", ColumnType.INT));
    }

    @Test
    public void testSubQuery() throws Exception {
        assertQuery(
                "select-choose x, y from ((select-choose x, y from (tab t2 latest by x where x > 100 and y > 0)) t1)",
                "select x, y from (select x, y from tab t2 latest by x where x > 100) t1 where y > 0",
                modelOf("tab").col("x", ColumnType.INT).col("y", ColumnType.INT)
        );
    }

    @Test
    public void testSubQueryAliasWithSpace() throws Exception {
        assertQuery(
                "select-choose" +
                        " a, x" +
                        " from (" +
                        "(x where a > 1 and x > 1) 'b a')",
                "(x where a > 1) 'b a' where x > 1",
                modelOf("x")
                        .col("x", ColumnType.INT)
                        .col("a", ColumnType.INT));
    }

    @Test
    public void testSubQueryAsArg() throws Exception {
        assertQuery(
                "select-choose customerId from (customers where (select-choose * column from (orders)) > 1)",
                "select * from customers where (select * from orders) > 1",
                modelOf("orders").col("orderId", ColumnType.INT),
                modelOf("customers").col("customerId", ColumnType.INT)
        );
    }

    @Test
    public void testSubQueryLimitLoHi() throws Exception {
        assertQuery(
                "select-choose" +
                        " x," +
                        " y" +
                        " from (" +
                        "(select-choose x, y from (tab where x > z and x = y limit 100,200)) _xQdbA1 limit 150)",
                "(select x x, y y from tab where x > z limit 100,200) where x = y limit 150",
                modelOf("tab").col("x", ColumnType.INT).col("y", ColumnType.INT).col("z", ColumnType.INT)
        );
    }

    @Test
    public void testSubQuerySyntaxError() {
        assertSyntaxError("select x from (select tab. tab where x > 10 t1)", 26, "'*' expected");
    }

    @Test
    public void testTableNameAsArithmetic() {
        assertSyntaxError(
                "select x from 'tab' + 1",
                20,
                "function, literal or constant is expected",
                modelOf("tab").col("x", ColumnType.INT)
        );
    }

    @Test
    public void testTableNameCannotOpen() {
        final FilesFacade ff = new FilesFacadeImpl() {
            @Override
            public long openRO(LPSZ name) {
                if (Chars.endsWith(name, TableUtils.META_FILE_NAME)) {
                    return -1;
                }
                return super.openRO(name);
            }
        };
        CairoConfiguration configuration = new DefaultCairoConfiguration(root) {
            @Override
            public FilesFacade getFilesFacade() {
                return ff;
            }
        };

        CairoEngine engine = new Engine(configuration);
        SqlCompiler compiler = new SqlCompiler(engine, configuration);

        assertSyntaxError(
                compiler,
                engine,
                "select * from tab",
                14,
                "Cannot open file",
                modelOf("tab").col("x", ColumnType.INT)
        );
    }

    @Test
    public void testTableNameJustNoRowidMarker() {
        assertSyntaxError(
                "select * from '*!*'",
                14,
                "come on"
        );
    }

    @Test
    public void testTableNameLocked() {
        engine.lock("tab");
        try {
            assertSyntaxError(
                    "select * from tab",
                    14,
                    "table is locked",
                    modelOf("tab").col("x", ColumnType.INT)
            );
        } finally {
            engine.unlock("tab", null);
        }
    }

    @Test
    public void testTableNameReserved() {
        try (Path path = new Path()) {
            configuration.getFilesFacade().touch(path.of(root).concat("tab").$());
        }

        assertSyntaxError(
                "select * from tab",
                14,
                "table directory is of unknown format"
        );
    }

    @Test
    public void testTableNameWithNoRowidMarker() throws SqlException {
        assertQuery(
                "select-choose x from (*!*tab)",
                "select * from '*!*tab'",
                modelOf("tab").col("x", ColumnType.INT)
        );
    }

    @Test
    public void testTimestampOnSubQuery() throws Exception {
        assertQuery("select-choose x from ((a b where x > y) _xQdbA1 timestamp (x))",
                "select x from (a b) timestamp(x) where x > y",
                modelOf("a").col("x", ColumnType.INT).col("y", ColumnType.INT));
    }

    @Test
    public void testTimestampOnTable() throws Exception {
        assertQuery(
                "select-choose x from (a b timestamp (x) where x > y)",
                "select x from a b timestamp(x) where x > y",
                modelOf("a")
                        .col("x", ColumnType.TIMESTAMP)
                        .col("y", ColumnType.TIMESTAMP));
    }

    @Test
    public void testTooManyColumnsEdgeInOrderBy() throws Exception {
        try (TableModel model = new TableModel(configuration, "x", PartitionBy.NONE)) {
            for (int i = 0; i < SqlParser.MAX_ORDER_BY_COLUMNS - 1; i++) {
                model.col("f" + i, ColumnType.INT);
            }
            CairoTestUtils.create(model);
        }

        StringBuilder b = new StringBuilder();
        b.append("x order by ");
        for (int i = 0; i < SqlParser.MAX_ORDER_BY_COLUMNS - 1; i++) {
            if (i > 0) {
                b.append(',');
            }
            b.append('f').append(i);
        }
        QueryModel st = (QueryModel) sqlCompiler.compileExecutionModel(b, bindVariableService, true);
        Assert.assertEquals(SqlParser.MAX_ORDER_BY_COLUMNS - 1, st.getOrderBy().size());
    }

    @Test
    public void testTooManyColumnsInOrderBy() {
        StringBuilder b = new StringBuilder();
        b.append("x order by ");
        for (int i = 0; i < SqlParser.MAX_ORDER_BY_COLUMNS; i++) {
            if (i > 0) {
                b.append(',');
            }
            b.append('f').append(i);
        }
        try {
            sqlCompiler.compileExecutionModel(b, bindVariableService, true);
        } catch (SqlException e) {
            TestUtils.assertEquals("Too many columns", e.getFlyweightMessage());
        }
    }

    @Test
    public void testTwoAnalyticColumns() throws Exception {
        assertQuery(
                "select-analytic a, b, f(c) my over (partition by b order by ts), d(c) d over () from (xyz)",
                "select a,b, f(c) my over (partition by b order by ts), d(c) over() from xyz",
                modelOf("xyz").col("c", ColumnType.INT).col("b", ColumnType.INT).col("a", ColumnType.INT)
        );
    }

    @Test
    public void testUnbalancedBracketInSubQuery() {
        assertSyntaxError("select x from (tab where x > 10 t1", 32, "expected");
    }

    @Test
    public void testUnderTerminatedOver() {
        assertSyntaxError("select a,b, f(c) my over (partition by b order by ts from xyz", 53, "expected");
    }

    @Test
    public void testUnderTerminatedOver2() {
        assertSyntaxError("select a,b, f(c) my over (partition by b order by ts", 50, "'asc' or 'desc' expected");
    }

    @Test
    public void testUnexpectedTokenInAnalyticFunction() {
        assertSyntaxError("select a,b, f(c) my over (by b order by ts) from xyz", 26, "expected");
    }

    @Test
    public void testWhereClause() throws Exception {
        assertQuery(
                "select-virtual x, sum + 25 ohoh from (select-group-by x, sum(z) sum from (select-virtual a + b * c x, z from (zyzy where in(10,0,a) and b = 10)))",
                "select a+b*c x, sum(z)+25 ohoh from zyzy where a in (0,10) and b = 10",
                modelOf("zyzy")
                        .col("a", ColumnType.INT)
                        .col("b", ColumnType.INT)
                        .col("c", ColumnType.INT)
                        .col("x", ColumnType.INT)
                        .col("y", ColumnType.INT)
                        .col("z", ColumnType.INT)
        );
    }

    @Test
    public void testWithDuplicateName() {
        assertSyntaxError(
                "with x as (tab), x as (tab2) x",
                17,
                "duplicate name",
                modelOf("tab").col("x", ColumnType.INT),
                modelOf("tab2").col("x", ColumnType.INT)
        );
    }

    @Test
    public void testWithSelectFrom() throws SqlException {
        assertQuery(
                "select-choose a from ((select-choose a from (tab)) x)",
                "with x as (" +
                        " select a from tab" +
                        ") select a from x",
                modelOf("tab").col("a", ColumnType.INT)
        );
    }

    @Test
    public void testWithSelectFrom2() throws SqlException {
        assertQuery(
                "select-choose a from ((select-choose a from (tab)) x)",
                "with x as (" +
                        " select a from tab" +
                        ") x",
                modelOf("tab").col("a", ColumnType.INT)
        );
    }

    @Test
    public void testWithSyntaxError() {
        assertSyntaxError(
                "with x as (" +
                        " select ,a from tab" +
                        ") x",
                19,
                "missing expression",
                modelOf("tab").col("a", ColumnType.INT)

        );
    }

    private static void assertSyntaxError(String query, int position, String contains, TableModel... tableModels) {
        assertSyntaxError(sqlCompiler, engine, query, position, contains, tableModels);
    }

    private static void assertSyntaxError(
            SqlCompiler compiler,
            CairoEngine engine,
            String query,
            int position,
            String contains,
            TableModel... tableModels) {
        try {
            for (int i = 0, n = tableModels.length; i < n; i++) {
                CairoTestUtils.create(tableModels[i]);
            }
            compiler.compileExecutionModel(query, bindVariableService, true);
            Assert.fail("Exception expected");
        } catch (SqlException e) {
            Assert.assertEquals(position, e.getPosition());
            TestUtils.assertContains(e.getMessage(), contains);
        } finally {
            Assert.assertTrue(engine.releaseAllReaders());
            for (int i = 0, n = tableModels.length; i < n; i++) {
                TableModel tableModel = tableModels[i];
                Path path = tableModel.getPath().of(tableModel.getCairoCfg().getRoot()).concat(tableModel.getName()).put(Files.SEPARATOR).$();
                Assert.assertTrue(configuration.getFilesFacade().rmdir(path));
                tableModel.close();
            }
        }
    }

    private void assertCreateTable(String expected, String ddl, TableModel... tableModels) throws SqlException {
        createModelsAndRun(() -> {
            ExecutionModel model = sqlCompiler.compileExecutionModel(ddl, bindVariableService, true);
            Assert.assertEquals(ExecutionModel.CREATE_TABLE, model.getModelType());
            Assert.assertTrue(model instanceof CreateTableModel);
            sink.clear();
            ((CreateTableModel) model).toSink(sink);
            TestUtils.assertEquals(expected, sink);
        }, tableModels);
    }

    private void assertQuery(String expected, String query, TableModel... tableModels) throws SqlException {
        createModelsAndRun(() -> {
            sink.clear();
            ExecutionModel model = sqlCompiler.compileExecutionModel(query, bindVariableService, true);
            Assert.assertEquals(model.getModelType(), ExecutionModel.QUERY);
            ((QueryModel) model).toSink(sink);
            TestUtils.assertEquals(expected, sink);
        }, tableModels);
    }

    private void createModelsAndRun(CairoAware runnable, TableModel... tableModels) throws SqlException {
        try {
            for (int i = 0, n = tableModels.length; i < n; i++) {
                CairoTestUtils.create(tableModels[i]);
            }
            runnable.run();
        } finally {
            Assert.assertTrue(engine.releaseAllReaders());
            for (int i = 0, n = tableModels.length; i < n; i++) {
                TableModel tableModel = tableModels[i];
                Path path = tableModel.getPath().of(tableModel.getCairoCfg().getRoot()).concat(tableModel.getName()).put(Files.SEPARATOR).$();
                Assert.assertTrue(configuration.getFilesFacade().rmdir(path));
                tableModel.close();
            }
        }
    }

    private TableModel modelOf(String tableName) {
        return new TableModel(configuration, tableName, PartitionBy.NONE);
    }

    @FunctionalInterface
    private interface CairoAware {
        void run() throws SqlException;
    }
}
