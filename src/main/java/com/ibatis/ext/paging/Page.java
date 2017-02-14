package com.ibatis.ext.paging;

import java.io.Serializable;

/**
 * 分页参数
 * @author fanwt7236@163.com
 */
public class Page implements Serializable {
	
	private static final long serialVersionUID = 2763308339502768368L;

	private Integer pageNum;//页码
	private Integer pageSize;
	private Integer totalPage;
	private Long totalRows;
	private Integer lastPage;
	private Integer nextPage;
	private Long startRow;
	private Long offset;//偏移量
	private Long endRow;
	private String sortField;
	private String sortType;

	public Integer getPageNum() {
		return pageNum;
	}

	public void setPageNum(Integer pageNum) {
		this.pageNum = pageNum;
	}

	public Integer getPageSize() {
		return pageSize;
	}

	public void setPageSize(Integer pageSize) {
		this.pageSize = pageSize;
	}

	public Integer getTotalPage() {
		return totalPage;
	}

	public void setTotalPage(Integer totalPage) {
		this.totalPage = totalPage;
	}

	public Long getTotalRows() {
		return totalRows;
	}

	public void setTotalRows(Long totalRows) {
		this.totalRows = totalRows;
	}

	public Integer getLastPage() {
		return lastPage;
	}

	public void setLastPage(Integer lastPage) {
		this.lastPage = lastPage;
	}

	public Integer getNextPage() {
		return nextPage;
	}

	public void setNextPage(Integer nextPage) {
		this.nextPage = nextPage;
	}

	public Long getStartRow() {
		return startRow;
	}

	public void setStartRow(Long startRow) {
		this.startRow = startRow;
	}

	public Long getOffset() {
		return offset;
	}

	public void setOffset(Long offset) {
		this.offset = offset;
	}

	public Long getEndRow() {
		return endRow;
	}

	public void setEndRow(Long endRow) {
		this.endRow = endRow;
	}

	public String getSortField() {
		return sortField;
	}

	public void setSortField(String sortField) {
		this.sortField = sortField;
	}

	public String getSortType() {
		return sortType;
	}

	public void setSortType(String sortType) {
		this.sortType = sortType;
	}

	@Override
	public String toString() {
		return "Page [pageNum=" + pageNum + ", pageSize=" + pageSize + ", totalPage=" + totalPage + ", totalRows="
				+ totalRows + ", lastPage=" + lastPage + ", nextPage=" + nextPage + ", startRow=" + startRow
				+ ", endRow=" + endRow + ", sortField=" + sortField + ", sortType=" + sortType + "]";
	}
}
