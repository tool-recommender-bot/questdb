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
import com.questdb.cairo.sql.*;
import com.questdb.griffin.engine.functions.columns.SymbolColumn;
import com.questdb.griffin.engine.groupby.*;
import com.questdb.griffin.engine.join.HashJoinRecordCursorFactory;
import com.questdb.griffin.engine.join.JoinRecordMetadata;
import com.questdb.griffin.engine.orderby.RecordComparatorCompiler;
import com.questdb.griffin.engine.orderby.SortedLightRecordCursorFactory;
import com.questdb.griffin.engine.orderby.SortedRecordCursorFactory;
import com.questdb.griffin.engine.table.*;
import com.questdb.griffin.model.*;
import com.questdb.std.*;
import org.jetbrains.annotations.NotNull;

public class SqlCodeGenerator {
    private final WhereClauseParser filterAnalyser = new WhereClauseParser();
    private final FunctionParser functionParser;
    private final CairoEngine engine;
    private final BytecodeAssembler asm = new BytecodeAssembler();
    // this list is used to generate record sinks
    private final ListColumnFilter listColumnFilter = new ListColumnFilter();
    private final SingleColumnType singleColumnType = new SingleColumnType();
    private final CairoConfiguration configuration;
    private final RecordComparatorCompiler recordComparatorCompiler;
    private final IntHashSet intHashSet = new IntHashSet();
    private final ArrayColumnTypes keyTypes = new ArrayColumnTypes();
    private final ArrayColumnTypes valueTypes = new ArrayColumnTypes();
    private final EntityColumnFilter entityColumnFilter = new EntityColumnFilter();

    public SqlCodeGenerator(CairoEngine engine, CairoConfiguration configuration, FunctionParser functionParser) {
        this.engine = engine;
        this.configuration = configuration;
        this.functionParser = functionParser;
        this.recordComparatorCompiler = new RecordComparatorCompiler(asm);
    }

    private void clearState() {
        // todo: clear
    }

    private RecordSink compileRecordSink(ObjList<ExpressionNode> columnNames, RecordMetadata masterMetadata) {
        listColumnFilter.clear();
        for (int i = 0, n = columnNames.size(); i < n; i++) {
            listColumnFilter.add(masterMetadata.getColumnIndex(columnNames.getQuick(i).token));
        }

        return RecordSinkFactory.getInstance(
                asm,
                masterMetadata,
                listColumnFilter,
                false
        );
    }

    private RecordMetadata copyMetadata(RecordMetadata that) {
        // todo: this metadata is immutable. Ideally we shouldn't be creating metadata for the same table over and over
        return GenericRecordMetadata.copyOf(that);
    }

    private RecordCursorFactory createHashJoin(
            QueryModel model,
            RecordCursorFactory master,
            CharSequence masterAlias,
            RecordCursorFactory slave,
            CharSequence slaveAlias
    ) {
        final JoinContext jc = model.getContext();
        final RecordMetadata masterMetadata = master.getMetadata();
        final RecordMetadata slaveMetadata = slave.getMetadata();
        final RecordSink masterSink = compileRecordSink(jc.bNodes, masterMetadata);
        final RecordSink slaveSink = compileRecordSink(jc.aNodes, slaveMetadata);

        for (int i = 0, n = jc.aNodes.size(); i < n; i++) {
            keyTypes.add(slaveMetadata.getColumnType(jc.aNodes.getQuick(i).token));
        }

        JoinRecordMetadata m = new JoinRecordMetadata(
                masterMetadata.getColumnCount() + slaveMetadata.getColumnCount()
        );

        m.copyColumnMetadataFrom(masterAlias, masterMetadata);
        m.copyColumnMetadataFrom(slaveAlias, slaveMetadata);

        valueTypes.reset();
        valueTypes.add(ColumnType.LONG);
        return new HashJoinRecordCursorFactory(
                configuration,
                m,
                master,
                slave,
                keyTypes,
                valueTypes,
                masterSink,
                slaveSink,
                masterMetadata.getColumnCount()
        );
    }

    RecordCursorFactory generate(QueryModel model, SqlExecutionContext executionContext) throws SqlException {
        clearState();
        return generateQuery(model, executionContext, true);
    }

