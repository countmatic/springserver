package io.countmatic.cmspringserver;

import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import io.countmatic.api.spring.model.Counter;
import io.countmatic.api.spring.model.Counters;
import io.countmatic.api.spring.model.Token;
import io.countmatic.cmspringserver.controller.CounterController;

@RunWith(SpringRunner.class)
public class CounterControllerTest {

	// @Autowired
	private CounterController cc = new CounterController();

	@Test
	public void testNotFound() {
		assertTrue("Wrong found", cc.addCounter("whatever", null).getStatusCode() == HttpStatus.NOT_FOUND);
		assertTrue("Wrong found", cc.deleteCounter("whatever", null).getStatusCode() == HttpStatus.NOT_FOUND);
		assertTrue("Wrong found", cc.getCurrentReading("whatever", null).getStatusCode() == HttpStatus.NOT_FOUND);
		assertTrue("Wrong found", cc.getReadOnlyToken("whatever").getStatusCode() == HttpStatus.NOT_FOUND);
		assertTrue("Wrong found", cc.previousNumber("whatever", null).getStatusCode() == HttpStatus.NOT_FOUND);
		assertTrue("Wrong found", cc.resetCounter("whatever", null).getStatusCode() == HttpStatus.NOT_FOUND);
		assertTrue("Wrong found", cc.nextNumber("whatever", null).getStatusCode() == HttpStatus.NOT_FOUND);
	}

	@Test
	public void testPermissionDenied() {
		ResponseEntity<Token> tresp = cc.getNewCounter("UnitTest");
		Token t = tresp.getBody();
		tresp = cc.getReadOnlyToken(t.getToken());
		assertTrue("Got no RO token", tresp.getStatusCode() == HttpStatus.OK);
		t = tresp.getBody();
		assertTrue("Wrong Permissiondenied", cc.addCounter(t.getToken(), null).getStatusCode() == HttpStatus.FORBIDDEN);
		assertTrue("Wrong Permissiondenied",
				cc.deleteCounter(t.getToken(), null).getStatusCode() == HttpStatus.FORBIDDEN);
		assertTrue("Wrong Permissiondenied", cc.getReadOnlyToken(t.getToken()).getStatusCode() == HttpStatus.FORBIDDEN);
		assertTrue("Wrong Permissiondenied",
				cc.previousNumber(t.getToken(), null).getStatusCode() == HttpStatus.FORBIDDEN);
		assertTrue("Wrong Permissiondenied",
				cc.resetCounter(t.getToken(), null).getStatusCode() == HttpStatus.FORBIDDEN);
		assertTrue("Wrong Permissiondenied", cc.nextNumber(t.getToken(), null).getStatusCode() == HttpStatus.FORBIDDEN);
	}

	@Test
	public void testGetNewCounter() throws Exception {
		ResponseEntity<Token> resp = cc.getNewCounter("UnitTest");
		Token t = resp.getBody();
		assertTrue("Response not OK on new", resp.getStatusCode() == HttpStatus.OK);
		assertTrue("Token is null", t != null && t.getToken() != null);
		assertTrue("Token doesnt start like rw", t.getToken().startsWith("rw"));
	}

	@Test
	public void testAddCounter() {
		ResponseEntity<Token> resp1 = cc.getNewCounter("UnitTest");
		Token t = resp1.getBody();
		ResponseEntity<Counter> resp2 = cc.addCounter(t.getToken(), "AnotherUnitTest");
		Counter c = resp2.getBody();
		assertTrue("Response not OK on add", resp2.getStatusCode() == HttpStatus.OK);
		assertTrue("Counter is null", c != null && c.getName() != null);
		assertTrue("Counter not set to 0", c.getCount() == 0l);
		ResponseEntity<Counters> resp3 = cc.getCurrentReading(t.getToken(), null);
		Counters cs = resp3.getBody();
		assertTrue("Not two counters in resp", cs.size() == 2);
		ResponseEntity<Counters> resp4 = cc.getCurrentReading(t.getToken(), "UnitTest");
		Counters cs2 = resp4.getBody();
		assertTrue("Not one counter in resp", cs2.size() == 1);
	}

	@Test
	public void testDeleteCounter() {
		// new counter
		ResponseEntity<Token> resp1 = cc.getNewCounter("UnitTest");
		Token t = resp1.getBody();
		// add another counter
		ResponseEntity<Counter> resp2 = cc.addCounter(t.getToken(), "AnotherUnitTest");
		Counter c = resp2.getBody();
		// inc another to 1
		ResponseEntity<Counter> resp3 = cc.nextNumber(t.getToken(), "AnotherUnitTest");
		Counter c2 = resp3.getBody();
		// delete another
		ResponseEntity<Counter> resp4 = cc.deleteCounter(t.getToken(), "AnotherUnitTest");
		Counter c3 = resp4.getBody();
		assertTrue("Counter not set to 1", c3.getCount() == 1l);
		// get current of another
		ResponseEntity<Counters> resp5 = cc.getCurrentReading(t.getToken(), "AnotherUnitTest");
		assertTrue("Counter not deleted", resp5.getStatusCode() == HttpStatus.NOT_FOUND);
		// get all current
		ResponseEntity<Counters> resp7 = cc.getCurrentReading(t.getToken(), null);
		Counters cs = resp7.getBody();
		assertTrue("Not one counters in resp", cs.size() == 1);
		// delete complete token
		ResponseEntity<Counter> resp6 = cc.deleteCounter(t.getToken(), null);
		assertTrue("Counter delete failed", resp6.getStatusCode() == HttpStatus.OK);
		// get current
		ResponseEntity<Counters> resp8 = cc.getCurrentReading(t.getToken(), null);
		assertTrue("Counter not deleted", resp8.getStatusCode() == HttpStatus.NOT_FOUND);
	}

