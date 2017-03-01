package com.ibatis.ext.cache.redis;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.UnsupportedEncodingException;
import java.util.Properties;
import java.util.Set;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;

import com.ibatis.common.logging.Log;
import com.ibatis.common.logging.LogFactory;
import com.ibatis.sqlmap.engine.cache.CacheController;
import com.ibatis.sqlmap.engine.cache.CacheModel;

/**
 * 基于redis的ibatis缓存。 redis支持三种模式：普通模式，分片模式，哨兵模式， 而且redis是基于传统bio和连接池技术的，配置的方式非常多
 * 所以我们不在ibatis的基础上做集成，而是使用spring-redis 通过template来操作redis,因此需要外部注入一个template,
 * 但是CacheController是由ibatis完成实例化的，并不是一个spring组件。
 * 因此，我们不能使用实现ApplicationContextAware接口或者其他一些spring提供的监听或抽象类的方式来获取beanFactory，
 * 同时我们也不确保ibatis的工作环境一定是一个web项目或依赖了spring-web包(web包中提供了ContextLoader.
 * getCurrentWebApplicationContext方法来获取ApplicationContext)
 * 我们将使用静态的方式来获取spring容器中的RedisTemplate。
 * 另外我们将使用redis的hash和SortedSet来分别存储缓存数据和缓存数据的命中次数，
 * 每次进行读数据的同时将命中次数+1，在缓存区大小大于等于预设值时，将会对SortedSet进行排序，取出命中次数最少的缓存数据并删除
 * 由于我们只是使用整个redis的两组键值对，在刷新缓存区时，只需要将这两组键值对删除即可.
 * 
 * @author fanwt7236@163.com
 */
public class RedisCacheController implements CacheController {

	private Log log = LogFactory.getLog(RedisCacheController.class);

	// 用来存放数据的hash域，用hash的原因是因为其时间复杂度为O(1)
	private static final String HASH_POSTFIX = ".hash_domain";
	// 这里用一个set来存放所有的key
	private static final String SET_POSTFIX = ".set_domain";
	// 保存的集合大小
	private long size;

	public void flush(final CacheModel cacheModel) {
		if (RedisTemplateBuilder.getRedisTemplate() == null) {
			return;
		}
		RedisTemplateBuilder.getRedisTemplate().execute(new RedisCallback<Object>() {
			public Object doInRedis(RedisConnection connection) throws DataAccessException {
				// 刷新时直接把hash表删掉就OK了
				connection.del(generateHashDomainKey(cacheModel));
				// 刷新是直接把保存key值的set集合删掉就OK了
				connection.del(generateSetDomainKey(cacheModel));
				return null;
			}
		});
	}

	public Object getObject(final CacheModel cacheModel, final Object key) {
		if (RedisTemplateBuilder.getRedisTemplate() == null) {
			return null;
		}
		return RedisTemplateBuilder.getRedisTemplate().execute(new RedisCallback<Object>() {
			public Object doInRedis(RedisConnection connection) throws DataAccessException {
				try {
					// 从hash中获取缓存的值
					byte[] value = connection.hGet(generateHashDomainKey(cacheModel), generateValueKey(cacheModel, key));
					if (value != null) {
						// 如果缓存中存在数据，那就把set集合中的点击次数+1，表示为常用
						connection.zIncrBy(generateSetDomainKey(cacheModel), 1.0, generateValueKey(cacheModel, key));
					}
					return toObject(value);
				} catch (Exception e) {
					log.error("redis getObject error!!!", e);
					return null;
				}
			}
		});
	}

	public Object removeObject(final CacheModel cacheModel, final Object key) {
		if (RedisTemplateBuilder.getRedisTemplate() == null) {
			return null;
		}
		return RedisTemplateBuilder.getRedisTemplate().execute(new RedisCallback<Object>() {
			public Object doInRedis(RedisConnection connection) throws DataAccessException {
				try {
					// 从hash中获取缓存的值
					byte[] value = connection.hGet(generateHashDomainKey(cacheModel), generateValueKey(cacheModel, key));
					// 删掉hash中缓存的值
					connection.hDel(generateHashDomainKey(cacheModel), generateValueKey(cacheModel, key));
					// 删掉set中缓存的值
					connection.zRem(generateSetDomainKey(cacheModel), generateValueKey(cacheModel, key));
					return toObject(value);
				} catch (Exception e) {
					log.error("redis removeObject error!!!", e);
					return null;
				}
			}
		});
	}

