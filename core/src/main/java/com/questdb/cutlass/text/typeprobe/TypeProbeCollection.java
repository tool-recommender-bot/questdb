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

package com.questdb.cutlass.text.typeprobe;

import com.questdb.cairo.CairoException;
import com.questdb.log.Log;
import com.questdb.log.LogFactory;
import com.questdb.std.*;
import com.questdb.std.str.DirectByteCharSequence;
import com.questdb.std.str.DirectCharSink;
import com.questdb.std.str.Path;
import com.questdb.std.time.DateFormatFactory;
import com.questdb.std.time.DateFormatUtils;
import com.questdb.std.time.DateLocale;
import com.questdb.std.time.DateLocaleFactory;
import com.questdb.store.ColumnType;

public class TypeProbeCollection implements Mutable {
    private static final Log LOG = LogFactory.getLog(TypeProbeCollection.class);

    private static final ObjList<String> DEFAULT_DATE_FORMATS = new ObjList<>();
    private final ObjList<TypeProbe> probes = new ObjList<>();
    private final int probeCount;
    private final StringProbe stringProbe;
    private final DirectCharSink utf8Sink;
    private final BooleanProbe booleanProbe = new BooleanProbe();
    private final DoubleProbe doubleProbe = new DoubleProbe();
    private final IntProbe intProbe = new IntProbe();
    private final LongProbe longProbe = new LongProbe();
    private final ShortProbe shortProbe = new ShortProbe();
    private final ObjectPool<DateProbe> dateProbePool;
    private final FloatProbe floatProbe = new FloatProbe();
    private final ByteProbe byteProbe = new ByteProbe();
    private final BadDateProbe badDateProbe = new BadDateProbe();
    private final SymbolProbe symbolProbe;

    public TypeProbeCollection(DirectCharSink utf8Sink) {
        this.utf8Sink = utf8Sink;
        this.dateProbePool = new ObjectPool<>(() -> new DateProbe(utf8Sink), 16);
        this.stringProbe = new StringProbe(utf8Sink);
        this.symbolProbe = new SymbolProbe(utf8Sink);
        addDefaultProbes();
        DateFormatFactory dateFormatFactory = new DateFormatFactory();
        DateLocale dateLocale = DateLocaleFactory.INSTANCE.getDefaultDateLocale();
        for (int i = 0, n = DEFAULT_DATE_FORMATS.size(); i < n; i++) {
            probes.add(new DateProbe(utf8Sink).of(dateFormatFactory.get(DEFAULT_DATE_FORMATS.getQuick(i)), dateLocale));
        }
        this.probeCount = probes.size();
    }

    public TypeProbeCollection(
            FilesFacade ff,
            @Transient Path path,
            CharSequence file,
            DateFormatFactory dateFormatFactory,
            DateLocaleFactory dateLocaleFactory,
            DirectCharSink utf8Sink
    ) {
        this.utf8Sink = utf8Sink;
        this.dateProbePool = new ObjectPool<>(() -> new DateProbe(utf8Sink), 16);
        this.stringProbe = new StringProbe(utf8Sink);
        this.symbolProbe = new SymbolProbe(utf8Sink);
        addDefaultProbes();
        parseFile(ff, path, file, dateFormatFactory, dateLocaleFactory);
        this.probeCount = probes.size();
    }

    @Override
    public void clear() {
        dateProbePool.clear();
    }

    public BadDateProbe getBadDateProbe() {
        return badDateProbe;
    }

    public TypeProbe getProbe(int index) {
        return probes.getQuick(index);
    }

    public int getProbeCount() {
        return probeCount;
    }

    public TypeProbe getProbeForType(int columnType) {
        switch (columnType) {
            case ColumnType.BYTE:
                return byteProbe;
            case ColumnType.SHORT:
                return shortProbe;
            case ColumnType.INT:
                return intProbe;
            case ColumnType.LONG:
                return longProbe;
            case ColumnType.BOOLEAN:
                return booleanProbe;
            case ColumnType.FLOAT:
                return floatProbe;
            case ColumnType.DOUBLE:
                return doubleProbe;
            case ColumnType.STRING:
                return stringProbe;
            case ColumnType.SYMBOL:
                return symbolProbe;
            case ColumnType.DATE:
                return dateProbePool.next();
            case ColumnType.BINARY:
                assert false;
            case ColumnType.TIMESTAMP:
                assert false;
            default:
                assert false;
        }
        return null;
    }

