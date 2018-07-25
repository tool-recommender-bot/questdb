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

package com.questdb.griffin.engine.functions.rnd;

import com.questdb.cairo.CairoConfiguration;
import com.questdb.cairo.sql.Function;
import com.questdb.cairo.sql.Record;
import com.questdb.common.ColumnType;
import com.questdb.griffin.FunctionFactory;
import com.questdb.griffin.SqlException;
import com.questdb.griffin.engine.functions.StatelessFunction;
import com.questdb.griffin.engine.functions.StrFunction;
import com.questdb.std.Chars;
import com.questdb.std.ObjList;
import com.questdb.std.Rnd;

public class RndStringlListFunctionFactory implements FunctionFactory {
    @Override
    public String getSignature() {
        return "rnd_str(V)";
    }

    @Override
    public Function newInstance(ObjList<Function> args, int position, CairoConfiguration configuration) throws SqlException {
        final ObjList<String> symbols = new ObjList<>(args.size());
        for (int i = 0, n = args.size(); i < n; i++) {
            Function f = args.getQuick(i);
            if (f.getType() != ColumnType.STRING || !f.isConstant()) {
                throw SqlException.$(f.getPosition(), "STRING constant expected");
            }
            symbols.add(Chars.toString(f.getStr(null)));
        }

        return new Func(position, symbols, configuration);
    }

    private static final class Func extends StrFunction implements StatelessFunction {
        private final ObjList<String> symbols;
        private final Rnd rnd;
        private final int count;

        public Func(int position, ObjList<String> symbols, CairoConfiguration configuration) {
            super(position);
            this.rnd = SharedRandom.getRandom(configuration);
            this.symbols = symbols;
            this.count = symbols.size();
        }

        @Override
        public CharSequence getStr(Record rec) {
            return symbols.getQuick(rnd.nextPositiveInt() % count);
        }

        @Override
        public CharSequence getStrB(Record rec) {
            return getStr(rec);
        }
    }
}
