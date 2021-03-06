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
import com.questdb.griffin.FunctionFactory;
import com.questdb.griffin.SqlException;
import com.questdb.griffin.engine.functions.ShortFunction;
import com.questdb.griffin.engine.functions.StatelessFunction;
import com.questdb.std.ObjList;
import com.questdb.std.Rnd;

public class RndShortCCFunctionFactory implements FunctionFactory {

    @Override
    public String getSignature() {
        return "rnd_short(ii)";
    }

    @Override
    public Function newInstance(ObjList<Function> args, int position, CairoConfiguration configuration) throws SqlException {

        short lo = (short) args.getQuick(0).getInt(null);
        short hi = (short) args.getQuick(1).getInt(null);

        if (lo < hi) {
            return new RndFunction(position, lo, hi, configuration);
        }

        throw SqlException.position(position).put("invalid range");
    }

    private static class RndFunction extends ShortFunction implements StatelessFunction {
        private final short lo;
        private final short range;
        private final Rnd rnd;

        public RndFunction(int position, short lo, short hi, CairoConfiguration configuration) {
            super(position);
            this.lo = lo;
            this.range = (short) (hi - lo + 1);
            this.rnd = SharedRandom.getRandom(configuration);
        }

        @Override
        public short getShort(Record rec) {
            short s = rnd.nextShort();
            if (s < 0) {
                return (short) (lo - s % range);
            }
            return (short) (lo + s % range);
        }
    }
}
