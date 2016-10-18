/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package jdk.tools.jlink.internal;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.stream.Stream;

import jdk.internal.jmod.JmodFile;
import jdk.tools.jlink.internal.Archive.Entry.EntryType;

/**
 * An Archive backed by a jmod file.
 */
public class JmodArchive implements Archive {
    private static final String JMOD_EXT    = ".jmod";

    /**
     * An entry located in a jmod file.
     */
    public class JmodEntry extends Entry {
        private final JmodFile.Entry entry;

        JmodEntry(String path, String name, EntryType type,
                  JmodFile.Entry entry) {
            super(JmodArchive.this, path, name, type);
            this.entry = Objects.requireNonNull(entry);
        }

        /**
         * Returns the number of uncompressed bytes for this entry.
         */
        @Override
        public long size() {
            return entry.size();
        }

        @Override
        public InputStream stream() throws IOException {
            return jmodFile.getInputStream(entry.section(), entry.name());
        }
    }

    private final Path file;
    private final String moduleName;
    private JmodFile jmodFile;

    public JmodArchive(String mn, Path jmod) {
        Objects.requireNonNull(mn);
        Objects.requireNonNull(jmod.getFileName());
        String filename = jmod.toString();
        if (!filename.endsWith(JMOD_EXT)) {
            throw new UnsupportedOperationException("Unsupported format: " + filename);
        }
        this.moduleName = mn;
        this.file = jmod;
    }

    @Override
    public String moduleName() {
        return moduleName;
    }

    @Override
    public Path getPath() {
        return file;
    }

    @Override
    public Stream<Entry> entries() {
        ensureOpen();
        return jmodFile.stream()
                       .map(this::toEntry);
    }

    @Override
    public void open() throws IOException {
        if (jmodFile != null) {
            jmodFile.close();
        }
        this.jmodFile = new JmodFile(file);
    }

    @Override
    public void close() throws IOException {
        if (jmodFile != null) {
            jmodFile.close();
        }
    }

    private void ensureOpen() {
        if (jmodFile == null) {
            try {
                open();
            } catch(IOException ioe){
                throw new UncheckedIOException(ioe);
            }
        }
    }

    private EntryType toEntryType(JmodFile.Section section) {
        switch (section) {
            case CLASSES:
                return EntryType.CLASS_OR_RESOURCE;
            case CONFIG:
                return EntryType.CONFIG;
            case NATIVE_LIBS:
                return EntryType.NATIVE_LIB;
            case NATIVE_CMDS:
                return EntryType.NATIVE_CMD;
            case HEADER_FILES:
                return EntryType.HEADER_FILE;
            case MAN_PAGES:
                return EntryType.MAN_PAGE;
            default:
                throw new InternalError("unexpected entry: " + section);
        }
    }

    private Entry toEntry(JmodFile.Entry entry) {
        EntryType type = toEntryType(entry.section());
        String name = entry.name();
        String path = entry.section().jmodDir() + "/" + name;

        // Entry.path() contains the kind of file native, conf, bin, ...
        // Keep it to avoid naming conflict (eg: native/jvm.cfg and config/jvm.cfg
        String resourceName = name;
        if (type != EntryType.CLASS_OR_RESOURCE) {
            resourceName = path;
        }

        return new JmodEntry(path, resourceName, type, entry);
    }
}
