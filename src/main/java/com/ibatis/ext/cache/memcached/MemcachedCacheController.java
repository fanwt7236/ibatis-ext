package com.ibatis.ext.cache.memcached;

import java.io.IOException;
import java.util.Properties;

import com.ibatis.common.logging.Log;
import com.ibatis.common.logging.LogFactory;
import com.ibatis.sqlmap.engine.cache.CacheController;
import com.ibatis.sqlmap.engine.cache.CacheModel;

import net.rubyeye.xmemcached.MemcachedClient;

/**
 * 基于memcached的ibatis缓存 作为外接存储的话，缓存的数据量可以适当增大一些，
 * 因此可以采用通过设置存活时间的方式来限制mem服务端的数据不会一直递增下去，
 * 而且memcached可以使用客户端分布式的方式，可以将大量的数据都分散到内存空间比较大的设备上去
 * 
 * @author fanwt7236@163.com
 */
public class MemcachedCacheController implements CacheController {

	private Log log = LogFactory.getLog(MemcachedCacheController.class);

	private int expTime;
	private MemcachedClient client;

	public void flush(CacheModel cacheModel) {
		// 我们利用时间来控制数量，这里就什么都不做了
	}

	public Object getObject(CacheModel cacheModel, Object key) {
		if(this.client == null){
			return null;
		}
		Object result = null;
		try {
			//这里使用getAndTouch方法，在获取数据的同时戳一下
			result = this.client.getAndTouch(generateKey(cacheModel, key), expTime);
		} catch (Exception e) {
			log.error("memcached getObject error!!!", e);
		}
		return result;
	}

	public Object removeObject(CacheModel cacheModel, Object key) {
		if(this.client == null){
			return null;
		}
		try {
			//这里使用deleteWithNoReply，不关注执行结果
			this.client.deleteWithNoReply(generateKey(cacheModel, key));
		} catch (Exception e) {
			log.error("memcached removeObject error!!!", e);
		}
		return null;
	}

	public void putObject(CacheModel cacheModel, Object key, Object object) {
		if (this.client == null) {
			return;
		}
		try {
			this.client.set(generateKey(cacheModel, key), expTime, object);
		} catch (Exception e) {
			log.error("memcached putObject error!!!", e);
		}
	}

	public void setProperties(Properties props) {
		String servers = props.getProperty("mem.servers");
		MemcachedClientBuilder builder = new MemcachedClientBuilder(servers);
		builder.setFailureMode(true);
		try {
			this.client = builder.build();
			String eTime = props.getProperty("mem.expTime", "3600");
			this.expTime = Integer.parseInt(eTime);
		} catch (IOException e) {
			log.error("初始化memcached-client失败", e);
		}
	}

	private String generateKey(CacheModel cacheModel, Object key) {
		return cacheModel.getId() + "|" + key.toString();
	}

}
