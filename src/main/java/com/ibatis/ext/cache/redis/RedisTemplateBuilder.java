package com.ibatis.ext.cache.redis;

import org.springframework.data.redis.core.RedisTemplate;

/**
 * 
 * @author fanwt7236@163.com
 */
public class RedisTemplateBuilder {

	private static RedisTemplate<String, byte[]> redisTemplate;
	
	public void setRedisTemplate(RedisTemplate<String, byte[]> redisTemplate) {
		RedisTemplateBuilder.redisTemplate = redisTemplate;
	}
	
	public static RedisTemplate<String, byte[]> getRedisTemplate() {
		return redisTemplate;
	}
	
}
