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

package com.questdb.ql.ops.col;

import com.questdb.ql.ops.AbstractVirtualColumn;
import com.questdb.store.ColumnType;
import com.questdb.store.Record;
import com.questdb.store.StorageFacade;
import com.questdb.store.SymbolTable;

public class SymRecordSourceColumn extends AbstractVirtualColumn {
    private final int index;
    private SymbolTable symbolTable;

    public SymRecordSourceColumn(int index, int position) {
        super(ColumnType.SYMBOL, position);
        this.index = index;
    }

    @Override
    public CharSequence getFlyweightStr(Record rec) {
        return rec.getSym(index);
    }

    @Override
    public CharSequence getFlyweightStrB(Record rec) {
        return rec.getSym(index);
    }

    @Override
    public int getInt(Record rec) {
        return rec.getInt(index);
    }

    @Override
    public CharSequence getSym(Record rec) {
        return rec.getSym(index);
    }

    @Override
    public SymbolTable getSymbolTable() {
        return symbolTable;
    }

    @Override
    public boolean isConstant() {
        return false;
    }

    @Override
    public void prepare(StorageFacade facade) {
        this.symbolTable = facade.getSymbolTable(index);
    }
}
