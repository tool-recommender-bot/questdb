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

import com.questdb.std.Mutable;
import com.questdb.std.ObjectFactory;

public class WithClauseModel implements Mutable {

    public static final ObjectFactory<WithClauseModel> FACTORY = WithClauseModel::new;
    private int lo;
    private int hi;
    private QueryModel model;

    private WithClauseModel() {
    }

    @Override
    public void clear() {
        this.lo = this.hi = 0;
        this.model = null;
    }

    public int getHi() {
        return hi;
    }

    public int getLo() {
        return lo;
    }

    public void of(int lo, int hi, QueryModel model) {
        this.lo = lo;
        this.hi = hi;
        this.model = model;
    }

    public QueryModel popModel() {
        QueryModel m = this.model;
        this.model = null;
        return m;
    }
}