    private RecordCursorFactory generateFunctionQuery(QueryModel model) throws SqlException {
        final Function function = model.getTableNameFunction();
        assert function != null;
        if (function.getType() != TypeEx.CURSOR) {
            throw SqlException.position(model.getTableName().position).put("function must return CURSOR [actual=").put(ColumnType.nameOf(function.getType())).put(']');
        }

        return function.getRecordCursorFactory();
    }

    private RecordCursorFactory generateJoins(QueryModel model, SqlExecutionContext executionContext) throws SqlException {


        // very cool, we have columns with 'model' and join semantics are within 'nestedModel'
        // before we crack on we go ahead and compile join models


        final ObjList<QueryModel> joinModels = model.getJoinModels();
/*
        final int joinModelsSize = joinModels.size();
        ObjList<RecordCursorFactory> factories = new ObjList<>(joinModelsSize);

        for (int i = 0; i < joinModelsSize; i++) {
            factories.add(generateQuery(joinModels.getQuick(i), executionContext));
        }

        final GenericRecordMetadata mm = new GenericRecordMetadata();
        // column pointer has two components - factory index and column index.
        // each of these are INTs but we will store them as single packed long for now
        final LongList columnPointers = new LongList();
        final ObjList<QueryColumn> selectColumns = model.getColumns();
        final int selectColumnSize = selectColumns.size();
        assert selectColumnSize > 0;


        for (int i = 0; i < selectColumnSize; i++) {
            CharSequence column = selectColumns.getQuick(i).getAst().token;

            int dot = Chars.indexOf(column, '.');
            int aliasIndex = -1;
            int columnIndex = -1;

            if (dot == -1) {
                for (int k = 0; k < factories.size(); k++) {
                    RecordMetadata metadata = factories.getQuick(k).getMetadata();
                    columnIndex = metadata.getColumnIndexQuiet(column);
                    if (columnIndex != -1) {
                        aliasIndex = k;
                        break;
                    }
                }
            } else {
                aliasIndex = nested.getAliasIndex(column, 0, dot);
                assert aliasIndex != -1;
                // todo: add method that looks up column based on a part of char sequence
                columnIndex = factories.getQuick(aliasIndex).getMetadata().getColumnIndex(Chars.stringOf(column).substring(dot + 1));
            }

            assert aliasIndex != -1;
            assert columnIndex != -1;

            columnPointers.add((((long) aliasIndex) << 32) | columnIndex);

            RecordMetadata fm = factories.getQuick(aliasIndex).getMetadata();
            mm.add(new TableColumnMetadata(
                    Chars.stringOf(fm.getColumnName(columnIndex)),
                    fm.getColumnType(columnIndex)
            ));
        }


//        final ObjList<QueryModel> joinModels = model.getJoinModels();
*/
        IntList ordered = model.getOrderedJoinModels();
        RecordCursorFactory master = null;
        CharSequence masterAlias = null;

        try {
            for (int i = 0, n = ordered.size(); i < n; i++) {
                int index = ordered.getQuick(i);
                QueryModel m = joinModels.getQuick(index);

                // compile
                RecordCursorFactory slave = generateQuery(m, executionContext, i > 0);

                // check if this is the root of joins
                if (master == null) {
                    // This is an opportunistic check of order by clause
                    // to determine if we can get away ordering main record source only
                    // Ordering main record source could benefit from rowid access thus
                    // making it faster compared to ordering of join record source that
                    // doesn't allow rowid access.
                    master = slave;
                    masterAlias = m.getName();
                } else {
                    // not the root, join to "master"
                    switch (m.getJoinType()) {
                        case QueryModel.JOIN_CROSS:
                            assert false;
                            break;
                        case QueryModel.JOIN_ASOF:
                            assert false;
                            break;
                        default:
                            master = createHashJoin(m, master, masterAlias, slave, m.getName());
                            masterAlias = null;
                            break;
                    }
                }

                // check if there are post-filters
                ExpressionNode filter = m.getPostJoinWhereClause();
                if (filter != null) {
                    master = new FilteredRecordCursorFactory(master, functionParser.parseFunction(filter, master.getMetadata(), executionContext));
                }
            }

            // unfortunately we had to go all out to create join metadata
            // now it is time to check if we have constant conditions


            ExpressionNode constFilter = model.getConstWhereClause();
            if (constFilter != null) {
                Function function = functionParser.parseFunction(constFilter, null, executionContext);
                if (!function.getBool(null)) {
                    return new EmptyTableRecordCursorFactory(master.getMetadata());
                }
            }
            return master;
        } catch (CairoException e) {
            Misc.free(master);
            throw e;
        }
    }

