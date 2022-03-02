package org.openmrs.module.esaudefeatures.web;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.apache.commons.io.IOUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.openmrs.Location;
import org.openmrs.Patient;
import org.openmrs.User;
import org.openmrs.api.AdministrationService;
import org.openmrs.api.LocationService;
import org.openmrs.api.UserService;
import org.openmrs.module.esaudefeatures.EsaudeFeaturesConstants;
import org.openmrs.module.webservices.rest.SimpleObject;
import org.openmrs.web.test.BaseModuleWebContextSensitiveTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * @uthor Willa Mhawila<a.mhawila@gmail.com> on 2/20/22.
 */
@Component
public class ImportHelperServiceTest extends BaseModuleWebContextSensitiveTest {
	private static final String OPENMRS_LOCATION_FILE = "/openmrs_rest_location.json";
	private static final String OPENMRS_CHILD_LOCATION_FILE = "/openmrs_rest_child_location.json";
	private static final String OPENMRS_PARENT_LOCATION_FILE = "/openmrs_rest_parent_location.json";
	private static final String OPENMRS_GRAND_LOCATION_FILE = "/openmrs_rest_grand_location.json";
	private static final String OPENMRS_USER_FILE = "/openmrs_rest_user_both_creator_and_changer_already_exist.json";
	private static final String OPENMRS_USER_CHANGER_NOT_EXISTS_FILE = "/openmrs_rest_user_changer_does_not_exist.json";
	private static final String OPENMRS_USER2_FILE = "/openmrs_rest_user2.json";
	private static final String USERS_TEST_FILE = "org/openmrs/api/include/UserServiceTest.xml";
	private static final String USERNAME = "user1";
	private static final String PASSWORD = "pa$$w0rd";
	private MockWebServer mockWebServer;
	
	@Mock
	private AdministrationService adminService;
	
	@InjectMocks
	@Autowired
	private ImportHelperService helperService;

	@Autowired
	private LocationService locationService;

	@Autowired
	private UserService userService;

	@Before
	public void setup() throws Exception {
		executeDataSet(USERS_TEST_FILE);
		mockWebServer = new MockWebServer();
		mockWebServer.start();
		Mockito.when(adminService.getGlobalProperty(EsaudeFeaturesConstants.OPENMRS_REMOTE_SERVER_URL_GP)).thenReturn(
		    mockWebServer.url("/").toString());
		Mockito.when(adminService.getGlobalProperty(EsaudeFeaturesConstants.OPENMRS_REMOTE_SERVER_USERNAME_GP)).thenReturn(
		    USERNAME);
		Mockito.when(adminService.getGlobalProperty(EsaudeFeaturesConstants.OPENMRS_REMOTE_SERVER_PASSWORD_GP)).thenReturn(
		    PASSWORD);
	}

	@After
	public void tearDown() throws Exception {
		mockWebServer.close();
	}

	@Test
	public void getPatientFromOpenmrsPayloadShouldReturnTheCorrectObject() throws Exception {
		final String PATIENT_JSON = IOUtils.toString(getClass().getResourceAsStream("/openmrs_rest_single_patient.json"));
		
		SimpleObject patientObject = SimpleObject.parseJson(PATIENT_JSON);
		Map<String, Object> auditInfo = patientObject.get("auditInfo");
		Boolean voided = patientObject.get("voided");
		Patient patient = helperService.getPatientFromOpenmrsRestPayload(patientObject);
		
		assertEquals(((Map) patientObject.get("person")).get("uuid"), patient.getUuid());
		assertEquals(voided, patient.getVoided());
	}

	@Test
	public void importUserFromRemoteOpenmrsServerShouldImportCorrectly() throws IOException {
		final String USER = IOUtils.toString(getClass().getResourceAsStream(OPENMRS_USER_FILE));
		SimpleObject userObject = SimpleObject.parseJson(USER);
		String userUuid = userObject.get("uuid");
		mockWebServer.enqueue(new MockResponse().setResponseCode(HttpServletResponse.SC_OK)
				.addHeader("Content-Type", "application/json").setBody(USER));

		Map auditInfo = userObject.get("auditInfo");
		helperService.importUserFromRemoteOpenmrsServer(userUuid);

		User importedUser = userService.getUserByUuid(userUuid);
		assertNotNull(importedUser);
		assertEquals(userUuid, importedUser.getUuid());
		assertEquals(userObject.get("username"), importedUser.getUsername());
		assertEquals(userObject.get("systemId"), importedUser.getSystemId());
		assertEquals(((Map)auditInfo.get("creator")).get("uuid"), importedUser.getCreator().getUuid());
		assertEquals(((Map)auditInfo.get("changedBy")).get("uuid"), importedUser.getChangedBy().getUuid());
		assertEquals(Utils.parseDateString((String) auditInfo.get("dateCreated")), importedUser.getDateCreated());
		assertEquals(Utils.parseDateString((String) auditInfo.get("dateChanged")), importedUser.getDateChanged());
	}

