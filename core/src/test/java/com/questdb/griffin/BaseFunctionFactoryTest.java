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

import com.questdb.cairo.GenericRecordMetadata;
import com.questdb.cairo.sql.Function;
import com.questdb.griffin.model.ExpressionNode;
import com.questdb.griffin.model.QueryModel;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;

import java.util.ArrayList;

public class BaseFunctionFactoryTest extends AbstractGriffinTest {
    protected static final ArrayList<FunctionFactory> functions = new ArrayList<>();
    protected final static QueryModel queryModel = QueryModel.FACTORY.newInstance();
    protected static final TestExecutionContext sqlExecutionContext = new TestExecutionContext(compiler.getCodeGenerator());

    @Before
    public void setUp4() {
        sqlExecutionContext.clear();
        functions.clear();
    }

    protected static Function parseFunction(CharSequence expression, GenericRecordMetadata metadata, FunctionParser functionParser) throws SqlException {
        return functionParser.parseFunction(expr(expression), metadata, sqlExecutionContext);
    }

    protected static ExpressionNode expr(CharSequence expression) throws SqlException {
        queryModel.clear();
        return compiler.parseExpression(expression, queryModel);
    }

    @NotNull
    protected FunctionParser createFunctionParser() {
        return new FunctionParser(configuration, functions);
    }
}
