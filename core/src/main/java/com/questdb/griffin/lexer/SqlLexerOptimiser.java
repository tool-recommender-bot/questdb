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

package com.questdb.griffin.lexer;

import com.questdb.cairo.CairoConfiguration;
import com.questdb.cairo.sql.CairoEngine;
import com.questdb.common.ColumnType;
import com.questdb.common.NumericException;
import com.questdb.common.RecordMetadata;
import com.questdb.griffin.common.ExprNode;
import com.questdb.griffin.lexer.model.*;
import com.questdb.ql.ops.FunctionFactories;
import com.questdb.std.*;
import com.questdb.std.str.CharSink;
import com.questdb.std.str.FlyweightCharSequence;
import com.questdb.std.str.StringSink;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayDeque;

public final class SqlLexerOptimiser {

    public static final int MAX_ORDER_BY_COLUMNS = 1560;
    private static final CharSequenceHashSet tableAliasStop = new CharSequenceHashSet();
    private static final CharSequenceHashSet columnAliasStop = new CharSequenceHashSet();
    private static final CharSequenceHashSet groupByStopSet = new CharSequenceHashSet();
    private static final CharSequenceIntHashMap joinStartSet = new CharSequenceIntHashMap();
    private static final int ORDER_BY_UNKNOWN = 0;
    private static final int ORDER_BY_REQUIRED = 1;
    private static final int ORDER_BY_INVARIANT = 2;
    private final static IntHashSet joinBarriers;
    private final static CharSequenceHashSet nullConstants = new CharSequenceHashSet();
    private final ObjectPool<ExprNode> exprNodePool = new ObjectPool<>(ExprNode.FACTORY, 128);
    private final ExprAstBuilder astBuilder = new ExprAstBuilder();
    private final ObjectPool<QueryModel> queryModelPool = new ObjectPool<>(QueryModel.FACTORY, 8);
    private final ObjectPool<QueryColumn> queryColumnPool = new ObjectPool<>(QueryColumn.FACTORY, 64);
    private final ObjectPool<AnalyticColumn> analyticColumnPool = new ObjectPool<>(AnalyticColumn.FACTORY, 8);
    private final ObjectPool<CreateTableModel> createTableModelPool = new ObjectPool<>(CreateTableModel.FACTORY, 4);
    private final ObjectPool<ColumnCastModel> columnCastModelPool = new ObjectPool<>(ColumnCastModel.FACTORY, 8);
    private final ObjectPool<RenameTableModel> renameTableModelPool = new ObjectPool<>(RenameTableModel.FACTORY, 8);
    private final ObjectPool<WithClauseModel> withClauseModelPool = new ObjectPool<>(WithClauseModel.FACTORY, 16);
    private final Lexer secondaryLexer = new Lexer();
    private final ExprParser exprParser = new ExprParser(exprNodePool);
    private final CairoConfiguration configuration;
    private final ArrayDeque<ExprNode> exprNodeStack = new ArrayDeque<>();
    private final PostOrderTreeTraversalAlgo traversalAlgo = new PostOrderTreeTraversalAlgo();
    private final LiteralMatcher literalMatcher = new LiteralMatcher(traversalAlgo);
    private final CairoEngine engine;
    private final IntList literalCollectorAIndexes = new IntList();
    private final ObjList<CharSequence> literalCollectorANames = new ObjList<>();
    private final IntList literalCollectorBIndexes = new IntList();
    private final ObjList<CharSequence> literalCollectorBNames = new ObjList<>();
    private final LiteralCollector literalCollector = new LiteralCollector();
    private final ObjectPool<FlyweightCharSequence> csPool = new ObjectPool<>(FlyweightCharSequence.FACTORY, 64);
    private final ObjectPool<JoinContext> contextPool = new ObjectPool<>(JoinContext.FACTORY, 16);
    private final IntHashSet deletedContexts = new IntHashSet();
    private final ObjList<JoinContext> joinClausesSwap1 = new ObjList<>();
    private final ObjList<JoinContext> joinClausesSwap2 = new ObjList<>();
    private final CharSequenceIntHashMap constNameToIndex = new CharSequenceIntHashMap();
    private final CharSequenceObjHashMap<ExprNode> constNameToNode = new CharSequenceObjHashMap<>();
    private final CharSequenceObjHashMap<String> constNameToToken = new CharSequenceObjHashMap<>();
    private final IntList tempCrosses = new IntList();
    private final IntList tempCrossIndexes = new IntList();
    private final IntList clausesToSteal = new IntList();
    private final ObjList<IntList> postFilterTableRefs = new ObjList<>();
    private final ObjectPool<IntList> intListPool = new ObjectPool<>(IntList::new, 16);
    private final IntHashSet tablesSoFar = new IntHashSet();
    private final IntHashSet postFilterRemoved = new IntHashSet();
    private final IntList nullCounts = new IntList();
    private final IntPriorityQueue orderingStack = new IntPriorityQueue();
    private final StringSink columnNameAssembly = new StringSink();
    private Lexer lexer = new Lexer();
    private ObjList<JoinContext> emittedJoinClauses;

    public SqlLexerOptimiser(CairoEngine engine, CairoConfiguration configuration) {
        this.engine = engine;
        this.configuration = configuration;

        ExprParser.configureLexer(lexer);
        ExprParser.configureLexer(secondaryLexer);
    }

    public void enumerateTableColumns(QueryModel model) throws ParserException {
        final CharSequenceIntHashMap columnNameType = model.getColumnNameTypeMap();

        final ObjList<QueryModel> jm = model.getJoinModels();

        // we have plain tables and possibly joins
        // deal with _this_ model first, it will always be the first element in join model list
        if (model.getTableName() != null) {
            RecordMetadata m = model.getTableMetadata(engine);
            for (int i = 0, k = m.getColumnCount(); i < k; i++) {
                model.addField(createColumnAlias(m.getColumnName(i), model.getColumnNameTypeMap()));
            }
        } else {
            if (model.getNestedModel() != null) {
                enumerateTableColumns(model.getNestedModel());
                // copy columns of nested model onto parent one
                // we must treat sub-query just like we do a table
                columnNameType.putAll(model.getNestedModel().getColumnNameTypeMap());
            }
        }
        for (int i = 1, n = jm.size(); i < n; i++) {
            enumerateTableColumns(jm.getQuick(i));
        }
    }

    public ParsedModel parse(CharSequence query) throws ParserException {
        clear();
        lexer.setContent(query);
        CharSequence tok = tok();

        if (Chars.equals(tok, "create")) {
            return parseCreateStatement();
        }

        if (Chars.equals(tok, "rename")) {
            return parseRenameStatement();
        }

        lexer.unparse();
        return optimise(parseDml(false));
    }

    private static void assertNotNull(ExprNode node, int position, String message) throws ParserException {
        if (node == null) {
            throw ParserException.$(position, message);
        }
    }

    private static void linkDependencies(QueryModel model, int parent, int child) {
        model.getJoinModels().getQuick(parent).addDependency(child);
    }

    private static void unlinkDependencies(QueryModel model, int parent, int child) {
        model.getJoinModels().getQuick(parent).removeDependency(child);
    }

    private static void addAllColumns(QueryModel root, ObjList<? extends QueryColumn> columns) {
        for (int i = 0, n = columns.size(); i < n; i++) {
            root.addColumn(columns.getQuick(i));
        }
    }

    private void addFilterOrEmitJoin(QueryModel parent, int idx, int ai, CharSequence an, ExprNode ao, int bi, CharSequence bn, ExprNode bo) {
        if (ai == bi && Chars.equals(an, bn)) {
            deletedContexts.add(idx);
            return;
        }

        if (ai == bi) {
            // (same table)
            ExprNode node = exprNodePool.next().of(ExprNode.OPERATION, "=", 0, 0);
            node.paramCount = 2;
            node.lhs = ao;
            node.rhs = bo;
            addWhereClause(parent, ai, node);
        } else {
            // (different tables)
            JoinContext jc = contextPool.next();
            jc.aIndexes.add(ai);
            jc.aNames.add(an);
            jc.aNodes.add(ao);
            jc.bIndexes.add(bi);
            jc.bNames.add(bn);
            jc.bNodes.add(bo);
            jc.slaveIndex = ai > bi ? ai : bi;
            jc.parents.add(ai < bi ? ai : bi);
            emittedJoinClauses.add(jc);
        }

        deletedContexts.add(idx);
    }

    private void addJoinContext(QueryModel parent, JoinContext context) {
        QueryModel jm = parent.getJoinModels().getQuick(context.slaveIndex);
        JoinContext other = jm.getContext();
        if (other == null) {
            jm.setContext(context);
        } else {
            jm.setContext(mergeContexts(parent, other, context));
        }
    }

    /**
     * Adds filters derived from transitivity of equals operation, for example
     * if there is filter:
     * <p>
     * a.x = b.x and b.x = 10
     * <p>
     * derived filter would be:
     * <p>
     * a.x = 10
     * <p>
     * this filter is not explicitly mentioned but it might help pre-filtering record sources
     * before hashing.
     */
    private void addTransitiveFilters(QueryModel parent) {
        ObjList<QueryModel> joinModels = parent.getJoinModels();
        for (int i = 0, n = joinModels.size(); i < n; i++) {
            JoinContext jc = joinModels.getQuick(i).getContext();
            if (jc != null) {
                for (int k = 0, kn = jc.bNames.size(); k < kn; k++) {
                    CharSequence name = jc.bNames.getQuick(k);
                    if (constNameToIndex.get(name) == jc.bIndexes.getQuick(k)) {
                        ExprNode node = exprNodePool.next().of(ExprNode.OPERATION, constNameToToken.get(name), 0, 0);
                        node.lhs = jc.aNodes.getQuick(k);
                        node.rhs = constNameToNode.get(name);
                        node.paramCount = 2;
                        addWhereClause(parent, jc.slaveIndex, node);
                    }
                }
            }
        }
    }

    private void addWhereClause(QueryModel parent, int index, ExprNode filter) {
        QueryModel m = parent.getJoinModels().getQuick(index);
        m.setWhereClause(concatFilters(m.getWhereClause(), filter));
    }

