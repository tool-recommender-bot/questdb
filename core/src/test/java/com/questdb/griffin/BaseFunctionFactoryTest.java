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
import com.questdb.common.RecordColumnMetadata;
import com.questdb.common.SymbolTable;
import com.questdb.griffin.engine.functions.bind.BindVariableService;
import com.questdb.ql.CollectionRecordMetadata;
import com.questdb.std.GenericLexer;
import com.questdb.std.ObjectPool;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;

import java.util.ArrayList;

public class BaseFunctionFactoryTest extends AbstractCairoTest {
    protected static final ArrayList<FunctionFactory> functions = new ArrayList<>();
    protected static final BindVariableService bindVariableService = new BindVariableService();
    private static final ExpressionLinker linker = new ExpressionLinker();
    private static final ObjectPool<SqlNode> nodePool = new ObjectPool<>(SqlNode.FACTORY, 128);
    private static final GenericLexer lexer = new GenericLexer();
    private static final ExpressionLexer parser = new ExpressionLexer(nodePool);

    @Before
    public void setUp2() {
        bindVariableService.clear();
        nodePool.clear();
        ExpressionLexer.configureLexer(lexer);
        functions.clear();
    }

    protected static Function parseFunction(CharSequence expression, CollectionRecordMetadata metadata, FunctionParser functionParser) throws SqlException {
        return functionParser.parseFunction(expr(expression), metadata, bindVariableService);
    }

    protected static SqlNode expr(CharSequence expression) throws SqlException {
        lexer.of(expression);
        linker.reset();
        parser.parseExpr(lexer, linker);
        return linker.poll();
    }

    @NotNull
    protected FunctionParser createFunctionParser() {
        return new FunctionParser(configuration, functions);
    }

    protected static class TestColumnMetadata implements RecordColumnMetadata {
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
