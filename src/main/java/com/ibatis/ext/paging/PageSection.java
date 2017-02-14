package com.ibatis.ext.paging;

public class PageSection {
	
	private static final ThreadLocal<Page> _PAGE = new ThreadLocal<Page>();

	public static Page get() {
		return _PAGE.get();
	}

	public static void put(Page page) {
		_PAGE.set(page);
	}
	
	public static void clear(){
		_PAGE.remove();
	}

}
