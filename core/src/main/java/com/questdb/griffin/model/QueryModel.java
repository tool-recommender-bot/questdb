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

package com.questdb.griffin.model;

import com.questdb.cairo.sql.Function;
import com.questdb.griffin.SqlNode;
import com.questdb.std.*;
import com.questdb.std.str.CharSink;

import java.util.ArrayDeque;

public class QueryModel implements Mutable, ExecutionModel, AliasTranslator, Sinkable {
    public static final QueryModelFactory FACTORY = new QueryModelFactory();
    public static final int ORDER_DIRECTION_ASCENDING = 0;
    public static final int ORDER_DIRECTION_DESCENDING = 1;
    public static final String NO_ROWID_MARKER = "*!*";
    public static final int JOIN_INNER = 1;
    public static final int JOIN_OUTER = 2;
    public static final int JOIN_CROSS = 3;
    public static final int JOIN_ASOF = 4;
    public static final String SUB_QUERY_ALIAS_PREFIX = "_xQdbA";
    public static final int SELECT_MODEL_NONE = 0;
    public static final int SELECT_MODEL_CHOOSE = 1;
    public static final int SELECT_MODEL_VIRTUAL = 2;
    public static final int SELECT_MODEL_ANALYTIC = 3;
    public static final int SELECT_MODEL_GROUP_BY = 4;
    private final ObjList<QueryColumn> columns = new ObjList<>();
    private final CharSequenceObjHashMap<CharSequence> aliasToColumnMap = new CharSequenceObjHashMap<>();
    private final CharSequenceObjHashMap<CharSequence> columnToAliasMap = new CharSequenceObjHashMap<>();
    private final ObjList<QueryModel> joinModels = new ObjList<>();
    private final ObjList<SqlNode> orderBy = new ObjList<>();
    private final IntList orderByDirection = new IntList();
    private final IntHashSet dependencies = new IntHashSet();
    private final IntList orderedJoinModels1 = new IntList();
    private final IntList orderedJoinModels2 = new IntList();
    private final CharSequenceIntHashMap aliasIndexes = new CharSequenceIntHashMap();
    // collect frequency of column names from each join model
    // and check if any of columns with frequency > 0 are selected
    // column name frequency of 1 corresponds to map value 0
    // column name frequency of 0 corresponds to map value -1
    private final CharSequenceIntHashMap columnNameTypeMap = new CharSequenceIntHashMap();
    // list of "and" concatenated expressions
    private final ObjList<SqlNode> parsedWhere = new ObjList<>();
    private final IntHashSet parsedWhereConsts = new IntHashSet();
    private final ArrayDeque<SqlNode> sqlNodeStack = new ArrayDeque<>();
    private final CharSequenceIntHashMap orderHash = new CharSequenceIntHashMap(4, 0.5, -1);
    private final ObjList<SqlNode> joinColumns = new ObjList<>(4);
    private final CharSequenceObjHashMap<WithClauseModel> withClauses = new CharSequenceObjHashMap<>();
    private SqlNode whereClause;
    private SqlNode postJoinWhereClause;
    private SqlNode constWhereClause;
    private QueryModel nestedModel;
    private SqlNode tableName;
    private Function tableNameFunction;
    private SqlNode alias;
    private SqlNode latestBy;
    private SqlNode timestamp;
    private SqlNode sampleBy;
    private JoinContext context;
    private SqlNode joinCriteria;
    private int joinType;
    private IntList orderedJoinModels = orderedJoinModels2;
    private SqlNode limitLo;
    private SqlNode limitHi;
    private int selectModelType = SELECT_MODEL_NONE;


    private QueryModel() {
        joinModels.add(this);
    }

    public boolean addAliasIndex(SqlNode node, int index) {
        return aliasIndexes.put(node.token, index);
    }

    public void addColumn(QueryColumn column) {
        columns.add(column);
        final CharSequence alias = column.getAlias();
        final SqlNode ast = column.getAst();
        assert alias != null;
        aliasToColumnMap.put(alias, ast.token);
        columnToAliasMap.put(ast.token, alias);
        columnNameTypeMap.put(alias, ast.type);
    }

    public void addDependency(int index) {
        dependencies.add(index);
    }

    public void addField(CharSequence name) {
        columnNameTypeMap.put(name, SqlNode.LITERAL);
        aliasToColumnMap.put(name, name);
    }

    public void addJoinColumn(SqlNode node) {
        joinColumns.add(node);
    }

    public void addJoinModel(QueryModel model) {
        joinModels.add(model);
    }

    public void addOrderBy(SqlNode node, int direction) {
        orderBy.add(node);
        orderByDirection.add(direction);
    }

