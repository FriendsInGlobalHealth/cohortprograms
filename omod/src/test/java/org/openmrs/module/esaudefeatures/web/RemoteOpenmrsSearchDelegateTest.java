package org.openmrs.module.esaudefeatures.web;

import okhttp3.Credentials;
import okhttp3.HttpUrl;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.apache.commons.io.IOUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;
import org.openmrs.api.AdministrationService;
import org.openmrs.module.esaudefeatures.EsaudeFeaturesConstants;
import org.openmrs.module.webservices.rest.SimpleObject;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * @uthor Willa Mhawila<a.mhawila@gmail.com> on 11/22/21.
 */
@RunWith(MockitoJUnitRunner.class)
public class RemoteOpenmrsSearchDelegateTest {
	
	private static final String MATCHED_PATIENT_LIST_FILE_NAME = "/openmrs-rest/patient_list.json";
	
	private static final String SINGLE_PATIENT_FILE_NAME = "/openmrs-rest/single_patient.json";
	
	private static final String EXPECTED_PATIENT_PATH = "/ws/rest/v1/patient";
	
	private static final String USERNAME = "user1";
	
	private static final String PASSWORD = "pa$$w0rd";
	
	private String baseRemoteServerUrl;
	
	private MockWebServer mockWebServer;
	
	@Mock
	private AdministrationService adminService;
	
	@Mock
	private ImportHelperService helperService;
	
	@InjectMocks
	private RemoteOpenmrsSearchDelegate delegate = new RemoteOpenmrsSearchDelegate();
	
	@Before
	public void setup() throws Exception {
		mockWebServer = new MockWebServer();
		mockWebServer.start();
		baseRemoteServerUrl = mockWebServer.url("/").toString();
		String[] hostUserPass = { baseRemoteServerUrl, USERNAME, PASSWORD };
		Mockito.when(helperService.getRemoteOpenmrsHostUsernamePassword()).thenReturn(hostUserPass);
	}
	
	@Test
	public void getPatientByUuidShouldIssueTheCorrectRequest() throws Exception {
		final String PATIENT_UUID = "6d24f374-8125-11e0-a437-00242122a7a8";
		String expectedFetchPath = EXPECTED_PATIENT_PATH.concat("/").concat(PATIENT_UUID);
		
		final String EXPECTED_JSON_RESPONSE = IOUtils.toString(getClass().getResourceAsStream(SINGLE_PATIENT_FILE_NAME));
		
		mockWebServer.enqueue(new MockResponse().setResponseCode(HttpServletResponse.SC_OK)
		        .addHeader("Content-Type", "application/json").setBody(EXPECTED_JSON_RESPONSE));
		
		delegate.getRemotePatientByUuid(PATIENT_UUID);
		
		RecordedRequest request = mockWebServer.takeRequest();
		HttpUrl requestUrl = request.getRequestUrl();
		assertEquals(Credentials.basic(USERNAME, PASSWORD), request.getHeader("Authorization"));
		assertEquals("GET", request.getMethod());
		assertTrue(request.getPath().startsWith(expectedFetchPath));
		assertFalse(requestUrl.isHttps());
		
		Set<String> parameterNames = requestUrl.queryParameterNames();
		assertEquals(1, parameterNames.size());
		assertEquals("v", parameterNames.iterator().next());
		assertEquals("full", requestUrl.queryParameter("v"));
	}
	
	@Test
	public void searchPatientsShouldIssueCorrectRequest() throws Exception {
		final Collection<String> EXPECTED_QUERY_PARAMETERS = new HashSet<String>();
		Collections.addAll(EXPECTED_QUERY_PARAMETERS, "q", "v");
		
		final String EXPECTED_JSON_RESPONSE = IOUtils.toString(getClass()
		        .getResourceAsStream(MATCHED_PATIENT_LIST_FILE_NAME));
		
		mockWebServer.enqueue(new MockResponse().setResponseCode(HttpServletResponse.SC_OK)
		        .addHeader("Content-Type", "application/json").setBody(EXPECTED_JSON_RESPONSE));
		
		final String SEARCH_TEXT = "Madrid";
		delegate.searchPatients(SEARCH_TEXT);
		
		RecordedRequest request = mockWebServer.takeRequest();
		HttpUrl requestUrl = request.getRequestUrl();
		assertNotNull(request.getHeader("Authorization"));
		assertEquals("GET", request.getMethod());
		assertTrue(request.getPath().startsWith(EXPECTED_PATIENT_PATH));
		assertFalse(requestUrl.isHttps());
		
		Set<String> parameterNames = requestUrl.queryParameterNames();
		assertEquals(2, parameterNames.size());
		assertTrue(parameterNames.containsAll(EXPECTED_QUERY_PARAMETERS));
		assertEquals(SEARCH_TEXT, requestUrl.queryParameter("q"));
		assertEquals("full", requestUrl.queryParameter("v"));
		
	}
	
	@Test
	public void searchPatientsShouldIssueACorrectQueryForMultipleNames() throws Exception {
		final String SEARCH_TEXT = "Nashada Mhawila";
		final String EXPECTED_JSON_RESPONSE = IOUtils.toString(getClass()
		        .getResourceAsStream(MATCHED_PATIENT_LIST_FILE_NAME));
		
		mockWebServer.enqueue(new MockResponse().setResponseCode(HttpServletResponse.SC_OK)
		        .addHeader("Content-Type", "application/json").setBody(EXPECTED_JSON_RESPONSE));
		
		delegate.searchPatients(SEARCH_TEXT);
		
		RecordedRequest request = mockWebServer.takeRequest();
		HttpUrl requestUrl = request.getRequestUrl();
		assertEquals("GET", request.getMethod());
		assertTrue(request.getPath().startsWith(EXPECTED_PATIENT_PATH));
		assertFalse(requestUrl.isHttps());
		
		assertEquals(2, requestUrl.querySize());
		assertTrue(requestUrl.queryParameter("q").equals(SEARCH_TEXT));
		assertEquals(Credentials.basic(USERNAME, PASSWORD), request.getHeader("Authorization"));
	}
	
	@Test(expected = RemoteOpenmrsSearchException.class)
	public void searchOpenmrsForPatientShouldThrowExceptionIfCouldNotAuthenticate() throws Exception {
		mockWebServer.enqueue(new MockResponse().setResponseCode(HttpServletResponse.SC_BAD_REQUEST)
		        .addHeader("Content-Type", "application/json").setBody("{}"));
		
		delegate.searchPatients("sometext");
	}
	
	@After
	public void teardown() throws IOException {
		mockWebServer.close();
		mockWebServer.shutdown();
	}
	
}
