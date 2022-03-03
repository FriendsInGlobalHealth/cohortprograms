package org.openmrs.module.esaudefeatures.web;

import okhttp3.Credentials;
import okhttp3.Request;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @uthor Willa Mhawila<a.mhawila@gmail.com> on 2/20/22.
 */
public class UtilsTest {
	
	private static final String USERNAME = "user";
	
	private static final String PASSWORD = "password";
	
	private static final String BASE_URL = "http://server.example.tz";
	
	@Test
	public void createBasicAuthGetRequestShouldCreateCorrectRequest() {
		String[] segements = { "seg1", "seg2/seg3" };
		final String EXPECTED_URI = BASE_URL.concat("/").concat(segements[0]).concat("/").concat(segements[1]);
		Request request = Utils.createBasicAuthGetRequest(BASE_URL, USERNAME, PASSWORD, segements, null);
		
		assertEquals(EXPECTED_URI, request.url().uri().toString());
		assertTrue(request.headers().names().contains("Authorization"));
		assertTrue(request.header("Authorization").endsWith(Credentials.basic(USERNAME, PASSWORD)));
		assertTrue(request.method().equalsIgnoreCase("GET"));
	}
}
