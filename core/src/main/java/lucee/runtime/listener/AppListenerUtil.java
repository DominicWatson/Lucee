/**
 * Copyright (c) 2014, the Railo Company Ltd.
 * Copyright (c) 2015, Lucee Assosication Switzerland
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
package lucee.runtime.listener;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

import lucee.commons.io.log.Log;
import lucee.commons.io.log.LogUtil;
import lucee.commons.io.res.Resource;
import lucee.commons.lang.ExceptionUtil;
import lucee.commons.lang.StringUtil;
import lucee.runtime.Mapping;
import lucee.runtime.MappingImpl;
import lucee.runtime.PageContext;
import lucee.runtime.PageContextImpl;
import lucee.runtime.PageSource;
import lucee.runtime.config.Config;
import lucee.runtime.config.ConfigImpl;
import lucee.runtime.config.ConfigWeb;
import lucee.runtime.config.ConfigWebImpl;
import lucee.runtime.config.ConfigWebUtil;
import lucee.runtime.db.ApplicationDataSource;
import lucee.runtime.db.ClassDefinition;
import lucee.runtime.db.DBUtil;
import lucee.runtime.db.DBUtil.DataSourceDefintion;
import lucee.runtime.db.DataSource;
import lucee.runtime.db.DataSourceImpl;
import lucee.runtime.db.ParamSyntax;
import lucee.runtime.engine.ThreadLocalPageContext;
import lucee.runtime.exp.ApplicationException;
import lucee.runtime.exp.PageException;
import lucee.runtime.net.mail.Server;
import lucee.runtime.net.mail.ServerImpl;
import lucee.runtime.net.s3.Properties;
import lucee.runtime.net.s3.PropertiesImpl;
import lucee.runtime.op.Caster;
import lucee.runtime.op.Decision;
import lucee.runtime.orm.ORMConfigurationImpl;
import lucee.runtime.type.Array;
import lucee.runtime.type.ArrayImpl;
import lucee.runtime.type.Collection;
import lucee.runtime.type.Collection.Key;
import lucee.runtime.type.KeyImpl;
import lucee.runtime.type.Struct;
import lucee.runtime.type.StructImpl;
import lucee.runtime.type.dt.TimeSpan;
import lucee.runtime.type.dt.TimeSpanImpl;
import lucee.runtime.type.scope.CookieImpl;
import lucee.runtime.type.scope.Scope;
import lucee.runtime.type.scope.Undefined;
import lucee.runtime.type.util.CollectionUtil;
import lucee.runtime.type.util.KeyConstants;
import lucee.runtime.type.util.ListUtil;
import lucee.transformer.library.ClassDefinitionImpl;

public final class AppListenerUtil {

	public static final Collection.Key ACCESS_KEY_ID = KeyImpl.intern("accessKeyId");
	public static final Collection.Key AWS_SECRET_KEY = KeyImpl.intern("awsSecretKey");
	public static final Collection.Key DEFAULT_LOCATION = KeyImpl.intern("defaultLocation");
	public static final Collection.Key ACL = KeyImpl.intern("acl");
	public static final Collection.Key CONNECTION_STRING = KeyImpl.intern("connectionString");
	
	public static final Collection.Key BLOB = KeyImpl.intern("blob");
	public static final Collection.Key CLOB = KeyImpl.intern("clob");
	public static final Collection.Key CONNECTION_LIMIT = KeyImpl.intern("connectionLimit");
	public static final Collection.Key CONNECTION_TIMEOUT = KeyImpl.intern("connectionTimeout");
	public static final Collection.Key META_CACHE_TIMEOUT = KeyImpl.intern("metaCacheTimeout");
	public static final Collection.Key TIMEZONE = KeyImpl.intern("timezone");
	public static final Collection.Key ALLOW = KeyImpl.intern("allow");
	public static final Collection.Key STORAGE = KeyImpl.intern("storage");
	public static final Collection.Key READ_ONLY = KeyImpl.intern("readOnly");
	public static final Collection.Key DATABASE = KeyConstants._database;
	public static final Collection.Key DISABLE_UPDATE = KeyImpl.intern("disableUpdate"); 
	

	private static final TimeSpan FIVE_MINUTES = new TimeSpanImpl(0, 0, 5, 0);
	private static final TimeSpan ONE_MINUTE = new TimeSpanImpl(0, 0, 1, 0);
	
	
	
	public static PageSource getApplicationPageSource(PageContext pc,PageSource requestedPage, String filename, int mode) {
		if(mode==ApplicationListener.MODE_CURRENT)return getApplicationPageSourceCurrent(requestedPage, filename);
		if(mode==ApplicationListener.MODE_ROOT)return getApplicationPageSourceRoot(pc, filename);
		return getApplicationPageSourceCurr2Root(pc, requestedPage, filename);
	}
	
	public static PageSource getApplicationPageSourceCurrent(PageSource requestedPage, String filename) {
		PageSource res=requestedPage.getRealPage(filename);
	    if(res.exists()) return res;
		return null;
	}
	
	public static PageSource getApplicationPageSourceRoot(PageContext pc, String filename) {
		PageSource ps = ((PageContextImpl)pc).getPageSourceExisting("/".concat(filename));
		if(ps!=null) return ps;
		return null;
	}
	
	public static PageSource getApplicationPageSourceCurr2Root(PageContext pc,PageSource requestedPage, String filename) {
		PageSource ps=requestedPage.getRealPage(filename);
	    if(ps.exists()) { 
			return ps;
		}
	    Array arr=lucee.runtime.type.util.ListUtil.listToArrayRemoveEmpty(requestedPage.getRealpathWithVirtual(),"/");
	    //Config config = pc.getConfig();
		for(int i=arr.size()-1;i>0;i--) {
			StringBuilder sb=new StringBuilder("/");
			for(int y=1;y<i;y++) {
			    sb.append((String)arr.get(y,""));
			    sb.append('/');
			}
			sb.append(filename);
			ps = ((PageContextImpl)pc).getPageSourceExisting(sb.toString());
			if(ps!=null) {
				return ps;
			}
		}
		return null;
	}

	public static String toStringMode(int mode) {
		if(mode==ApplicationListener.MODE_CURRENT)	return "curr";
		if(mode==ApplicationListener.MODE_ROOT)		return "root";
		if(mode==ApplicationListener.MODE_CURRENT2ROOT)		return "curr2root";
		if(mode==ApplicationListener.MODE_CURRENT_OR_ROOT)		return "currorroot";
		return "curr2root";
	}

	public static String toStringType(ApplicationListener listener) {
		if(listener instanceof NoneAppListener)			return "none";
		else if(listener instanceof MixedAppListener)	return "mixed";
		else if(listener instanceof ClassicAppListener)	return "classic";
		else if(listener instanceof ModernAppListener)	return "modern";
		return "";
	}
	
	public static DataSource[] toDataSources(Config config, Object o,DataSource[] defaultValue,Log log) {
		try {
			return toDataSources(config,o,log);
		} catch(Throwable t) {
			ExceptionUtil.rethrowIfNecessary(t);
			return defaultValue;
		}
	}

	public static DataSource[] toDataSources(Config config, Object o,Log log) throws PageException {
		Struct sct = Caster.toStruct(o);
		Iterator<Entry<Key, Object>> it = sct.entryIterator();
		Entry<Key, Object> e;
		java.util.List<DataSource> dataSources=new ArrayList<DataSource>();
		while(it.hasNext()) {
			e = it.next();
			dataSources.add(toDataSource(config,e.getKey().getString().trim(), Caster.toStruct(e.getValue()),log));
		}
		return dataSources.toArray(new DataSource[dataSources.size()]);
	}

	public static DataSource toDataSource(Config config, String name,Struct data,Log log) throws PageException {
			String user = Caster.toString(data.get(KeyConstants._username,null),null);
			String pass = Caster.toString(data.get(KeyConstants._password,""),"");
			if(StringUtil.isEmpty(user)) {
				user=null;
				pass=null;
			}
			else {
				user=user.trim();
				pass=pass.trim();
			}
			
			// first check for {class:... , connectionString:...}
			Object oConnStr=data.get(CONNECTION_STRING,null);
			if(oConnStr!=null) {
				String className=Caster.toString(data.get(KeyConstants._class));
				if("com.microsoft.jdbc.sqlserver.SQLServerDriver".equals(className)) {
					className="com.microsoft.sqlserver.jdbc.SQLServerDriver";
				}
				ClassDefinition cd=new ClassDefinitionImpl(
					className
					, Caster.toString(data.get(KeyConstants._bundleName,null),null)
					, Caster.toString(data.get(KeyConstants._bundleVersion,null),null)
					, ThreadLocalPageContext.getConfig().getIdentification()
				);
				
				try{
				return ApplicationDataSource.getInstance(
					config,
					name, 
					cd, 
					Caster.toString(oConnStr), 
					user, pass,
					Caster.toBooleanValue(data.get(BLOB,null),false),
					Caster.toBooleanValue(data.get(CLOB,null),false), 
					Caster.toIntValue(data.get(CONNECTION_LIMIT,null),-1), 
					Caster.toIntValue(data.get(CONNECTION_TIMEOUT,null),1), 
					Caster.toLongValue(data.get(META_CACHE_TIMEOUT,null),60000L), 
					Caster.toTimeZone(data.get(TIMEZONE,null),null), 
					Caster.toIntValue(data.get(ALLOW,null),DataSource.ALLOW_ALL),
					Caster.toBooleanValue(data.get(STORAGE,null),false),
					Caster.toBooleanValue(data.get(READ_ONLY,null),false),log);
				}
				catch(Exception cnfe){
					throw Caster.toPageException(cnfe);
				}
			}
			// then for {type:... , host:... , ...}
			String type=Caster.toString(data.get(KeyConstants._type));
			DataSourceDefintion dbt = DBUtil.getDataSourceDefintionForType(type, null);
			if(dbt==null) throw new ApplicationException("no datasource type ["+type+"] found");
			try {
				return new DataSourceImpl(config,
					name, 
					dbt.classDefinition, 
					Caster.toString(data.get(KeyConstants._host)), 
					dbt.connectionString,
					Caster.toString(data.get(DATABASE)), 
					Caster.toIntValue(data.get(KeyConstants._port,null),-1), 
					user,pass, 
					Caster.toIntValue(data.get(CONNECTION_LIMIT,null),-1), 
					Caster.toIntValue(data.get(CONNECTION_TIMEOUT,null),1), 
					Caster.toLongValue(data.get(META_CACHE_TIMEOUT,null),60000L), 
					Caster.toBooleanValue(data.get(BLOB,null),false), 
					Caster.toBooleanValue(data.get(CLOB,null),false), 
					DataSource.ALLOW_ALL, 
					Caster.toStruct(data.get(KeyConstants._custom,null),null,false), 
					Caster.toBooleanValue(data.get(READ_ONLY,null),false), 
					true, 
					Caster.toBooleanValue(data.get(STORAGE,null),false), 
					Caster.toTimeZone(data.get(TIMEZONE,null),null),
					"",
					ParamSyntax.toParamSyntax(data,ParamSyntax.DEFAULT),
					Caster.toBooleanValue(data.get("literalTimestampWithTSOffset",null),false),
					Caster.toBooleanValue(data.get("alwaysSetTimeout",null),false),
					log
				);
			}
			catch(Exception cnfe){
				throw Caster.toPageException(cnfe);
			}

		
	}

	public static Mapping[] toMappings(ConfigWeb cw,Object o,Mapping[] defaultValue, Resource source) { 
		try {
			return toMappings(cw, o,source);
		} catch(Throwable t) {
			ExceptionUtil.rethrowIfNecessary(t);
			return defaultValue;
		}
	}

	public static Mapping[] toMappings(ConfigWeb cw,Object o, Resource source) throws PageException {
		Struct sct = Caster.toStruct(o);
		Iterator<Entry<Key, Object>> it = sct.entryIterator();
		Entry<Key, Object> e;
		java.util.List<Mapping> mappings=new ArrayList<Mapping>();
		ConfigWebImpl config=(ConfigWebImpl) cw;
		String virtual;
		while(it.hasNext()) {
			e = it.next();
			virtual=translateMappingVirtual(e.getKey().getString());
			MappingData md=toMappingData(e.getValue(),source);
			mappings.add(config.getApplicationMapping("application",virtual,md.physical,md.archive,md.physicalFirst,false));
		}
		return ConfigWebUtil.sort(mappings.toArray(new Mapping[mappings.size()]));
	}
	

	private static MappingData toMappingData(Object value, Resource source) throws PageException {
		MappingData md=new MappingData();
		
		if(Decision.isStruct(value)) {
			Struct map=Caster.toStruct(value);
			
			
			// physical
			String physical=Caster.toString(map.get("physical",null),null);
			if(!StringUtil.isEmpty(physical,true)) 
				md.physical=translateMappingPhysical(physical.trim(),source);

			// archive
			String archive = Caster.toString(map.get("archive",null),null);
			if(!StringUtil.isEmpty(archive,true)) 
				md.archive=translateMappingPhysical(archive.trim(),source);
			
			if(archive==null && physical==null) 
				throw new ApplicationException("you must define archive or/and physical!");
			
			// primary
			md.physicalFirst=true;
			// primary is only of interest when both values exists
			if(archive!=null && physical!=null) {
				String primary = Caster.toString(map.get("primary",null),null);
				if(primary!=null && primary.trim().equalsIgnoreCase("archive")) md.physicalFirst=false;
			}
			// only a archive
			else if(archive!=null) md.physicalFirst=false;
		}
		// simple value == only a physical path
		else {
			md.physical=translateMappingPhysical(Caster.toString(value).trim(),source);
			md.physicalFirst=true;
		}
		
		return md;
	}

	private static String translateMappingPhysical(String path, Resource source) {
		if(source==null) return path;
		source=source.getParentResource().getRealResource(path);
		if(source.exists()) return source.getAbsolutePath();
		return path;
	}

	private static String translateMappingVirtual(String virtual) {
		virtual=virtual.replace('\\', '/');
		if(!StringUtil.startsWith(virtual,'/'))virtual="/".concat(virtual);
		return virtual;
	}
	
	public static Mapping[] toCustomTagMappings(ConfigWeb cw, Object o, Resource source) throws PageException {
		return toMappings(cw,"custom", o,false,source);
	}

	public static Mapping[] toCustomTagMappings(ConfigWeb cw, Object o, Resource source, Mapping[] defaultValue) {
		try {
			return toMappings(cw,"custom", o,false,source);
		} catch(Throwable t) {
			ExceptionUtil.rethrowIfNecessary(t);
			return defaultValue;
		}
	}

	public static Mapping[] toComponentMappings(ConfigWeb cw, Object o, Resource source) throws PageException {
		return toMappings(cw,"component", o,true,source);
	}

	public static Mapping[] toComponentMappings(ConfigWeb cw, Object o, Resource source,Mapping[] defaultValue) {
		
		try {
			return toMappings(cw,"component", o,true,source);
		} catch(Throwable t) {
			ExceptionUtil.rethrowIfNecessary(t);
			return defaultValue;
		}
	}

	private static Mapping[] toMappings(ConfigWeb cw,String type, Object o, boolean useStructNames, Resource source) throws PageException {
		ConfigWebImpl config=(ConfigWebImpl) cw;
		Array array;
		if(o instanceof String){
			array=ListUtil.listToArrayRemoveEmpty(Caster.toString(o),',');
		}
		else if(o instanceof Struct){
			Struct sct=(Struct) o;
			if(useStructNames) {
				Iterator<Entry<Key, Object>> it = sct.entryIterator();
				List<Mapping> list=new ArrayList<Mapping>();
				Entry<Key, Object> e;
				String virtual;
				while(it.hasNext()) {
					e = it.next();
					virtual=e.getKey().getString();
					if(virtual.length()==0) virtual="/";
					if(!virtual.startsWith("/")) virtual="/"+virtual;
			        if(!virtual.equals("/") && virtual.endsWith("/"))virtual=virtual.substring(0,virtual.length()-1);
			        MappingData md=toMappingData(e.getValue(),source);
					list.add(config.getApplicationMapping(type,virtual,md.physical,md.archive,md.physicalFirst,true));
				}
				return list.toArray(new Mapping[list.size()]);
			}
			
			array=new ArrayImpl();
			Iterator<Object> it = sct.valueIterator();
			while(it.hasNext()) {
				array.append(it.next());
			}
		}
		else {
			array=Caster.toArray(o);
		}
		MappingImpl[] mappings=new MappingImpl[array.size()];
		for(int i=0;i<mappings.length;i++) {
			
			MappingData md=toMappingData(array.getE(i+1),source);
			mappings[i]=(MappingImpl) config.getApplicationMapping(type,"/"+i,md.physical,md.archive,md.physicalFirst,true);
		}
		return mappings;
	}


	public static String toLocalMode(int mode, String defaultValue) {
		if(Undefined.MODE_LOCAL_OR_ARGUMENTS_ALWAYS==mode) return "modern";
		if(Undefined.MODE_LOCAL_OR_ARGUMENTS_ONLY_WHEN_EXISTS==mode)return "classic";
		return defaultValue;
	}
	
	public static int toLocalMode(Object oMode, int defaultValue) {
		if(oMode==null) return defaultValue;
		
		if(Decision.isBoolean(oMode)) {
			if(Caster.toBooleanValue(oMode, false))
				return Undefined.MODE_LOCAL_OR_ARGUMENTS_ALWAYS;
			return Undefined.MODE_LOCAL_OR_ARGUMENTS_ONLY_WHEN_EXISTS;
		}
		String strMode=Caster.toString(oMode,null);
		if("always".equalsIgnoreCase(strMode) || "modern".equalsIgnoreCase(strMode)) 
			return Undefined.MODE_LOCAL_OR_ARGUMENTS_ALWAYS;
		if("update".equalsIgnoreCase(strMode) || "classic".equalsIgnoreCase(strMode)) 
			return Undefined.MODE_LOCAL_OR_ARGUMENTS_ONLY_WHEN_EXISTS;
		return defaultValue;
	}
	
	public static int toLocalMode(String strMode) throws ApplicationException {
		int lm = toLocalMode(strMode, -1);
		if(lm!=-1) return lm;
		throw new ApplicationException("invalid localMode definition ["+strMode+"] for tag application, valid values are [classic,modern,true,false]");
	}

	public static String toSessionType(short type, String defaultValue) {
		if(type==Config.SESSION_TYPE_APPLICATION) return "application";
		if(type==Config.SESSION_TYPE_JEE) return "jee";
		return defaultValue;
	}
	public static short toSessionType(String str, short defaultValue) {
		if(!StringUtil.isEmpty(str,true)){
			str=str.trim().toLowerCase();
			if("cfml".equals(str)) return Config.SESSION_TYPE_APPLICATION;
			if("j2ee".equals(str)) return Config.SESSION_TYPE_JEE;
			
			if("cfm".equals(str)) return Config.SESSION_TYPE_APPLICATION;
			if("application".equals(str)) return Config.SESSION_TYPE_APPLICATION;
			if("jee".equals(str)) return Config.SESSION_TYPE_JEE;
			if("j".equals(str)) return Config.SESSION_TYPE_JEE;
			if("c".equals(str)) return Config.SESSION_TYPE_APPLICATION;
		}
		return defaultValue;
	}

	public static short toSessionType(String str) throws ApplicationException {
		short undefined=(short)-1;
		short type=toSessionType(str,undefined);
		if(type!=undefined) return type;
		
		throw new ApplicationException("invalid sessionType definition ["+str+"] for tag application, valid values are [application,jee]");
	}
	
	public static Properties toS3(Struct sct) {
		String host=Caster.toString(sct.get(KeyConstants._host,null),null);
		if(StringUtil.isEmpty(host))host=Caster.toString(sct.get(KeyConstants._server,null),null);
		
		return toS3(
				Caster.toString(sct.get(ACCESS_KEY_ID,null),null),
				Caster.toString(sct.get(AWS_SECRET_KEY,null),null),
				Caster.toString(sct.get(DEFAULT_LOCATION,null),null),
				host,
				Caster.toString(sct.get(ACL,null),null)
			);
	}

	public static Properties toS3(String accessKeyId, String awsSecretKey, String defaultLocation, String host, String acl) {
		PropertiesImpl s3 = new PropertiesImpl();
		if(!StringUtil.isEmpty(accessKeyId))s3.setAccessKeyId(accessKeyId);
		if(!StringUtil.isEmpty(awsSecretKey))s3.setSecretAccessKey(awsSecretKey);
		if(!StringUtil.isEmpty(defaultLocation))s3.setDefaultLocation(defaultLocation);
		if(!StringUtil.isEmpty(host))s3.setHost(host);
		if(!StringUtil.isEmpty(acl))s3.setACL(acl);
		return s3;
	}

	public static void setORMConfiguration(PageContext pc, ApplicationContext ac,Struct sct) throws PageException {
		if(sct==null)sct=new StructImpl();
		ConfigImpl config=(ConfigImpl) pc.getConfig();
		PageSource curr = pc.getCurrentTemplatePageSource();
		Resource res=curr==null?null:curr.getResourceTranslated(pc).getParentResource();
		ac.setORMConfiguration(ORMConfigurationImpl.load(config,ac,sct,res,config.getORMConfig()));
		
		// datasource
		Object o = sct.get(KeyConstants._datasource,null);
		
		if(o!=null) {
			o=toDefaultDatasource(config,o,LogUtil.getLog(pc,"application"));
			if(o!=null) ac.setORMDataSource(o);
		}
	}
	
	
	/**
	 * translate int definition of script protect to string definition
	 * @param scriptProtect
	 * @return
	 */
	public static String translateScriptProtect(int scriptProtect) {
		if(scriptProtect==ApplicationContext.SCRIPT_PROTECT_NONE) return "none";
		if(scriptProtect==ApplicationContext.SCRIPT_PROTECT_ALL) return "all";
		
		Array arr=new ArrayImpl();
		if((scriptProtect&ApplicationContext.SCRIPT_PROTECT_CGI)>0) arr.appendEL("cgi");
		if((scriptProtect&ApplicationContext.SCRIPT_PROTECT_COOKIE)>0) arr.appendEL("cookie");
		if((scriptProtect&ApplicationContext.SCRIPT_PROTECT_FORM)>0) arr.appendEL("form");
		if((scriptProtect&ApplicationContext.SCRIPT_PROTECT_URL)>0) arr.appendEL("url");
		
		
		
		try {
			return ListUtil.arrayToList(arr, ",");
		} catch (PageException e) {
			return "none";
		} 
	}
	

	/**
	 * translate string definition of script protect to int definition
	 * @param strScriptProtect
	 * @return
	 */
	public static int translateScriptProtect(String strScriptProtect) {
		strScriptProtect=strScriptProtect.toLowerCase().trim();
		
		if("none".equals(strScriptProtect)) return ApplicationContext.SCRIPT_PROTECT_NONE;
		if("no".equals(strScriptProtect)) return ApplicationContext.SCRIPT_PROTECT_NONE;
		if("false".equals(strScriptProtect)) return ApplicationContext.SCRIPT_PROTECT_NONE;
		
		if("all".equals(strScriptProtect)) return ApplicationContext.SCRIPT_PROTECT_ALL;
		if("true".equals(strScriptProtect)) return ApplicationContext.SCRIPT_PROTECT_ALL;
		if("yes".equals(strScriptProtect)) return ApplicationContext.SCRIPT_PROTECT_ALL;
		
		String[] arr = ListUtil.listToStringArray(strScriptProtect, ',');
		String item;
		int scriptProtect=0;
		for(int i=0;i<arr.length;i++) {
			item=arr[i].trim();
			if("cgi".equals(item) && (scriptProtect&ApplicationContext.SCRIPT_PROTECT_CGI)==0)
				scriptProtect+=ApplicationContext.SCRIPT_PROTECT_CGI;
			else if("cookie".equals(item) && (scriptProtect&ApplicationContext.SCRIPT_PROTECT_COOKIE)==0)
				scriptProtect+=ApplicationContext.SCRIPT_PROTECT_COOKIE;
			else if("form".equals(item) && (scriptProtect&ApplicationContext.SCRIPT_PROTECT_FORM)==0)
				scriptProtect+=ApplicationContext.SCRIPT_PROTECT_FORM;
			else if("url".equals(item) && (scriptProtect&ApplicationContext.SCRIPT_PROTECT_URL)==0)
				scriptProtect+=ApplicationContext.SCRIPT_PROTECT_URL;
		}
		return scriptProtect;
	}
	

	public static String translateLoginStorage(int loginStorage) {
		if(loginStorage==Scope.SCOPE_SESSION) return "session";
		return "cookie";
	}
	

	public static int translateLoginStorage(String strLoginStorage, int defaultValue) {
		strLoginStorage=strLoginStorage.toLowerCase().trim();
	    if(strLoginStorage.equals("session"))return Scope.SCOPE_SESSION;
	    if(strLoginStorage.equals("cookie"))return Scope.SCOPE_COOKIE;
	    return defaultValue;
	}
	

	public static int translateLoginStorage(String strLoginStorage) throws ApplicationException {
		int ls=translateLoginStorage(strLoginStorage, -1);
		if(ls!=-1) return ls;
	    throw new ApplicationException("invalid loginStorage definition ["+strLoginStorage+"], valid values are [session,cookie]");
	}
	
	public static Object toDefaultDatasource(Config config,Object o,Log log) throws PageException {
		if(Decision.isStruct(o)) {
			Struct sct=(Struct) o;
			
			// fix for Jira ticket LUCEE-1931
			if(sct.size()==1) {
				Key[] keys = CollectionUtil.keys(sct);
				if(keys.length==1 && keys[0].equalsIgnoreCase(KeyConstants._name)) {
					return Caster.toString(sct.get(KeyConstants._name));
				}
			}
			
			try {
				return AppListenerUtil.toDataSource(config,"__default__",sct,log);
			} 
			catch (PageException pe) { 
				// again try fix for Jira ticket LUCEE-1931
				String name= Caster.toString(sct.get(KeyConstants._name,null),null);
				if(!StringUtil.isEmpty(name)) return name;
				throw pe;
			}
			catch (Exception e) {
				throw Caster.toPageException(e);
			}
		}
		return Caster.toString(o);
	}

	public static String toWSType(short wstype, String defaultValue) {
		if(ApplicationContext.WS_TYPE_AXIS1== wstype) return "Axis1";
		if(ApplicationContext.WS_TYPE_JAX_WS== wstype) return "JAX-WS";
		if(ApplicationContext.WS_TYPE_CXF== wstype) return "CXF";
		return defaultValue;
	}
	
	public static short toWSType(String wstype, short defaultValue) {
		if(wstype==null) return defaultValue;
		wstype=wstype.trim();
		
		if("axis".equalsIgnoreCase(wstype) || "axis1".equalsIgnoreCase(wstype))
			return ApplicationContext.WS_TYPE_AXIS1;
		/*if("jax".equalsIgnoreCase(wstype) || "jaxws".equalsIgnoreCase(wstype) || "jax-ws".equalsIgnoreCase(wstype))
			return ApplicationContextPro.WS_TYPE_JAX_WS;
		if("cxf".equalsIgnoreCase(wstype))
			return ApplicationContextPro.WS_TYPE_CXF;*/
		return defaultValue;
	}
	
	public static int toCachedWithinType(String type, int defaultValue)	{
		if(StringUtil.isEmpty(type,true)) return defaultValue;
		
		type=type.trim().toLowerCase();
		if("function".equalsIgnoreCase(type)) return Config.CACHEDWITHIN_FUNCTION;
		if("udf".equalsIgnoreCase(type)) return Config.CACHEDWITHIN_FUNCTION;
		if("include".equalsIgnoreCase(type)) return Config.CACHEDWITHIN_INCLUDE;
		if("query".equalsIgnoreCase(type)) return Config.CACHEDWITHIN_QUERY;
		if("resource".equalsIgnoreCase(type)) return Config.CACHEDWITHIN_RESOURCE;
		if("http".equalsIgnoreCase(type)) return Config.CACHEDWITHIN_HTTP;
		if("file".equalsIgnoreCase(type)) return Config.CACHEDWITHIN_FILE;
		if("webservice".equalsIgnoreCase(type)) return Config.CACHEDWITHIN_WEBSERVICE;
		
		return defaultValue;
	}
	
	public static String toCachedWithinType(int type, String defaultValue)	{
		
		if(type==Config.CACHEDWITHIN_FUNCTION) return "function";
		if(type==Config.CACHEDWITHIN_INCLUDE) return "include";
		if(type==Config.CACHEDWITHIN_QUERY) return "query";
		if(type==Config.CACHEDWITHIN_RESOURCE) return "resource";
		if(type==Config.CACHEDWITHIN_HTTP) return "http";
		if(type==Config.CACHEDWITHIN_FILE) return "file";
		if(type==Config.CACHEDWITHIN_WEBSERVICE) return "webservice";
		
		return defaultValue;
	}
	
	public static short toWSType(String wstype) throws ApplicationException {
		String str="";
		KeyImpl cs=new KeyImpl(str){
			
			public String getString() {
				return null;
			}
			
		};
		
		
		
		short wst = toWSType(wstype,(short)-1);
		if(wst!=-1) return wst;
		throw new ApplicationException("invalid webservice type ["+wstype+"], valid values are [axis1]");
		//throw new ApplicationException("invalid webservice type ["+wstype+"], valid values are [axis1,jax-ws,cxf]");
	}
	static private class MappingData {
		private String physical;
		private String archive;
		private boolean physicalFirst;
	}
	
	public static SessionCookieData toSessionCookie(ConfigWeb config, Struct data) {
		if(data==null) return SessionCookieDataImpl.DEFAULT; 
		
		return new SessionCookieDataImpl(
			Caster.toBooleanValue(data.get(KeyConstants._httponly,null),SessionCookieDataImpl.DEFAULT.isHttpOnly()), 
			Caster.toBooleanValue(data.get(KeyConstants._secure,null),SessionCookieDataImpl.DEFAULT.isSecure()), 
			toTimespan(data.get(KeyConstants._timeout,null),SessionCookieDataImpl.DEFAULT.getTimeout()), 
			Caster.toString(data.get(KeyConstants._domain,null),SessionCookieDataImpl.DEFAULT.getDomain()), 
			Caster.toBooleanValue(data.get(DISABLE_UPDATE,null),SessionCookieDataImpl.DEFAULT.isDisableUpdate())
		);
	}

	public static AuthCookieData toAuthCookie(ConfigWeb config, Struct data) { 
		if(data==null) return AuthCookieDataImpl.DEFAULT; 
		return new AuthCookieDataImpl(
			toTimespan(data.get(KeyConstants._timeout,null),AuthCookieDataImpl.DEFAULT.getTimeout()), 
			Caster.toBooleanValue(data.get(DISABLE_UPDATE,null),AuthCookieDataImpl.DEFAULT.isDisableUpdate())
		);
	}

	private static TimeSpan toTimespan(Object obj, TimeSpan defaultValue) {
		if(!(obj instanceof TimeSpan)) {
			Double tmp = Caster.toDouble(obj,null);
			if(tmp!=null && tmp.doubleValue()<=0d) return TimeSpanImpl.fromMillis(CookieImpl.NEVER*1000);
		}
		
		
		return Caster.toTimespan(obj, defaultValue);
	}
	

	public static Server[] toMailServers(Config config,Array data, Server defaultValue) {
		List<Server> list=new ArrayList<Server>();
		if(data!=null){ 
			Iterator<Object> it = data.valueIterator();
			Struct sct;
			Server se;
			while(it.hasNext()) {
				sct=Caster.toStruct(it.next(),null);
				if(sct==null) continue;
				
				se=toMailServer(config,sct,null);
				
				if(se!=null) list.add(se);
			}
		}
		return list.toArray(new Server[list.size()]);
	}
	
	public static Server toMailServer(Config config,Struct data, Server defaultValue) {
		String hostName = Caster.toString(data.get(KeyConstants._host,null),null);
		if(StringUtil.isEmpty(hostName,true)) hostName = Caster.toString(data.get(KeyConstants._server,null),null);
		if(StringUtil.isEmpty(hostName,true)) return defaultValue;
		
		int port = Caster.toIntValue(data.get(KeyConstants._port,null),25);
		
		String username = Caster.toString(data.get(KeyConstants._username,null),null);
		if(StringUtil.isEmpty(username,true))username = Caster.toString(data.get(KeyConstants._user,null),null);
		String password = ConfigWebUtil.decrypt(Caster.toString(data.get(KeyConstants._password,null),null));

		TimeSpan lifeTimespan = Caster.toTimespan(data.get("lifeTimespan",null),null);
		if(lifeTimespan==null)lifeTimespan = Caster.toTimespan(data.get("life",null),FIVE_MINUTES);

		TimeSpan idleTimespan = Caster.toTimespan(data.get("idleTimespan",null),null);
		if(idleTimespan==null)idleTimespan = Caster.toTimespan(data.get("idle",null),ONE_MINUTE);
		

		boolean tls = Caster.toBooleanValue(data.get("tls",null),false);
		boolean ssl = Caster.toBooleanValue(data.get("ssl",null),false);
		
		return new ServerImpl(-1,hostName, port, username, password, lifeTimespan.getMillis(), idleTimespan.getMillis(), tls, ssl, false,ServerImpl.TYPE_LOCAL); // MUST improve store connection somehow
	}

}