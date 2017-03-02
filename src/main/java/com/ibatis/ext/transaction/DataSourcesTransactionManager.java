package com.ibatis.ext.transaction;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.sql.DataSource;

import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.JdbcTransactionObjectSupport;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 多数据源的事务管理器
 * 
 * @author fanwt7236@163.com
 */
public class DataSourcesTransactionManager extends DataSourceTransactionManager {

	private static final long serialVersionUID = 1L;

	private List<DataSource> dataSources;

	public DataSourcesTransactionManager() {
		setNestedTransactionAllowed(true);
	}

	public DataSourcesTransactionManager(List<DataSource> dataSources) {
		this();
		setDataSources(dataSources);
		afterPropertiesSet();
	}

	@SuppressWarnings("unchecked")
	@Override
	protected Object doGetTransaction() throws TransactionException {
		DataSourceTransactionObject txObject = new DataSourceTransactionObject();
		txObject.setSavepointAllowed(isNestedTransactionAllowed());
		List<ConnectionsHolder> conHolders = (List<ConnectionsHolder>) TransactionSynchronizationManager.getResource(this.dataSources);
		txObject.setConnectionHolders(conHolders, false);
		return txObject;
	}

	@Override
	protected void doBegin(Object transaction, TransactionDefinition definition) throws TransactionException {
		DataSourceTransactionObject txObject = (DataSourceTransactionObject) transaction;
		try {
			List<ConnectionsHolder> chs = txObject.getConnectionHolders();
			boolean reset = false;
			if (chs == null || chs.isEmpty()) {
				reset = true;
			} else {
				Iterator<ConnectionsHolder> it = chs.iterator();
				while (it.hasNext()) {
					ConnectionsHolder holder = it.next();
					if (holder.isSynchronizedWithTransaction()) {
						reset = true;
						break;
					}
				}
			}

			if (reset) {
				List<ConnectionsHolder> connectionHolders = new ArrayList<ConnectionsHolder>();
				for (DataSource ds : this.dataSources) {
					Connection newCon = ds.getConnection();
					if (logger.isDebugEnabled()) {
						logger.debug("Acquired Connection [" + newCon + "] for JDBC transaction");
					}
					ConnectionsHolder connectionsHolder = new ConnectionsHolder(newCon, ds);
					connectionsHolder.setSynchronizedWithTransaction(true);
					connectionHolders.add(connectionsHolder);
				}
				txObject.setConnectionHolders(connectionHolders, true);
			} else {
				for (ConnectionHolder holder : txObject.getConnectionHolders()) {
					holder.setSynchronizedWithTransaction(true);
				}
			}

			List<ConnectionsHolder> connectionHolders = txObject.getConnectionHolders();

			int timeout = determineTimeout(definition);
			for (ConnectionsHolder holder : connectionHolders) {
				Connection con = holder.getConnection();
				Integer previousIsolationLevel = DataSourceUtils.prepareConnectionForTransaction(con, definition);
				txObject.setPreviousIsolationLevel(previousIsolationLevel);
				if (con.getAutoCommit()) {
					txObject.setMustRestoreAutoCommit(true);
					if (logger.isDebugEnabled()) {
						logger.debug("Switching JDBC Connection [" + con + "] to manual commit");
					}
					con.setAutoCommit(false);
				}
				holder.setTransactionActive(true);
				if (timeout != TransactionDefinition.TIMEOUT_DEFAULT) {
					holder.setTimeoutInSeconds(timeout);
				}
			}

			if (txObject.isNewConnectionHolder()) {
				TransactionSynchronizationManager.bindResource(this.dataSources, connectionHolders);
				for(ConnectionsHolder holder : connectionHolders){
					TransactionSynchronizationManager.bindResource(holder.getDataSource(), holder);
				}
			}
		}

		catch (Throwable ex) {
			List<ConnectionsHolder> connectionHolders = txObject.getConnectionHolders();
			if (connectionHolders != null && !connectionHolders.isEmpty()) {
				for (ConnectionsHolder holder : connectionHolders) {
					DataSourceUtils.releaseConnection(holder.getConnection(), holder.getDataSource());
				}
			}
			throw new CannotCreateTransactionException("Could not open JDBC Connection for transaction", ex);
		}
	}

	@Override
	protected void doCommit(DefaultTransactionStatus status) throws TransactionException {
		DataSourceTransactionObject txObject = (DataSourceTransactionObject) status.getTransaction();
		try {
			List<ConnectionsHolder> connectionHolders = txObject.getConnectionHolders();
			for(ConnectionsHolder holder : connectionHolders){
				Connection con = holder.getConnection();
				if (status.isDebug()) {
					logger.debug("Committing JDBC transaction on Connection [" + con + "]");
				}
				con.commit();
			}
		} catch (SQLException ex) {
			throw new TransactionSystemException("Could not commit JDBC transaction", ex);
		}
	}

	@Override
	protected void doRollback(DefaultTransactionStatus status) throws TransactionException {
		DataSourceTransactionObject txObject = (DataSourceTransactionObject) status.getTransaction();
		try {
			List<ConnectionsHolder> connectionHolders = txObject.getConnectionHolders();
			for(ConnectionsHolder holder : connectionHolders){
				Connection con = holder.getConnection();
				if (status.isDebug()) {
					logger.debug("Rolling back JDBC transaction on Connection [" + con + "]");
				}
				con.rollback();
			}
		} catch (SQLException ex) {
			throw new TransactionSystemException("Could not rollback JDBC transaction", ex);
		}
	}

