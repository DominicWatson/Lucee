/**
 *
 * Copyright (c) 2014, the Railo Company Ltd. All rights reserved.
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
 **/
package lucee.commons.io.log;

import java.io.PrintStream;

import lucee.commons.io.log.log4j.LogAdapter;
import lucee.commons.lang.ExceptionUtil;
import lucee.runtime.PageContext;
import lucee.runtime.PageContextImpl;

import org.apache.log4j.Logger;

/**
 * Helper class for the logs
 */
public final class LogUtil {

	public static void log(Log log, int level, String logName, Throwable t) { 
		log(log,level,logName,"",t);
	}   

	public static void log(Log log, int level, String logName,String msg, Throwable t) { 
		if(log instanceof LogAdapter) {
			log.log(level, logName, msg,t);
		}
		else {
			String em = ExceptionUtil.getMessage(t);
			String est = ExceptionUtil.getStacktrace(t, false);
			if(msg.equals(em)) msg=em+";"+est;
			else msg+=";"+em+";"+est;
			
			if(log!=null) {
				log.log(level, logName,msg);
			}
			else {
				PrintStream ps=(level>=Log.LEVEL_WARN)?System.err:System.out;
				ps.println(logName+";"+msg);
			}

		}
	}

	public static void log(Log log, int level, String logName, String msg, StackTraceElement[] stackTrace) {
		Throwable t = new Throwable();
		t.setStackTrace(stackTrace);
		log(log,level,logName,msg,t);
	}

	public static Logger toLogger(lucee.commons.io.log.Log log) {
		if(log==null) return null;
		return ((LogAdapter)log).getLogger();
	}

	public static Log getLog(PageContext pc, String name) {
		return ((PageContextImpl)pc).getLog(name);
	}
	
	public static Log getLog(PageContext pc, String name, boolean createIfNecessary) {
		return ((PageContextImpl)pc).getLog(name,createIfNecessary);
	}
}