    /**
     * Move fields that belong to slave table to left and parent fields
     * to right of equals operator.
     */
    private void alignJoinClauses(QueryModel parent) {
        ObjList<QueryModel> joinModels = parent.getJoinModels();
        for (int i = 0, n = joinModels.size(); i < n; i++) {
            JoinContext jc = joinModels.getQuick(i).getContext();
            if (jc != null) {
                int index = jc.slaveIndex;
                for (int k = 0, kc = jc.aIndexes.size(); k < kc; k++) {
                    if (jc.aIndexes.getQuick(k) != index) {
                        int idx = jc.aIndexes.getQuick(k);
                        CharSequence name = jc.aNames.getQuick(k);
                        ExprNode node = jc.aNodes.getQuick(k);

                        jc.aIndexes.setQuick(k, jc.bIndexes.getQuick(k));
                        jc.aNames.setQuick(k, jc.bNames.getQuick(k));
                        jc.aNodes.setQuick(k, jc.bNodes.getQuick(k));

                        jc.bIndexes.setQuick(k, idx);
                        jc.bNames.setQuick(k, name);
                        jc.bNodes.setQuick(k, node);
                    }
                }
            }

        }
    }

    private void analyseEquals(QueryModel parent, ExprNode node) throws ParserException {
        traverseNamesAndIndices(parent, node);

        int aSize = literalCollectorAIndexes.size();
        int bSize = literalCollectorBIndexes.size();

        JoinContext jc;

        switch (aSize) {
            case 0:
                if (bSize == 1
                        && literalCollector.nullCount == 0
                        // table must not be OUTER or ASOF joined
                        && !joinBarriers.contains(parent.getJoinModels().get(literalCollectorBIndexes.getQuick(0)).getJoinType())) {
                    // single table reference + constant
                    jc = contextPool.next();
                    jc.slaveIndex = literalCollectorBIndexes.getQuick(0);

                    addWhereClause(parent, jc.slaveIndex, node);
                    addJoinContext(parent, jc);

                    CharSequence cs = literalCollectorBNames.getQuick(0);
                    constNameToIndex.put(cs, jc.slaveIndex);
                    constNameToNode.put(cs, node.lhs);
                    constNameToToken.put(cs, node.token);
                } else {
                    parent.addParsedWhereNode(node);
                }
                break;
            case 1:
                jc = contextPool.next();
                int lhi = literalCollectorAIndexes.getQuick(0);
                if (bSize == 1) {
                    int rhi = literalCollectorBIndexes.getQuick(0);
                    if (lhi == rhi) {
                        // single table reference
                        jc.slaveIndex = lhi;
                        addWhereClause(parent, lhi, node);
                    } else {
                        jc.aNodes.add(node.lhs);
                        jc.bNodes.add(node.rhs);
                        jc.aNames.add(literalCollectorANames.getQuick(0));
                        jc.bNames.add(literalCollectorBNames.getQuick(0));
                        jc.aIndexes.add(lhi);
                        jc.bIndexes.add(rhi);
                        int max = lhi > rhi ? lhi : rhi;
                        int min = lhi < rhi ? lhi : rhi;
                        jc.slaveIndex = max;
                        jc.parents.add(min);
                        linkDependencies(parent, min, max);
                    }
                    addJoinContext(parent, jc);
                } else if (bSize == 0
                        && literalCollector.nullCount == 0
                        && !joinBarriers.contains(parent.getJoinModels().get(literalCollectorAIndexes.getQuick(0)).getJoinType())) {
                    // single table reference + constant
                    jc.slaveIndex = lhi;
                    addWhereClause(parent, lhi, node);
                    addJoinContext(parent, jc);

                    CharSequence cs = literalCollectorANames.getQuick(0);
                    constNameToIndex.put(cs, lhi);
                    constNameToNode.put(cs, node.rhs);
                    constNameToToken.put(cs, node.token);
                } else {
                    parent.addParsedWhereNode(node);
                }
                break;
            default:
                parent.addParsedWhereNode(node);
                break;
        }
    }

    private void analyseRegex(QueryModel parent, ExprNode node) throws ParserException {
        traverseNamesAndIndices(parent, node);

        if (literalCollector.nullCount == 0) {
            int aSize = literalCollectorAIndexes.size();
            int bSize = literalCollectorBIndexes.size();
            if (aSize == 1 && bSize == 0) {
                CharSequence name = literalCollectorANames.getQuick(0);
                constNameToIndex.put(name, literalCollectorAIndexes.getQuick(0));
                constNameToNode.put(name, node.rhs);
                constNameToToken.put(name, node.token);
            }
        }
    }

    private void assignFilters(QueryModel parent) throws ParserException {

        tablesSoFar.clear();
        postFilterRemoved.clear();
        postFilterTableRefs.clear();
        nullCounts.clear();

        literalCollector.withParent(parent);
        ObjList<ExprNode> filterNodes = parent.getParsedWhere();
        // collect table indexes from each part of global filter
        int pc = filterNodes.size();
        for (int i = 0; i < pc; i++) {
            IntList indexes = intListPool.next();
            literalCollector.resetNullCount();
            traversalAlgo.traverse(filterNodes.getQuick(i), literalCollector.to(indexes));
            postFilterTableRefs.add(indexes);
            nullCounts.add(literalCollector.nullCount);
        }

        IntList ordered = parent.getOrderedJoinModels();
        // match table references to set of table in join order
        for (int i = 0, n = ordered.size(); i < n; i++) {
            int index = ordered.getQuick(i);
            tablesSoFar.add(index);

            for (int k = 0; k < pc; k++) {
                if (postFilterRemoved.contains(k)) {
                    continue;
                }

                IntList refs = postFilterTableRefs.getQuick(k);
                int rs = refs.size();
                if (rs == 0) {
                    // condition has no table references
                    // must evaluate as constant
                    postFilterRemoved.add(k);
                    parent.addParsedWhereConst(k);
                } else if (rs == 1
                        && nullCounts.getQuick(k) == 0
                        // single table reference and this table is not joined via OUTER or ASOF
                        && !joinBarriers.contains(parent.getJoinModels().getQuick(refs.getQuick(0)).getJoinType())) {
                    // get single table reference out of the way right away
                    // we don't have to wait until "our" table comes along
                    addWhereClause(parent, refs.getQuick(0), filterNodes.getQuick(k));
                    postFilterRemoved.add(k);
                } else {
                    boolean qualifies = true;
                    // check if filter references table processed so far
                    for (int y = 0; y < rs; y++) {
                        if (!tablesSoFar.contains(refs.getQuick(y))) {
                            qualifies = false;
                            break;
                        }
                    }
                    if (qualifies) {
                        postFilterRemoved.add(k);
                        QueryModel m = parent.getJoinModels().getQuick(index);
                        m.setPostJoinWhereClause(concatFilters(m.getPostJoinWhereClause(), filterNodes.getQuick(k)));
                    }
                }
            }
        }

        assert postFilterRemoved.size() == pc;
    }

    private void clear() {
        queryModelPool.clear();
        queryColumnPool.clear();
        exprNodePool.clear();
        analyticColumnPool.clear();
        createTableModelPool.clear();
        columnCastModelPool.clear();
        renameTableModelPool.clear();
        withClauseModelPool.clear();
        csPool.clear();
        contextPool.clear();
        intListPool.clear();
        joinClausesSwap1.clear();
        joinClausesSwap2.clear();
        constNameToIndex.clear();
        constNameToNode.clear();
        constNameToToken.clear();
        literalCollectorAIndexes.clear();
        literalCollectorBIndexes.clear();
        literalCollectorANames.clear();
        literalCollectorBNames.clear();
    }

    private void collectJoinModelAliases(QueryModel model) throws ParserException {
        collectJoinModelAliases0(model);
        if (model.getNestedModel() != null) {
            collectJoinModelAliases(model.getNestedModel());
        }
    }

    private void collectJoinModelAliases0(QueryModel model) throws ParserException {
        final ObjList<QueryModel> joinModels = model.getJoinModels();
        for (int i = 0, n = joinModels.size(); i < n; i++) {
            QueryModel joinModel = model.getJoinModels().getQuick(i);

            if (joinModel.getAlias() != null) {
                if (!model.addAliasIndex(joinModel.getAlias(), i)) {
                    throw ParserException.$(joinModel.getAlias().position, "Duplicate alias");
                }
            } else if (joinModel.getTableName() != null) {
                model.addAliasIndex(joinModel.getTableName(), i);
            }

            if (joinModel != model) {
                collectJoinModelAliases(joinModel);
            }
        }
    }

    private ExprNode concatFilters(ExprNode old, ExprNode filter) {
        if (filter == null || filter == old) {
            return old;
        }

        if (old == null) {
            return filter;
        } else {
            ExprNode n = exprNodePool.next().of(ExprNode.OPERATION, "and", 0, 0);
            n.paramCount = 2;
            n.lhs = old;
            n.rhs = filter;
            return n;
        }
    }

    private String createColumnAlias(CharSequence base, CharSequenceIntHashMap nameTypeMap) {
        columnNameAssembly.clear();
        int dot = Chars.indexOf(base, '.');
        if (dot == -1) {
            columnNameAssembly.put(base);
        } else {
            columnNameAssembly.put(base, dot + 1, base.length());
        }
        int len = columnNameAssembly.length();
        int sequence = 0;
        while (true) {
            if (sequence > 0) {
                columnNameAssembly.clear(len);
                columnNameAssembly.put(sequence);
            }
            sequence++;
            if (nameTypeMap.keyIndex(columnNameAssembly) < 0) {
                continue;
            }
            return columnNameAssembly.toString();
        }
    }

