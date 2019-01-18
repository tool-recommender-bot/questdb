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

package com.questdb.cutlass.text.types;

import com.questdb.cairo.TableWriter;
import com.questdb.std.Numbers;
import com.questdb.std.NumericException;
import com.questdb.std.str.DirectByteCharSequence;
import com.questdb.store.ColumnType;

public final class DoubleAdapter implements TypeAdapter {

    public static final DoubleAdapter INSTANCE = new DoubleAdapter();

    private DoubleAdapter() {
    }

    @Override
    public int getType() {
        return ColumnType.DOUBLE;
    }

    @Override
    public boolean probe(CharSequence text) {
        if (text.length() > 2 && text.charAt(0) == '0' && text.charAt(1) != '.') {
            return false;
        }
        try {
            Numbers.parseDouble(text);
            return true;
        } catch (NumericException e) {
            return false;
        }
    }

    @Override
    public void write(TableWriter.Row row, int column, DirectByteCharSequence value) throws Exception {
        row.putDouble(column, Numbers.parseDouble(value));
    }

    @Override
    public String toString() {
        return "DOUBLE";
    }
}
