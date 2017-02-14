package com.ibatis.ext;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

import com.ibatis.ext.paging.IPageSqlBuilder;
import com.ibatis.ext.paging.Page;
import com.ibatis.ext.paging.PageSection;
import com.ibatis.ext.paging.dialect.MySqlPageSqlBuilder;
import com.ibatis.sqlmap.engine.config.SqlMapConfiguration;
import com.ibatis.sqlmap.engine.execution.DefaultSqlExecutor;
import com.ibatis.sqlmap.engine.impl.SqlMapClientImpl;
import com.ibatis.sqlmap.engine.impl.SqlMapExecutorDelegate;
import com.ibatis.sqlmap.engine.mapping.result.ResultObjectFactoryUtil;
import com.ibatis.sqlmap.engine.mapping.statement.MappedStatement;
import com.ibatis.sqlmap.engine.mapping.statement.RowHandlerCallback;
import com.ibatis.sqlmap.engine.scope.ErrorContext;
import com.ibatis.sqlmap.engine.scope.SessionScope;
import com.ibatis.sqlmap.engine.scope.StatementScope;

/**
 * 扩展了基本的SqlExecutor,实现了自动真分页查询的功能
 * @author fanwt7236@163.com
 *
 */
public class SqlExecutorExt extends DefaultSqlExecutor {
	
	//一个分页sql构造器，默认是mysql的，也可以在sql-map-config文件中去配置
	private IPageSqlBuilder pageSqlBuilder = new MySqlPageSqlBuilder();
	//分页sql，statementId的正则表达式，只有匹配的statementId才可能会分页
	private String pageSqlRegEx = "selectList(.*)";
	
	@Override
	public void executeQuery(StatementScope statementScope, Connection conn, String sql, Object[] parameters, int skipResults, int maxResults, RowHandlerCallback callback) throws SQLException {
		try {
			Page page = PageSection.get();
			String id = statementScope.getStatement().getId();
			String methodName = id.substring(id.lastIndexOf('.') + 1);
			if (methodName.matches(this.pageSqlRegEx) && page != null) {
				setPageParameter(statementScope, conn, sql, parameters, skipResults, maxResults, callback, page);
				sql = this.pageSqlBuilder.buildSql(sql, page);
			}
		} finally {
			super.executeQuery(statementScope, conn, sql, parameters, skipResults, maxResults, callback);
		}
	}

	private void setPageParameter(StatementScope statementScope, Connection conn, String sql, Object[] parameters, int skipResults, int maxResults, RowHandlerCallback callback, Page page) throws SQLException {
		sql = "select count(*) from (" + sql + ") temp";
		ErrorContext errorContext = statementScope.getErrorContext();
		errorContext.setActivity("executing query");
		errorContext.setObjectId(sql);
		PreparedStatement ps = null;
		ResultSet rs = null;
		setupResultObjectFactory(statementScope);
		try {
			errorContext.setMoreInfo("Check the SQL Statement (preparation failed).");
			Integer rsType = statementScope.getStatement().getResultSetType();
			if (rsType != null) {
				ps = prepareStatement(statementScope.getSession(), conn, sql, rsType);
			} else {
				ps = prepareStatement(statementScope.getSession(), conn, sql);
			}
			setStatementTimeout(statementScope.getStatement(), ps);
			Integer fetchSize = statementScope.getStatement().getFetchSize();
			if (fetchSize != null) {
				ps.setFetchSize(fetchSize.intValue());
			}
			errorContext.setMoreInfo("Check the parameters (set parameters failed).");
			statementScope.getParameterMap().setParameters(statementScope, ps, parameters);
			errorContext.setMoreInfo("Check the statement (query failed).");
			rs = ps.executeQuery();
			errorContext.setMoreInfo("Check the results (failed to retrieve results).");
			if (rs.next()) {
				long totalRows = rs.getLong(1);
				page.setTotalRows(totalRows);
				page.setTotalPage((int) (totalRows % page.getPageSize() == 0 ? totalRows / page.getPageSize() : totalRows / page.getPageSize() + 1));
				page.setEndRow(Math.min(page.getPageNum() * page.getPageSize(), totalRows));
				page.setStartRow((long) (page.getPageSize() * (page.getPageNum() - 1)));
				page.setLastPage(page.getPageNum() > 1 ? page.getPageNum() - 1 : 1);
				page.setNextPage(page.getPageNum() < page.getTotalPage() ? page.getPageNum() + 1 : page.getTotalPage());
			}
			PageSection.put(page);
		} finally {
			try {
				closeResultSet(rs);
			} finally {
				closeStatement(statementScope.getSession(), ps);
			}
		}
	}

