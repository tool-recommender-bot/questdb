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

import com.questdb.cairo.CairoConfiguration;
import com.questdb.cairo.FullTableFrameCursorFactory;
import com.questdb.cairo.IntervalFrameCursorFactory;
import com.questdb.cairo.TableReader;
import com.questdb.cairo.sql.CairoEngine;
import com.questdb.cairo.sql.DataFrameCursorFactory;
import com.questdb.cairo.sql.RecordCursorFactory;
import com.questdb.cairo.sql.RowCursorFactory;
import com.questdb.common.ColumnType;
import com.questdb.common.RecordColumnMetadata;
import com.questdb.common.RecordMetadata;
import com.questdb.griffin.common.ExprNode;
import com.questdb.griffin.engine.functions.Parameter;
import com.questdb.griffin.engine.table.FilteredTableRecordCursorFactory;
import com.questdb.griffin.engine.table.SymbolIndexFilteredRowCursorFactory;
import com.questdb.griffin.engine.table.SymbolIndexRowCursorFactory;
import com.questdb.griffin.lexer.ParserException;
import com.questdb.griffin.lexer.SqlLexerOptimiser;
import com.questdb.griffin.lexer.model.ExecutionModel;
import com.questdb.griffin.lexer.model.IntrinsicModel;
import com.questdb.griffin.lexer.model.IntrinsicValue;
import com.questdb.griffin.lexer.model.QueryModel;
import com.questdb.std.CharSequenceObjHashMap;

import java.util.ServiceLoader;

public class SqlParser {
    private final SqlLexerOptimiser sqlLexer;
    private final WhereClauseParser filterAnalyser = new WhereClauseParser();
    private final FunctionParser functionParser;
    private final CairoEngine engine;

    public SqlParser(CairoEngine engine, CairoConfiguration configuration) {
        this.engine = engine;
        this.sqlLexer = new SqlLexerOptimiser(engine, configuration);
        this.functionParser = new FunctionParser(configuration, ServiceLoader.load(FunctionFactory.class));
    }

    public RecordCursorFactory parseQuery(CharSequence query) throws ParserException {
        return parse(sqlLexer.parse(query));
    }

    private void clearState() {
        // todo: clear
    }

    private RecordCursorFactory parse(ExecutionModel model) throws ParserException {
        if (model.getModelType() == ExecutionModel.QUERY) {
            clearState();
            return parseQuery((QueryModel) model);
        }
        throw new IllegalArgumentException("QueryModel expected");
    }

    private RecordCursorFactory parseNoSelect(QueryModel model) throws ParserException {
        if (model.getTableName() != null) {
            return parseTableQuery(model);

        }
        assert model.getNestedModel() != null;
        return parseQuery(model.getNestedModel());
    }

    private RecordCursorFactory parseQuery(QueryModel model) throws ParserException {
        switch (model.getSelectModelType()) {
            case QueryModel.SELECT_MODEL_CHOOSE:
                return parseSelectChoose(model);
            case QueryModel.SELECT_MODEL_GROUP_BY:
                return parseSelectGroupBy(model);
            case QueryModel.SELECT_MODEL_VIRTUAL:
                return parseSelectVirtual(model);
            case QueryModel.SELECT_MODEL_ANALYTIC:
                return parseSelectAnalytic(model);
            default:
                return parseNoSelect(model);
        }
    }

    private RecordCursorFactory parseSelectAnalytic(QueryModel model) throws ParserException {
        assert model.getNestedModel() != null;
        return parseQuery(model.getNestedModel());
    }

    private RecordCursorFactory parseSelectChoose(QueryModel model) throws ParserException {
        assert model.getNestedModel() != null;
        return parseQuery(model.getNestedModel());
    }

    private RecordCursorFactory parseSelectGroupBy(QueryModel model) throws ParserException {
        assert model.getNestedModel() != null;
        return parseQuery(model.getNestedModel());
    }

    private RecordCursorFactory parseSelectVirtual(QueryModel model) throws ParserException {
        assert model.getNestedModel() != null;
        return parseQuery(model.getNestedModel());
    }