	public void setDataSources(List<DataSource> dataSources) {
		this.dataSources = dataSources;
	}

	public List<DataSource> getDataSources() {
		return dataSources;
	}
	
	@Override
	protected boolean isExistingTransaction(Object transaction) {
		DataSourceTransactionObject txObject = (DataSourceTransactionObject) transaction;
		return (txObject.getConnectionHolders() != null && !txObject.getConnectionHolders().isEmpty() && txObject.getConnectionHolders().get(0).isTransactionActive());
	}
	
	@SuppressWarnings("unchecked")
	@Override
	protected Object doSuspend(Object transaction) {
		DataSourceTransactionObject txObject = (DataSourceTransactionObject) transaction;
		List<ConnectionsHolder> conHolder = (List<ConnectionsHolder>)TransactionSynchronizationManager.unbindResource(this.dataSources);
		for(ConnectionsHolder holder : conHolder){
			TransactionSynchronizationManager.unbindResource(holder.getDataSource());
		}
		txObject.setConnectionHolders(conHolder);
		return conHolder;
	}

	@SuppressWarnings("unchecked")
	@Override
	protected void doResume(Object transaction, Object suspendedResources) {
		List<ConnectionsHolder> conHolder = (List<ConnectionsHolder>) suspendedResources;
		TransactionSynchronizationManager.bindResource(this.dataSources, conHolder);
		for(ConnectionsHolder holder : conHolder){
			TransactionSynchronizationManager.bindResource(holder.getDataSource(), holder);
		}
	}
	
	@Override
	protected void doSetRollbackOnly(DefaultTransactionStatus status) {
		DataSourceTransactionObject txObject = (DataSourceTransactionObject) status.getTransaction();
		txObject.setRollbackOnly();
	}

	@SuppressWarnings("unchecked")
	@Override
	protected void doCleanupAfterCompletion(Object transaction) {
		DataSourceTransactionObject txObject = (DataSourceTransactionObject) transaction;

		// Remove the connection holder from the thread, if exposed.
		if (txObject.isNewConnectionHolder()) {
			List<ConnectionsHolder> holders = (List<ConnectionsHolder>)TransactionSynchronizationManager.unbindResource(this.dataSources);
			for(ConnectionsHolder holder : holders){
				TransactionSynchronizationManager.unbindResource(holder.getDataSource());
			}
		}

		List<ConnectionsHolder> connectionHolders = txObject.getConnectionHolders();
		
		for(ConnectionsHolder holder : connectionHolders){
			Connection con = holder.getConnection();
			try {
				if (txObject.isMustRestoreAutoCommit()) {
					con.setAutoCommit(true);
				}
				DataSourceUtils.resetConnectionAfterTransaction(con, txObject.getPreviousIsolationLevel());
			} catch (Throwable ex) {
				logger.debug("Could not reset JDBC Connection after transaction", ex);
			}
			if (txObject.isNewConnectionHolder()) {
				if (logger.isDebugEnabled()) {
					logger.debug("Releasing JDBC Connection [" + con + "] after transaction");
				}
				DataSourceUtils.releaseConnection(con, holder.getDataSource());
			}
			holder.clear();
		}

	}


	public void afterPropertiesSet() {
		if (getDataSources() == null) {
			throw new IllegalArgumentException("Property 'dataSources' is required");
		}
	}

	public Object getResourceFactory() {
		return getDataSources();
	}

	/**
	 * DataSource transaction object, representing a ConnectionHolder. Used as
	 * transaction object by DataSourceTransactionManager.
	 */
	private static class DataSourceTransactionObject extends JdbcTransactionObjectSupport {

		private boolean newConnectionHolder;

		private boolean mustRestoreAutoCommit;
		
		private List<ConnectionsHolder> connectionHolders;
		
		public DataSourceTransactionObject() {
			this.connectionHolders = new ArrayList<ConnectionsHolder>();
		}
		
		public void setConnectionHolders(List<ConnectionsHolder> connectionHolders){
			this.connectionHolders = connectionHolders;
		}
		
		public void setConnectionHolders(List<ConnectionsHolder> connectionHolders, boolean newConnectionHolder) {
			this.connectionHolders = connectionHolders;
			this.newConnectionHolder = newConnectionHolder;
		}

		public List<ConnectionsHolder> getConnectionHolders() {
			return connectionHolders;
		}

		public boolean isNewConnectionHolder() {
			return this.newConnectionHolder;
		}

		public void setMustRestoreAutoCommit(boolean mustRestoreAutoCommit) {
			this.mustRestoreAutoCommit = mustRestoreAutoCommit;
		}

		public boolean isMustRestoreAutoCommit() {
			return this.mustRestoreAutoCommit;
		}

		public void setRollbackOnly() {
			List<ConnectionsHolder> holders = getConnectionHolders();
			for(ConnectionsHolder holder : holders){
				holder.setRollbackOnly();
			}
		}

		public boolean isRollbackOnly() {
			return getConnectionHolders().get(0).isRollbackOnly();
		}

		

		
		
	}

}
