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

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.graalvm.nativeimage.hosted.Feature;

// TEMPORARY native build diagnostic, to be removed before merge.
// Dumps the threads alive during the analysis, to find build-time threads
// that may leave class loader resource enumerations in the image heap.
public class TmpHeapTraceFeature implements Feature {

    private final AtomicInteger calls = new AtomicInteger();

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
