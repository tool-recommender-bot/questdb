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

package com.questdb.griffin.engine.functions.constants;

import com.questdb.cairo.sql.Record;
import com.questdb.griffin.engine.functions.StrFunction;
import com.questdb.std.Chars;

public class StrConstant extends StrFunction {
    private final String value;

    public StrConstant(int positiom, CharSequence value) {
        super(positiom);
        if (Chars.startsWith(value, '\'')) {
            this.value = Chars.toString(value, 1, value.length() - 1);
        } else {
            this.value = Chars.toString(value);
        }
    }

    @Override
    public CharSequence getStr(Record rec) {
        return value;
    }

    @Override
    public CharSequence getStrB(Record rec) {
        return value;
    }

    @Override
    public boolean isConstant() {
        return true;
    }
}
