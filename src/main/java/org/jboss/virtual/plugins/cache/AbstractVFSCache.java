/*
* JBoss, Home of Professional Open Source
* Copyright 2006, JBoss Inc., and individual contributors as indicated
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
package org.jboss.virtual.plugins.cache;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Arrays;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.jboss.logging.Logger;
import org.jboss.virtual.VFS;
import org.jboss.virtual.VFSUtils;
import org.jboss.virtual.VirtualFile;
import org.jboss.virtual.spi.VFSContext;
import org.jboss.virtual.spi.VirtualFileHandler;
import org.jboss.virtual.spi.cache.CacheStatistics;
import org.jboss.virtual.spi.cache.VFSCache;

/**
 * Abstract vfs cache.
 *
 * @author <a href="mailto:ales.justin@jboss.com">Ales Justin</a>
 */
public abstract class AbstractVFSCache implements VFSCache, CacheStatistics
{
   protected Logger log = Logger.getLogger(getClass());
   
   private ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
   private long timestamp;

   public long lastInsert()
   {
      return timestamp;
   }

   /**
    * Is cache valid.
    */
   protected abstract void check();

   public VirtualFile getFile(URI uri) throws IOException
   {
      check();

      VFSContext context = findContext(uri);
      if (context != null)
      {
         VirtualFileHandler root = context.getRoot();
         String relativePath = getRelativePath(context, uri);
         VirtualFileHandler child = root.getChild(relativePath);
         return child.getVirtualFile();
      }
      return VFS.getRoot(uri);
   }

   /**
    * Get relative path.
    *
    * @param context the vfs context
    * @param uri the uri
    * @return uri's relative path to context's root
    */
   protected String getRelativePath(VFSContext context, URI uri)
   {
      String uriPath = stripProtocol(uri);
      String contextKey = getKey(context);
      return uriPath.substring(contextKey.length());
   }

   public VirtualFile getFile(URL url) throws IOException
   {
      check();

      try
      {
         return getFile(VFSUtils.toURI(url));
      }
      catch (URISyntaxException e)
      {
         IOException ioe = new IOException();
         ioe.initCause(e);
         throw ioe;
      }
   }

   /**
    * Strip protocol from url string.
    *
    * @param uri the uri
    * @return uri's path string
    */
   protected static String stripProtocol(URI uri)
   {
      String path = uri.getPath();
      if (path != null && path.length() > 0)
      {
         StringBuilder sb = new StringBuilder(path);

         if (sb.charAt(0) != '/')
            sb.insert(0, '/');
         if (sb.charAt(sb.length() - 1) != '/')
            sb.append('/');

         path = sb.toString();
      }
      else
      {
         path = "/";
      }

      return path;
   }

   /**
    * Get the cached context.
    *
    * @param path the path to match
    * @return cached context or null if not found
    */
   protected abstract VFSContext getContext(String path);

   /**
    * Find cached context.
    * This method must take read lock.
    *
    * @param uri the uri to match
    * @return found context or null
    */
   protected abstract VFSContext findContext(URI uri);

   /**
    * Get path key.
    *
    * @param context the vfs context
    * @return contex's root path w/o protocol
    */
   protected static String getKey(VFSContext context)
   {
      URI uri = context.getRootURI();
      return stripProtocol(uri);
   }

   public void putContext(VFSContext context)
   {
      if (context == null)
         throw new IllegalArgumentException("Null context");

      check();

      if (log.isTraceEnabled())
      {
         StackTraceElement[] stackTraceElements = Thread.currentThread().getStackTrace();
         log.trace("VFSContext: " + context + ", Stack-trace:\n" + Arrays.toString(stackTraceElements));
      }

      String path = getKey(context);
      writeLock();
      try
      {
         putContext(path, context);
         timestamp = System.currentTimeMillis();
      }
      finally
      {
         writeUnlock();
      }
   }

   /**
    * Put vfs context and its path key into cache.
    *
    * @param path the context's path
    * @param context the vfs context
    */
   protected abstract void putContext(String path, VFSContext context);

   public void removeContext(VFSContext context)
   {
      if (context == null)
         throw new IllegalArgumentException("Null context");

      check();

      String path = getKey(context);
      writeLock();
      try
      {
         removeContext(path, context);
      }
      finally
      {
         writeUnlock();
      }
   }

   /**
    * Remove vfs context and its path key from cache.
    *
    * @param path the context's path
    * @param context the vfs context
    */
   protected abstract void removeContext(String path, VFSContext context);

   /**
    * Read lock.
    */
   protected void readLock()
   {
      lock.readLock().lock();
   }

   /**
    * Read unlock.
    */
   protected void readUnlock()
   {
      lock.readLock().unlock();
   }

   /**
    * Write lock.
    */
   protected void writeLock()
   {
      lock.writeLock().lock();
   }

   /**
    * Write unlock.
    */
   protected void writeUnlock()
   {
      lock.writeLock().unlock();
   }
}