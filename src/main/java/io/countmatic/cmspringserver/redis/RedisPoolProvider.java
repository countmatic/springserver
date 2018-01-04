package io.countmatic.cmspringserver.redis;

import java.time.Duration;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

/**
 * Provide resources for two redis instances. One for persistent data, another
 * one for in-memory data that gets lost on downtime.
 * 
 * @author Rainer Feike
 *
 */
@Component
public class RedisPoolProvider {

	private JedisPool persistentPool;
	private JedisPool volatilePool;

	@Value("${countmatic.persistentServer}")
	private String persitentHost;

	@Value("${countmatic.volatileServer}")
	private String volatileHost;

	public RedisPoolProvider() {
	}

	@PostConstruct
	public void postConstruct() {
		if (null == volatileHost || null == persitentHost || volatileHost.isEmpty() || persitentHost.isEmpty()) {
			throw new RuntimeException(
					"Please set countmatic.persitentServer and countmatic.volatileServer in application.properties");
		}
		final JedisPoolConfig poolConfig = buildPoolConfig();
		this.persistentPool = new JedisPool(poolConfig, persitentHost);
		this.volatilePool = new JedisPool(poolConfig, volatileHost);

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

	public Jedis getPersistentResource() {
		return this.persistentPool.getResource();
	}

	public Jedis getVolatileResource() {
		return this.volatilePool.getResource();
	}

	public void returnResource(Jedis resource) {
		// returnResource is deprecated !
		resource.close();
	}

}