    public void addParsedWhereNode(SqlNode node) {
        parsedWhere.add(node);
    }

    public void addWithClause(CharSequence name, WithClauseModel model) {
        withClauses.put(name, model);
    }

    public void clear() {
        columns.clear();
        aliasToColumnMap.clear();
        joinModels.clear();
        joinModels.add(this);
        sampleBy = null;
        orderBy.clear();
        orderByDirection.clear();
        dependencies.clear();
        parsedWhere.clear();
        whereClause = null;
        constWhereClause = null;
        nestedModel = null;
        tableName = null;
        alias = null;
        latestBy = null;
        joinCriteria = null;
        joinType = JOIN_INNER;
        orderedJoinModels1.clear();
        orderedJoinModels2.clear();
        parsedWhereConsts.clear();
        aliasIndexes.clear();
        postJoinWhereClause = null;
        context = null;
        orderedJoinModels = orderedJoinModels2;
        limitHi = null;
        limitLo = null;
        columnNameTypeMap.clear();
        timestamp = null;
        sqlNodeStack.clear();
        joinColumns.clear();
        withClauses.clear();
        selectModelType = SELECT_MODEL_NONE;
        columnToAliasMap.clear();
        tableNameFunction = null;
    }

    public void clearOrderBy() {
        orderBy.clear();
        orderByDirection.clear();
    }

    public void copyColumnsFrom(QueryModel other) {
        this.columnNameTypeMap.clear();
        this.aliasToColumnMap.clear();
        this.columnNameTypeMap.putAll(other.columnNameTypeMap);
        this.aliasToColumnMap.putAll(other.aliasToColumnMap);
    }

    public SqlNode getAlias() {
        return alias;
    }

    public void setAlias(SqlNode alias) {
        this.alias = alias;
    }

    public int getAliasIndex(CharSequence column, int start, int end) {
        int index = aliasIndexes.keyIndex(column, start, end);
        if (index < 0) {
            return aliasIndexes.valueAt(index);
        }
        return -1;
    }

    public CharSequenceObjHashMap<CharSequence> getAliasToColumnMap() {
        return aliasToColumnMap;
    }

    public CharSequenceIntHashMap getColumnNameTypeMap() {
        return columnNameTypeMap;
    }

    public ObjList<CharSequence> getColumnNames() {
        return aliasToColumnMap.keys();
    }

    public CharSequenceObjHashMap<CharSequence> getColumnToAliasMap() {
        return columnToAliasMap;
    }

    public ObjList<QueryColumn> getColumns() {
        return columns;
    }

    public SqlNode getConstWhereClause() {
        return constWhereClause;
    }

    public void setConstWhereClause(SqlNode constWhereClause) {
        this.constWhereClause = constWhereClause;
    }

    public JoinContext getContext() {
        return context;
    }

    public void setContext(JoinContext context) {
        this.context = context;
    }

    public IntHashSet getDependencies() {
        return dependencies;
    }

    public ObjList<SqlNode> getJoinColumns() {
        return joinColumns;
    }

    public SqlNode getJoinCriteria() {
        return joinCriteria;
    }

    public void setJoinCriteria(SqlNode joinCriteria) {
        this.joinCriteria = joinCriteria;
    }

    public ObjList<QueryModel> getJoinModels() {
        return joinModels;
    }

    public int getJoinType() {
        return joinType;
    }

    public void setJoinType(int joinType) {
        this.joinType = joinType;
    }

    public SqlNode getLatestBy() {
        return latestBy;
    }

    public void setLatestBy(SqlNode latestBy) {
        this.latestBy = latestBy;
    }

    public SqlNode getLimitHi() {
        return limitHi;
    }

    public SqlNode getLimitLo() {
        return limitLo;
    }

    @Override
    public int getModelType() {
        return ExecutionModel.QUERY;
    }

    public CharSequence getName() {
        if (alias != null) {
            return alias.token;
        }

        if (tableName != null) {
            return tableName.token;
        }

        return null;
    }

    public QueryModel getNestedModel() {
        return nestedModel;
    }

    public void setNestedModel(QueryModel nestedModel) {
        this.nestedModel = nestedModel;
    }

    public ObjList<SqlNode> getOrderBy() {
        return orderBy;
    }

    public IntList getOrderByDirection() {
        return orderByDirection;
    }

    public CharSequenceIntHashMap getOrderHash() {
        return orderHash;
    }

    public IntList getOrderedJoinModels() {
        return orderedJoinModels;
    }

    public void setOrderedJoinModels(IntList that) {
        assert that == orderedJoinModels1 || that == orderedJoinModels2;
        this.orderedJoinModels = that;
    }

