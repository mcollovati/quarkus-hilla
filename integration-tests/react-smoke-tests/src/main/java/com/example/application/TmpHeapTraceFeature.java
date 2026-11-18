/*
 * Copyright 2026 Marco Collovati, Dario Götze
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.application;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.graalvm.nativeimage.hosted.Feature;

// TEMPORARY native build diagnostic, to be removed before merge.
// Dumps the threads alive during the analysis, to find build-time threads
// that may leave class loader resource enumerations in the image heap.
public class TmpHeapTraceFeature implements Feature {

    private final AtomicInteger calls = new AtomicInteger();

    private static final sun.misc.Unsafe U;

    static {
        try {
            Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            U = (sun.misc.Unsafe) f.get(null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static final ConcurrentHashMap<Class<?>, Object[]> FIELDS = new ConcurrentHashMap<>();

    @Override
    public void duringSetup(DuringSetupAccess access) {
        access.registerObjectReplacer(obj -> {
            try {
                Class<?> c = obj.getClass();
                if (isTarget(obj)) {
                    new Throwable("[HEAPTRACE] target object scanned: " + c.getName()).printStackTrace(System.out);
                    return obj;
                }
                if (c.isArray()) {
                    if (!c.getComponentType().isPrimitive()) {
                        Object[] a = (Object[]) obj;
                        for (int i = 0; i < a.length; i++) {
                            if (isTarget(a[i])) {
                                System.out.println("[HEAPTRACE] holder array " + c.getName() + "[" + i + "]");
                            }
                        }
                    }
                    return obj;
                }
                Object[] meta = FIELDS.computeIfAbsent(c, TmpHeapTraceFeature::fields);
                long[] offs = (long[]) meta[0];
                String[] names = (String[]) meta[1];
                for (int i = 0; i < offs.length; i++) {
                    Object v = U.getObject(obj, offs[i]);
                    if (isTarget(v)) {
                        System.out.println("[HEAPTRACE] holder " + c.getName() + " field " + names[i] + " -> "
                                + v.getClass().getName());
                    }
                }
            } catch (Throwable t) {
                // ignore
            }
            return obj;
        });
    }

    private static Object[] fields(Class<?> c) {
        List<Long> o = new ArrayList<>();
        List<String> n = new ArrayList<>();
        for (Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass()) {
            Field[] fs;
            try {
                fs = k.getDeclaredFields();
            } catch (Throwable t) {
                continue;
            }
            for (Field f : fs) {
                if (Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive()) continue;
                try {
                    o.add(U.objectFieldOffset(f));
                    n.add(k.getName() + "." + f.getName());
                } catch (Throwable t) {
                    // ignore
                }
            }
        }
        return new Object[] {o.stream().mapToLong(Long::longValue).toArray(), n.toArray(String[]::new)};
    }

    private static boolean isTarget(Object o) {
        return o != null && o.getClass().getName().startsWith("com.oracle.svm.hosted.NativeImageClassLoader$");
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        dump("beforeAnalysis");
        Thread t = new Thread(
                () -> {
                    for (int i = 0; i < 100; i++) {
                        try {
                            Thread.sleep(200);
                        } catch (InterruptedException e) {
                            return;
                        }
                        dump("timer-" + i);
                    }
                },
                "tmp-thread-dumper");
        t.setDaemon(true);
        t.start();
    }

    @Override
    public void duringAnalysis(DuringAnalysisAccess access) {
        if (calls.getAndIncrement() < 3) {
            dump("duringAnalysis-" + calls.get());
        }
    }

    private static void dump(String label) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Thread, StackTraceElement[]> e :
                Thread.getAllStackTraces().entrySet()) {
            StackTraceElement[] st = e.getValue();
            boolean interesting = false;
            for (StackTraceElement el : st) {
                String c = el.getClassName();
                if (!(c.startsWith("java.")
                        || c.startsWith("jdk.")
                        || c.startsWith("sun.")
                        || c.startsWith("com.oracle.")
                        || c.startsWith("org.graalvm.")
                        || c.startsWith("jdk.graal.")
                        || c.startsWith("com.example.application.TmpHeap"))) {
                    interesting = true;
                    break;
                }
            }
            if (!interesting) continue;
            sb.append("[THREADDUMP ")
                    .append(label)
                    .append("] ")
                    .append(e.getKey().getName())
                    .append(" state=")
                    .append(e.getKey().getState())
                    .append('\n');
            for (int i = 0; i < Math.min(st.length, 60); i++) {
                sb.append("[THREADDUMP ")
                        .append(label)
                        .append("]     at ")
                        .append(st[i])
                        .append('\n');
            }
        }
        System.out.print(sb);
    }
}
