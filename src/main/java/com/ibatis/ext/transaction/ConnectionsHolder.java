package com.ibatis.ext.transaction;

import java.sql.Connection;

import javax.sql.DataSource;

import org.springframework.jdbc.datasource.ConnectionHandle;
import org.springframework.jdbc.datasource.ConnectionHolder;

public class ConnectionsHolder extends ConnectionHolder {

	private DataSource dataSource;

	public ConnectionsHolder(Connection connection, DataSource dataSource) {
		super(connection);
		this.dataSource = dataSource;
	}
	
	public ConnectionsHolder(Connection connection, boolean transactionActive) {
		super(connection, transactionActive);
	}

	public ConnectionsHolder(Connection connection) {
		super(connection);
	}

	public ConnectionsHolder(ConnectionHandle connectionHandle) {
		super(connectionHandle);
	}
	
	@Override
	public void setTransactionActive(boolean transactionActive) {
		super.setTransactionActive(transactionActive);
	}
	
	public DataSource getDataSource() {
		return dataSource;
	}
	
	@Override
	public boolean isTransactionActive() {
		return super.isTransactionActive();
	}

}