	@Test
	public void testGetCurrentReading() {
		ResponseEntity<Token> tresp = cc.getNewCounter("UnitTest");
		Token t = tresp.getBody();
		// inc to 1
		ResponseEntity<Counter> cresp = cc.nextNumber(t.getToken(), "AnotherUnitTest");
		assertTrue("Counter not found worng", cresp.getStatusCode() == HttpStatus.NOT_FOUND);
		cresp = cc.nextNumber(t.getToken(), "UnitTest");
		assertTrue("named Counter found worng", cresp.getStatusCode() == HttpStatus.OK);
		Counter c = cresp.getBody();
		assertTrue("named Counter not 1", c.getCount() == 1l);
		cresp = cc.nextNumber(t.getToken(), null);
		assertTrue("Counter found worng", cresp.getStatusCode() == HttpStatus.OK);
		c = cresp.getBody();
		assertTrue("Counter not 2", c.getCount() == 2l);
	}

	@Test
	public void testGetReadOnlyToken() {
		ResponseEntity<Token> tresp = cc.getNewCounter("UnitTest");
		Token t = tresp.getBody();
		ResponseEntity<Counter> cresp = cc.nextNumber(t.getToken(), "UnitTest");
		tresp = cc.getReadOnlyToken(t.getToken());
		assertTrue("Got no RO token", tresp.getStatusCode() == HttpStatus.OK);
		t = tresp.getBody();
		ResponseEntity<Counters> rresp = cc.getCurrentReading(t.getToken(), null);
		assertTrue("could not read with RO token", rresp.getStatusCode() == HttpStatus.OK);
		Counters r = rresp.getBody();
		assertTrue("do not have one counter", r.size() == 1);
		cresp = cc.nextNumber(t.getToken(), "UnitTest");
		assertTrue("Permission granted", cresp.getStatusCode() == HttpStatus.FORBIDDEN);
		rresp = cc.getCurrentReading(t.getToken(), null);
		r = rresp.getBody();
		Counter c = r.get(0);
		assertTrue("Counter not 1", c.getCount() == 1l);
	}

	@Test
	public void testNextNumber() {
		ResponseEntity<Token> tresp = cc.getNewCounter("UnitTest");
		Token t = tresp.getBody();
		ResponseEntity<Counter> cresp = cc.nextNumber(t.getToken(), "UnitTest");
		ResponseEntity<Counters> rresp = cc.getCurrentReading(t.getToken(), null);
		assertTrue("could not read", rresp.getStatusCode() == HttpStatus.OK);
		Counters r = rresp.getBody();
		Counter c = r.get(0);
		assertTrue("Counter not 1", c.getCount() == 1l);
	}

	@Test
	public void testPreviousNumber() {
		ResponseEntity<Token> tresp = cc.getNewCounter("UnitTest");
		Token t = tresp.getBody();
		ResponseEntity<Counter> cresp = cc.previousNumber(t.getToken(), "UnitTest");
		ResponseEntity<Counters> rresp = cc.getCurrentReading(t.getToken(), null);
		assertTrue("could not read", rresp.getStatusCode() == HttpStatus.OK);
		Counters r = rresp.getBody();
		Counter c = r.get(0);
		assertTrue("Counter not -1", c.getCount() == -1l);
	}

	@Test
	public void testResetCounter() {
		ResponseEntity<Token> tresp = cc.getNewCounter("UnitTest");
		Token t = tresp.getBody();
		ResponseEntity<Counter> cresp = cc.previousNumber(t.getToken(), "UnitTest");
		ResponseEntity<Counters> rresp = cc.getCurrentReading(t.getToken(), null);
		assertTrue("could not read", rresp.getStatusCode() == HttpStatus.OK);
		Counters r = rresp.getBody();
		Counter c = r.get(0);
		assertTrue("Counter not 1", c.getCount() == -1l);
		cresp = cc.resetCounter(t.getToken(), "UnitTest");
		assertTrue("Counter not 1", cresp.getBody().getCount() == 1l);
		cresp = cc.nextNumber(t.getToken(), "UnitTest");
		assertTrue("Counter not 1", cresp.getBody().getCount() == 2l);
	}

}
