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

package com.questdb.ql.ops.round;

import com.questdb.common.ColumnType;
import com.questdb.common.Record;
import com.questdb.ql.ops.AbstractBinaryOperator;
import com.questdb.ql.ops.Function;
import com.questdb.ql.ops.VirtualColumnFactory;
import com.questdb.std.Numbers;
import com.questdb.std.NumericException;

public class RoundHalfEvenFunction extends AbstractBinaryOperator {

    public final static VirtualColumnFactory<Function> FACTORY = (position, configuration) -> new RoundHalfEvenFunction(position);

    private RoundHalfEvenFunction(int position) {
        super(ColumnType.DOUBLE, position);
    }

    @Override
    public double getDouble(Record rec) {
        try {
            return Numbers.roundHalfEven(lhs.getDouble(rec), rhs.getInt(rec));
        } catch (NumericException e) {
            return Double.NaN;
        }
    }
}
