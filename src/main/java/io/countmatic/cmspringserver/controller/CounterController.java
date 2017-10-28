package io.countmatic.cmspringserver.controller;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestParam;

import io.countmatic.api.spring.model.Counter;
import io.countmatic.api.spring.model.Counters;
import io.countmatic.api.spring.model.Token;
import io.countmatic.api.spring.server.CounterApi;
import io.countmatic.cmspringserver.redis.RedisPoolProvider;
import io.swagger.annotations.ApiParam;
import redis.clients.jedis.Jedis;

@Controller
public class CounterController implements CounterApi {

	private static final Logger LOGGER = LoggerFactory.getLogger(CounterController.class);
	// One week default TTL
	private static final int DEFAULT_TTL = 3600 * 24 * 7;

	/**
	 * Create a unique token
	 * 
	 * @param readonly
	 * @return
	 */
	private String createToken(boolean readonly) {
		UUID uuid = UUID.randomUUID();
		// String token = Long.toString(System.currentTimeMillis(), 16);
		// Long rand = ThreadLocalRandom.current().nextLong();
		String prefix = readonly ? "ro-" : "rw-";

		return prefix + uuid;
	}

	@Override
	public ResponseEntity<Token> getNewCounter(
			@NotNull @ApiParam(value = "The name of the counter", required = true) @RequestParam(value = "name", required = true) String name) {
		LOGGER.debug("creating new Counter for: " + name);
		// create token
		Token token = new Token().token(this.createToken(false));
		// add hash to redis
		Jedis j = null;
		try {
			// FIXME: check that name doesnt start like "__"
			j = RedisPoolProvider.getInstance().getResource();
			Map<String, String> hash = new HashMap<String, String>();
			hash.put("name", name);
			hash.put("__access", "rw");
			j.hmset(token.getToken(), hash);
			j.expire(token.getToken(), DEFAULT_TTL);
		} finally {
			if (null != j) {
				RedisPoolProvider.getInstance().returnResource(j);
			}
		}
		ResponseEntity<Token> response = new ResponseEntity<>(token, HttpStatus.OK);
		return response;
	}

	@Override
	public ResponseEntity<Counter> addCounter(
			@NotNull @ApiParam(value = "Your access token", required = true) @RequestParam(value = "token", required = true) String token,
			@NotNull @ApiParam(value = "The name of the counter", required = true) @RequestParam(value = "name", required = true) String name) {

		LOGGER.debug("adding Counter " + name + " to " + token);
		Jedis j = null;
		ResponseEntity<Counter> response = null;
		// check token
		try {
			// FIXME: check that name doesnt start like "__"
			j = RedisPoolProvider.getInstance().getResource();
			String access = j.hget(token, "__access");
			if (null == access) {
				LOGGER.debug("Token unknown");
				response = new ResponseEntity<>(HttpStatus.NOT_FOUND);
			} else if (!"rw".equals(access)) {
				LOGGER.debug("Token not a rw token");
				response = new ResponseEntity<>(HttpStatus.FORBIDDEN);
			} else {
				LOGGER.debug("OK, adding counter");
				j.hset(token, name, "0");
				j.expire(token, DEFAULT_TTL);
				Counter c = new Counter().count(0l).name(name);
				response = new ResponseEntity<>(c, HttpStatus.OK);
			}
		} finally {
			if (null != j) {
				RedisPoolProvider.getInstance().returnResource(j);
			}
		}

		return response;
	}

	@Override
	public ResponseEntity<Counter> deleteCounter(
			@NotNull @ApiParam(value = "Your access token", required = true) @RequestParam(value = "token", required = true) String token,
			@ApiParam(value = "Optionally the name of the requested counter, mandatory for grouptokens") @RequestParam(value = "name", required = false) String name) {
		LOGGER.debug("deleting Counter " + name + " in " + token);
		Jedis j = null;
		ResponseEntity<Counter> response = null;
		// check token
		try {
			// FIXME: check that name doesnt start like "__"
			j = RedisPoolProvider.getInstance().getResource();
			String access = j.hget(token, "__access");
			if (null == access) {
				LOGGER.debug("Token unknown");
				response = new ResponseEntity<>(HttpStatus.NOT_FOUND);
			} else if (!"rw".equals(access)) {
				LOGGER.debug("Token not a rw token");
				response = new ResponseEntity<>(HttpStatus.FORBIDDEN);
			} else {
				if (null != name) {
					String val = j.hget(token, name);
					if (null == val) {
						LOGGER.debug("Name unknown");
						response = new ResponseEntity<>(HttpStatus.NOT_FOUND);
					} else {
						LOGGER.debug("OK, deleting field in groupcounter");
						Counter c = new Counter().count(Long.valueOf(val)).name(name);
						j.hdel(token, name);
						j.expire(token, DEFAULT_TTL);
						response = new ResponseEntity<>(c, HttpStatus.OK);
					}
				} else {
					LOGGER.debug("OK, deleting groupcounter");
					j.del(token);
					Counter c = new Counter().count(0l).name(name);
					response = new ResponseEntity<>(c, HttpStatus.OK);
				}
			}
		} finally {
			if (null != j) {
				RedisPoolProvider.getInstance().returnResource(j);
			}
		}

		return response;
	}

	@Override
	public ResponseEntity<Counters> getCurrentReading(
			@NotNull @ApiParam(value = "Your access token", required = true) @RequestParam(value = "token", required = true) String token,
			@ApiParam(value = "Optionally the name of the requested counter") @RequestParam(value = "name", required = false) String name) {
		// TODO: impl
		return new ResponseEntity<Counters>(HttpStatus.OK);
	}

	@Override
	public ResponseEntity<Token> getReadOnlyToken(
			@NotNull @ApiParam(value = "Your access token", required = true) @RequestParam(value = "token", required = true) String token) {
		// TODO: impl
		return new ResponseEntity<Token>(HttpStatus.OK);
	}

	@Override
	public ResponseEntity<Counter> nextNumber(
			@NotNull @ApiParam(value = "Your access token", required = true) @RequestParam(value = "token", required = true) String token,
			@ApiParam(value = "Optionally the name of the requested counter, mandatory for grouptokens") @RequestParam(value = "name", required = false) String name) {
		// TODO: impl
		return new ResponseEntity<Counter>(HttpStatus.OK);
	}

	@Override
	public ResponseEntity<Counter> previousNumber(
			@NotNull @ApiParam(value = "Your access token", required = true) @RequestParam(value = "token", required = true) String token,
			@ApiParam(value = "Optionally the name of the requested counter, mandatory for grouptokens") @RequestParam(value = "name", required = false) String name) {
		// TODO: impl
		return new ResponseEntity<Counter>(HttpStatus.OK);
	}

	@Override
	public ResponseEntity<Counter> resetCounter(
			@NotNull @ApiParam(value = "Your access token", required = true) @RequestParam(value = "token", required = true) String token,
			@ApiParam(value = "Optionally the name of the requested counter, mandatory for grouptokens") @RequestParam(value = "name", required = false) String name) {
		// TODO: impl
		return new ResponseEntity<Counter>(HttpStatus.OK);
	}
}
