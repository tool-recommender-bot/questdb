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

package com.questdb.std;

import com.questdb.log.Log;
import com.questdb.log.LogFactory;
import com.questdb.std.ex.BytecodeException;
import com.questdb.std.str.AbstractCharSink;
import com.questdb.std.str.CharSink;

import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class BytecodeAssembler {

    private static final int ACC_PUBLIC = 0x01;
    private static final int ACC_PRIVATE = 0x02;
    private static final Log LOG = LogFactory.getLog(BytecodeAssembler.class);
    private static final int aload = 0x19;
    private static final int aload_0 = 0x2a;
    private static final int aload_1 = 0x2b;
    private static final int aload_2 = 0x2c;
    private static final int aload_3 = 0x2d;
    private static final int istore = 0x36;
    private static final int istore_0 = 0x3b;
    private static final int istore_1 = 0x3c;
    private static final int istore_2 = 0x3d;
    private static final int istore_3 = 0x3e;
    private static final int lstore = 0x37;
    private static final int lstore_0 = 0x3f;
    private static final int lstore_1 = 0x40;
    private static final int lstore_2 = 0x41;
    private static final int lstore_3 = 0x42;
    private static final int iinc = 0x84;
    private static final int lload = 0x16;
    private static final int lload_0 = 0x1e;
    private static final int lload_1 = 0x1f;
    private static final int lload_2 = 0x20;
    private static final int lload_3 = 0x21;
    private static final int iload = 0x15;
    private static final int iload_0 = 0x1a;
    private static final int iload_1 = 0x1b;
    private static final int iload_2 = 0x1c;
    private static final int iload_3 = 0x1d;
    private static final int iconst_m1 = 2;
    private static final int iconst_0 = 3;
    private static final int bipush = 0x10;
    private static final int sipush = 0x11;
    private static final int invokespecial = 183;
    private static final int O_POOL_COUNT = 8;
    private final Utf8Appender utf8Appender = new Utf8Appender();
    private final CharSequenceIntHashMap utf8Cache = new CharSequenceIntHashMap();
    private final ObjIntHashMap<Class> classCache = new ObjIntHashMap<>();
    private ByteBuffer buf;
    private int poolCount;
    private int objectClassIndex;
    private int defaultConstructorNameIndex;
    private int defaultConstructorDescIndex;
    private int defaultConstructorMethodIndex;
    private int codeAttributeIndex;
    private int codeAttributeStart;
    private int codeStart;
    private int stackMapTableCut;
    private Class<?> host;

    public BytecodeAssembler() {
        this.buf = ByteBuffer.allocate(1024 * 1024).order(ByteOrder.BIG_ENDIAN);
        this.poolCount = 1;
    }

    public void aload(int value) {
        optimisedIO(aload_0, aload_1, aload_2, aload_3, aload, value);
    }

    public void append_frame(int itemCount, int offset) {
        put(0xfc + itemCount - 1);
        putShort(offset);
    }

    public void d2f() {
        putShort(0x90);
    }

    public void d2i() {
        putShort(0x8E);
    }

    public void d2l() {
        putShort(0x8F);
    }

    public void defineClass(int thisClassIndex) {
        defineClass(thisClassIndex, objectClassIndex);
    }

    public void defineClass(int thisClassIndex, int superclassIndex) {
        // access flags
        putShort(ACC_PUBLIC);
        // this class index
        putShort(thisClassIndex);
        // super class
        putShort(superclassIndex);
    }

    public void defineDefaultConstructor() {
        defineDefaultConstructor(defaultConstructorMethodIndex);
    }

    public void defineDefaultConstructor(int superIndex) {
        // constructor method entry
        startMethod(defaultConstructorNameIndex, defaultConstructorDescIndex, 1, 1);
        // code
        aload(0);
        put(invokespecial);
        putShort(superIndex);
        return_();
        endMethodCode();
        // exceptions
        putShort(0);
        // attribute count
        putShort(0);
        endMethod();
    }

    public void defineField(int nameIndex, int typeIndex) {
        putShort(ACC_PRIVATE);
        putShort(nameIndex);
        putShort(typeIndex);
        // attribute count
        putShort(0);
    }

    public void dump(String path) {
        try (FileOutputStream fos = new FileOutputStream(path)) {
            int p = buf.position();
            int l = buf.limit();
            buf.flip();
            fos.getChannel().write(buf);
            buf.limit(l);
            buf.position(p);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void endMethod() {
        putInt(codeAttributeStart - 4, position() - codeAttributeStart);
    }

    public void endMethodCode() {
        int len = position() - codeStart;
        if (len > 64 * 1024) {
            LOG.error().$("Too much input to generate ").$(host.getName()).$(". Bytecode is too long").$();
            throw BytecodeException.INSTANCE;
        }
        putInt(codeStart - 4, position() - codeStart);
    }

    public void endStackMapTables() {
        putInt(stackMapTableCut, position() - stackMapTableCut - 4);
    }

    public void f2d() {
        putShort(0x8D);
    }

    public void f2i() {
        putShort(0x8B);
    }

    public void f2l() {
        putShort(0x8C);
    }

    public void fieldCount(int count) {
        putShort(count);
    }

    public void finishPool() {
        putShort(O_POOL_COUNT, poolCount);
    }

    public void full_frame(int offset) {
        put(0xff);
        putShort(offset);
    }

    public int getCodeStart() {
        return codeStart;
    }

    public void getfield(int index) {
        put(0xb4);
        putShort(index);
    }

    public int goto_() {
        return genericGoto(0xa7);
    }

    public void i2b() {
        putShort(0x91);
    }

    public void i2d() {
        putShort(0x87);
    }

    public void i2f() {
        putShort(0x86);
    }

    public void i2l() {
        putShort(0x85);
    }

    public void i2s() {
        putShort(0x93);
    }

    public void iadd() {
        put(0x60);
    }

    public void iconst(int v) {
        if (v == -1) {
            put(iconst_m1);
        } else if (v > -1 && v < 6) {
            put(iconst_0 + v);
        } else if (v < 0) {
            put(sipush);
            putShort(v);
        } else if (v < 128) {
            put(bipush);
            put(v);
        } else {
            put(sipush);
            putShort(v);
        }
    }

    public int if_icmpge() {
        return genericGoto(0xa2);
    }

    public int if_icmpne() {
        return genericGoto(0xa0);
    }

    public int ifne() {
        return genericGoto(0x9a);
    }

    public void iinc(int index, int inc) {
        put(iinc);
        put(index);
        put(inc);
    }

    public void iload(int value) {
        optimisedIO(iload_0, iload_1, iload_2, iload_3, iload, value);
    }

    public void ineg() {
        put(0x74);
    }

    public void init(Class<?> host) {
        this.host = host;
        this.buf.clear();
        this.poolCount = 1;
        this.utf8Cache.clear();
        this.classCache.clear();
    }

    public void interfaceCount(int count) {
        putShort(count);
    }

    public void invokeInterface(int interfaceIndex, int argCount) {
        put(185);
        putShort(interfaceIndex);
        put(argCount + 1);
        put(0);
    }

    public void invokeStatic(int index) {
        put(184);
        putShort(index);
    }

    public void invokeVirtual(int index) {
        put(182);
        putShort(index);
    }

    public void irem() {
        put(0x70);
    }

    public void ireturn() {
        put(0xac);
    }

    public void istore(int value) {
        optimisedIO(istore_0, istore_1, istore_2, istore_3, istore, value);
    }

    public void isub() {
        put(0x64);
    }

    public void l2d() {
        putShort(0x8A);
    }

    public void l2f() {
        putShort(0x89);
    }

    public void l2i() {
        putShort(0x88);
    }

    public void lcmp() {
        put(0x94);
    }

    public void lconst_0() {
        put(0x09);
    }

    public void ldc(int index) {
        put(0x12);
        put(index);
    }

    public void ldc2_w(int index) {
        put(0x14);
        putShort(index);
    }

    public void lload(int value) {
        optimisedIO(lload_0, lload_1, lload_2, lload_3, lload, value);
    }

    public void lmul() {
        put(0x69);
    }

    @SuppressWarnings("unchecked")
    public <T> Class<T> loadClass(Class<?> host) {
        byte b[] = new byte[position()];
        System.arraycopy(buf.array(), 0, b, 0, b.length);
        return (Class<T>) Unsafe.getUnsafe().defineAnonymousClass(host, b, null);
    }

    public void lreturn() {
        put(0xad);
    }

    public void lstore(int value) {
        optimisedIO(lstore_0, lstore_1, lstore_2, lstore_3, lstore, value);
    }

    public void methodCount(int count) {
        putShort(count);
    }

    public <T> T newInstance() {
        Class<T> x = loadClass(host);
        try {
            return x.newInstance();
        } catch (Exception e) {
            LOG.error().$("Failed to create an instance of ").$(host.getName()).$(", cause: ").$(e).$();
            throw BytecodeException.INSTANCE;
        }
    }

    public int poolClass(int classIndex) {
        put(0x07);
        putShort(classIndex);
        return poolCount++;
    }

    public int poolClass(Class clazz) {
        int index = classCache.keyIndex(clazz);
        if (index > -1) {
            String name = clazz.getName();
            put(0x01);
            int n;
            putShort(n = name.length());
            for (int i = 0; i < n; i++) {
                char c = name.charAt(i);
                switch (c) {
                    case '.':
                        put('/');
                        break;
                    default:
                        put(c);
                        break;
                }
            }
            int result = poolClass(this.poolCount++);
            classCache.putAt(index, clazz, result);
            return result;
        }
        return classCache.valueAt(index);
    }

    public int poolField(int classIndex, int nameAndTypeIndex) {
        return poolRef(0x09, classIndex, nameAndTypeIndex);
    }

    public int poolInterfaceMethod(Class clazz, String name, String sig) {
        return poolInterfaceMethod(poolClass(clazz), poolNameAndType(poolUtf8(name), poolUtf8(sig)));
    }

    public int poolInterfaceMethod(int classIndex, String name, String sig) {
        return poolInterfaceMethod(classIndex, poolNameAndType(poolUtf8(name), poolUtf8(sig)));
    }

    public int poolLongConst(long value) {
        put(0x05);
        putLong(value);
        int index = poolCount;
        poolCount += 2;
        return index;
    }

    public int poolMethod(int classIndex, int nameAndTypeIndex) {
        return poolRef(0x0A, classIndex, nameAndTypeIndex);
    }

    public int poolMethod(int classIndex, CharSequence methodName, CharSequence signature) {
        return poolMethod(classIndex, poolNameAndType(poolUtf8(methodName), poolUtf8(signature)));
    }

    public int poolMethod(Class clazz, CharSequence methodName, CharSequence signature) {
        return poolMethod(poolClass(clazz), poolNameAndType(poolUtf8(methodName), poolUtf8(signature)));
    }

    public int poolNameAndType(int nameIndex, int typeIndex) {
        return poolRef(0x0C, nameIndex, typeIndex);
    }

    public int poolStringConst(int utf8Index) {
        put(0x8);
        putShort(utf8Index);
        return poolCount++;
    }

    public Utf8Appender poolUtf8() {
        put(0x01);
        utf8Appender.lenpos = position();
        utf8Appender.utf8len = 0;
        putShort(0);
        return utf8Appender;
    }

    public int poolUtf8(CharSequence cs) {
        int cachedIndex = utf8Cache.get(cs);
        if (cachedIndex == -1) {
            put(0x01);
            int n;
            putShort(n = cs.length());
            for (int i = 0; i < n; i++) {
                put(cs.charAt(i));
            }
            utf8Cache.put(cs, poolCount);
            return this.poolCount++;
        }

        return cachedIndex;
    }

    public void pop() {
        put(0x57);
    }

    public int position() {
        return buf.position();
    }

    public void put(int b) {
        if (buf.remaining() == 0) {
            resize();
        }
        buf.put((byte) b);
    }

    public void putITEM_Integer() {
        put(0x01);
    }

    public void putITEM_Long() {
        put(0x04);
    }

    public void putITEM_Object(int classIndex) {
        put(0x07);
        putShort(classIndex);
    }

    public void putITEM_Top() {
        put(0);
    }

    public void putLong(long value) {
        if (buf.remaining() < 4) {
            resize();
        }
        buf.putLong(value);
    }

    public void putShort(int v) {
        putShort((short) v);
    }

    public void putShort(int pos, int v) {
        buf.putShort(pos, (short) v);
    }

    public void putfield(int index) {
        put(181);
        putShort(index);
    }

    public void return_() {
        put(0xb1);
    }

    public void same_frame(int offset) {
        if (offset < 64) {
            put(offset);
        } else {
            put(251);
            putShort(offset);
        }
    }

    public void setJmp(int branch, int target) {
        putShort(branch, target - branch + 1);
    }

    public void setupPool() {
        // magic
        putInt(0xCAFEBABE);
        // version
        putInt(0x33);
        // skip pool count, write later when we know the value
        putShort(0);

        // add standard stuff
        objectClassIndex = poolClass(Object.class);
        defaultConstructorMethodIndex = poolMethod(objectClassIndex, poolNameAndType(
                defaultConstructorNameIndex = poolUtf8("<init>"),
                defaultConstructorDescIndex = poolUtf8("()V"))
        );
        codeAttributeIndex = poolUtf8("Code");
    }

    public void startMethod(int nameIndex, int descriptorIndex, int maxStack, int maxLocal) {
        // access flags
        putShort(ACC_PUBLIC);
        // name index
        putShort(nameIndex);
        // descriptor index
        putShort(descriptorIndex);
        // attribute count
        putShort(1);

        // code
        putShort(codeAttributeIndex);

        // attribute len
        putInt(0);
        // come back to this later
        this.codeAttributeStart = position();
        // max stack
        putShort(maxStack);
        // max locals
        putShort(maxLocal);

        // code len
        putInt(0);
        this.codeStart = position();
    }

    public void startStackMapTables(int attributeNameIndex, int frameCount) {
        putShort(attributeNameIndex);
        this.stackMapTableCut = position();
        // length - we will come back here
        putInt(0);
        // number of entries
        putShort(frameCount);
    }

    private int genericGoto(int cmd) {
        put(cmd);
        int pos = position();
        putShort(0);
        return pos;
    }

    private void optimisedIO(int code0, int code1, int code2, int code3, int code, int value) {
        switch (value) {
            case 0:
                put(code0);
                break;
            case 1:
                put(code1);
                break;
            case 2:
                put(code2);
                break;
            case 3:
                put(code3);
                break;
            default:
                put(code);
                put(value);
                break;
        }
    }

    private int poolInterfaceMethod(int classIndex, int nameAndTypeIndex) {
        return poolRef(0x0B, classIndex, nameAndTypeIndex);
    }

    private int poolRef(int op, int name, int type) {
        put(op);
        putShort(name);
        putShort(type);
        return poolCount++;
    }

    private void putInt(int pos, int v) {
        buf.putInt(pos, v);
    }

    private void putInt(int v) {
        if (buf.remaining() < 4) {
            resize();
        }
        buf.putInt(v);
    }

    private void putShort(short v) {
        if (buf.remaining() < 2) {
            resize();
        }
        buf.putShort(v);
    }

    private void resize() {
        ByteBuffer b = ByteBuffer.allocate(buf.capacity() * 2).order(ByteOrder.BIG_ENDIAN);
        System.arraycopy(buf.array(), 0, b.array(), 0, buf.capacity());
        b.position(buf.position());
        buf = b;
    }

    public class Utf8Appender extends AbstractCharSink implements CharSink {
        private int utf8len = 0;
        private int lenpos;

        public int $() {
            putShort(lenpos, utf8len);
            return poolCount++;
        }

        @Override
        public Utf8Appender put(CharSequence cs) {
            int n = cs.length();
            for (int i = 0; i < n; i++) {
                BytecodeAssembler.this.put(cs.charAt(i));
            }
            utf8len += n;
            return this;
        }

        @Override
        public Utf8Appender put(char c) {
            BytecodeAssembler.this.put(c);
            utf8len++;
            return this;
        }

        @Override
        public Utf8Appender put(int value) {
            super.put(value);
            return this;
        }
    }
}
