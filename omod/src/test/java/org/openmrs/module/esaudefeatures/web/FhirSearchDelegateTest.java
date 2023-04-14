package org.openmrs.module.esaudefeatures.web;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import okhttp3.HttpUrl;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.apache.commons.io.IOUtils;
import org.hl7.fhir.r4.model.Bundle;
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
import org.openmrs.module.esaudefeatures.web.controller.FhirProviderAuthenticationException;
import org.openmrs.module.esaudefeatures.web.exception.FhirResourceSearchException;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @uthor Willa Mhawila<a.mhawila@gmail.com> on 11/22/21.
 */
@RunWith(MockitoJUnitRunner.class)
public class FhirSearchDelegateTest {
	
	private static final FhirContext FHIR_CONTEXT = FhirContext.forR4();
	
	private static final String MATCHED_PATIENT_LIST_FILE_NAME = "/opencr/opencr_matched_patient_list.json";
	
	private static final String IDENTIFIER_MATCHED_PATIENT = "/opencr/opencr_identifier_matched_patient.json";
	
	private static final String EXPECTED_PATIENT_PATH = "/ocrux/fhir/Patient";
	
	private static final String USERNAME = "test-user";
	
	private static final String PASSWORD = "pa$$w0rd";
	
	private String baseRemoteServerUrl;
	
	private MockWebServer mockWebServer;
	
	@Mock
	private AdministrationService adminService;
	
	@InjectMocks
	private FhirSearchDelegate delegate = new FhirSearchDelegate();
	
	@Before
	public void setup() throws Exception {
		mockWebServer = new MockWebServer();
		mockWebServer.start();
		baseRemoteServerUrl = mockWebServer.url("/").toString();
		Mockito.when(adminService.getGlobalProperty(EsaudeFeaturesConstants.FHIR_REMOTE_SERVER_URL_GP)).thenReturn(
		    baseRemoteServerUrl);
		Mockito.when(adminService.getGlobalProperty(EsaudeFeaturesConstants.OPENCR_REMOTE_SERVER_USERNAME_GP)).thenReturn(
		    USERNAME);
		Mockito.when(adminService.getGlobalProperty(EsaudeFeaturesConstants.OPENCR_REMOTE_SERVER_PASSWORD_GP)).thenReturn(
		    PASSWORD);
	}
	
	@Test
	public void getServerJWTAuthenticationTokenSuccessfulRequest() throws Exception {
		final String EXPECTED_PATH = "/ocrux/user/authenticate";
		final Collection<String> EXPECTED_QUERY_PARAMETERS = new HashSet<String>();
		Collections.addAll(EXPECTED_QUERY_PARAMETERS, "username", "password");
		
		mockWebServer.noClientAuth();
		final String EXPECTED_TOKEN = "{ \"token\": \"test-token\",\"userID\": \"test-user\", \"username\": \"user\" }";
		mockWebServer.enqueue(new MockResponse().setResponseCode(200).addHeader("Content-Type", "application/json")
		        .setBody(EXPECTED_TOKEN));
		
		delegate.clearServerAuthenticationToken();
		String token = delegate.getServerJWTAuthenticationToken();
		RecordedRequest request = mockWebServer.takeRequest();
		HttpUrl requestUrl = request.getRequestUrl();
		assertEquals("POST", request.getMethod().toUpperCase());
		assertTrue(requestUrl.url().toString().startsWith(baseRemoteServerUrl));
		assertTrue(request.getPath().startsWith(EXPECTED_PATH));
		assertFalse(requestUrl.isHttps());
		
		Set<String> parameterNames = requestUrl.queryParameterNames();
		assertEquals(2, parameterNames.size());
		assertTrue(parameterNames.containsAll(EXPECTED_QUERY_PARAMETERS));
		assertEquals(USERNAME, requestUrl.queryParameter("username"));
		assertEquals(PASSWORD, requestUrl.queryParameter("password"));
		
		assertEquals("test-token", token);
	}
	
	@Test(expected = FhirProviderAuthenticationException.class)
	public void getServerJWTAuthenticationTokenShouldThrowForWrongCredentials() throws Exception {
		mockWebServer.enqueue(new MockResponse().setResponseCode(HttpServletResponse.SC_BAD_REQUEST)
		        .addHeader("Content-Type", "application/json").setBody("{}"));
		delegate.clearServerAuthenticationToken();
		delegate.getServerJWTAuthenticationToken();
		mockWebServer.takeRequest();
	}
	
