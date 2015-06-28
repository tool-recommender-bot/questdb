/*******************************************************************************
 *   _  _ ___ ___     _ _
 *  | \| | __/ __| __| | |__
 *  | .` | _|\__ \/ _` | '_ \
 *  |_|\_|_| |___/\__,_|_.__/
 *
 *  Copyright (c) 2014-2015. The NFSdb project and its contributors.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 ******************************************************************************/
package com.nfsdb.ql.ops;

import com.nfsdb.ql.Record;
import com.nfsdb.storage.ColumnType;

public class AddLongOperator extends AbstractBinaryOperator {

    public AddLongOperator() {
        super(ColumnType.LONG);
    }

    @Override
    public double getDouble(Record rec) {
        long l = lhs.getLong(rec);
        long r = rhs.getLong(rec);
        return l == Long.MIN_VALUE || r == Long.MIN_VALUE ? Double.NaN : l + r;
    }

    @Override
    public long getLong(Record rec) {
        long l = lhs.getLong(rec);
        long r = rhs.getLong(rec);
        return l == Long.MIN_VALUE || r == Long.MIN_VALUE ? Long.MIN_VALUE : l + r;
    }
}