    /**
     * Creates dependencies via implied columns, typically timestamp.
     * Dependencies like that are not explicitly expressed in SQL query and
     * therefore are not created by analyzing "where" clause.
     * <p>
     * Explicit dependencies however are required for table ordering.
     *
     * @param parent the parent model
     */
    private void createImpliedDependencies(QueryModel parent) {
        ObjList<QueryModel> models = parent.getJoinModels();
        JoinContext jc;
        for (int i = 0, n = models.size(); i < n; i++) {
            QueryModel m = models.getQuick(i);
            if (m.getJoinType() == QueryModel.JOIN_ASOF) {
                linkDependencies(parent, 0, i);
                if (m.getContext() == null) {
                    m.setContext(jc = contextPool.next());
                    jc.parents.add(0);
                    jc.slaveIndex = i;
                }
            }
        }
    }

    // order hash is used to determine redundant order by clauses
    private void createOrderHash(QueryModel model) {
        CharSequenceIntHashMap hash = model.getOrderHash();
        hash.clear();

        final ObjList<ExprNode> orderBy = model.getOrderBy();
        final int n = orderBy.size();
        final ObjList<QueryColumn> columns = model.getColumns();
        final int m = columns.size();
        final QueryModel nestedModel = model.getNestedModel();

        if (n > 0) {
            final IntList orderByDirection = model.getOrderByDirection();
            for (int i = 0; i < n; i++) {
                hash.put(orderBy.getQuick(i).token, orderByDirection.getQuick(i));
            }
        } else if (nestedModel != null && m > 0) {
            createOrderHash(nestedModel);
            CharSequenceIntHashMap thatHash = nestedModel.getOrderHash();
            if (thatHash.size() > 0) {
                for (int i = 0; i < m; i++) {
                    QueryColumn column = columns.getQuick(i);
                    ExprNode node = column.getAst();
                    if (node.type == ExprNode.LITERAL) {
                        int direction = thatHash.get(node.token);
                        if (direction != -1) {
                            hash.put(column.getName(), direction);
                        }
                    }
                }
            }
        }
    }

    private int doReorderTables(QueryModel parent, IntList ordered) {
        tempCrossIndexes.clear();
        ordered.clear();
        this.orderingStack.clear();
        ObjList<QueryModel> joinModels = parent.getJoinModels();

        int cost = 0;

        for (int i = 0, n = joinModels.size(); i < n; i++) {
            QueryModel q = joinModels.getQuick(i);
            if (q.getJoinType() == QueryModel.JOIN_CROSS || q.getContext() == null || q.getContext().parents.size() == 0) {
                if (q.getDependencies().size() > 0) {
                    orderingStack.push(i);
                } else {
                    tempCrossIndexes.add(i);
                }
            } else {
                q.getContext().inCount = q.getContext().parents.size();
            }
        }

        while (orderingStack.notEmpty()) {
            //remove a node n from orderingStack
            int index = orderingStack.pop();

            ordered.add(index);

            QueryModel m = joinModels.getQuick(index);

            switch (m.getJoinType()) {
                case QueryModel.JOIN_CROSS:
                    cost += 10;
                    break;
                default:
                    cost += 5;
                    break;
            }

            IntHashSet dependencies = m.getDependencies();

            //for each node m with an edge e from n to m do
            for (int i = 0, k = dependencies.size(); i < k; i++) {
                int depIndex = dependencies.get(i);
                JoinContext jc = joinModels.getQuick(depIndex).getContext();
                if (--jc.inCount == 0) {
                    orderingStack.push(depIndex);
                }
            }
        }

        //Check to see if all edges are removed
        for (int i = 0, n = joinModels.size(); i < n; i++) {
            QueryModel m = joinModels.getQuick(i);
            if (m.getContext() != null && m.getContext().inCount > 0) {
                return Integer.MAX_VALUE;
            }
        }

        // add pure crosses at end of ordered table list
        for (int i = 0, n = tempCrossIndexes.size(); i < n; i++) {
            ordered.add(tempCrossIndexes.getQuick(i));
        }

        return cost;
    }

    private void emitAggregates(@Transient ExprNode node, QueryModel model) {

        this.exprNodeStack.clear();

        // pre-order iterative tree traversal
        // see: http://en.wikipedia.org/wiki/Tree_traversal

        while (!this.exprNodeStack.isEmpty() || node != null) {
            if (node != null) {

                if (node.rhs != null) {
                    ExprNode n = replaceIfAggregate(node.rhs, model);
                    if (node.rhs == n) {
                        this.exprNodeStack.push(node.rhs);
                    } else {
                        node.rhs = n;
                    }
                }

                ExprNode n = replaceIfAggregate(node.lhs, model);
                if (n == node.lhs) {
                    node = node.lhs;
                } else {
                    node.lhs = n;
                    node = null;
                }
            } else {
                node = this.exprNodeStack.poll();
            }
        }
    }

    private void emitLiterals(@Transient ExprNode node, QueryModel model) {

        this.exprNodeStack.clear();

        // pre-order iterative tree traversal
        // see: http://en.wikipedia.org/wiki/Tree_traversal

        while (!this.exprNodeStack.isEmpty() || node != null) {
            if (node != null) {

                if (node.rhs != null) {
                    ExprNode n = replaceLiteral(node.rhs, model);
                    if (node.rhs == n) {
                        this.exprNodeStack.push(node.rhs);
                    } else {
                        node.rhs = n;
                    }
                }

                ExprNode n = replaceLiteral(node.lhs, model);
                if (n == node.lhs) {
                    node = node.lhs;
                } else {
                    node.lhs = n;
                    node = null;
                }
            } else {
                node = this.exprNodeStack.poll();
            }
        }
    }

    private ParserException err(String msg) {
        return ParserException.$(lexer.position(), msg);
    }

    private ExprNode expectExpr() throws ParserException {
        ExprNode n = expr();
        if (n == null) {
            throw ParserException.$(lexer.position(), "Expression expected");
        }
        return n;
    }

    private ExprNode expectLiteral() throws ParserException {
        CharSequence tok = tok();
        int pos = lexer.position();
        validateLiteral(pos, tok);
        ExprNode node = exprNodePool.next();
        node.token = tok.toString();
        node.type = ExprNode.LITERAL;
        node.position = pos;
        return node;
    }

    private void expectTok(CharSequence tok, int position, CharSequence expected) throws ParserException {
        if (tok == null || !Chars.equals(tok, expected)) {
            throw ParserException.position(position).put('\'').put(expected).put("' expected");
        }
    }

    private void expectTok(CharSequence expected) throws ParserException {
        expectTok(tok(), lexer.position(), expected);
    }

    private void expectTok(char expected) throws ParserException {
        expectTok(tok(), lexer.position(), expected);
    }

    private void expectTok(CharSequence tok, int pos, char expected) throws ParserException {
        if (tok == null || !Chars.equals(tok, expected)) {
            throw ParserException.position(pos).put('\'').put(expected).put("' expected");
        }
    }

    private ExprNode expr() throws ParserException {
        astBuilder.reset();
        exprParser.parseExpr(lexer, astBuilder);
        return astBuilder.poll();
    }

    private CharSequence extractColumnName(CharSequence token, int dot) {
        return dot == -1 ? token : csPool.next().of(token, dot + 1, token.length() - dot - 1);
    }

    private int getIndexOfTableForColumn(QueryModel model, @Transient CharSequence tableAlias, CharSequence col, int position) throws ParserException {
//        CharSequence column = model.translateAlias(col);
        ObjList<QueryModel> joinModels = model.getJoinModels();
        int index = -1;
        if (tableAlias == null) {
            for (int i = 0, n = joinModels.size(); i < n; i++) {
                if (joinModels.getQuick(i).getColumnNameTypeMap().get(col) == -1) {
                    continue;
                }

                if (index != -1) {
                    throw ParserException.ambiguousColumn(position);
                }

                index = i;
            }

            if (index == -1) {
                throw ParserException.invalidColumn(position, col);
            }

            return index;
        } else {
            index = model.getAliasIndex(tableAlias);

            if (index == -1) {
                throw ParserException.$(position, "Invalid table name or alias");
            }

            if (joinModels.getQuick(index).getColumnNameTypeMap().get(col) == -1) {
                throw ParserException.invalidColumn(position, col);
            }

            return index;
        }
    }

    private QueryModel getOrParseQueryModelFromWithClause(WithClauseModel wcm) throws ParserException {
        QueryModel m = wcm.popModel();
        if (m != null) {
            return m;
        }

        secondaryLexer.setContent(lexer.getContent(), wcm.getLo(), wcm.getHi());

        Lexer tmp = this.lexer;
        this.lexer = secondaryLexer;
        try {
            return parseDml(true);
        } finally {
            lexer = tmp;
        }
    }

    private boolean hasAggregates(ExprNode node) {

        this.exprNodeStack.clear();

        // pre-order iterative tree traversal
        // see: http://en.wikipedia.org/wiki/Tree_traversal

        while (!this.exprNodeStack.isEmpty() || node != null) {
            if (node != null) {
                switch (node.type) {
                    case ExprNode.LITERAL:
                        node = null;
                        continue;
                    case ExprNode.FUNCTION:
                        if (FunctionFactories.isAggregate(node.token)) {
                            return true;
                        }
                        break;
                    default:
                        if (node.rhs != null) {
                            this.exprNodeStack.push(node.rhs);
                        }
                        break;
                }

                node = node.lhs;
            } else {
                node = this.exprNodeStack.poll();
            }
        }
        return false;
    }

    private void homogenizeCrossJoins(QueryModel parent) {
        ObjList<QueryModel> joinModels = parent.getJoinModels();
        for (int i = 0, n = joinModels.size(); i < n; i++) {
            QueryModel m = joinModels.getQuick(i);
            JoinContext c = m.getContext();

            if (m.getJoinType() == QueryModel.JOIN_CROSS) {
                if (c != null && c.parents.size() > 0) {
                    m.setJoinType(QueryModel.JOIN_INNER);
                }
            } else if (m.getJoinType() != QueryModel.JOIN_ASOF && (c == null || c.parents.size() == 0)) {
                m.setJoinType(QueryModel.JOIN_CROSS);
            }
        }
    }

    private boolean isFieldTerm(CharSequence tok) {
        return Chars.equals(tok, ')') || Chars.equals(tok, ',');
    }

