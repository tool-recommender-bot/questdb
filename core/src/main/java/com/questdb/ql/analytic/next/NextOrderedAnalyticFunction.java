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

package com.questdb.ql.analytic.next;

import com.questdb.ql.analytic.AbstractOrderedAnalyticFunction;
import com.questdb.ql.ops.VirtualColumn;
import com.questdb.store.Record;

public class NextOrderedAnalyticFunction extends AbstractOrderedAnalyticFunction {
    private long prevRow = -1;

    public NextOrderedAnalyticFunction(int pageSize, VirtualColumn valueColumn) {
        super(pageSize, valueColumn);
    }

    @Override
    public void add(Record record) {
        long row = record.getRowId();
        if (prevRow != -1) {
//            DirectMap.KeyWriter kw = map.keyWriter();
//            kw.putLong(prevRow);
            map.locate(prevRow);
            map.getOrCreateValues().putLong(0, row);
        }
        prevRow = row;
    }

    @Override
    public void toTop() {
        prevRow = -1;
    }

    @Override
    public void reset() {
        super.reset();
        prevRow = -1;
    }
}