    @NotNull
    private RecordCursorFactory generateLatestByQuery(
            QueryModel model,
            TableReader reader,
            RecordMetadata metadata,
            int latestByIndex,
            String tableName,
            IntrinsicModel intrinsicModel,
            Function filter,
            SqlExecutionContext executionContext) throws SqlException {
        final boolean indexed = metadata.isColumnIndexed(latestByIndex);
        final DataFrameCursorFactory dataFrameCursorFactory;
        if (intrinsicModel.intervals != null) {
            dataFrameCursorFactory = new IntervalBwdDataFrameCursorFactory(engine, tableName, model.getTableVersion(), intrinsicModel.intervals);
        } else {
            dataFrameCursorFactory = new FullBwdDataFrameCursorFactory(engine, tableName, model.getTableVersion());
        }

        if (intrinsicModel.keyColumn != null) {
            // key column must always be the same as latest by column
            assert latestByIndex == metadata.getColumnIndexQuiet(intrinsicModel.keyColumn);

            if (intrinsicModel.keySubQuery != null) {

                final RecordCursorFactory rcf = generate(intrinsicModel.keySubQuery, executionContext);
                final int firstColumnType = validateSubQueryColumnAndGetType(intrinsicModel, rcf.getMetadata());

                return new LatestBySubQueryRecordCursorFactory(
                        configuration,
                        metadata,
                        dataFrameCursorFactory,
                        latestByIndex,
                        rcf,
                        filter,
                        indexed,
                        firstColumnType
                );
            }

            final int nKeyValues = intrinsicModel.keyValues.size();
            if (indexed) {

                assert nKeyValues > 0;
                // deal with key values as a list
                // 1. resolve each value of the list to "int"
                // 2. get first row in index for each value (stream)

                final SymbolMapReader symbolMapReader = reader.getSymbolMapReader(latestByIndex);
                final RowCursorFactory rcf;
                if (nKeyValues == 1) {
                    final CharSequence symbolValue = intrinsicModel.keyValues.get(0);
                    final int symbol = symbolMapReader.getQuick(symbolValue);

                    if (filter == null) {
                        if (symbol == SymbolTable.VALUE_NOT_FOUND) {
                            rcf = new LatestByValueDeferredIndexedRowCursorFactory(latestByIndex, Chars.toString(symbolValue), false);
                        } else {
                            rcf = new LatestByValueIndexedRowCursorFactory(latestByIndex, symbol, false);
                        }
                        return new DataFrameRecordCursorFactory(copyMetadata(metadata), dataFrameCursorFactory, rcf, null);
                    }

                    if (symbol == SymbolTable.VALUE_NOT_FOUND) {
                        return new LatestByValueDeferredIndexedFilteredRecordCursorFactory(
                                copyMetadata(metadata),
                                dataFrameCursorFactory,
                                latestByIndex,
                                Chars.toString(symbolValue),
                                filter);
                    }
                    return new LatestByValueIndexedFilteredRecordCursorFactory(
                            copyMetadata(metadata),
                            dataFrameCursorFactory,
                            latestByIndex,
                            symbol,
                            filter);
                }

                return new LatestByValuesIndexedFilteredRecordCursorFactory(
                        configuration,
                        copyMetadata(metadata),
                        dataFrameCursorFactory,
                        latestByIndex,
                        intrinsicModel.keyValues,
                        symbolMapReader,
                        filter
                );
            }

            assert nKeyValues > 0;

            // we have "latest by" column values, but no index
            final SymbolMapReader symbolMapReader = reader.getSymbolMapReader(latestByIndex);

            if (nKeyValues > 1) {
                return new LatestByValuesFilteredRecordCursorFactory(
                        configuration,
                        copyMetadata(metadata),
                        dataFrameCursorFactory,
                        latestByIndex,
                        intrinsicModel.keyValues,
                        symbolMapReader,
                        filter
                );
            }

            // we have a single symbol key
            int symbolKey = symbolMapReader.getQuick(intrinsicModel.keyValues.get(0));
            if (symbolKey == SymbolTable.VALUE_NOT_FOUND) {
                return new LatestByValueDeferredFilteredRecordCursorFactory(
                        copyMetadata(metadata),
                        dataFrameCursorFactory,
                        latestByIndex,
                        Chars.toString(intrinsicModel.keyValues.get(0)),
                        filter
                );
            }

            return new LatestByValueFilteredRecordCursorFactory(copyMetadata(metadata), dataFrameCursorFactory, latestByIndex, symbolKey, filter);
        }
        // we select all values of "latest by" column

        assert intrinsicModel.keyValues.size() == 0;
        // get latest rows for all values of "latest by" column

        if (indexed) {
            return new LatestByAllIndexedFilteredRecordCursorFactory(
                    configuration,
                    copyMetadata(metadata),
                    dataFrameCursorFactory,
                    latestByIndex,
                    filter);
        }

        listColumnFilter.clear();
        listColumnFilter.add(latestByIndex);
        return new LatestByAllFilteredRecordCursorFactory(
                copyMetadata(metadata),
                configuration,
                dataFrameCursorFactory,
                RecordSinkFactory.getInstance(asm, metadata, listColumnFilter, false),
                singleColumnType.of(metadata.getColumnType(latestByIndex)),
                filter
        );
    }