    private ExprNode literal() {
        CharSequence tok = lexer.optionTok();
        if (tok == null) {
            return null;
        }
        return exprNodePool.next().of(ExprNode.LITERAL, Chars.stripQuotes(tok.toString()), 0, lexer.position());
    }

    private ExprNode makeJoinAlias(int index) {
        CharSink b = Misc.getThreadLocalBuilder();
        ExprNode node = exprNodePool.next();
        node.token = b.put(QueryModel.SUB_QUERY_ALIAS_PREFIX).put(index).toString();
        node.type = ExprNode.LITERAL;
        return node;
    }

    private ExprNode makeModelAlias(String modelAlias, ExprNode node) {
        CharSink b = Misc.getThreadLocalBuilder();
        ExprNode exprNode = exprNodePool.next();
        b.put(modelAlias).put('.').put(node.token);
        exprNode.token = b.toString();
        exprNode.type = ExprNode.LITERAL;
        exprNode.position = node.position;
        return exprNode;
    }

    private ExprNode makeOperation(String token, ExprNode lhs, ExprNode rhs) {
        ExprNode expr = exprNodePool.next();
        expr.token = token;
        expr.type = ExprNode.OPERATION;
        expr.position = 0;
        expr.paramCount = 2;
        expr.lhs = lhs;
        expr.rhs = rhs;
        return expr;
    }

    private JoinContext mergeContexts(QueryModel parent, JoinContext a, JoinContext b) {
        assert a.slaveIndex == b.slaveIndex;

        deletedContexts.clear();
        JoinContext r = contextPool.next();
        // check if we merging a.x = b.x to a.y = b.y
        // or a.x = b.x to a.x = b.y, e.g. one of columns in the same
        for (int i = 0, n = b.aNames.size(); i < n; i++) {

            CharSequence ban = b.aNames.getQuick(i);
            int bai = b.aIndexes.getQuick(i);
            ExprNode bao = b.aNodes.getQuick(i);

            CharSequence bbn = b.bNames.getQuick(i);
            int bbi = b.bIndexes.getQuick(i);
            ExprNode bbo = b.bNodes.getQuick(i);

            for (int k = 0, z = a.aNames.size(); k < z; k++) {

                if (deletedContexts.contains(k)) {
                    continue;
                }

                CharSequence aan = a.aNames.getQuick(k);
                int aai = a.aIndexes.getQuick(k);
                ExprNode aao = a.aNodes.getQuick(k);
                CharSequence abn = a.bNames.getQuick(k);
                int abi = a.bIndexes.getQuick(k);
                ExprNode abo = a.bNodes.getQuick(k);

                if (aai == bai && Chars.equals(aan, ban)) {
                    // a.x = ?.x
                    //  |     ?
                    // a.x = ?.y
                    addFilterOrEmitJoin(parent, k, abi, abn, abo, bbi, bbn, bbo);
                    break;
                } else if (abi == bai && Chars.equals(abn, ban)) {
                    // a.y = b.x
                    //    /
                    // b.x = a.x
                    addFilterOrEmitJoin(parent, k, aai, aan, aao, bbi, bbn, bbo);
                    break;
                } else if (aai == bbi && Chars.equals(aan, bbn)) {
                    // a.x = b.x
                    //     \
                    // b.y = a.x
                    addFilterOrEmitJoin(parent, k, abi, abn, abo, bai, ban, bao);
                    break;
                } else if (abi == bbi && Chars.equals(abn, bbn)) {
                    // a.x = b.x
                    //        |
                    // a.y = b.x
                    addFilterOrEmitJoin(parent, k, aai, aan, aao, bai, ban, bao);
                    break;
                }
            }
            r.aIndexes.add(bai);
            r.aNames.add(ban);
            r.aNodes.add(bao);
            r.bIndexes.add(bbi);
            r.bNames.add(bbn);
            r.bNodes.add(bbo);
            int max = bai > bbi ? bai : bbi;
            int min = bai < bbi ? bai : bbi;
            r.slaveIndex = max;
            r.parents.add(min);
            linkDependencies(parent, min, max);
        }

        // add remaining a nodes
        for (int i = 0, n = a.aNames.size(); i < n; i++) {
            int aai, abi, min, max;

            aai = a.aIndexes.getQuick(i);
            abi = a.bIndexes.getQuick(i);

            if (aai < abi) {
                min = aai;
                max = abi;
            } else {
                min = abi;
                max = aai;
            }

            if (deletedContexts.contains(i)) {
                if (!r.parents.contains(min)) {
                    unlinkDependencies(parent, min, max);
                }
            } else {
                r.aNames.add(a.aNames.getQuick(i));
                r.bNames.add(a.bNames.getQuick(i));
                r.aIndexes.add(aai);
                r.bIndexes.add(abi);
                r.aNodes.add(a.aNodes.getQuick(i));
                r.bNodes.add(a.bNodes.getQuick(i));

                r.parents.add(min);
                linkDependencies(parent, min, max);
            }
        }

        return r;
    }

    private JoinContext moveClauses(QueryModel parent, JoinContext from, JoinContext to, IntList positions) {
        int p = 0;
        int m = positions.size();

        if (m == 0) {
            return from;
        }

        JoinContext result = contextPool.next();
        result.slaveIndex = from.slaveIndex;

        for (int i = 0, n = from.aIndexes.size(); i < n; i++) {
            // logically those clauses we move away from "from" context
            // should not longer exist in "from", but instead of implementing
            // "delete" function, which would be manipulating underlying array
            // on every invocation, we copy retained clauses to new context,
            // which is "result".
            // hence whenever exists in "positions" we copy clause to "to"
            // otherwise copy to "result"
            JoinContext t = p < m && i == positions.getQuick(p) ? to : result;
            int ai = from.aIndexes.getQuick(i);
            int bi = from.bIndexes.getQuick(i);
            t.aIndexes.add(ai);
            t.aNames.add(from.aNames.getQuick(i));
            t.aNodes.add(from.aNodes.getQuick(i));
            t.bIndexes.add(bi);
            t.bNames.add(from.bNames.getQuick(i));
            t.bNodes.add(from.bNodes.getQuick(i));

            // either ai or bi is definitely belongs to this context
            if (ai != t.slaveIndex) {
                t.parents.add(ai);
                linkDependencies(parent, ai, bi);
            } else {
                t.parents.add(bi);
                linkDependencies(parent, bi, ai);
            }
        }

        return result;
    }

    private CharSequence notTermTok() throws ParserException {
        CharSequence tok = tok();
        if (isFieldTerm(tok)) {
            throw err("Invalid column definition");
        }
        return tok;
    }

    private QueryModel optimise(QueryModel model) throws ParserException {
        resolveJoinColumns(model);
        optimiseInvertedBooleans(model);
        collectJoinModelAliases(model);
        optimiseOrderBy(model, ORDER_BY_UNKNOWN);
        enumerateTableColumns(model);
        QueryModel rewrittenModel = rewriteSelectClause(model);
        optimiseSubQueries(rewrittenModel);
        createOrderHash(rewrittenModel);
        optimiseJoins(rewrittenModel);
        return rewrittenModel;
    }

    private ExprNode optimiseInvertedBooleans(final ExprNode node, boolean reverse) throws ParserException {
        switch (node.token) {
            case "not":
                if (reverse) {
                    return optimiseInvertedBooleans(node.rhs, false);
                } else {
                    switch (node.rhs.type) {
                        case ExprNode.LITERAL:
                        case ExprNode.CONSTANT:
                            return node;
                        default:
                            assertNotNull(node.rhs, node.position, "Missing right argument");
                            return optimiseInvertedBooleans(node.rhs, true);
                    }
                }
            case "and":
                if (reverse) {
                    node.token = "or";
                }
                assertNotNull(node.lhs, node.position, "Missing left argument");
                assertNotNull(node.rhs, node.position, "Missing right argument");
                node.lhs = optimiseInvertedBooleans(node.lhs, reverse);
                node.rhs = optimiseInvertedBooleans(node.rhs, reverse);
                return node;
            case "or":
                if (reverse) {
                    node.token = "and";
                }
                assertNotNull(node.lhs, node.position, "Missing left argument");
                assertNotNull(node.rhs, node.position, "Missing right argument");
                node.lhs = optimiseInvertedBooleans(node.lhs, reverse);
                node.rhs = optimiseInvertedBooleans(node.rhs, reverse);
                return node;
            case ">":
                if (reverse) {
                    node.token = "<=";
                }
                return node;
            case ">=":
                if (reverse) {
                    node.token = "<";
                }
                return node;

            case "<":
                if (reverse) {
                    node.token = ">=";
                }
                return node;
            case "<=":
                if (reverse) {
                    node.token = ">";
                }
                return node;
            case "=":
                if (reverse) {
                    node.token = "!=";
                }
                return node;
            case "!=":
                if (reverse) {
                    node.token = "=";
                }
                return node;
            default:
                if (reverse) {
                    ExprNode n = exprNodePool.next();
                    n.token = "not";
                    n.paramCount = 1;
                    n.rhs = node;
                    n.type = ExprNode.OPERATION;
                    return n;
                }
                return node;
        }
    }

    private void optimiseInvertedBooleans(QueryModel model) throws ParserException {
        ExprNode where = model.getWhereClause();
        if (where != null) {
            model.setWhereClause(optimiseInvertedBooleans(where, false));
        }

        if (model.getNestedModel() != null) {
            optimiseInvertedBooleans(model.getNestedModel());
        }

        ObjList<QueryModel> joinModels = model.getJoinModels();
        for (int i = 0, n = joinModels.size(); i < n; i++) {
            QueryModel m = joinModels.getQuick(i);
            if (m != model) {
                optimiseInvertedBooleans(joinModels.getQuick(i));
            }
        }
    }

