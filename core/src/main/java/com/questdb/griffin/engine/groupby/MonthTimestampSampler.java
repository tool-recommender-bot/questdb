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

package com.questdb.griffin.engine.groupby;

import com.questdb.std.microtime.Dates;

class MonthTimestampSampler implements TimestampSampler {
    private final int bucket;

    MonthTimestampSampler(int bucket) {
        this.bucket = bucket;
    }

    @Override
    public long nextTimestamp(long timestamp) {
        return Dates.addMonths(timestamp, bucket);
    }

    @Override
    public long previousTimestamp(long timestamp) {
        return Dates.addMonths(timestamp, -bucket);
    }

    @Override
    public long round(long value) {
        int y = Dates.getYear(value);
        boolean l = Dates.isLeapYear(y);
        int m = Dates.getMonthOfYear(value, y, l);
        // target month
        int n = ((m - 1) / bucket) * bucket + 1;
        return Dates.yearMicros(y, l) +
                Dates.monthOfYearMicros(n, l);
    }
}
