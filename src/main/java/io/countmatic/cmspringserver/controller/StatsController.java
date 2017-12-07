package io.countmatic.cmspringserver.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CrossOrigin;

import io.countmatic.api.spring.model.Counter;
import io.countmatic.api.spring.server.StatsApi;
import io.countmatic.cmspringserver.redis.RedisPoolProvider;
import redis.clients.jedis.Jedis;

@Controller
public class StatsController implements StatsApi {

	@Override
	@CrossOrigin
	public ResponseEntity<Counter> getNumberOfCounters() {
		ResponseEntity<Counter> response = null;
		Jedis j = null;
		try {
			j = RedisPoolProvider.getInstance().getResource();
			Long keys = j.dbSize();
			response = new ResponseEntity<>(new Counter().count(keys).name("dbsize"), HttpStatus.OK);
		} finally {
			if (null != j) {
				RedisPoolProvider.getInstance().returnResource(j);
			}
		}

		return response;
	}

}
