package com.ibatis.ext.proxy;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import com.ibatis.ext.proxy.annotation.Key;
import com.ibatis.ext.proxy.annotation.Param;
import com.ibatis.sqlmap.client.SqlMapClient;
import com.ibatis.sqlmap.engine.impl.SqlMapClientImpl;
import com.ibatis.sqlmap.engine.mapping.statement.MappedStatement;
import com.ibatis.sqlmap.engine.mapping.statement.StatementType;

class ProxyFactory implements InvocationHandler {

	private static final Logger logger = Logger.getLogger(ProxyFactory.class.getName());

	private Class<?> clazz;
	private SqlMapClient client;

	private ProxyFactory(Class<?> mapperInterface, SqlMapClient client) {
		this.clazz = mapperInterface;
		this.client = client;
	}

	@SuppressWarnings("unchecked")
	public static <T> T newProxy(Class<T> mapperInterface, SqlMapClient client) {
		ClassLoader classLoader = mapperInterface.getClassLoader();
		Class<?>[] interfaces = new Class[] { mapperInterface };
		ProxyFactory proxy = new ProxyFactory(mapperInterface, client);
		T t = (T) Proxy.newProxyInstance(classLoader, interfaces, proxy);
		logger.info("create proxy success!! [" + mapperInterface.getName() + "]");
		return t;
	}

	public Object invoke(Object target, Method method, Object[] params) throws Throwable {
		try {
			if (method.getName().equals("toString")) {
				return this.clazz.getName() + target.getClass().getName();
			}
			MappedStatement id = ((SqlMapClientImpl) this.client).getMappedStatement(this.clazz.getName() + "." + method.getName());
			if (id.getStatementType() == StatementType.INSERT) {
				Object value = this.client.insert(id.getId(), getParam(params, method));
				if(method.getReturnType().isPrimitive() && value == null){
					throw new SQLException("声明了insert方法的返回值类型为基本类型，而实际并没有返回值。");
				}
				return this.client.insert(id.getId(), getParam(params, method));
			} else if (id.getStatementType() == StatementType.UPDATE) {
				return this.client.update(id.getId(), getParam(params, method));
			} else if (id.getStatementType() == StatementType.DELETE) {
				return this.client.delete(id.getId(), getParam(params, method));
			} else {
				ResultStruct result = returnTypeParse(method);
				switch (result.type) {
				case LIST:
					return this.client.queryForList(id.getId(), getParam(params, method));
				case MAP:
					return this.client.queryForMap(id.getId(), getParam(params, method), result.key);
				case OBJECT:
					return this.client.queryForObject(id.getId(), getParam(params, method));
				default:
					return null;
				}
			}
		} catch (Throwable t) {
			throw new SQLException(t);
		}
	}

	private ResultStruct returnTypeParse(Method method) throws SQLException {
		ResultStruct struct = new ResultStruct();
		if (method.getReturnType().equals(Void.TYPE)) {
			struct.type = ReturnType.VOID;
		} else if (method.getReturnType().isArray() || List.class.isAssignableFrom(method.getReturnType())) {
			struct.type = ReturnType.LIST;
		} else if (Map.class.isAssignableFrom(method.getReturnType())) {
			Key key = method.getAnnotation(Key.class);
			if (key == null) {
				throw new SQLException("can not find annotation @Key");
			}
			struct.type = ReturnType.MAP;
			struct.key = key.value();
		} else {
			struct.type = ReturnType.OBJECT;
		}
		return struct;
	}

	private Object getParam(Object[] args, Method method) {
		if (args == null || args.length == 0) {
			return null;
		} else if (args.length == 1) {
			return args[0];
		} else {
			Map<String, Object> param = new HashMap<String, Object>();
			Annotation[][] parameterAnnotations = method.getParameterAnnotations();
			List<String> paramNames = new ArrayList<String>();
			for (Annotation[] pAnnotations : parameterAnnotations) {
				for (Annotation a : pAnnotations) {
					if (a instanceof Param) {
						paramNames.add(((Param) a).value());
					}
				}
			}
			for (int i = 0; i < args.length; i++) {
				param.put(paramNames.get(i), args[i]);
			}
			return param;
		}
	}

	private class ResultStruct {
		ReturnType type;
		String key;
	}
}
