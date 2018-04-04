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

package com.questdb.parser.typeprobe;

import com.questdb.common.ColumnType;
import com.questdb.std.NumericException;
import com.questdb.std.time.DateFormat;
import com.questdb.std.time.DateFormatFactory;
import com.questdb.std.time.DateLocale;

public class DateProbe implements TypeProbe {
    private final String pattern;
    private final DateLocale dateLocale;
    private final DateFormat format;

    public DateProbe(DateFormatFactory dateFormatFactory, DateLocale dateLocale, String pattern) {
        this.dateLocale = dateLocale;
        this.pattern = pattern;
        this.format = dateFormatFactory.get(pattern);
    }

    @Override
    public DateFormat getDateFormat() {
        return format;
    }

    @Override
    public DateLocale getDateLocale() {
        return dateLocale;
    }

    @Override
    public String getFormat() {
        return pattern;
    }

    @Override
    public int getType() {
        return ColumnType.DATE;
    }

    @Override
    public boolean probe(CharSequence text) {
        try {
            format.parse(text, dateLocale);
            return true;
        } catch (NumericException e) {
            return false;
        }
    }
}