    private RecordCursorFactory generateNoSelect(QueryModel model, SqlExecutionContext executionContext) throws SqlException {
        ExpressionNode tableName = model.getTableName();
        if (tableName != null) {
            if (tableName.type == ExpressionNode.FUNCTION) {
                return generateFunctionQuery(model);
            } else {
                return generateTableQuery(model, executionContext);
            }

        }
        return generateSubQuery(model, executionContext);
    }

    private RecordCursorFactory generateOrderBy(RecordCursorFactory recordCursorFactory, QueryModel model) throws SqlException {
        try {
            final ObjList<ExpressionNode> orderBy = model.getOrderBy();
            final int size = orderBy.size();

            if (size > 0) {

                final RecordMetadata metadata = recordCursorFactory.getMetadata();
                final IntList orderByDirection = model.getOrderByDirection();
                listColumnFilter.clear();
                intHashSet.clear();

                // column index sign indicates direction
                // therefore 0 index is not allowed
                for (int i = 0; i < size; i++) {
                    ExpressionNode node = orderBy.getQuick(i);
                    int index = metadata.getColumnIndexQuiet(node.token);

                    // check if column type is supported
                    if (metadata.getColumnType(index) == ColumnType.BINARY) {
                        throw SqlException.$(node.position, "unsupported column type: ").put(ColumnType.nameOf(metadata.getColumnType(index)));
                    }

                    // we also maintain unique set of column indexes for better performance
                    if (intHashSet.add(index)) {
                        if (orderByDirection.getQuick(i) == QueryModel.ORDER_DIRECTION_DESCENDING) {
                            listColumnFilter.add(-index - 1);
                        } else {
                            listColumnFilter.add(index + 1);
                        }
                    }
                }

                // if first column index is the same as timestamp of underling record cursor factory
                // we could have two possibilities:
                // 1. if we only have one column to order by - the cursor would already be ordered
                //    by timestamp; we have nothing to do
                // 2. metadata of the new cursor will have timestamp

                RecordMetadata orderedMetadata;
                if (metadata.getTimestampIndex() == -1) {
                    orderedMetadata = GenericRecordMetadata.copyOfSansTimestamp(metadata);
                } else {
                    int index = metadata.getColumnIndexQuiet(orderBy.getQuick(0).token);
                    if (index == metadata.getTimestampIndex()) {

                        if (size == 1) {
                            return recordCursorFactory;
                        }

                        orderedMetadata = copyMetadata(metadata);

                    } else {
                        orderedMetadata = GenericRecordMetadata.copyOfSansTimestamp(metadata);
                    }
                }

                if (recordCursorFactory.isRandomAccessCursor()) {
                    return new SortedLightRecordCursorFactory(
                            configuration,
                            orderedMetadata,
                            recordCursorFactory,
                            recordComparatorCompiler.compile(metadata, listColumnFilter)
                    );
                }

                // when base record cursor does not support random access
                // we have to copy entire record into ordered structure

                entityColumnFilter.of(orderedMetadata.getColumnCount());

                return new SortedRecordCursorFactory(
                        configuration,
                        orderedMetadata,
                        recordCursorFactory,
                        orderedMetadata,
                        RecordSinkFactory.getInstance(
                                asm,
                                orderedMetadata,
                                entityColumnFilter,
                                false
                        ),
                        recordComparatorCompiler.compile(metadata, listColumnFilter)
                );
            }

            return recordCursorFactory;
        } catch (SqlException | CairoException e) {
            recordCursorFactory.close();
            throw e;
        }
    }

