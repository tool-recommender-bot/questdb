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

package com.questdb.ql.sys;

import com.questdb.store.PartitionBy;
import com.questdb.store.SymbolTable;

public class PartitionBySymbolTable implements SymbolTable {

    public static final PartitionBySymbolTable INSTANCE = new PartitionBySymbolTable();

    @Override
    public int getQuick(CharSequence value) {
        return PartitionBy.fromString(value);
    }

    @Override
    public int size() {
        return PartitionBy.count();
    }

    @Override
    public String value(int key) {
        return key == SymbolTable.VALUE_IS_NULL ? null : PartitionBy.toString(key);
    }
}
