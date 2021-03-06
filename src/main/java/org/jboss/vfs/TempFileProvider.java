/*
 * JBoss, Home of Professional Open Source
 * Copyright 2009, JBoss Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.vfs;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.Random;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jboss.logging.Logger;

/**
 * A provider for temporary physical files and directories.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public final class TempFileProvider implements Closeable {

    private static final Logger log = Logger.getLogger(TempFileProvider.class);
    private static final String JBOSS_TMP_DIR_PROPERTY = "jboss.server.temp.dir";
    private static final String JVM_TMP_DIR_PROPERTY = "java.io.tmpdir";
    private static final File TMP_ROOT;
    private static final int RETRIES = 10;
    private final AtomicBoolean open = new AtomicBoolean(true);

    static {
        String configTmpDir = System.getProperty(JBOSS_TMP_DIR_PROPERTY);
        if (configTmpDir == null)
            configTmpDir = System.getProperty(JVM_TMP_DIR_PROPERTY);
        try {
            TMP_ROOT = new File(configTmpDir, "vfs");
            TMP_ROOT.mkdirs();
        }
        catch (Exception e) {
            throw new RuntimeException("Can't set up temp file provider", e);
        }
    }

    /**
     * Create a temporary file provider for a given type.
     *
     * @param providerType the provider type string (used as a prefix in the temp file dir name)
     * @param executor the executor
     * @return the new provider
     *
     * @throws IOException if an I/O error occurs
     */
    public static TempFileProvider create(String providerType, ScheduledExecutorService executor) throws IOException {
        return new TempFileProvider(createTempDir(providerType, "", TMP_ROOT), executor);
    }

    private final File providerRoot;
    private final ScheduledExecutorService executor;

    private TempFileProvider(File providerRoot, ScheduledExecutorService executor) {
        this.providerRoot = providerRoot;
        this.executor = executor;
    }

    /**
     * Create a temp directory, into which temporary files may be placed.
     *
     * @param originalName the original file name
     *
     * @return the temp directory
     *
     * @throws IOException for any error
     */
    public TempDir createTempDir(String originalName) throws IOException {
        if (!open.get()) {
            throw new IOException("Temp file provider closed");
        }
        final String name = createTempName(originalName + "-", "");
        final File f = new File(providerRoot, name);
        for (int i = 0; i < RETRIES; i++) {
            if (f.mkdir())
                return new TempDir(this, f);
        }
        throw new IOException(
                String.format("Could not create directory for original name '%s' after %d attempts", originalName, RETRIES));
    }

    private static final Random rng = new Random();

    private static File createTempDir(String prefix, String suffix, File root) throws IOException {
        for (int i = 0; i < RETRIES; i++) {
            final File f = new File(root, createTempName(prefix, suffix));
            if (f.mkdir())
                return f;
        }
        throw new IOException(
                String.format("Could not create directory for root '%s' (prefix '%s', suffix '%s') after %d attempts",
                        root,
                        prefix,
                        suffix,
                        RETRIES));
    }

    static String createTempName(String prefix, String suffix) {
        return prefix + Long.toHexString(rng.nextLong()) + suffix;
    }

    /**
     * Close this provider and delete any temp files associated with it.
     */
    public void close() throws IOException {
        if (open.getAndSet(false)) {
            new DeleteTask(providerRoot).run();
        }
    }

    protected void finalize() {
        VFSUtils.safeClose(this);
    }

    class DeleteTask implements Runnable {

        private final File root;

        public DeleteTask(File root) {
            this.root = root;
        }

        public void run() {
            if (VFSUtils.recursiveDelete(root) == false) {
                log.tracef("Failed to delete root (%s), retrying in 30sec.", root);
                if(executor == null){
                	log.tracef("Unable to delete root (%s), executor is null.", root);
                }else{
                	executor.schedule(this, 30L, TimeUnit.SECONDS);                	
                }
            }
        }
    }
}