    private RecordCursorFactory generateQuery(QueryModel model, SqlExecutionContext executionContext, boolean processJoins) throws SqlException {
        return generateOrderBy(generateSelect(model, executionContext, processJoins), model);
    }

    @NotNull
    private RecordCursorFactory generateSampleBy(QueryModel model, SqlExecutionContext executionContext, ExpressionNode sampleByNode) throws SqlException {
        final RecordCursorFactory factory = generateSubQuery(model, executionContext);
        final ObjList<ExpressionNode> sampleByFill = model.getSampleByFill();
        final TimestampSampler timestampSampler = TimestampSamplerFactory.getInstance(sampleByNode.token, sampleByNode.position);

        assert model.getNestedModel() != null;
        final int fillCount = sampleByFill.size();
        try {
            keyTypes.reset();
            valueTypes.reset();
            listColumnFilter.clear();

            if (fillCount == 0 || fillCount == 1 && Chars.equals(sampleByFill.getQuick(0).token, "none")) {
                return new SampleByFillNoneRecordCursorFactory(
                        configuration,
                        factory,
                        timestampSampler,
                        model,
                        listColumnFilter,
                        functionParser,
                        executionContext,
                        asm,
                        keyTypes,
                        valueTypes
                );
            }


            if (fillCount == 1 && Chars.equals(sampleByFill.getQuick(0).token, "prev")) {
                return new SampleByFillPrevRecordCursorFactory(
                        configuration,
                        factory,
                        timestampSampler,
                        model,
                        listColumnFilter,
                        functionParser,
                        executionContext,
                        asm,
                        keyTypes,
                        valueTypes
                );
            }

            if (fillCount == 1 && Chars.equals(sampleByFill.getQuick(0).token, "null")) {
                return new SampleByFillNullRecordCursorFactory(
                        configuration,
                        factory,
                        timestampSampler,
                        model,
                        listColumnFilter,
                        functionParser,
                        executionContext,
                        asm,
                        keyTypes,
                        valueTypes
                );
            }

            if (fillCount == 1 && Chars.equals(sampleByFill.getQuick(0).token, "linear")) {
                return new SampleByInterpolateRecordCursorFactory(
                        configuration,
                        factory,
                        timestampSampler,
                        model,
                        listColumnFilter,
                        functionParser,
                        executionContext,
                        asm,
                        keyTypes,
                        valueTypes,
                        entityColumnFilter
                );
            }

            assert fillCount > 0;

            return new SampleByFillValueRecordCursorFactory(
                    configuration,
                    factory,
                    timestampSampler,
                    model,
                    listColumnFilter,
                    functionParser,
                    executionContext,
                    asm,
                    sampleByFill,
                    keyTypes,
                    valueTypes
            );
        } catch (SqlException | CairoException e) {
            factory.close();
            throw e;
        }
    }

    private RecordCursorFactory generateSelect(QueryModel model, SqlExecutionContext executionContext, boolean processJoins) throws SqlException {
        switch (model.getSelectModelType()) {
            case QueryModel.SELECT_MODEL_CHOOSE:
                return generateSelectChoose(model, executionContext);
            case QueryModel.SELECT_MODEL_GROUP_BY:
                return generateSelectGroupBy(model, executionContext);
            case QueryModel.SELECT_MODEL_VIRTUAL:
                return generateSelectVirtual(model, executionContext);
            case QueryModel.SELECT_MODEL_ANALYTIC:
                return generateSelectAnalytic(model, executionContext);
            default:
                if (model.getJoinModels().size() > 1 && processJoins) {
                    return generateJoins(model, executionContext);
                }
                return generateNoSelect(model, executionContext);
        }
    }

