package com.ibatis.ext.proxy;

import org.springframework.beans.factory.FactoryBean;

import com.ibatis.sqlmap.client.SqlMapClient;

class MapperFactoryBean<T> implements FactoryBean<T>{

    private Class<T> mapperInterface;
    
    private SqlMapClient client;
    
    public void setClient(SqlMapClient client) {
        this.client = client;
    }
    
    public void setMapperInterface(Class<T> mapperInterface) {
        this.mapperInterface = mapperInterface;
    }
    
    public T getObject() throws Exception {
        return ProxyFactory.newProxy(this.mapperInterface, client);
    }

    public Class<?> getObjectType() {
        return mapperInterface;
    }

    public boolean isSingleton() {
        return true;
    }

}