	private void setupResultObjectFactory(StatementScope statementScope) {
		SqlMapClientImpl client = (SqlMapClientImpl) statementScope.getSession().getSqlMapClient();
		ResultObjectFactoryUtil.setupResultObjectFactory(client.getResultObjectFactory(), statementScope.getStatement().getId());
	}

	private PreparedStatement prepareStatement(SessionScope sessionScope, Connection conn, String sql, Integer rsType)
			throws SQLException {
		SqlMapExecutorDelegate delegate = ((SqlMapClientImpl) sessionScope.getSqlMapExecutor()).getDelegate();
		if (sessionScope.hasPreparedStatementFor(sql)) {
			return sessionScope.getPreparedStatement((sql));
		} else {
			PreparedStatement ps = conn.prepareStatement(sql, rsType.intValue(), ResultSet.CONCUR_READ_ONLY);
			sessionScope.putPreparedStatement(delegate, sql, ps);
			return ps;
		}
	}

	private static PreparedStatement prepareStatement(SessionScope sessionScope, Connection conn, String sql)
			throws SQLException {
		SqlMapExecutorDelegate delegate = ((SqlMapClientImpl) sessionScope.getSqlMapExecutor()).getDelegate();
		if (sessionScope.hasPreparedStatementFor(sql)) {
			return sessionScope.getPreparedStatement((sql));
		} else {
			PreparedStatement ps = conn.prepareStatement(sql);
			sessionScope.putPreparedStatement(delegate, sql, ps);
			return ps;
		}
	}

	private static void setStatementTimeout(MappedStatement mappedStatement, Statement statement) throws SQLException {
		if (mappedStatement.getTimeout() != null) {
			statement.setQueryTimeout(mappedStatement.getTimeout().intValue());
		}
	}

	private static void closeResultSet(ResultSet rs) {
		if (rs != null) {
			try {
				rs.close();
			} catch (SQLException e) {
				// ignore
			}
		}
	}

	private static void closeStatement(SessionScope sessionScope, PreparedStatement ps) {
		if (ps != null) {
			if (!sessionScope.hasPreparedStatement(ps)) {
				try {
					ps.close();
				} catch (SQLException e) {
				}
			}
		}
	}
	
	@Override
	public void init(SqlMapConfiguration config, Properties globalProps) {
		//读取外部配置，适配pageSqlBuilder和pageSqlRegEx
		String className = globalProps.getProperty("pagingBuilderClass", MySqlPageSqlBuilder.class.getName());
		try {
			this.pageSqlBuilder = ((IPageSqlBuilder) Class.forName(className).newInstance());
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		this.pageSqlRegEx = globalProps.getProperty("pagingRegEx", "selectList(.*)");
		//这里把原有client的执行代理进行了扩展，扩展的执行代理支持了insert返回影响记录数以及支持了真分页查询
		SqlMapExecutorDelegate delegate = new SqlMapExecutorDelegateExt(config.getDelegate(), this);
		config.getClient().delegate = delegate;
		try {
			Field field = SqlMapConfiguration.class.getDeclaredField("delegate");
			field.setAccessible(true);
			field.set(config, delegate);
			field = SqlMapConfiguration.class.getDeclaredField("typeHandlerFactory");
			field.setAccessible(true);
			field.set(config, delegate.getTypeHandlerFactory());
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
}