    private RecordCursorFactory generateSelectAnalytic(QueryModel model, SqlExecutionContext executionContext) throws SqlException {
        return generateSubQuery(model, executionContext);
    }

    private RecordCursorFactory generateSelectChoose(QueryModel model, SqlExecutionContext executionContext) throws SqlException {
        assert model.getNestedModel() != null;
        final RecordCursorFactory factory = generateSubQuery(model, executionContext);
        final RecordMetadata metadata = factory.getMetadata();
        final int selectColumnCount = model.getColumns().size();
        final ExpressionNode timestamp = model.getTimestamp();

        boolean entity;
        // the model is considered entity when it doesn't add any value to its nested model
        //
        if (timestamp == null && metadata.getColumnCount() == selectColumnCount) {
            entity = true;
            for (int i = 0; i < selectColumnCount; i++) {
                if (!Chars.equals(metadata.getColumnName(i), model.getColumns().getQuick(i).getAst().token)) {
                    entity = false;
                    break;
                }
            }
        } else {
            entity = false;
        }

        if (entity) {
            return factory;
        }

        IntList columnCrossIndex = new IntList(selectColumnCount);
        GenericRecordMetadata selectMetadata = new GenericRecordMetadata();
        final int timestampIndex;
        if (timestamp == null) {
            timestampIndex = metadata.getTimestampIndex();
        } else {
            timestampIndex = metadata.getColumnIndex(timestamp.token);
        }
        for (int i = 0; i < selectColumnCount; i++) {
            int index = metadata.getColumnIndexQuiet(model.getColumns().getQuick(i).getAst().token);
            assert index > -1 : "wtf? " + model.getColumns().getQuick(i).getAst().token;
            columnCrossIndex.add(index);

            selectMetadata.add(new TableColumnMetadata(
                    Chars.toString(model.getColumns().getQuick(i).getName()),
                    metadata.getColumnType(index),
                    metadata.isColumnIndexed(index),
                    metadata.getIndexValueBlockCapacity(index)
            ));

            if (index == timestampIndex) {
                selectMetadata.setTimestampIndex(i);
            }
        }

        return new SelectedRecordCursorFactory(selectMetadata, columnCrossIndex, factory);
    }

    private RecordCursorFactory generateSelectGroupBy(QueryModel model, SqlExecutionContext executionContext) throws SqlException {

        // fail fast if we cannot create timestamp sampler

        final ExpressionNode sampleByNode = model.getSampleBy();
        if (sampleByNode != null) {
            return generateSampleBy(model, executionContext, sampleByNode);
        }

        final RecordCursorFactory factory = generateSubQuery(model, executionContext);
        try {
            keyTypes.reset();
            valueTypes.reset();
            listColumnFilter.clear();

            return new GroupByRecordCursorFactory(
                    configuration,
                    factory,
                    model,
                    listColumnFilter,
                    functionParser,
                    executionContext,
                    asm,
                    keyTypes,
                    valueTypes
            );

        } catch (CairoException e) {
            factory.close();
            throw e;
        }
    }