    public StringProbe getStringProbe() {
        return stringProbe;
    }

    private void addDefaultProbes() {
        probes.add(intProbe);
        probes.add(longProbe);
        probes.add(doubleProbe);
        probes.add(booleanProbe);
    }

    private void parseFile(
            FilesFacade ff,
            Path path,
            CharSequence fileName,
            DateFormatFactory dateFormatFactory,
            DateLocaleFactory dateLocaleFactory
    ) {
        long fd = ff.openRO(path.of(fileName).$());
        if (fd < 0) {
            throw CairoException.instance(Os.errno()).put("could not open [file=").put(fileName).put(']');
        }

        try {
            // read the whole file
            final long sz = ff.length(fd);
            long buf = Unsafe.malloc(sz);
            try {
                long read = ff.read(fd, buf, sz, 0);
                if (read != sz) {
                    throw CairoException.instance(Os.errno()).put("could not read [file=").put(fileName).put(']');
                }

                long p = buf;
                long hi = p + sz;
                long _lo = p;

                boolean newline = true;
                boolean comment = false;
                boolean quote = false;
                boolean space = true;

                String pattern = null;
                final DirectByteCharSequence dbcs = new DirectByteCharSequence();

                while (p < hi) {
                    char b = (char) Unsafe.getUnsafe().getByte(p++);

                    switch (b) {
                        case '#':
                            comment = newline;
                            break;
                        case '\'':
                            // inside comment, ignore
                            if (comment) {
                                continue;
                            }

                            if (quote) {
                                // we were inside quote, close out and check which part to assign result to
                                if (pattern == null) {
                                    pattern = dbcs.of(_lo, p - 1).toString();
                                    _lo = p;
                                    space = true;
                                    quote = false;
                                } else {
                                    // pattern has been assigned, should never end up here
                                    LOG.error().$("Internal error").$();
                                }
                            } else if (newline) {
                                // only start quote if it is at beginning of line
                                _lo = p;
                                quote = true;
                            }
                            break;
                        case ' ':
                        case '\t':
                            if (comment || quote) {
                                continue;
                            }

                            if (space) {
                                _lo = p;
                                continue;
                            }

                            space = true;
                            newline = false;

                            String s = dbcs.of(_lo, p - 1).toString();
                            if (pattern == null) {
                                pattern = s;
                                _lo = p;
                                space = true;
                            } else {
                                DateLocale locale = dateLocaleFactory.getDateLocale(s);
                                if (locale == null) {
                                    LOG.error().$("Unknown date locale: ").$(s).$();
                                    // skip rest of line
                                    comment = true;
                                    continue;
                                }
                                probes.add(new DateProbe(utf8Sink).of(dateFormatFactory.get(pattern), locale));
                            }
                            break;
                        case '\n':
                        case '\r':
                            if (!comment) {
                                if (_lo < p - 1) {
                                    s = dbcs.of(_lo, p - 1).toString();
                                    if (pattern == null) {
                                        // no date locale, use default
                                        probes.add(new DateProbe(utf8Sink).of(dateFormatFactory.get(s), dateLocaleFactory.getDefaultDateLocale()));
                                    } else {
                                        DateLocale locale = dateLocaleFactory.getDateLocale(s);
                                        if (locale == null) {
                                            LOG.error().$("Unknown date locale: ").$(s).$();
                                        } else {
                                            probes.add(new DateProbe(utf8Sink).of(dateFormatFactory.get(pattern), locale));
                                        }
                                    }
                                } else if (pattern != null) {
                                    probes.add(new DateProbe(utf8Sink).of(dateFormatFactory.get(pattern), dateLocaleFactory.getDefaultDateLocale()));
                                }
                            }

                            newline = true;
                            comment = false;
                            quote = false;
                            pattern = null;
                            space = false;
                            _lo = p;
                            break;
                        default:
                            if (newline) {
                                newline = false;
                            }

                            if (space) {
                                space = false;
                            }
                            break;
                    }
                }
            } finally {
                Unsafe.free(buf, sz);
            }
        } finally {
            ff.close(fd);
        }
    }

    static {
        DEFAULT_DATE_FORMATS.add(DateFormatUtils.UTC_PATTERN);
        DEFAULT_DATE_FORMATS.add("yyyy-MM-dd HH:mm:ss");
        DEFAULT_DATE_FORMATS.add("dd/MM/y");
        DEFAULT_DATE_FORMATS.add("MM/dd/y");
    }
}
