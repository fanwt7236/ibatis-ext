package com.ibatis.ext.cache.memcached;

import java.net.InetSocketAddress;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.util.StringUtils;

import net.rubyeye.xmemcached.XMemcachedClientBuilder;

public class MemcachedClientBuilder extends XMemcachedClientBuilder{
	
	private static Map<InetSocketAddress, InetSocketAddress> buildAddress(String servers){
		String[] sArr = StringUtils.split(servers, ";");
		Map<InetSocketAddress, InetSocketAddress> map = new LinkedHashMap<InetSocketAddress, InetSocketAddress>();
		for(int i = 0; i < sArr.length; i ++){
			String server = sArr[i];
			String[] ipw = StringUtils.split(server, ":");
			String ip = ipw[0].trim();
			int port = Integer.parseInt(ipw[1].trim());
			InetSocketAddress address = new InetSocketAddress(ip, port);
			map.put(address, null);
		}
		return map;
	}

	private static int[] buildWeights(String servers){
		String[] sArr = StringUtils.split(servers, ";");
		int[] weights = new int[sArr.length];
		for(int i = 0; i < sArr.length; i ++){
			String server = sArr[i];
			String[] ipw = StringUtils.split(server, ":");
			int weight = ipw.length < 3 ? 1 : Integer.parseInt(ipw[2].trim());
			weights[i] = weight;
		}
		return weights;
	}

	public MemcachedClientBuilder(String servers) {
		this(buildAddress(servers), buildWeights(servers));
	}

	public MemcachedClientBuilder(Map<InetSocketAddress, InetSocketAddress> addressMap, int[] weights) {
		super(addressMap, weights);
	}
	
	
}