    private RecordCursorFactory generateSelectVirtual(QueryModel model, SqlExecutionContext executionContext) throws SqlException {
        assert model.getNestedModel() != null;
        final RecordCursorFactory factory = generateSubQuery(model, executionContext);

        try {
            final int columnCount = model.getColumns().size();
            final RecordMetadata metadata = factory.getMetadata();
            final ObjList<Function> functions = new ObjList<>(columnCount);
            final GenericRecordMetadata virtualMetadata = new GenericRecordMetadata();

            // attempt to preserve timestamp on new data set
            CharSequence timestampColumn;
            final int timestampIndex = metadata.getTimestampIndex();
            if (timestampIndex > -1) {
                timestampColumn = metadata.getColumnName(timestampIndex);
            } else {
                timestampColumn = null;
            }

            IntList symbolTableCrossIndex = null;

            for (int i = 0; i < columnCount; i++) {
                final QueryColumn column = model.getColumns().getQuick(i);
                ExpressionNode node = column.getAst();
                if (timestampColumn != null && node.type == ExpressionNode.LITERAL && Chars.equals(timestampColumn, node.token)) {
                    virtualMetadata.setTimestampIndex(i);
                }

                final Function function = functionParser.parseFunction(
                        column.getAst(),
                        metadata,
                        executionContext
                );
                functions.add(function);


                virtualMetadata.add(new TableColumnMetadata(
                        Chars.toString(column.getAlias()),
                        function.getType()
                ));

                if (function instanceof SymbolColumn) {
                    if (symbolTableCrossIndex == null) {
                        symbolTableCrossIndex = new IntList(columnCount);
                    }
                    symbolTableCrossIndex.extendAndSet(i, ((SymbolColumn) function).getColumnIndex());
                }
            }

            return new VirtualRecordCursorFactory(virtualMetadata, functions, factory, symbolTableCrossIndex);
        } catch (SqlException | CairoException e) {
            factory.close();
            throw e;
        }
    }

    private RecordCursorFactory generateSubQuery(QueryModel model, SqlExecutionContext executionContext) throws SqlException {
        assert model.getNestedModel() != null;
        return generateQuery(model.getNestedModel(), executionContext, true);
    }

