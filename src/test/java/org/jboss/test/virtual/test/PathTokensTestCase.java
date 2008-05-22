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
package org.jboss.test.virtual.test;

import java.net.URL;

import junit.framework.Test;
import org.jboss.virtual.VFS;
import org.jboss.virtual.VirtualFile;

/**
 * Test path tokens.
 *
 * @author <a href="mailto:ales.justin@jboss.com">Ales Justin</a>
 */
public class PathTokensTestCase extends AbstractVFSTest
{
   public PathTokensTestCase(String s)
   {
      super(s);
   }

   public static Test suite()
   {
      return suite(PathTokensTestCase.class);
   }

   protected void testPath(String path) throws Throwable
   {
      try
      {
         URL url = getResource("/vfs");
         VirtualFile vf = VFS.getRoot(url);
         vf.getChild(path);
         fail("Should not be here");
      }
      catch (Throwable t)
      {
         assertInstanceOf(t, IllegalArgumentException.class, false);
      }
   }

   public void testSpecialTokens() throws Throwable
   {
      testPath("/.../");
      testPath(".../");
      testPath("/...");
      testPath("...");
      testPath("/..somemorepath/");
      testPath("..somemorepath/");
      testPath("/..somemorepath");
      testPath("..somemorepath");
      testPath("path//morepath");
      testPath("//morepath");
      // we need 3 '/', since by default we always remove the last one
      testPath("///"); 
      testPath("morepath///");
   }
}