	@Test
	public void importUserFromRemoteOpenmrsServerShouldImportCorrectlyWithChangerNotExisting() throws IOException {
		final String USER = IOUtils.toString(getClass().getResourceAsStream(OPENMRS_USER_CHANGER_NOT_EXISTS_FILE));
		final String CHANGER = IOUtils.toString(getClass().getResourceAsStream(OPENMRS_USER2_FILE));
		SimpleObject userObject = SimpleObject.parseJson(USER);
		String userUuid = userObject.get("uuid");
		mockWebServer.enqueue(new MockResponse().setResponseCode(HttpServletResponse.SC_OK)
				.addHeader("Content-Type", "application/json").setBody(USER));
		mockWebServer.enqueue(new MockResponse().setResponseCode(HttpServletResponse.SC_OK)
				.addHeader("Content-Type", "application/json").setBody(CHANGER));


		Map auditInfo = userObject.get("auditInfo");
		helperService.importUserFromRemoteOpenmrsServer(userUuid);

		User importedUser = userService.getUserByUuid(userUuid);
		assertNotNull(importedUser);
		assertEquals(userUuid, importedUser.getUuid());
		assertEquals(userObject.get("username"), importedUser.getUsername());
		assertEquals(userObject.get("systemId"), importedUser.getSystemId());
		assertEquals(((Map)auditInfo.get("creator")).get("uuid"), importedUser.getCreator().getUuid());
		assertEquals(((Map)auditInfo.get("changedBy")).get("uuid"), importedUser.getChangedBy().getUuid());
		assertEquals(Utils.parseDateString((String) auditInfo.get("dateCreated")), importedUser.getDateCreated());
		assertEquals(Utils.parseDateString((String) auditInfo.get("dateChanged")), importedUser.getDateChanged());

		importedUser = importedUser.getChangedBy();
		userObject = SimpleObject.parseJson(CHANGER);
		auditInfo = userObject.get("auditInfo");
		assertEquals(userObject.get("username"), importedUser.getUsername());
		assertEquals(userObject.get("systemId"), importedUser.getSystemId());
		assertEquals(((Map)auditInfo.get("creator")).get("uuid"), importedUser.getCreator().getUuid());
		assertNull(importedUser.getChangedBy());
		assertEquals(Utils.parseDateString((String) auditInfo.get("dateCreated")), importedUser.getDateCreated());
		assertNull(importedUser.getDateChanged());
	}

	@Test
	public void importLocationFromRemoteOpenmrsServerShouldImportLocationWithoutParent() throws IOException, InterruptedException {
		final String LOCATION = IOUtils.toString(getClass().getResourceAsStream(OPENMRS_LOCATION_FILE));
		mockWebServer.enqueue(new MockResponse().setResponseCode(HttpServletResponse.SC_OK)
				.addHeader("Content-Type", "application/json").setBody(LOCATION));

		SimpleObject locationObj = SimpleObject.parseJson(LOCATION);
		String locUuid = locationObj.get("uuid");
		Map auditInfo = locationObj.get("auditInfo");
		helperService.importLocationFromRemoteOpenmrsServer(locUuid);

		final Location location = locationService.getLocationByUuid(locUuid);

		assertNotNull(location);
		assertEquals(locUuid, location.getUuid());
		assertEquals(locationObj.get("name"), location.getName());
		assertEquals(locationObj.get("description"), location.getDescription());
		assertEquals(locationObj.get("countyDistrict"), location.getCountyDistrict());
		assertEquals(locationObj.get("stateProvince"), location.getStateProvince());
		assertEquals(locationObj.get("country"), location.getCountry());
		assertFalse(location.isRetired());
		assertNull(location.getAddress1());
		assertNull(location.getAddress2());
		assertNull(location.getAddress3());
		assertNull(location.getAddress4());
		assertNull(location.getAddress5());
		assertNull(location.getAddress6());
		assertNull(location.getLongitude());
		assertNull(location.getLatitude());
		assertNull(location.getPostalCode());
		assertEquals(((Map)auditInfo.get("creator")).get("uuid"), location.getCreator().getUuid());
		assertEquals(Utils.parseDateString((String) auditInfo.get("dateCreated")), location.getDateCreated());
	}

	@Test
	public void importLocationFromRemoteOpenmrsServerShouldImportLocationWithAncestors() throws IOException, InterruptedException {
		final String LOCATION = IOUtils.toString(getClass().getResourceAsStream(OPENMRS_CHILD_LOCATION_FILE));
		final String PARENT_LOCATION = IOUtils.toString(getClass().getResourceAsStream(OPENMRS_PARENT_LOCATION_FILE));
		final String GRAND_PARENT_LOCATION = IOUtils.toString(getClass().getResourceAsStream(OPENMRS_GRAND_LOCATION_FILE));

		mockWebServer.enqueue(new MockResponse().setResponseCode(HttpServletResponse.SC_OK)
				.addHeader("Content-Type", "application/json").setBody(LOCATION));
		mockWebServer.enqueue(new MockResponse().setResponseCode(HttpServletResponse.SC_OK)
				.addHeader("Content-Type", "application/json").setBody(PARENT_LOCATION));
		mockWebServer.enqueue(new MockResponse().setResponseCode(HttpServletResponse.SC_OK)
				.addHeader("Content-Type", "application/json").setBody(GRAND_PARENT_LOCATION));

		SimpleObject locationObj = SimpleObject.parseJson(LOCATION);
		String locUuid = locationObj.get("uuid");
		SimpleObject parentLocationObj = SimpleObject.parseJson(PARENT_LOCATION);
		String parentUuid = parentLocationObj.get("uuid");
		SimpleObject grandLocationObj = SimpleObject.parseJson(GRAND_PARENT_LOCATION);
		String grandUuid = grandLocationObj.get("uuid");

		helperService.importLocationFromRemoteOpenmrsServer(locUuid);

		Location location = locationService.getLocationByUuid(locUuid);
		Location parentLocation = locationService.getLocationByUuid(parentUuid);
		Location grandParentLocation = locationService.getLocationByUuid(grandUuid);
		assertNotNull(location);
		assertNotNull(parentLocation);
		assertNotNull(grandParentLocation);
		assertEquals(parentLocation, location.getParentLocation());
		assertEquals(grandParentLocation, parentLocation.getParentLocation());
	}
}