    @SuppressWarnings("ConstantConditions")
    private RecordCursorFactory generateTableQuery(QueryModel model, SqlExecutionContext executionContext) throws SqlException {

//        applyLimit(model);

        final ExpressionNode latestBy = model.getLatestBy();
        final ExpressionNode whereClause = model.getWhereClause();

        try (TableReader reader = engine.getReader(model.getTableName().token, model.getTableVersion())) {
            final RecordMetadata metadata = reader.getMetadata();

            final int latestByIndex;
            if (latestBy != null) {
                // validate latest by against current reader
                // first check if column is valid
                latestByIndex = metadata.getColumnIndexQuiet(latestBy.token);
                if (latestByIndex == -1) {
                    throw SqlException.invalidColumn(latestBy.position, latestBy.token);
                }
            } else {
                latestByIndex = -1;
            }

            final String tableName = Chars.toString(model.getTableName().token);

            if (whereClause != null) {

                final int timestampIndex;

                final ExpressionNode timestamp = model.getTimestamp();
                if (timestamp != null) {
                    timestampIndex = metadata.getColumnIndexQuiet(timestamp.token);
                } else {
                    timestampIndex = -1;
                }

                final IntrinsicModel intrinsicModel = filterAnalyser.extract(model, whereClause, metadata, latestBy != null ? latestBy.token : null, timestampIndex);

                if (intrinsicModel.intrinsicValue == IntrinsicModel.FALSE) {
                    return new EmptyTableRecordCursorFactory(copyMetadata(metadata));
                }

                Function filter;

                if (intrinsicModel.filter != null) {
                    filter = functionParser.parseFunction(intrinsicModel.filter, metadata, executionContext);

                    if (filter.getType() != ColumnType.BOOLEAN) {
                        throw SqlException.$(intrinsicModel.filter.position, "boolean expression expected");
                    }

                    if (filter.isConstant()) {
                        // can pass null to constant function
                        if (filter.getBool(null)) {
                            // filter is constant "true", do not evaluate for every row
                            filter = null;
                        } else {
                            return new EmptyTableRecordCursorFactory(copyMetadata(metadata));
                        }
                    }
                } else {
                    filter = null;
                }

                DataFrameCursorFactory dfcFactory;

                if (latestByIndex > -1) {
                    return generateLatestByQuery(
                            model,
                            reader,
                            metadata,
                            latestByIndex,
                            tableName,
                            intrinsicModel,
                            filter,
                            executionContext);
                }


                // below code block generates index-based filter

                if (intrinsicModel.intervals != null) {
                    dfcFactory = new IntervalFwdDataFrameCursorFactory(engine, tableName, model.getTableVersion(), intrinsicModel.intervals);
                } else {
                    dfcFactory = new FullFwdDataFrameCursorFactory(engine, tableName, model.getTableVersion());
                }

                if (intrinsicModel.keyColumn != null) {
                    // existence of column would have been already validated
                    final int keyColumnIndex = reader.getMetadata().getColumnIndexQuiet(intrinsicModel.keyColumn);
                    final int nKeyValues = intrinsicModel.keyValues.size();

                    if (intrinsicModel.keySubQuery != null) {
                        final RecordCursorFactory rcf = generate(intrinsicModel.keySubQuery, executionContext);
                        final int firstColumnType = validateSubQueryColumnAndGetType(intrinsicModel, rcf.getMetadata());

                        return new FilterOnSubQueryRecordCursorFactory(
                                metadata,
                                dfcFactory,
                                rcf,
                                keyColumnIndex,
                                filter,
                                firstColumnType
                        );
                    }
                    assert nKeyValues > 0;

                    if (nKeyValues == 1) {
                        final RowCursorFactory rcf;
                        final CharSequence symbol = intrinsicModel.keyValues.get(0);
                        final int symbolKey = reader.getSymbolMapReader(keyColumnIndex).getQuick(symbol);
                        if (symbolKey == SymbolTable.VALUE_NOT_FOUND) {
                            if (filter == null) {
                                rcf = new DeferredSymbolIndexRowCursorFactory(keyColumnIndex, Chars.toString(symbol), true);
                            } else {
                                rcf = new DeferredSymbolIndexFilteredRowCursorFactory(keyColumnIndex, Chars.toString(symbol), filter, true);
                            }
                        } else {
                            if (filter == null) {
                                rcf = new SymbolIndexRowCursorFactory(keyColumnIndex, symbolKey, true);
                            } else {
                                rcf = new SymbolIndexFilteredRowCursorFactory(keyColumnIndex, symbolKey, filter, true);
                            }
                        }
                        return new DataFrameRecordCursorFactory(copyMetadata(metadata), dfcFactory, rcf, filter);
                    }

                    return new FilterOnValuesRecordCursorFactory(
                            metadata,
                            dfcFactory,
                            intrinsicModel.keyValues,
                            keyColumnIndex,
                            reader,
                            filter
                    );
                }

                if (filter != null) {
                    // filter lifecycle is managed by top level
                    return new FilteredRecordCursorFactory(new DataFrameRecordCursorFactory(copyMetadata(metadata), dfcFactory, new DataFrameRowCursorFactory(), null), filter);
                }
                return new DataFrameRecordCursorFactory(copyMetadata(metadata), dfcFactory, new DataFrameRowCursorFactory(), filter);
            }

            // no where clause
            if (latestByIndex == -1) {
                return new TableReaderRecordCursorFactory(copyMetadata(metadata), engine, tableName, model.getTableVersion());
            }

            if (metadata.isColumnIndexed(latestByIndex)) {
                return new LatestByAllIndexedFilteredRecordCursorFactory(
                        configuration,
                        copyMetadata(metadata),
                        new FullBwdDataFrameCursorFactory(engine, tableName, model.getTableVersion()),
                        latestByIndex,
                        null);
            }

            listColumnFilter.clear();
            listColumnFilter.add(latestByIndex);
            return new LatestByAllFilteredRecordCursorFactory(
                    copyMetadata(metadata),
                    configuration,
                    new FullBwdDataFrameCursorFactory(engine, tableName, model.getTableVersion()),
                    RecordSinkFactory.getInstance(asm, metadata, listColumnFilter, false),
                    singleColumnType.of(metadata.getColumnType(latestByIndex)),
                    null
            );
        }
    }

    private int validateSubQueryColumnAndGetType(IntrinsicModel intrinsicModel, RecordMetadata metadata) throws SqlException {
        final int firstColumnType = metadata.getColumnType(0);
        if (firstColumnType != ColumnType.STRING && firstColumnType != ColumnType.SYMBOL) {
            assert intrinsicModel.keySubQuery.getColumns() != null;
            assert intrinsicModel.keySubQuery.getColumns().size() > 0;

            throw SqlException
                    .position(intrinsicModel.keySubQuery.getColumns().getQuick(0).getAst().position)
                    .put("unsupported column type: ")
                    .put(metadata.getColumnName(0))
                    .put(": ")
                    .put(ColumnType.nameOf(firstColumnType));
        }
        return firstColumnType;
    }
}