    private void optimiseJoins(QueryModel model) throws ParserException {
        ObjList<QueryModel> joinModels = model.getJoinModels();

        int n = joinModels.size();
        if (n > 1) {
            emittedJoinClauses = joinClausesSwap1;

            // for sake of clarity, "model" model is the first in the list of
            // joinModels, e.g. joinModels.get(0) == model
            // only model model is allowed to have "where" clause
            // so we can assume that "where" clauses of joinModel elements are all null (except for element 0).
            // in case one of joinModels is subquery, its entire query model will be set as
            // nestedModel, e.g. "where" clause is still null there as well

            ExprNode where = model.getWhereClause();

            // clear where clause of model so that
            // optimiser can assign there correct nodes

            model.setWhereClause(null);
            processJoinConditions(model, where);

            for (int i = 1; i < n; i++) {
                processJoinConditions(model, joinModels.getQuick(i).getJoinCriteria());
            }

            if (emittedJoinClauses.size() > 0) {
                processEmittedJoinClauses(model);
            }

            createImpliedDependencies(model);
            reorderTables(model);
            homogenizeCrossJoins(model);
            assignFilters(model);
            alignJoinClauses(model);
            addTransitiveFilters(model);
        }

        if (model.getNestedModel() != null) {
            optimiseJoins(model.getNestedModel());
        }
    }

    private void optimiseOrderBy(QueryModel model, int orderByState) {
        ObjList<QueryColumn> columns = model.getColumns();
        int subQueryOrderByState;

        int n = columns.size();
        // determine if ordering is required
        switch (orderByState) {
            case ORDER_BY_UNKNOWN:
                // we have sample by, so expect sub-query has to be ordered
                subQueryOrderByState = ORDER_BY_REQUIRED;
                if (model.getSampleBy() == null) {
                    for (int i = 0; i < n; i++) {
                        QueryColumn col = columns.getQuick(i);
                        if (hasAggregates(col.getAst())) {
                            subQueryOrderByState = ORDER_BY_INVARIANT;
                            break;
                        }
                    }
                }
                break;
            case ORDER_BY_REQUIRED:
                // parent requires order
                // if this model forces ordering - sub-query ordering is not needed
                if (model.getOrderBy().size() > 0) {
                    subQueryOrderByState = ORDER_BY_INVARIANT;
                } else {
                    subQueryOrderByState = ORDER_BY_REQUIRED;
                }
                break;
            default:
                // sub-query ordering is not needed
                model.getOrderBy().clear();
                if (model.getSampleBy() != null) {
                    subQueryOrderByState = ORDER_BY_REQUIRED;
                } else {
                    subQueryOrderByState = ORDER_BY_INVARIANT;
                }
                break;
        }

        ObjList<QueryModel> jm = model.getJoinModels();
        for (int i = 0, k = jm.size(); i < k; i++) {
            QueryModel qm = jm.getQuick(i).getNestedModel();
            if (qm != null) {
                optimiseOrderBy(qm, subQueryOrderByState);
            }
        }
    }

    /**
     * Objective of this method is to move filters inside of sub-queries where
     * possible. This should reduce data flow into heavier algos such as
     * sorting and hashing. Method achieves this by analysing columns returned by
     * sub-queries and matching them to components of "where" clause.
     * <p/>
     *
     * @param model query model to analyse
     * @throws ParserException usually syntax exceptions
     */
    private void optimiseSubQueries(QueryModel model) throws ParserException {
        QueryModel nm;
        // We are taking advantage of the fact that first element of join model list
        // is the parent model itself.
        ObjList<QueryModel> jm = model.getJoinModels();
        ObjList<ExprNode> where = model.parseWhereClause();
        ExprNode thisWhere = null;

        // match each of where conditions to join models
        // if "where" matches two models, we have ambiguous column name
        for (int j = 0, k = where.size(); j < k; j++) {
            ExprNode node = where.getQuick(j);
            int matchModel = -1;
            for (int i = 0, n = jm.size(); i < n; i++) {
                QueryModel qm = jm.getQuick(i);
                nm = qm.getNestedModel();
                if (nm != null && literalMatcher.matches(node, nm.getColumnNameTypeMap(), qm.getAlias() != null ? qm.getAlias().token : null)) {
                    if (matchModel > -1) {
                        throw ParserException.ambiguousColumn(node.position);
                    }
                    matchModel = i;
                }
            }

            if (matchModel > -1) {
                nm = jm.getQuick(matchModel).getNestedModel();
                nm.setWhereClause(concatFilters(nm.getWhereClause(), node));
            } else {
                thisWhere = concatFilters(thisWhere, node);
            }
        }

        model.getParsedWhere().clear();
        model.setWhereClause(thisWhere);

        // recursively apply same logic to nested model of each of join model
        for (int i = 0, n = jm.size(); i < n; i++) {
            QueryModel qm = jm.getQuick(i);
            nm = qm.getNestedModel();
            if (nm != null) {
                optimiseSubQueries(nm);
            }
        }
    }

    private ParsedModel parseCreateStatement() throws ParserException {
        CharSequence tok = tok();
        if (Chars.equals(tok, "table")) {
            return parseCreateTable();
        }

        throw err("table expected");
    }

    private ParsedModel parseCreateTable() throws ParserException {
        final CreateTableModel model = createTableModelPool.next();

        ExprNode name = exprNodePool.next();
        name.token = Chars.stripQuotes(tok().toString());
        name.position = lexer.position();
        name.type = ExprNode.LITERAL;
        model.setName(name);

        CharSequence tok = tok();

        if (Chars.equals(tok, '(')) {
            lexer.unparse();
            parseTableFields(model);
        } else if (Chars.equals(tok, "as")) {
            expectTok('(');
            model.setQueryModel(parseDml(true));
            expectTok(')');
        } else {
            throw ParserException.position(lexer.position()).put("Unexpected token");
        }


        tok = lexer.optionTok();
        while (tok != null && Chars.equals(tok, ',')) {

            int pos = lexer.position();

            tok = tok();
            if (Chars.equals(tok, "index")) {
                expectTok('(');

                pos = lexer.position();
                ExprNode columnName = expectLiteral();
                final int columnIndex = model.getColumnIndex(columnName.token);
                if (columnIndex == -1) {
                    throw ParserException.invalidColumn(pos, columnName.token);
                }

                pos = lexer.position();
                tok = tok();
                if (Chars.equals(tok, "block")) {

                    expectTok("size");

                    try {
                        model.setIndexFlags(columnIndex, true, Numbers.ceilPow2(Numbers.parseInt(tok())) - 1);
                    } catch (NumericException e) {
                        throw ParserException.$(pos, "Int constant expected");
                    }
                    pos = lexer.position();
                    tok = tok();
                } else {
                    model.setIndexFlags(columnIndex, true, configuration.getIndexValueBlockSize());
                }
                expectTok(tok, pos, ')');

                tok = lexer.optionTok();
            } else if (Chars.equals(tok, "cast")) {
                expectTok('(');
                ColumnCastModel columnCastModel = columnCastModelPool.next();

                columnCastModel.setName(expectLiteral());
                expectTok("as");

                ExprNode node = expectLiteral();
                int type = ColumnType.columnTypeOf(node.token);
                if (type == -1) {
                    throw ParserException.$(node.position, "invalid type");
                }

                columnCastModel.setType(type, node.position);

                if (type == ColumnType.SYMBOL) {
                    tok = lexer.optionTok();
                    pos = lexer.position();

                    if (Chars.equals(tok, "count")) {
                        try {
                            columnCastModel.setCount(Numbers.parseInt(tok()));
                            tok = tok();
                        } catch (NumericException e) {
                            throw ParserException.$(pos, "int value expected");
                        }
                    }
                } else {
                    pos = lexer.position();
                    tok = tok();
                }

                expectTok(tok, pos, ')');

                if (!model.addColumnCastModel(columnCastModel)) {
                    throw ParserException.$(columnCastModel.getName().position, "duplicate cast");
                }

                tok = lexer.optionTok();
            } else {
                throw ParserException.$(pos, "Unexpected token");
            }
        }

        ExprNode timestamp = parseTimestamp(tok);
        if (timestamp != null) {
            model.setTimestamp(timestamp);
            tok = lexer.optionTok();
        }

        ExprNode partitionBy = parsePartitionBy(tok);
        if (partitionBy != null) {
            model.setPartitionBy(partitionBy);
            tok = lexer.optionTok();
        }

        ExprNode hint = parseRecordHint(tok);
        if (hint != null) {
            model.setRecordHint(hint);
            tok = lexer.optionTok();
        }

        if (tok != null) {
            throw ParserException.$(lexer.position(), "Unexpected token");
        }
        return model;
    }

    private QueryModel parseDml(boolean subQuery) throws ParserException {

        CharSequence tok;
        QueryModel model = queryModelPool.next();

        tok = tok();

        if (Chars.equals(tok, "with")) {
            parseWithClauses(model);
            tok = tok();
        }

        // [select]
        if (Chars.equals(tok, "select")) {
            model.setSelectModelType(QueryModel.SELECT_MODEL_CHOOSE);
            parseSelectClause(model);
            QueryModel nestedModel = queryModelPool.next();
            parseFromClause(nestedModel, subQuery);

            // keep "sample by" with select as this is an implicit aggregation column
            if (nestedModel.getSampleBy() != null) {
                model.setSampleBy(nestedModel.getSampleBy());
                nestedModel.setSampleBy(null);
            }

            model.setNestedModel(nestedModel);
            return model;
        } else {
            lexer.unparse();
            return parseFromClause(model, subQuery);
        }
    }

