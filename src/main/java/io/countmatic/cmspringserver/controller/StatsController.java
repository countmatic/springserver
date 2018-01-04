package io.countmatic.cmspringserver.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CrossOrigin;

import io.countmatic.api_v2.spring.model.Counter;
import io.countmatic.api_v2.spring.model.ServerInfo;
import io.countmatic.api_v2.spring.server.StatsApi;
import io.countmatic.cmspringserver.redis.RedisPoolProvider;
import redis.clients.jedis.Jedis;

@Controller
public class StatsController implements StatsApi {

	@Autowired
	RedisPoolProvider redisPoolProvider;
	
	@Override
	@CrossOrigin
	public ResponseEntity<Counter> getNumberOfCounters() {
		ResponseEntity<Counter> response = null;
		Jedis j = null;
		try {
			j = redisPoolProvider.getPersistentResource();
			Long keys = j.dbSize();
			response = new ResponseEntity<>(new Counter().count(keys).name("dbsize"), HttpStatus.OK);
		} finally {
			if (null != j) {
				redisPoolProvider.returnResource(j);
			}
		}

		return response;
	}

	@Override
	@CrossOrigin
	public ResponseEntity<ServerInfo> getServerInfo() {
        // TODO: impl!
        return new ResponseEntity<ServerInfo>(HttpStatus.OK);
    }
	
}