	public void putObject(final CacheModel cacheModel, final Object key, final Object object) {
		if (RedisTemplateBuilder.getRedisTemplate() == null) {
			return;
		}
		RedisTemplate<String, byte[]> redisTemplate = RedisTemplateBuilder.getRedisTemplate();
		redisTemplate.execute(new RedisCallback<Object>() {
			public Object doInRedis(RedisConnection connection) throws DataAccessException {
				try {
					// 先检查set集合中保存的数据有没有超过设置
					Long count = connection.zCard(generateSetDomainKey(cacheModel));
					if (count != null && count >= RedisCacheController.this.size) {
						// 如果超过设置的话，那就把多余的都删除掉
						long s = count - RedisCacheController.this.size + 1;
						Set<byte[]> zRevRangeByScore = connection.zRevRangeByScore(generateSetDomainKey(cacheModel), 0.0, Double.MAX_VALUE, 0, s);
						// 删除set集合中的key值
						connection.zRem(generateSetDomainKey(cacheModel), zRevRangeByScore.toArray(new byte[][] {}));
						// 删除hash中数据
						connection.hDel(generateHashDomainKey(cacheModel), zRevRangeByScore.toArray(new byte[][] {}));
					}
					// 将需要缓存的key值添加到set集合中，并且给初始值1
					connection.zIncrBy(generateSetDomainKey(cacheModel), 1.0, generateValueKey(cacheModel, key));
					// 将需要缓存的数据缓存到hash
					connection.hSet(generateHashDomainKey(cacheModel), generateValueKey(cacheModel, key), toBytes(object));
				} catch (IOException e) {
					log.error("redis putObject error!!!", e);
				}
				return null;
			}
		});
	}

	public void setProperties(Properties props) {
		this.size = Long.parseLong(props.getProperty("redis.size", "5000"));
	}

	private static byte[] generateValueKey(CacheModel cacheModel, Object key) {
		String sKey = cacheModel.getId() + "|" + key.toString();
		try {
			return sKey.getBytes("UTF-8");
		} catch (UnsupportedEncodingException e) {
			return null;
		}
	}

	/**
	 * 生成存放缓存数据的hash表的key值
	 * 
	 * @param cacheModel
	 * @return
	 */
	private static byte[] generateHashDomainKey(CacheModel cacheModel) {
		String hashDomainKey = cacheModel.getId() + HASH_POSTFIX;
		try {
			return hashDomainKey.getBytes("UTF-8");
		} catch (UnsupportedEncodingException e) {
			return null;
		}
	}

	/**
	 * 生成存放缓存key值的有序set集合的key值
	 * 
	 * @param cacheModel
	 * @return
	 */
	private static byte[] generateSetDomainKey(CacheModel cacheModel) {
		String setDomainKey = cacheModel.getId() + SET_POSTFIX;
		try {
			return setDomainKey.getBytes("UTF-8");
		} catch (UnsupportedEncodingException e) {
			return null;
		}
	}

	private static Object toObject(byte[] buf) throws IOException, ClassNotFoundException {
		ByteArrayInputStream bais = new ByteArrayInputStream(buf);
		ObjectInputStream ois = null;
		try {
			ois = new ObjectInputStream(bais);
			return ois.readObject();
		} finally {
			try {
				bais.close();
				if (ois != null) {
					ois.close();
				}
			} catch (Exception e) {
			}
		}
	}

	private static byte[] toBytes(Object object) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		ObjectOutputStream oos = null;
		try {
			oos = new ObjectOutputStream(baos);
			oos.writeObject(object);
			oos.flush();
			return baos.toByteArray();
		} finally {
			try {
				baos.close();
				if (oos != null) {
					oos.close();
				}
			} catch (IOException e) {
			}
		}
	}

}