	@Test
	public void searchOpencrForPatientShouldIssueCorrectRequestForName() throws Exception {
		final Collection<String> EXPECTED_QUERY_PARAMETERS = new HashSet<String>();
		Collections.addAll(EXPECTED_QUERY_PARAMETERS, "active", "name");
		
		final String EXPECTED_JSON_RESPONSE = IOUtils.toString(getClass()
		        .getResourceAsStream(MATCHED_PATIENT_LIST_FILE_NAME));
		
		mockWebServer.enqueue(new MockResponse().setResponseCode(HttpServletResponse.SC_OK)
		        .addHeader("Content-Type", "application/json").setBody(EXPECTED_JSON_RESPONSE));
		delegate.setCachedToken("test-token");
		
		Bundle matchedEntries = delegate.searchForPatients("Atibo", "OPENCR");
		
		RecordedRequest request = mockWebServer.takeRequest();
		HttpUrl requestUrl = request.getRequestUrl();
		assertEquals("GET", request.getMethod());
		assertTrue(request.getPath().startsWith(EXPECTED_PATIENT_PATH));
		assertFalse(requestUrl.isHttps());
		
		Set<String> parameterNames = requestUrl.queryParameterNames();
		assertEquals(2, parameterNames.size());
		assertTrue(parameterNames.containsAll(EXPECTED_QUERY_PARAMETERS));
		assertEquals("Atibo", requestUrl.queryParameter("name"));
		assertEquals("true", requestUrl.queryParameter("active"));
		
		IParser parser = FHIR_CONTEXT.newJsonParser();
		parser.setPrettyPrint(true);
		assertEquals(EXPECTED_JSON_RESPONSE, parser.encodeResourceToString(matchedEntries));
	}
	
	@Test
	public void searchOpencrForPatientShouldIssueCorrectRequestForIdentifier() throws Exception {
		final String IDENTIFIER_TO_SEARCH = "id12000M2";
		final Collection<String> EXPECTED_QUERY_PARAMETERS = new HashSet<String>();
		Collections.addAll(EXPECTED_QUERY_PARAMETERS, "active", "identifier");
		
		final String EXPECTED_JSON_RESPONSE = IOUtils.toString(getClass().getResourceAsStream(IDENTIFIER_MATCHED_PATIENT));
		
		mockWebServer.enqueue(new MockResponse().setResponseCode(HttpServletResponse.SC_OK)
		        .addHeader("Content-Type", "application/json").setBody(EXPECTED_JSON_RESPONSE));
		delegate.setCachedToken("test-token");
		
		delegate.searchForPatients(IDENTIFIER_TO_SEARCH, "OPENCR");
		
		RecordedRequest request = mockWebServer.takeRequest();
		HttpUrl requestUrl = request.getRequestUrl();
		assertEquals("GET", request.getMethod());
		assertTrue(request.getPath().startsWith(EXPECTED_PATIENT_PATH));
		
		Set<String> parameterNames = requestUrl.queryParameterNames();
		assertEquals(2, parameterNames.size());
		assertTrue(parameterNames.containsAll(EXPECTED_QUERY_PARAMETERS));
		assertEquals(IDENTIFIER_TO_SEARCH, requestUrl.queryParameter("identifier"));
		assertEquals("true", requestUrl.queryParameter("active"));
	}
	
	@Test
	public void searchOpencrForPatientShouldIssueACorrectQueryForMultipleNames() throws Exception {
		final String SEARCH_TEXT = "Nashada Mhawila";
		String[] names = SEARCH_TEXT.split(" ");
		final String EXPECTED_JSON_RESPONSE = IOUtils.toString(getClass()
		        .getResourceAsStream(MATCHED_PATIENT_LIST_FILE_NAME));
		
		mockWebServer.enqueue(new MockResponse().setResponseCode(HttpServletResponse.SC_OK)
		        .addHeader("Content-Type", "application/json").setBody(EXPECTED_JSON_RESPONSE));
		delegate.setCachedToken("test-token");
		
		delegate.searchForPatients(SEARCH_TEXT, "OPENCR");
		
		RecordedRequest request = mockWebServer.takeRequest();
		HttpUrl requestUrl = request.getRequestUrl();
		assertEquals("GET", request.getMethod());
		assertTrue(request.getPath().startsWith(EXPECTED_PATIENT_PATH));
		assertFalse(requestUrl.isHttps());
		
		assertEquals(3, requestUrl.querySize());
		assertTrue(requestUrl.queryParameter("name").equals(names[0]) || requestUrl.queryParameter("name").equals(names[1]));
		assertEquals("true", requestUrl.queryParameter("active"));
	}
	
	@Test(expected = FhirResourceSearchException.class)
	public void searchOpencrForPatientShouldThrowExceptionIfCouldNotAuthenticate() throws Exception {
		mockWebServer.enqueue(new MockResponse().setResponseCode(HttpServletResponse.SC_BAD_REQUEST)
		        .addHeader("Content-Type", "application/json").setBody("{}"));
		
		delegate.clearServerAuthenticationToken();
		delegate.searchForPatients("sometext", "OPENCR");
	}
	
	@After
	public void teardown() throws IOException {
		mockWebServer.close();
		mockWebServer.shutdown();
	}
	
}
