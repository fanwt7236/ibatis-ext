package com.ibatis.ext.paging;

/**
 * 分页SQL构造器
 * @author fanwt7236@163.com
 */
public interface IPageSqlBuilder {
	
	/**
     * 
     * 根据Page参数构造分页sql
     * @param sql
     * @param page
     * @return
     */
    String buildSql(String sql, Page page);
}
