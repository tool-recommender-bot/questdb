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

package com.questdb.griffin.lexer.model;

import com.questdb.griffin.common.ExprNode;
import com.questdb.std.Mutable;
import com.questdb.std.ObjectFactory;

public class QueryColumn implements Mutable {
    public final static ObjectFactory<QueryColumn> FACTORY = QueryColumn::new;
    private String alias;
    private ExprNode ast;

    protected QueryColumn() {
    }

    @Override
    public void clear() {
        alias = null;
        ast = null;
    }

    public String getAlias() {
        return alias;
    }

    public ExprNode getAst() {
        return ast;
    }

    public String getName() {
        return alias != null ? alias : ast.token;
    }

    public QueryColumn of(String alias, ExprNode ast) {
        this.alias = alias;
        this.ast = ast;
        return this;
    }
}
