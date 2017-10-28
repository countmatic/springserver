package io.countmatic.cmspringserver.redis;

import java.time.Duration;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

public class RedisPoolProvider {

	private JedisPool pool;
	private static RedisPoolProvider instance = null;

	public static synchronized RedisPoolProvider getInstance() {
		if (null == instance) {
			instance = new RedisPoolProvider();
		}

		return instance;
	}

	private RedisPoolProvider() {
		final JedisPoolConfig poolConfig = buildPoolConfig();
		this.pool = new JedisPool(poolConfig, "localhost");
	}

	private JedisPoolConfig buildPoolConfig() {
		final JedisPoolConfig poolConfig = new JedisPoolConfig();
		poolConfig.setMaxTotal(128);
		poolConfig.setMaxIdle(128);
		poolConfig.setMinIdle(16);
		poolConfig.setTestOnBorrow(true);
		poolConfig.setTestOnReturn(true);
		poolConfig.setTestWhileIdle(true);
		poolConfig.setMinEvictableIdleTimeMillis(Duration.ofSeconds(60).toMillis());
		poolConfig.setTimeBetweenEvictionRunsMillis(Duration.ofSeconds(30).toMillis());
		poolConfig.setNumTestsPerEvictionRun(3);
		poolConfig.setBlockWhenExhausted(true);
		return poolConfig;
	}

	public Jedis getResource() {
		return this.pool.getResource();
	}

	public void returnResource(Jedis resource) {
		// returnResource is depricated !
		resource.close();
	}

}
