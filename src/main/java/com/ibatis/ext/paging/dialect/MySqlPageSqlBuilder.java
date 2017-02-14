package com.ibatis.ext.paging.dialect;

import com.ibatis.ext.paging.IPageSqlBuilder;
import com.ibatis.ext.paging.Page;

public class MySqlPageSqlBuilder implements IPageSqlBuilder{

	public String buildSql(String sql, Page page) {
		StringBuilder pageSql = new StringBuilder("select * from (");
        if (page.getSortField() != null && page.getSortField().trim().length() > 0) {
            String order = page.getSortType();
            String[] sqlSegs = sql.split("(?i)order\\s+by");
            pageSql.append(sqlSegs[0] + " order by " + page.getSortField() + " " + (order == null || order.trim().length() == 0 ? "desc" : order));
            if (sqlSegs.length > 1) {
                pageSql.append(" , " + sqlSegs[1]);
            }
        } else {
            pageSql.append(sql);
        }
        if(page.getPageNum() != null){
        	pageSql.append(") temp limit " + ((page.getPageNum() - 1) * page.getPageSize()) + "," + page.getPageSize());
        	page.setOffset((long) ((page.getPageNum() - 1) * page.getPageSize()));
        }else if(page.getOffset() != null){
        	pageSql.append(") temp limit " + page.getOffset() + "," + page.getPageSize());
        	page.setPageSize((int) ((page.getOffset() / page.getPageSize()) + 1));
        }
        return pageSql.toString();
	}

}
