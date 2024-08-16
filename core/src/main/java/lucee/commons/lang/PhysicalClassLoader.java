/**
 * Copyright (c) 2014, the Railo Company Ltd.
 * Copyright (c) 2015, Lucee Association Switzerland
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either 
 * version 2.1 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public 
 * License along with this library.  If not, see <http://www.gnu.org/licenses/>.
 * 
 */
package lucee.commons.lang;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.UnmodifiableClassException;
import java.lang.ref.SoftReference;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import lucee.commons.io.IOUtil;
import lucee.commons.io.SystemUtil;
import lucee.commons.io.log.LogUtil;
import lucee.commons.io.res.Resource;
import lucee.commons.io.res.util.ResourceClassLoader;
import lucee.runtime.PageSourcePool;
import lucee.runtime.config.Config;
import lucee.runtime.config.ConfigPro;
import lucee.runtime.exp.ApplicationException;
import lucee.transformer.bytecode.util.ClassRenamer;

/**
 * Directory ClassLoader
 */
public final class PhysicalClassLoader extends ExtendableClassLoader {

	static {
		boolean res = registerAsParallelCapable();
	}
	private Resource directory;
	private ConfigPro config;
	private final ClassLoader[] parents;

	private Map<String, String> loadedClasses = new ConcurrentHashMap<String, String>();
	private Map<String, String> allLoadedClasses = new ConcurrentHashMap<String, String>(); // this includes all renames
	private Map<String, String> unavaiClasses = new ConcurrentHashMap<String, String>();

	private Map<String, SoftReference<PhysicalClassLoader>> customCLs;
	private PageSourcePool pageSourcePool;

	private static long counter = 0L;
	private static long _start = 0L;
	private static String start = Long.toString(_start, Character.MAX_RADIX);
	private static Object countToken = new Object();

	public static String uid() {
		synchronized (countToken) {
			counter++;
			if (counter < 0) {
				counter = 1;
				start = Long.toString(++_start, Character.MAX_RADIX);
			}
			if (_start == 0L) return Long.toString(counter, Character.MAX_RADIX);
			return start + "_" + Long.toString(counter, Character.MAX_RADIX);
		}
	}

	/**
	 * Constructor of the class
	 * 
	 * @param directory
	 * @param parent
	 * @throws IOException
	 */
	public PhysicalClassLoader(Config c, Resource directory, PageSourcePool pageSourcePool) throws IOException {
		this(c, directory, (ClassLoader) null, true, pageSourcePool);
	}

	public PhysicalClassLoader(Config c, Resource directory, ClassLoader parentClassLoader, boolean includeCoreCL, PageSourcePool pageSourcePool) throws IOException {
		super(parentClassLoader == null ? c.getClassLoader() : parentClassLoader);
		config = (ConfigPro) c;

		this.pageSourcePool = pageSourcePool;
		// ClassLoader resCL = parent!=null?parent:config.getResourceClassLoader(null);

		List<ClassLoader> tmp = new ArrayList<ClassLoader>();
		if (parentClassLoader == null) {
			ResourceClassLoader _cl = config.getResourceClassLoader(null);
			if (_cl != null) tmp.add(_cl);
		}
		else {
			tmp.add(parentClassLoader);
		}

		if (includeCoreCL) tmp.add(config.getClassLoaderCore());
		parents = tmp.toArray(new ClassLoader[tmp.size()]);

		// check directory
		if (!directory.exists()) directory.mkdirs();
		if (!directory.isDirectory()) throw new IOException("Resource [" + directory + "] is not a directory");
		if (!directory.canRead()) throw new IOException("Access denied to [" + directory + "] directory");
		this.directory = directory;
	}

	@Override
	public Class<?> loadClass(String name) throws ClassNotFoundException {
		return loadClass(name, false);
	}