    @SuppressWarnings("ConstantConditions")
    private RecordCursorFactory parseTableQuery(QueryModel model) throws ParserException {

//        applyLimit(model);

        final ExprNode latestBy = model.getLatestBy();
        final ExprNode whereClause = model.getWhereClause();

        try (TableReader reader = engine.getReader(model.getTableName().token)) {
            if (whereClause != null) {

                final RecordMetadata metadata = reader.getMetadata();
                final int timestampIndex;

                ExprNode timestamp = model.getTimestamp();
                if (timestamp != null) {
                    timestampIndex = metadata.getColumnIndex(timestamp.token);
                } else {
                    timestampIndex = -1;
                }

                final IntrinsicModel intrinsicModel = filterAnalyser.extract(model, whereClause, reader.getMetadata(), latestBy != null ? latestBy.token : null, timestampIndex);

                if (intrinsicModel.intrinsicValue == IntrinsicValue.FALSE) {
                    // todo: return empty factory
                    return null;
                }

                Function filter;

                CharSequenceObjHashMap<Parameter> parameterMap = new CharSequenceObjHashMap<>();
                if (intrinsicModel.filter != null) {
                    filter = functionParser.parseFunction(intrinsicModel.filter, metadata, parameterMap);
                } else {
                    filter = null;
                }

                // validate filter
                if (filter != null) {
                    if (filter.getType() != ColumnType.BOOLEAN) {
                        throw ParserException.$(intrinsicModel.filter.position, "Boolean expression expected");
                    }

                    if (filter.isConstant()) {
                        // can pass null to constant function
                        if (filter.getBool(null)) {
                            // filter is constant "true", do not evaluate for every row
                            filter = null;
                        } else {
                            //todo: return factory, which would create empty record cursor for the given table
                            return null;
                        }
                    }
                }

                DataFrameCursorFactory dfcFactory;
                if (intrinsicModel.intervals != null) {
                    dfcFactory = new IntervalFrameCursorFactory(engine, model.getTableName().token.toString(), intrinsicModel.intervals);
                } else {
                    dfcFactory = new FullTableFrameCursorFactory(engine, model.getTableName().token.toString());
                }

                if (latestBy != null) {
                    // this is everything "latest by"

                    // first check if column is valid
                    int latestByIndex = metadata.getColumnIndex(latestBy.token);
                    RecordColumnMetadata latestByMeta = metadata.getColumnQuick(latestByIndex);
                    if (latestByMeta.getType() != ColumnType.SYMBOL) {
                        throw ParserException.$(latestBy.position, "has to be SYMBOL");
                    }

                    if (intrinsicModel.keyColumn != null) {
                        // we also have key lookup, is the the same column as "latest by"
                        // note: key column is always indexed
                        int keyColumnIndex = metadata.getColumnIndex(intrinsicModel.keyColumn);

                        if (keyColumnIndex == latestByIndex) {
                            // we somewhat in luck
                            if (intrinsicModel.keyValuesIsLambda) {
                                // treat key values as lambda
                                // 1. get lambda cursor
                                // 2. for each value of first column of lambda: resolve to "int" of symbol, find first row in index
                                assert intrinsicModel.keyValues.size() == 1;
                            } else {
                                assert intrinsicModel.keyValues != null && intrinsicModel.keyValues.size() > 0;
                                // deal with key values as a list
                                // 1. resolve each value of the list to "int"
                                // 2. get first row in index for each value (stream)
                            }
                        } else {
                            // this could only happen when "latest by" is not indexed
                            // this is because "latest by" is preferred key column for filter analyzer
                            // and filter analyzer always picks indexed column as key
                            if (intrinsicModel.keyValuesIsLambda) {
                                assert intrinsicModel.keyValues.size() == 1;
                            } else {
                                assert intrinsicModel.keyValues.size() > 0;
                            }
                        }
                    } else {
                        assert intrinsicModel.keyValues.size() == 0;
                        // get latest rows for all values of "latest by" column
                    }
                } else {
                    // no "latest by" clause
                    if (intrinsicModel.keyColumn != null) {
                        if (intrinsicModel.keyValuesIsLambda) {
                            // perform lambda based key lookup
                            assert intrinsicModel.keyValues.size() == 1;
                        } else {
                            assert intrinsicModel.keyValues.size() > 0;
                            if (intrinsicModel.keyValues.size() == 1) {
                                if (filter == null) {
                                    RowCursorFactory rcf = new SymbolIndexRowCursorFactory(
                                            engine,
                                            model.getTableName().token,
                                            intrinsicModel.keyColumn,
                                            intrinsicModel.keyValues.get(0));
                                    return new FilteredTableRecordCursorFactory(dfcFactory, rcf);
                                } else {
                                    RowCursorFactory rcf = new SymbolIndexFilteredRowCursorFactory(
                                            engine,
                                            model.getTableName().token,
                                            intrinsicModel.keyColumn,
                                            intrinsicModel.keyValues.get(0),
                                            filter
                                    );
                                    return new FilteredTableRecordCursorFactory(dfcFactory, rcf);
                                }
                            } else {
                                // multiple key values
                                if (filter == null) {
                                    // without filter
                                } else {
                                    // with filter
                                }
                            }
                        }
                    }
                }

                // after we dealt with "latest by" clause and key lookups we must apply filter if we have one
                // NOTE! when "latest by" is present filter must be applied *before* latest by is evaluated
                if (filter != null) {
                    // apply filter
                }

                return null;

            }
        }
        return null;
    }
}