    private QueryModel parseFromClause(QueryModel model, boolean subQuery) throws ParserException {
        CharSequence tok = tok();
        // expect "(" in case of sub-query

        if (Chars.equals(tok, '(')) {
            model.setNestedModel(parseDml(true));

            // expect closing bracket
            expectTok(')');

            tok = lexer.optionTok();

            // check if tok is not "where" - should be alias

            if (tok != null && !tableAliasStop.contains(tok)) {
                lexer.unparse();
                model.setAlias(literal());
                tok = lexer.optionTok();
            }

            // expect [timestamp(column)]

            ExprNode timestamp = parseTimestamp(tok);
            if (timestamp != null) {
                model.setTimestamp(timestamp);
                tok = lexer.optionTok();
            }
        } else {

            lexer.unparse();

            parseTableName(model, model);

            tok = lexer.optionTok();

            if (tok != null && !tableAliasStop.contains(tok)) {
                lexer.unparse();
                model.setAlias(literal());
                tok = lexer.optionTok();
            }

            // expect [timestamp(column)]

            ExprNode timestamp = parseTimestamp(tok);
            if (timestamp != null) {
                model.setTimestamp(timestamp);
                tok = lexer.optionTok();
            }

            // expect [latest by]

            if (Chars.equalsNc("latest", tok)) {
                parseLatestBy(model);
                tok = lexer.optionTok();
            }
        }

        // expect multiple [[inner | outer | cross] join]

        int joinType;
        while (tok != null && (joinType = joinStartSet.get(tok)) != -1) {
            model.addJoinModel(parseJoin(tok, joinType, model));
            tok = lexer.optionTok();
        }

        // expect [where]

        if (tok != null && Chars.equals(tok, "where")) {
            model.setWhereClause(expr());
            tok = lexer.optionTok();
        }

        // expect [group by]

        if (tok != null && Chars.equals(tok, "sample")) {
            expectTok("by");
            model.setSampleBy(expectLiteral());
            tok = lexer.optionTok();
        }

        // expect [order by]

        if (tok != null && Chars.equals(tok, "order")) {
            expectTok("by");
            do {
                tok = tok();

                if (Chars.equals(tok, ')')) {
                    throw err("Expression expected");
                }

                lexer.unparse();
                ExprNode n = expectLiteral();

                tok = lexer.optionTok();

                if (tok != null && Chars.equalsIgnoreCase(tok, "desc")) {

                    model.addOrderBy(n, QueryModel.ORDER_DIRECTION_DESCENDING);
                    tok = lexer.optionTok();

                } else {

                    model.addOrderBy(n, QueryModel.ORDER_DIRECTION_ASCENDING);

                    if (tok != null && Chars.equalsIgnoreCase(tok, "asc")) {
                        tok = lexer.optionTok();
                    }
                }

                if (model.getOrderBy().size() >= MAX_ORDER_BY_COLUMNS) {
                    throw err("Too many columns");
                }

            } while (tok != null && Chars.equals(tok, ','));
        }

        // expect [limit]
        if (tok != null && Chars.equals(tok, "limit")) {
            ExprNode lo = expr();
            ExprNode hi = null;

            tok = lexer.optionTok();
            if (tok != null && Chars.equals(tok, ',')) {
                hi = expr();
                tok = lexer.optionTok();
            }
            model.setLimit(lo, hi);
        }

        if (subQuery) {
            lexer.unparse();
        } else if (tok != null) {
            throw ParserException.position(lexer.position()).put("Unexpected token: ").put(tok);
        }
        return model;
    }

    private CharSequence parseIndexDefinition(CreateTableModel model) throws ParserException {
        CharSequence tok = tok();

        if (isFieldTerm(tok)) {
            return tok;
        }

        expectTok(tok, lexer.position(), "index");

        if (isFieldTerm(tok = tok())) {
            model.setIndexFlags(true, configuration.getIndexValueBlockSize());
            return tok;
        }

        expectTok(tok, lexer.position(), "block");
        expectTok("size");

        try {
            model.setIndexFlags(true, Numbers.parseInt(tok()));
        } catch (NumericException e) {
            throw err("bad int");
        }

        return null;
    }

    private QueryModel parseJoin(CharSequence tok, int joinType, QueryModel parent) throws ParserException {
        QueryModel joinModel = queryModelPool.next();
        joinModel.setJoinType(joinType);

        if (!Chars.equals(tok, "join")) {
            expectTok("join");
        }

        tok = tok();

        if (Chars.equals(tok, '(')) {
            joinModel.setNestedModel(parseDml(true));
            expectTok(')');
        } else {
            lexer.unparse();
            parseTableName(joinModel, parent);
        }

        tok = lexer.optionTok();

        if (tok != null && !tableAliasStop.contains(tok)) {
            lexer.unparse();
            joinModel.setAlias(expr());
        } else {
            lexer.unparse();
        }

        tok = lexer.optionTok();

        if (joinType == QueryModel.JOIN_CROSS && tok != null && Chars.equals(tok, "on")) {
            throw ParserException.$(lexer.position(), "Cross joins cannot have join clauses");
        }

        switch (joinType) {
            case QueryModel.JOIN_ASOF:
                if (tok == null || !Chars.equals("on", tok)) {
                    lexer.unparse();
                    break;
                }
                // intentional fall through
            case QueryModel.JOIN_INNER:
            case QueryModel.JOIN_OUTER:
                expectTok(tok, lexer.position(), "on");
                astBuilder.reset();
                exprParser.parseExpr(lexer, astBuilder);
                ExprNode expr;
                switch (astBuilder.size()) {
                    case 0:
                        throw ParserException.$(lexer.position(), "Expression expected");
                    case 1:
                        expr = astBuilder.poll();
                        if (expr.type == ExprNode.LITERAL) {
                            do {
                                joinModel.addJoinColumn(expr);
                            } while ((expr = astBuilder.poll()) != null);
                        } else {
                            joinModel.setJoinCriteria(expr);
                        }
                        break;
                    default:
                        while ((expr = astBuilder.poll()) != null) {
                            if (expr.type != ExprNode.LITERAL) {
                                throw ParserException.$(lexer.position(), "Column name expected");
                            }
                            joinModel.addJoinColumn(expr);
                        }
                        break;
                }
                break;
            default:
                lexer.unparse();
        }

        return joinModel;
    }

    private void parseLatestBy(QueryModel model) throws ParserException {
        expectTok("by");
        model.setLatestBy(expr());
    }

    private ExprNode parsePartitionBy(CharSequence tok) throws ParserException {
        if (Chars.equalsNc("partition", tok)) {
            expectTok("by");
            return expectLiteral();
        }
        return null;
    }

    private ExprNode parseRecordHint(CharSequence tok) throws ParserException {
        if (Chars.equalsNc("record", tok)) {
            expectTok("hint");
            ExprNode hint = expectExpr();
            if (hint.type != ExprNode.CONSTANT) {
                throw ParserException.$(hint.position, "Constant expected");
            }
            return hint;
        }
        return null;
    }

    private ParsedModel parseRenameStatement() throws ParserException {
        expectTok("table");
        RenameTableModel model = renameTableModelPool.next();
        ExprNode e = expectExpr();
        if (e.type != ExprNode.LITERAL && e.type != ExprNode.CONSTANT) {
            throw ParserException.$(e.position, "literal or constant expected");
        }
        model.setFrom(e);
        expectTok("to");

        e = expectExpr();
        if (e.type != ExprNode.LITERAL && e.type != ExprNode.CONSTANT) {
            throw ParserException.$(e.position, "literal or constant expected");
        }
        model.setTo(e);
        return model;
    }

    private void parseSelectClause(QueryModel model) throws ParserException {
        CharSequence tok;
        while (true) {
            ExprNode expr = expr();
            if (expr == null) {
                throw ParserException.$(lexer.position(), "missing column");
            }

            String alias;
            int aliasPosition = lexer.position();

            tok = tok();

            if (!columnAliasStop.contains(tok)) {
                if (Chars.indexOf(tok, '.') != -1) {
                    throw ParserException.$(aliasPosition, "'.' is not allowed here");
                }
                alias = tok.toString();
                tok = tok();
            } else {
                alias = createColumnAlias(expr.token, model.getColumnNameTypeMap());
                aliasPosition = -1;
            }

            if (Chars.equals(tok, "over")) {
                // analytic
                expectTok('(');

                AnalyticColumn col = analyticColumnPool.next().of(alias, aliasPosition, expr);
                tok = tok();

                if (Chars.equals(tok, "partition")) {
                    expectTok("by");

                    ObjList<ExprNode> partitionBy = col.getPartitionBy();

                    do {
                        partitionBy.add(expectLiteral());
                        tok = tok();
                    } while (Chars.equals(tok, ','));
                }

                if (Chars.equals(tok, "order")) {
                    expectTok("by");

                    do {
                        ExprNode e = expectLiteral();
                        tok = tok();

                        if (Chars.equalsIgnoreCase(tok, "desc")) {
                            col.addOrderBy(e, QueryModel.ORDER_DIRECTION_DESCENDING);
                            tok = tok();
                        } else {
                            col.addOrderBy(e, QueryModel.ORDER_DIRECTION_ASCENDING);
                            if (Chars.equalsIgnoreCase(tok, "asc")) {
                                tok = tok();
                            }
                        }
                    } while (Chars.equals(tok, ','));
                }

                if (!Chars.equals(tok, ')')) {
                    throw err(") expected");
                }

                model.addColumn(col);

                tok = tok();
            } else {
                model.addColumn(queryColumnPool.next().of(alias, aliasPosition, expr));
            }

            if (Chars.equals(tok, "from")) {
                break;
            }

            if (!Chars.equals(tok, ',')) {
                throw err(",|from expected");
            }
        }
    }

    private void parseTableFields(CreateTableModel model) throws ParserException {
        if (!Chars.equals(tok(), '(')) {
            throw err("( expected");
        }

        while (true) {
            final int position = lexer.position();
            final String name = notTermTok().toString();
            final int type = ColumnType.columnTypeOf(notTermTok());

            if (!model.addColumn(name, type)) {
                throw ParserException.$(position, "Duplicate column");
            }

            CharSequence tok;
            switch (type) {
                case ColumnType.INT:
                case ColumnType.LONG:
                case ColumnType.STRING:
                case ColumnType.SYMBOL:
                    tok = parseIndexDefinition(model);
                    break;
                default:
                    tok = null;
                    break;
            }

            if (tok == null) {
                tok = tok();
            }

            if (Chars.equals(tok, ')')) {
                break;
            }

            if (!Chars.equals(tok, ',')) {
                throw err(", or ) expected");
            }
        }
    }

    private void parseTableName(QueryModel target, QueryModel model) throws ParserException {
        ExprNode tableName = literal();
        WithClauseModel withClause = tableName != null ? model.getWithClause(tableName.token) : null;
        if (withClause != null) {
            target.setNestedModel(getOrParseQueryModelFromWithClause(withClause));
        } else {
            target.setTableName(tableName);
        }
    }

