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

package com.questdb.griffin.engine.functions.math;

import com.questdb.cairo.CairoConfiguration;
import com.questdb.cairo.sql.Record;
import com.questdb.griffin.Function;
import com.questdb.griffin.FunctionFactory;
import com.questdb.griffin.engine.functions.ByteFunction;
import com.questdb.std.ObjList;

public class AddByteFunctionFactory implements FunctionFactory {
    @Override
    public String getSignature() {
        return "+(BB)";
    }

    @Override
    public Function newInstance(ObjList<Function> args, int position, CairoConfiguration configuration1) {
        return new AddShortVVFunc(position, args.getQuick(0), args.getQuick(1));
    }

    private static class AddShortVVFunc extends ByteFunction {
        final Function left;
        final Function right;

        public AddShortVVFunc(int position, Function left, Function right) {
            super(position);
            this.left = left;
            this.right = right;
        }

        @Override
        public byte getByte(Record rec) {
            return (byte) (left.getByte(rec) + right.getByte(rec));
        }

        @Override
        public boolean isConstant() {
            return left.isConstant() && right.isConstant();
        }
    }
}