	@Override
	protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
		synchronized (SystemUtil.createToken("pcl", name)) {
			return loadClass(name, resolve, true);
		}
	}

	private Class<?> loadClass(String name, boolean resolve, boolean loadFromFS) throws ClassNotFoundException {
		// First, check if the class has already been loaded
		Class<?> c = findLoadedClass(name);
		if (c == null) {
			for (ClassLoader p: parents) {
				try {
					c = p.loadClass(name);
					break;
				}
				catch (Exception e) {
				}
			}
			if (c == null) {
				if (loadFromFS) c = findClass(name);
				else throw new ClassNotFoundException(name);
			}
		}
		if (resolve) resolveClass(c);
		return c;
	}

	@Override
	protected Class<?> findClass(String name) throws ClassNotFoundException {// if(name.indexOf("sub")!=-1)print.ds(name);
		synchronized (SystemUtil.createToken("pcl", name)) {
			Resource res = directory.getRealResource(name.replace('.', '/').concat(".class"));

			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			try {
				IOUtil.copy(res, baos, false);
			}
			catch (IOException e) {
				this.unavaiClasses.put(name, "");
				throw new ClassNotFoundException("Class [" + name + "] is invalid or doesn't exist", e);
			}

			byte[] barr = baos.toByteArray();
			IOUtil.closeEL(baos);
			return _loadClass(name, barr, false);
		}
	}

	@Override
	public Class<?> loadClass(String name, byte[] barr) throws UnmodifiableClassException {
		Class<?> clazz = null;

		synchronized (SystemUtil.createToken("pcl", name)) {

			// new class , not in memory yet
			try {
				clazz = loadClass(name, false, false); // we do not load existing class from disk
			}
			catch (ClassNotFoundException cnf) {
			}
			if (clazz == null) return _loadClass(name, barr, false);

			// first we try to update the class what needs instrumentation object
			/*
			 * try { InstrumentationFactory.getInstrumentation(config).redefineClasses(new
			 * ClassDefinition(clazz, barr)); return clazz; } catch (Exception e) { LogUtil.log(null,
			 * "compilation", e); }
			 */
			// in case instrumentation fails, we rename it
			return rename(clazz, barr);
		}
	}

	private Class<?> rename(Class<?> clazz, byte[] barr) {
		String newName = clazz.getName() + "$" + uid();
		return _loadClass(newName, ClassRenamer.rename(barr, newName), true);
	}

	private Class<?> _loadClass(String name, byte[] barr, boolean rename) {
		Class<?> clazz = defineClass(name, barr, 0, barr.length);
		if (clazz != null) {
			if (!rename) loadedClasses.put(name, "");
			allLoadedClasses.put(name, "");

			resolveClass(clazz);
		}
		return clazz;
	}

	@Override
	public URL getResource(String name) {
		return null;
	}

	public int getSize(boolean includeAllRenames) {
		return includeAllRenames ? allLoadedClasses.size() : loadedClasses.size();
	}

	@Override
	public InputStream getResourceAsStream(String name) {
		InputStream is = super.getResourceAsStream(name);
		if (is != null) return is;

		Resource f = _getResource(name);
		if (f != null) {
			try {
				return IOUtil.toBufferedInputStream(f.getInputStream());
			}
			catch (IOException e) {
			}
		}
		return null;
	}

	/**
	 * returns matching File Object or null if file not exust
	 * 
	 * @param name
	 * @return matching file
	 */
	public Resource _getResource(String name) {
		Resource f = directory.getRealResource(name);
		if (f != null && f.exists() && f.isFile()) return f;
		return null;
	}

	public boolean hasClass(String className) {
		return hasResource(className.replace('.', '/').concat(".class"));
	}

	public boolean isClassLoaded(String className) {
		return findLoadedClass(className) != null;
	}

	public boolean hasResource(String name) {
		return _getResource(name) != null;
	}

	/**
	 * @return the directory
	 */
	public Resource getDirectory() {
		return directory;
	}

	public void clear() {
		clear(true);
	}

	public void clear(boolean clearPagePool) {
		if (clearPagePool && pageSourcePool != null) pageSourcePool.clearPages(this);
		this.loadedClasses.clear();
		this.allLoadedClasses.clear();
		this.unavaiClasses.clear();
	}

	/**
	 * removes memory based appendix from class name, for example it translates
	 * [test.test_cfc$sub2$cf$5] to [test.test_cfc$sub2$cf]
	 * 
	 * @param name
	 * @return
	 * @throws IOException
	 */
	public static String substractAppendix(String name) throws ApplicationException {
		if (name.endsWith("$cf")) return name;
		int index = name.lastIndexOf('$');
		if (index != -1) {
			name = name.substring(0, index);
		}
		if (name.endsWith("$cf")) return name;
		throw new ApplicationException("could not remove appendix from [" + name + "]");
	}

	@Override
	public void finalize() throws Throwable {
		try {
			clear();
		}
		catch (Exception e) {
			LogUtil.log(config, "classloader", e);
		}
		super.finalize();
	}

}
