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

package com.questdb.griffin.lexer;

import com.questdb.griffin.common.ExprNode;
import com.questdb.std.LongList;
import com.questdb.std.microtime.DateFormatUtils;
import com.questdb.std.str.StringSink;

public class GriffinParserTestUtils {
    private static final StringSink sink = new StringSink();

    public static CharSequence intervalToString(LongList intervals) {
        sink.clear();
        sink.put('[');
        for (int i = 0, n = intervals.size(); i < n; i += 2) {
            if (i > 0) {
                sink.put(',');
            }
            sink.put('{');
            sink.put("lo=");
            DateFormatUtils.appendDateTimeUSec(sink, intervals.getQuick(i));
            sink.put(", ");
            sink.put("hi=");
            DateFormatUtils.appendDateTimeUSec(sink, intervals.getQuick(i + 1));
            sink.put('}');
        }
        sink.put(']');
        return sink;
    }

    public static String toRpn(ExprNode node) {
        switch (node.paramCount) {
            case 0:
                return node.token.toString();
            case 1:
                return toRpn(node.rhs) + node.token.toString();
            case 2:
                return toRpn(node.lhs) + toRpn(node.rhs) + node.token.toString();
            default:
                StringBuilder result = new StringBuilder();
                for (int i = 0; i < node.paramCount; i++) {
                    result.insert(0, toRpn(node.args.getQuick(i)));
                }
                return result.append(node.token).toString();
        }
    }

}
