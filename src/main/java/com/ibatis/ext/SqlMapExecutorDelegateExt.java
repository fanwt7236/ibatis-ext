package com.ibatis.ext;

import java.sql.SQLException;

import com.ibatis.common.beans.ProbeFactory;
import com.ibatis.sqlmap.engine.execution.SqlExecutor;
import com.ibatis.sqlmap.engine.impl.SqlMapExecutorDelegate;
import com.ibatis.sqlmap.engine.mapping.statement.InsertStatement;
import com.ibatis.sqlmap.engine.mapping.statement.MappedStatement;
import com.ibatis.sqlmap.engine.mapping.statement.SelectKeyStatement;
import com.ibatis.sqlmap.engine.scope.SessionScope;
import com.ibatis.sqlmap.engine.scope.StatementScope;
import com.ibatis.sqlmap.engine.transaction.Transaction;

/**
 * 重写了insert方法，insert方法能够返回执行sql影响的记录数，而非selectKey的执行结果
 * @author fanwt7236@163.com
 */
public class SqlMapExecutorDelegateExt extends SqlMapExecutorDelegate {
	
	public SqlMapExecutorDelegateExt(SqlMapExecutorDelegate delegate, SqlExecutor sqlExecutor) {
		super();
		setCacheModelsEnabled(delegate.isCacheModelsEnabled());
		setLazyLoadingEnabled(delegate.isLazyLoadingEnabled());
		setEnhancementEnabled(delegate.isEnhancementEnabled());
		setUseColumnLabel(delegate.isUseColumnLabel());
		setForceMultipleResultSetSupport(delegate.isForceMultipleResultSetSupport());
		setTxManager(delegate.getTxManager());
		setStatementCacheEnabled(delegate.isStatementCacheEnabled());
		setResultObjectFactory(delegate.getResultObjectFactory());
		this.sqlExecutor = sqlExecutor;
	}

	public Object insert(SessionScope sessionScope, String id, Object param) throws SQLException {
		Object rows = null;
		
		MappedStatement ms = getMappedStatement(id);
		Transaction trans = getTransaction(sessionScope);
		boolean autoStart = trans == null;

		try {
			trans = autoStartTransaction(sessionScope, autoStart, trans);

			SelectKeyStatement selectKeyStatement = null;
			if (ms instanceof InsertStatement) {
				selectKeyStatement = ((InsertStatement) ms).getSelectKeyStatement();
			}

			// Here we get the old value for the key property. We'll want it
			// later if for some reason the
			// insert fails.
			Object oldKeyValue = null;
			String keyProperty = null;
			boolean resetKeyValueOnFailure = false;
			if (selectKeyStatement != null && !selectKeyStatement.isRunAfterSQL()) {
				keyProperty = selectKeyStatement.getKeyProperty();
				oldKeyValue = ProbeFactory.getProbe().getObject(param, keyProperty);
				executeSelectKey(sessionScope, trans, ms, param);
				resetKeyValueOnFailure = true;
			}

			StatementScope statementScope = beginStatementScope(sessionScope, ms);
			try {
				rows = ms.executeUpdate(statementScope, trans, param);
			} catch (SQLException e) {
				// uh-oh, the insert failed, so if we set the reset flag
				// earlier, we'll put the old value
				// back...
				if (resetKeyValueOnFailure)
					ProbeFactory.getProbe().setObject(param, keyProperty, oldKeyValue);
				// ...and still throw the exception.
				throw e;
			} finally {
				endStatementScope(statementScope);
			}

			if (selectKeyStatement != null && selectKeyStatement.isRunAfterSQL()) {
				executeSelectKey(sessionScope, trans, ms, param);
			}

			autoCommitTransaction(sessionScope, autoStart);
		} finally {
			autoEndTransaction(sessionScope, autoStart);
		}

		return rows;
	}

	private Object executeSelectKey(SessionScope sessionScope, Transaction trans, MappedStatement ms, Object param)
			throws SQLException {
		Object generatedKey = null;
		StatementScope statementScope;
		InsertStatement insert = (InsertStatement) ms;
		SelectKeyStatement selectKeyStatement = insert.getSelectKeyStatement();
		if (selectKeyStatement != null) {
			statementScope = beginStatementScope(sessionScope, selectKeyStatement);
			try {
				generatedKey = selectKeyStatement.executeQueryForObject(statementScope, trans, param, null);
				String keyProp = selectKeyStatement.getKeyProperty();
				if (keyProp != null) {
					ProbeFactory.getProbe().setObject(param, keyProp, generatedKey);
				}
			} finally {
				endStatementScope(statementScope);
			}
		}
		return generatedKey;
	}
	

}
