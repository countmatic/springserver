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
			hash.put(name, "0");
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

	/**
	 * Check token for existance and rw access
	 * 
	 * @param j
	 * @param token
	 * @return OK or NOT_FOUND or FORBIDDEN
	 */
	private HttpStatus checkIfExistsAndRw(Jedis j, String token) {
		HttpStatus rc = HttpStatus.OK;
		String access = j.hget(token, "__access");
		if (null == access) {
			LOGGER.debug("Token unknown");
			rc = HttpStatus.NOT_FOUND;
		} else if (!"rw".equals(access)) {
			LOGGER.debug("Token not a rw token");
			rc = HttpStatus.FORBIDDEN;
		}

		return rc;
	}

	/**
	 * Get the single counter within token. If there are more counters, returns null
	 * 
	 * @param j
	 * @param token
	 * @return
	 */
	private Counter getTheOnlyOne(Jedis j, String token) {
		Counter rc = null;
		Map<String, String> map = j.hgetAll(token);
		for (String key : map.keySet()) {
			if (!key.startsWith("__")) {
				if (null == rc) {
					rc = new Counter().count(Long.valueOf(map.get(key))).name(key);
				} else {
					rc = null;
					break;
				}
			}
		}
		return rc;
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
			HttpStatus s = this.checkIfExistsAndRw(j, token);
			if (s != HttpStatus.OK) {
				response = new ResponseEntity<>(s);
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
			HttpStatus s = this.checkIfExistsAndRw(j, token);
			if (s != HttpStatus.OK) {
				response = new ResponseEntity<>(s);
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
		LOGGER.debug("getCurrentReading Counter " + name + " in " + token);
		Jedis j = null;
		ResponseEntity<Counters> response = null;
		// check token
		try {
			// FIXME: check that name doesnt start like "__"
			j = RedisPoolProvider.getInstance().getResource();
			String access = j.hget(token, "__access");
			if (null == access) {
				LOGGER.debug("Token unknown");
				response = new ResponseEntity<>(HttpStatus.NOT_FOUND);
			} else if (!"rw".equals(access)) {
				LOGGER.debug("Forwarding to rw Token");
				String rwToken = j.hget(token, "__token");
				j.expire(token, DEFAULT_TTL);
				return getCurrentReading(rwToken, name);
			} else {
				Counters counters = new Counters();
				if (null != name) {
					String val = j.hget(token, name);
					if (null == val) {
						LOGGER.debug("Name unknown");
						response = new ResponseEntity<>(HttpStatus.NOT_FOUND);
					} else {
						LOGGER.debug("OK, have field in counter");
						Counter c = new Counter().count(Long.valueOf(val)).name(name);
						j.expire(token, DEFAULT_TTL);
						counters.add(c);
						response = new ResponseEntity<>(counters, HttpStatus.OK);
					}
				} else {
					LOGGER.debug("OK, fetching all groupcounters");
					Map<String, String> map = j.hgetAll(token);
					for (String key : map.keySet()) {
						if (!key.startsWith("__")) {
							Counter c = new Counter().count(Long.valueOf(map.get(key))).name(key);
							counters.add(c);
						}
					}
					j.expire(token, DEFAULT_TTL);
					response = new ResponseEntity<>(counters, HttpStatus.OK);
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
	public ResponseEntity<Token> getReadOnlyToken(
			@NotNull @ApiParam(value = "Your access token", required = true) @RequestParam(value = "token", required = true) String token) {
		LOGGER.debug("creating ro token for " + token);
		Jedis j = null;
		ResponseEntity<Token> response = null;
		// check token
		try {
			j = RedisPoolProvider.getInstance().getResource();
			HttpStatus s = this.checkIfExistsAndRw(j, token);
			if (s != HttpStatus.OK) {
				response = new ResponseEntity<>(s);
			} else {
				LOGGER.debug("OK, creating ro token");
				Map<String, String> hash = new HashMap<String, String>();
				hash.put("__token", token);
				hash.put("__access", "ro");
				String roToken = this.createToken(true);
				j.hmset(roToken, hash);
				j.expire(roToken, DEFAULT_TTL);
				response = new ResponseEntity<>(new Token().token(roToken), HttpStatus.OK);
			}
		} finally {
			if (null != j) {
				RedisPoolProvider.getInstance().returnResource(j);
			}
		}

		return response;
	}

	@Override
	public ResponseEntity<Counter> nextNumber(
			@NotNull @ApiParam(value = "Your access token", required = true) @RequestParam(value = "token", required = true) String token,
			@ApiParam(value = "Optionally the name of the requested counter, mandatory for grouptokens") @RequestParam(value = "name", required = false) String name) {
		LOGGER.debug("next-ing Counter " + name + " in " + token);
		Jedis j = null;
		ResponseEntity<Counter> response = null;
		// check token
		try {
			j = RedisPoolProvider.getInstance().getResource();
			HttpStatus s = this.checkIfExistsAndRw(j, token);
			if (s != HttpStatus.OK) {
				response = new ResponseEntity<>(s);
			} else {
				LOGGER.debug("OK, make next");
				if (null != name) {
					String val = j.hget(token, name);
					if (null == val) {
						LOGGER.debug("Name unknown");
						response = new ResponseEntity<>(HttpStatus.NOT_FOUND);
					} else {
						LOGGER.debug("OK, nexting field in counter");
						Long newVal = j.hincrBy(token, name, 1l);
						Counter c = new Counter().count(newVal).name(name);
						j.expire(token, DEFAULT_TTL);
						response = new ResponseEntity<>(c, HttpStatus.OK);
					}
				} else {
					LOGGER.debug("OK, nexting singlecounter");
					Counter c = this.getTheOnlyOne(j, token);
					if (null == c) {
						response = new ResponseEntity<>(HttpStatus.BAD_REQUEST);
					} else {
						Long newVal = j.hincrBy(token, c.getName(), 1l);
						j.expire(token, DEFAULT_TTL);
						response = new ResponseEntity<>(new Counter().count(newVal).name(c.getName()), HttpStatus.OK);
					}
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
	public ResponseEntity<Counter> previousNumber(
			@NotNull @ApiParam(value = "Your access token", required = true) @RequestParam(value = "token", required = true) String token,
			@ApiParam(value = "Optionally the name of the requested counter, mandatory for grouptokens") @RequestParam(value = "name", required = false) String name) {
		LOGGER.debug("previous-ing Counter " + name + " in " + token);
		Jedis j = null;
		ResponseEntity<Counter> response = null;
		// check token
		try {
			j = RedisPoolProvider.getInstance().getResource();
			HttpStatus s = this.checkIfExistsAndRw(j, token);
			if (s != HttpStatus.OK) {
				response = new ResponseEntity<>(s);
			} else {
				LOGGER.debug("OK, make previousing");
				if (null != name) {
					String val = j.hget(token, name);
					if (null == val) {
						LOGGER.debug("Name unknown");
						response = new ResponseEntity<>(HttpStatus.NOT_FOUND);
					} else {
						LOGGER.debug("OK, previousing field in counter");
						Long newVal = j.hincrBy(token, name, -1l);
						Counter c = new Counter().count(newVal).name(name);
						j.expire(token, DEFAULT_TTL);
						response = new ResponseEntity<>(c, HttpStatus.OK);
					}
				} else {
					LOGGER.debug("OK, previousing singlecounter");
					Counter c = this.getTheOnlyOne(j, token);
					if (null == c) {
						response = new ResponseEntity<>(HttpStatus.BAD_REQUEST);
					} else {
						Long newVal = j.hincrBy(token, c.getName(), -1l);
						j.expire(token, DEFAULT_TTL);
						response = new ResponseEntity<>(new Counter().count(newVal).name(c.getName()), HttpStatus.OK);
					}
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
	public ResponseEntity<Counter> resetCounter(
			@NotNull @ApiParam(value = "Your access token", required = true) @RequestParam(value = "token", required = true) String token,
			@ApiParam(value = "Optionally the name of the requested counter, mandatory for grouptokens") @RequestParam(value = "name", required = false) String name) {
		LOGGER.debug("reset-ing Counter " + name + " in " + token);
		Jedis j = null;
		ResponseEntity<Counter> response = null;
		// check token
		try {
			j = RedisPoolProvider.getInstance().getResource();
			HttpStatus s = this.checkIfExistsAndRw(j, token);
			if (s != HttpStatus.OK) {
				response = new ResponseEntity<>(s);
			} else {
				LOGGER.debug("OK, make reset");
				if (null != name) {
					String val = j.hget(token, name);
					if (null == val) {
						LOGGER.debug("Name unknown");
						response = new ResponseEntity<>(HttpStatus.NOT_FOUND);
					} else {
						LOGGER.debug("OK, reset field in counter");
						j.hset(token, name, "1");
						j.expire(token, DEFAULT_TTL);
						response = new ResponseEntity<>(new Counter().count(1l).name(name), HttpStatus.OK);
					}
				} else {
					LOGGER.debug("OK, previousing singlecounter");
					Counter c = this.getTheOnlyOne(j, token);
					if (null == c) {
						response = new ResponseEntity<>(HttpStatus.BAD_REQUEST);
					} else {
						j.hset(token, c.getName(), "1");
						j.expire(token, DEFAULT_TTL);
						response = new ResponseEntity<>(new Counter().count(1l).name(c.getName()), HttpStatus.OK);
					}
				}
			}
		} finally {
			if (null != j) {
				RedisPoolProvider.getInstance().returnResource(j);
			}
		}

		return response;
	}
}