    public ObjList<SqlNode> getParsedWhere() {
        return parsedWhere;
    }

    public SqlNode getPostJoinWhereClause() {
        return postJoinWhereClause;
    }

    public void setPostJoinWhereClause(SqlNode postJoinWhereClause) {
        this.postJoinWhereClause = postJoinWhereClause;
    }

    public SqlNode getSampleBy() {
        return sampleBy;
    }

    public void setSampleBy(SqlNode sampleBy) {
        this.sampleBy = sampleBy;
    }

    public int getSelectModelType() {
        return selectModelType;
    }

    public void setSelectModelType(int selectModelType) {
        this.selectModelType = selectModelType;
    }

    public SqlNode getTableName() {
        return tableName;
    }

    public void setTableName(SqlNode tableName) {
        this.tableName = tableName;
    }

    public Function getTableNameFunction() {
        return tableNameFunction;
    }

    public void setTableNameFunction(Function function) {
        this.tableNameFunction = function;
    }

    public SqlNode getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(SqlNode timestamp) {
        this.timestamp = timestamp;
    }

    public SqlNode getWhereClause() {
        return whereClause;
    }

    public void setWhereClause(SqlNode whereClause) {
        this.whereClause = whereClause;
    }

    public WithClauseModel getWithClause(CharSequence name) {
        return withClauses.get(name);
    }

    /**
     * Optimiser may be attempting to order join clauses several times.
     * Every time ordering takes place optimiser will keep at most two lists:
     * one is last known order the other is new order. If new order cost is better
     * optimiser will replace last known order with new one.
     * <p>
     * To facilitate this behaviour the function will always return non-current list.
     *
     * @return non current order list.
     */
    public IntList nextOrderedJoinModels() {
        IntList ordered = orderedJoinModels == orderedJoinModels1 ? orderedJoinModels2 : orderedJoinModels1;
        ordered.clear();
        return ordered;
    }

    /*
     * Splits "where" clauses into "and" chunks
     */
    public ObjList<SqlNode> parseWhereClause() {
        SqlNode n = getWhereClause();
        // pre-order traversal
        sqlNodeStack.clear();
        while (!sqlNodeStack.isEmpty() || n != null) {
            if (n != null) {
                if (Chars.equals("and", n.token)) {
                    if (n.rhs != null) {
                        sqlNodeStack.push(n.rhs);
                    }
                    n = n.lhs;
                } else {
                    addParsedWhereNode(n);
                    n = null;

                }
            } else {
                n = sqlNodeStack.poll();
            }
        }
        return getParsedWhere();
    }

    public void removeDependency(int index) {
        dependencies.remove(index);
    }

    public void replaceJoinModel(int pos, QueryModel model) {
        joinModels.setQuick(pos, model);
    }

    public void setLimit(SqlNode lo, SqlNode hi) {
        this.limitLo = lo;
        this.limitHi = hi;
    }

    @Override
    public void toSink(CharSink sink) {
        toSink0(sink, false);
    }

    @Override
    public CharSequence translateAlias(CharSequence column) {
        return aliasToColumnMap.get(column);
    }

    private static void aliasToSink(CharSequence alias, CharSink sink) {
        sink.put(' ');
        boolean quote = Chars.indexOf(alias, ' ') != -1;
        if (quote) {
            sink.put('\'').put(alias).put('\'');
        } else {
            sink.put(alias);
        }
    }

    private String getSelectModelTypeText() {
        switch (selectModelType) {
            case SELECT_MODEL_CHOOSE:
                return "select-choose";
            case SELECT_MODEL_VIRTUAL:
                return "select-virtual";
            case SELECT_MODEL_ANALYTIC:
                return "select-analytic";
            default:
                return "select-group-by";
        }
    }