    private ExprNode parseTimestamp(CharSequence tok) throws ParserException {
        if (Chars.equalsNc("timestamp", tok)) {
            expectTok('(');
            final ExprNode result = expectLiteral();
            expectTok(')');
            return result;
        }
        return null;
    }

    private void parseWithClauses(QueryModel model) throws ParserException {
        do {
            ExprNode name = expectLiteral();

            if (model.getWithClause(name.token) != null) {
                throw ParserException.$(name.position, "duplicate name");
            }

            expectTok("as");
            expectTok('(');
            int lo, hi;
            lo = lexer.position();
            QueryModel m = parseDml(true);
            hi = lexer.position();
            WithClauseModel wcm = withClauseModelPool.next();
            wcm.of(lo + 1, hi, m);
            expectTok(')');
            model.addWithClause(name.token, wcm);

            CharSequence tok = lexer.optionTok();
            if (tok == null || !Chars.equals(tok, ',')) {
                lexer.unparse();
                break;
            }
        } while (true);
    }

    private void processEmittedJoinClauses(QueryModel model) {
        // process emitted join conditions
        do {
            ObjList<JoinContext> clauses = emittedJoinClauses;

            if (clauses == joinClausesSwap1) {
                emittedJoinClauses = joinClausesSwap2;
            } else {
                emittedJoinClauses = joinClausesSwap1;
            }
            emittedJoinClauses.clear();
            for (int i = 0, k = clauses.size(); i < k; i++) {
                addJoinContext(model, clauses.getQuick(i));
            }
        } while (emittedJoinClauses.size() > 0);

    }

    /**
     * Splits "where" clauses into "and" concatenated list of boolean expressions.
     *
     * @param node expression n
     */
    private void processJoinConditions(QueryModel parent, ExprNode node) throws ParserException {
        ExprNode n = node;
        // pre-order traversal
        exprNodeStack.clear();
        while (!exprNodeStack.isEmpty() || n != null) {
            if (n != null) {
                switch (n.token) {
                    case "and":
                        if (n.rhs != null) {
                            exprNodeStack.push(n.rhs);
                        }
                        n = n.lhs;
                        break;
                    case "=":
                        analyseEquals(parent, n);
                        n = null;
                        break;
                    case "or":
                        processOrConditions(parent, n);
                        n = null;
                        break;
                    case "~":
                        analyseRegex(parent, n);
                        // intentional fallthrough
                    default:
                        parent.addParsedWhereNode(n);
                        n = null;
                        break;
                }
            } else {
                n = exprNodeStack.poll();
            }
        }
    }

    /**
     * There are two ways "or" conditions can go:
     * - all "or" conditions have at least one fields in common
     * e.g. a.x = b.x or a.x = b.y
     * this can be implemented as a hash join where master table is "b"
     * and slave table is "a" keyed on "a.x" so that
     * if HashTable contains all rows of "a" keyed on "a.x"
     * hash join algorithm can do:
     * rows = HashTable.get(b.x);
     * if (rows == null) {
     * rows = HashTable.get(b.y);
     * }
     * <p>
     * in this case tables can be reordered as long as "b" is processed
     * before "a"
     * <p>
     * - second possibility is where all "or" conditions are random
     * in which case query like this:
     * <p>
     * from a
     * join c on a.x = c.x
     * join b on a.x = b.x or c.y = b.y
     * <p>
     * can be rewritten to:
     * <p>
     * from a
     * join c on a.x = c.x
     * join b on a.x = b.x
     * union
     * from a
     * join c on a.x = c.x
     * join b on c.y = b.y
     */
    private void processOrConditions(QueryModel parent, ExprNode node) {
        // stub: use filter
        parent.addParsedWhereNode(node);
    }

    /**
     * Identify joined tables without join clause and try to find other reversible join clauses
     * that may be applied to it. For example when these tables joined"
     * <p>
     * from a
     * join b on c.x = b.x
     * join c on c.y = a.y
     * <p>
     * the system that prefers child table with lowest index will attribute c.x = b.x clause to
     * table "c" leaving "b" without clauses.
     */
    @SuppressWarnings({"StatementWithEmptyBody", "ConstantConditions"})
    private void reorderTables(QueryModel model) throws ParserException {
        ObjList<QueryModel> joinModels = model.getJoinModels();
        int n = joinModels.size();

        tempCrosses.clear();
        // collect crosses
        for (int i = 0; i < n; i++) {
            QueryModel q = joinModels.getQuick(i);
            if (q.getContext() == null || q.getContext().parents.size() == 0) {
                tempCrosses.add(i);
            }
        }

        int cost = Integer.MAX_VALUE;
        int root = -1;

        // analyse state of tree for each set of n-1 crosses
        for (int z = 0, zc = tempCrosses.size(); z < zc; z++) {
            for (int i = 0; i < zc; i++) {
                if (z != i) {
                    int to = tempCrosses.getQuick(i);
                    JoinContext jc = joinModels.getQuick(to).getContext();
                    // look above i up to OUTER join
                    for (int k = i - 1; k > -1 && swapJoinOrder(model, to, k, jc); k--) ;
                    // look below i for up to OUTER join
                    for (int k = i + 1; k < n && swapJoinOrder(model, to, k, jc); k++) ;
                }
            }

            IntList ordered = model.nextOrderedJoinModels();
            int thisCost = doReorderTables(model, ordered);
            if (thisCost < cost) {
                root = z;
                cost = thisCost;
                model.setOrderedJoinModels(ordered);
            }
        }

        if (root == -1) {
            throw ParserException.$(0, "Cycle");
        }
    }

    private ExprNode replaceIfAggregate(@Transient ExprNode node, QueryModel model) {
        if (node != null && FunctionFactories.isAggregate(node.token)) {
            QueryColumn c = queryColumnPool.next().of(createColumnAlias(node.token, model.getColumnNameTypeMap()), node.position, node);
            model.addColumn(c);
            return exprNodePool.next().of(ExprNode.LITERAL, c.getAlias(), 0, 0);
        }
        return node;
    }

    private ExprNode replaceLiteral(@Transient ExprNode node, QueryModel model) {
        if (node != null && node.type == ExprNode.LITERAL) {
            final CharSequenceObjHashMap<String> map = model.getColumnToAliasMap();
            int index = map.keyIndex(node.token);
            if (index > -1) {
                // this is the first time we see this column and must create alias
                String alias = createColumnAlias(node.token, model.getColumnNameTypeMap());
                model.addColumn(queryColumnPool.next().of(alias, node.position, node));
                return exprNodePool.next().of(ExprNode.LITERAL, alias, 0, node.position);
            }
            return exprNodePool.next().of(ExprNode.LITERAL, map.valueAt(index), 0, node.position);
        }
        return node;
    }

    private void resolveJoinColumns(QueryModel model) {
        ObjList<QueryModel> joinModels = model.getJoinModels();
        if (joinModels.size() < 2) {
            return;
        }

        String modelAlias;
        int defaultAliasCount = 0;

        if (model.getAlias() != null) {
            modelAlias = model.getAlias().token;
        } else if (model.getTableName() != null) {
            modelAlias = model.getTableName().token;
        } else {
            ExprNode alias = makeJoinAlias(defaultAliasCount++);
            model.setAlias(alias);
            modelAlias = alias.token;
        }

        for (int i = 1, n = joinModels.size(); i < n; i++) {
            QueryModel jm = joinModels.getQuick(i);

            ObjList<ExprNode> jc = jm.getJoinColumns();
            if (jc.size() > 0) {

                String jmAlias;

                if (jm.getAlias() != null) {
                    jmAlias = jm.getAlias().token;
                } else if (jm.getTableName() != null) {
                    jmAlias = jm.getTableName().token;
                } else {
                    ExprNode alias = makeJoinAlias(defaultAliasCount++);
                    jm.setAlias(alias);
                    jmAlias = alias.token;
                }

                ExprNode joinCriteria = jm.getJoinCriteria();
                for (int j = 0, m = jc.size(); j < m; j++) {
                    ExprNode node = jc.getQuick(j);
                    ExprNode eq = makeOperation("=", makeModelAlias(modelAlias, node), makeModelAlias(jmAlias, node));
                    if (joinCriteria == null) {
                        joinCriteria = eq;
                    } else {
                        joinCriteria = makeOperation("and", joinCriteria, eq);
                    }
                }
                jm.setJoinCriteria(joinCriteria);
            }
        }
    }

    private QueryModel rewriteSelectClause(QueryModel model) throws ParserException {
        QueryModel nestedModel = model.getNestedModel();
        if (nestedModel != null) {
            QueryModel rewritten = rewriteSelectClause(nestedModel);
            if (rewritten != nestedModel) {
                model.setNestedModel(rewritten);
            }
        }
        return model.getColumns().size() > 0 ? rewriteSelectClause0(model) : model;
    }