    private void toSink0(CharSink sink, boolean joinSlave) {
        if (columns.size() > 0) {
            sink.put(getSelectModelTypeText()).put(' ');
            for (int i = 0, n = columns.size(); i < n; i++) {
                if (i > 0) {
                    sink.put(", ");
                }
                QueryColumn column = columns.getQuick(i);
                CharSequence name = column.getName();
                CharSequence alias = column.getAlias();
                SqlNode ast = column.getAst();
                if (column instanceof AnalyticColumn || name == null) {
                    ast.toSink(sink);

                    if (alias != null) {
                        aliasToSink(alias, sink);
                    }

                    // this can only be analytic column
                    if (name != null) {
                        AnalyticColumn ac = (AnalyticColumn) column;
                        sink.put(" over (");
                        final ObjList<SqlNode> partitionBy = ac.getPartitionBy();
                        if (partitionBy.size() > 0) {
                            sink.put("partition by ");
                            for (int k = 0, z = partitionBy.size(); k < z; k++) {
                                if (k > 0) {
                                    sink.put(", ");
                                }
                                partitionBy.getQuick(k).toSink(sink);
                            }
                        }

                        final ObjList<SqlNode> orderBy = ac.getOrderBy();
                        if (orderBy.size() > 0) {
                            if (partitionBy.size() > 0) {
                                sink.put(' ');
                            }
                            sink.put("order by ");
                            for (int k = 0, z = orderBy.size(); k < z; k++) {
                                if (k > 0) {
                                    sink.put(", ");
                                }
                                orderBy.getQuick(k).toSink(sink);
                                if (ac.getOrderByDirection().getQuick(k) == 1) {
                                    sink.put(" desc");
                                }
                            }
                        }
                        sink.put(')');
                    }
                } else {
                    ast.toSink(sink);
                    // do not repeat alias when it is the same as AST token, provided AST is a literal
                    if (alias != null && (ast.type != SqlNode.LITERAL || !ast.token.equals(alias))) {
                        aliasToSink(alias, sink);
                    }
                }
            }
            sink.put(" from ");
        }
        if (tableName != null) {
            tableName.toSink(sink);
            if (alias != null) {
                aliasToSink(alias.token, sink);
            }
        } else {
            sink.put('(');
            nestedModel.toSink(sink);
            sink.put(')');
            if (alias != null) {
                aliasToSink(alias.token, sink);
            }
        }

        if (timestamp != null) {
            sink.put(" timestamp (");
            timestamp.toSink(sink);
            sink.put(')');
        }

        if (getLatestBy() != null) {
            sink.put(" latest by ");
            getLatestBy().toSink(sink);
        }

        if (orderedJoinModels.size() > 1) {
            for (int i = 0, n = orderedJoinModels.size(); i < n; i++) {
                QueryModel model = joinModels.getQuick(orderedJoinModels.getQuick(i));
                if (model != this) {
                    switch (model.getJoinType()) {
                        case JOIN_OUTER:
                            sink.put(" outer join ");
                            break;
                        case JOIN_ASOF:
                            sink.put(" asof join ");
                            break;
                        case JOIN_CROSS:
                            sink.put(" cross join ");
                            break;
                        default:
                            sink.put(" join ");
                    }

                    if (model.getWhereClause() != null) {
                        sink.put('(');
                        model.toSink0(sink, true);
                        sink.put(')');
                        if (model.getAlias() != null) {
                            aliasToSink(model.getAlias().token, sink);
                        } else if (model.getTableName() != null) {
                            aliasToSink(model.getTableName().token, sink);
                        }
                    } else {
                        model.toSink0(sink, true);
                    }

                    JoinContext jc = model.getContext();
                    if (jc != null && jc.aIndexes.size() > 0) {
                        // join clause
                        sink.put(" on ");
                        for (int k = 0, z = jc.aIndexes.size(); k < z; k++) {
                            if (k > 0) {
                                sink.put(" and ");
                            }
                            jc.aNodes.getQuick(k).toSink(sink);
                            sink.put(" = ");
                            jc.bNodes.getQuick(k).toSink(sink);
                        }
                    }

                    if (model.getPostJoinWhereClause() != null) {
                        sink.put(" post-join-where ");
                        model.getPostJoinWhereClause().toSink(sink);
                    }
                }
            }
        }

        if (whereClause != null) {
            sink.put(" where ");
            whereClause.toSink(sink);
        }

        if (constWhereClause != null) {
            sink.put(" const-where ");
            constWhereClause.toSink(sink);
        }

        if (!joinSlave && postJoinWhereClause != null) {
            sink.put(" post-join-where ");
            postJoinWhereClause.toSink(sink);
        }

        if (sampleBy != null) {
            sink.put(" sample by ");
            sampleBy.toSink(sink);
        }

        if (orderBy.size() > 0) {
            sink.put(" order by ");
            for (int i = 0, n = orderBy.size(); i < n; i++) {
                if (i > 0) {
                    sink.put(", ");
                }
                orderBy.getQuick(i).toSink(sink);
                if (orderByDirection.getQuick(i) == 1) {
                    sink.put(" desc");
                }
            }
        }

        if (getLimitLo() != null || getLimitHi() != null) {
            sink.put(" limit ");
            if (getLimitLo() != null) {
                getLimitLo().toSink(sink);
            }
            if (getLimitHi() != null) {
                sink.put(',');
                getLimitHi().toSink(sink);
            }
        }
    }

    private static final class QueryModelFactory implements ObjectFactory<QueryModel> {
        @Override
        public QueryModel newInstance() {
            return new QueryModel();
        }
    }
}