    @NotNull
    private QueryModel rewriteSelectClause0(QueryModel model) throws ParserException {
        assert model.getNestedModel() != null;

        QueryModel groupByModel = queryModelPool.next();
        groupByModel.setSelectModelType(QueryModel.SELECT_MODEL_GROUP_BY);
        groupByModel.setSampleBy(model.getSampleBy());
        QueryModel outerModel = queryModelPool.next();
        outerModel.setSelectModelType(QueryModel.SELECT_MODEL_VIRTUAL);
        QueryModel innerModel = queryModelPool.next();
        innerModel.setSelectModelType(QueryModel.SELECT_MODEL_VIRTUAL);
        QueryModel analyticModel = queryModelPool.next();
        analyticModel.setSelectModelType(QueryModel.SELECT_MODEL_ANALYTIC);
        QueryModel translatingModel = queryModelPool.next();
        translatingModel.setSelectModelType(QueryModel.SELECT_MODEL_CHOOSE);
        boolean useInnerModel = false;
        boolean useAnalyticModel = false;
        boolean useGroupByModel = false;
        boolean useOuterModel = false;

        ObjList<QueryColumn> columns = model.getColumns();

        // create virtual columns from select list
        for (int i = 0, k = columns.size(); i < k; i++) {
            final QueryColumn qc = columns.getQuick(i);
            final boolean analytic = qc instanceof AnalyticColumn;

            // fail-fast if this is an arithmetic expression where we expect analytic function
            if (analytic && qc.getAst().type != ExprNode.FUNCTION) {
                throw ParserException.$(qc.getAst().position, "Analytic function expected");
            }

            if (qc.getAst().type == ExprNode.LITERAL) {
                // in general sense we need to create new column in case
                // there is change of alias, for example we may have something as simple as
                // select a.f, b.f from ....
                QueryColumn column = queryColumnPool.next().of(
                        createColumnAlias(
                                qc.getAlias(),
                                translatingModel.getColumnNameTypeMap()
                        ),
                        0,
                        qc.getAst()
                );

                translatingModel.addColumn(column);

                QueryColumn translatedColumn = queryColumnPool.next().of(
                        column.getAlias(),
                        0,
                        // flatten the node
                        exprNodePool.next().of(ExprNode.LITERAL, column.getAlias(), 0, 0));
                // create column that references inner alias we just created
                innerModel.addColumn(translatedColumn);
                groupByModel.addColumn(translatedColumn);
                analyticModel.addColumn(translatedColumn);
                outerModel.addColumn(translatedColumn);
            } else {
                // Non-literal column
                // Strip AST of any literal references and replace with consistent names in
                // inner query model namespace
                emitLiterals(qc.getAst(), translatingModel);

                // when column is direct call to aggregation function, such as
                // select sum(x) ...
                // we can add it to group-by model right away
                if (qc.getAst().type == ExprNode.FUNCTION) {
                    if (analytic) {
                        analyticModel.addColumn(qc);
                        useAnalyticModel = true;
                        continue;
                    } else if (FunctionFactories.isAggregate(qc.getAst().token)) {
                        groupByModel.addColumn(qc);
                        // group-by column references might be needed when we have
                        // outer model supporting arithmetic such as:
                        // select sum(a)+sum(b) ....
                        QueryColumn groupByColumn = queryColumnPool.next().of(
                                qc.getAlias(),
                                0,
                                // flatten node down to alias
                                exprNodePool.next().of(ExprNode.LITERAL, qc.getAlias(), 0, 0));
                        outerModel.addColumn(groupByColumn);
                        useGroupByModel = true;
                        continue;
                    }
                }

                // this is not a direct call to aggregation function, in which case
                // we emit aggregation function into group-by model and leave the
                // rest in outer model
                int beforeSplit = groupByModel.getColumns().size();
                emitAggregates(qc.getAst(), groupByModel);
                if (beforeSplit < groupByModel.getColumns().size()) {
                    outerModel.addColumn(qc);
                    useGroupByModel = true;
                    useOuterModel = true;
                } else {
                    // there were no aggregation functions emitted therefore
                    // this is just a function that goes into virtual model
                    innerModel.addColumn(qc);
                    useInnerModel = true;

                    // we also create column that references this inner layer from outer layer,
                    // for example when we have:
                    // select a, b+c ...
                    // it should translate to:
                    // select a, x from (select a, b+c x from (select a,b,c ...))
                    QueryColumn innerColumn = queryColumnPool.next().of(
                            qc.getAlias(),
                            0,
                            exprNodePool.next().of(ExprNode.LITERAL, qc.getAlias(), 0, 0)
                    );
                    groupByModel.addColumn(innerColumn);
                    analyticModel.addColumn(innerColumn);
                    outerModel.addColumn(innerColumn);
                }
            }
        }
        // fail if we have both analytic and group-by models
        if (useAnalyticModel && useGroupByModel) {
            throw ParserException.$(0, "Analytic function is not allowed in context of aggregation. Use sub-query.");
        }

        // check if translating model is redundant, e.g.
        // that it neither chooses between tables nor renames columns
        boolean translationIsRedundant = useInnerModel || useGroupByModel || useAnalyticModel;
        if (translationIsRedundant) {
            for (int i = 0, n = translatingModel.getColumns().size(); i < n; i++) {
                QueryColumn column = translatingModel.getColumns().getQuick(i);
                if (!column.getAst().token.equals(column.getAlias())) {
                    translationIsRedundant = false;
                }
            }
        }

        QueryModel root;

        if (translationIsRedundant) {
            root = model.getNestedModel();
        } else {
            root = translatingModel;
            translatingModel.setNestedModel(model.getNestedModel());
        }

        if (useInnerModel) {
            innerModel.setNestedModel(root);
            root = innerModel;
        }

        if (useAnalyticModel) {
            analyticModel.setNestedModel(root);
            root = analyticModel;
        } else if (useGroupByModel) {
            groupByModel.setNestedModel(root);
            root = groupByModel;
            if (useOuterModel) {
                outerModel.setNestedModel(root);
                root = outerModel;
            }
        }

        return root;
    }

    /**
     * Moves reversible join clauses, such as a.x = b.x from table "from" to table "to".
     *
     * @param to      target table index
     * @param from    source table index
     * @param context context of target table index
     * @return false if "from" is outer joined table, otherwise - true
     */
    private boolean swapJoinOrder(QueryModel parent, int to, int from, final JoinContext context) {
        ObjList<QueryModel> joinModels = parent.getJoinModels();
        QueryModel jm = joinModels.getQuick(from);
        if (joinBarriers.contains(jm.getJoinType())) {
            return false;
        }

        JoinContext jc = context;
        clausesToSteal.clear();

        JoinContext that = jm.getContext();
        if (that != null && that.parents.contains(to)) {
            int zc = that.aIndexes.size();
            for (int z = 0; z < zc; z++) {
                if (that.aIndexes.getQuick(z) == to || that.bIndexes.getQuick(z) == to) {
                    clausesToSteal.add(z);
                }
            }

            if (clausesToSteal.size() < zc) {
                QueryModel target = joinModels.getQuick(to);
                target.getDependencies().clear();
                if (jc == null) {
                    target.setContext(jc = contextPool.next());
                }
                jc.slaveIndex = to;
                jm.setContext(moveClauses(parent, that, jc, clausesToSteal));
                if (target.getJoinType() == QueryModel.JOIN_CROSS) {
                    target.setJoinType(QueryModel.JOIN_INNER);
                }
            }
        }
        return true;
    }

    private CharSequence tok() throws ParserException {
        CharSequence tok = lexer.optionTok();
        if (tok == null) {
            throw err("Unexpected end of input");
        }
        return tok;
    }

    private void traverseNamesAndIndices(QueryModel parent, ExprNode node) throws ParserException {
        literalCollectorAIndexes.clear();
        literalCollectorBIndexes.clear();

        literalCollectorANames.clear();
        literalCollectorBNames.clear();

        literalCollector.withParent(parent);
        literalCollector.resetNullCount();
        traversalAlgo.traverse(node.lhs, literalCollector.lhs());
        traversalAlgo.traverse(node.rhs, literalCollector.rhs());
    }

    private void validateLiteral(int pos, CharSequence tok) throws ParserException {
        switch (tok.charAt(0)) {
            case '(':
            case ')':
            case ',':
            case '`':
            case '"':
            case '\'':
                throw ParserException.$(pos, "literal expected");
            default:
                break;

        }
    }

    private class LiteralCollector implements PostOrderTreeTraversalAlgo.Visitor {
        private IntList indexes;
        private ObjList<CharSequence> names;
        private int nullCount;
        private QueryModel parent;

        @Override
        public void visit(ExprNode node) throws ParserException {
            switch (node.type) {
                case ExprNode.LITERAL:
                    int dot = node.token.indexOf('.');
                    CharSequence name = extractColumnName(node.token, dot);
                    indexes.add(getIndexOfTableForColumn(parent, dot == -1 ? null : csPool.next().of(node.token, 0, dot), name, node.position));
                    if (names != null) {
                        names.add(name);
                    }
                    break;
                case ExprNode.CONSTANT:
                    if (nullConstants.contains(node.token)) {
                        nullCount++;
                    }
                    break;
                default:
                    break;
            }
        }

        private PostOrderTreeTraversalAlgo.Visitor lhs() {
            indexes = literalCollectorAIndexes;
            names = literalCollectorANames;
            return this;
        }

        private void resetNullCount() {
            nullCount = 0;
        }

        private PostOrderTreeTraversalAlgo.Visitor rhs() {
            indexes = literalCollectorBIndexes;
            names = literalCollectorBNames;
            return this;
        }

        private PostOrderTreeTraversalAlgo.Visitor to(IntList indexes) {
            this.indexes = indexes;
            this.names = null;
            return this;
        }

        private void withParent(QueryModel parent) {
            this.parent = parent;
        }
    }

    static {
        nullConstants.add("null");
        nullConstants.add("NaN");
    }

    static {
        joinBarriers = new IntHashSet();
        joinBarriers.add(QueryModel.JOIN_OUTER);
        joinBarriers.add(QueryModel.JOIN_ASOF);
    }

    static {
        tableAliasStop.add("where");
        tableAliasStop.add("latest");
        tableAliasStop.add("join");
        tableAliasStop.add("inner");
        tableAliasStop.add("outer");
        tableAliasStop.add("asof");
        tableAliasStop.add("cross");
        tableAliasStop.add("sample");
        tableAliasStop.add("order");
        tableAliasStop.add("on");
        tableAliasStop.add("timestamp");
        tableAliasStop.add("limit");
        tableAliasStop.add(")");
        //
        columnAliasStop.add("from");
        columnAliasStop.add(",");
        columnAliasStop.add("over");
        //
        groupByStopSet.add("order");
        groupByStopSet.add(")");
        groupByStopSet.add(",");

        joinStartSet.put("join", QueryModel.JOIN_INNER);
        joinStartSet.put("inner", QueryModel.JOIN_INNER);
        joinStartSet.put("outer", QueryModel.JOIN_OUTER);
        joinStartSet.put("cross", QueryModel.JOIN_CROSS);
        joinStartSet.put("asof", QueryModel.JOIN_ASOF);
    }
}
