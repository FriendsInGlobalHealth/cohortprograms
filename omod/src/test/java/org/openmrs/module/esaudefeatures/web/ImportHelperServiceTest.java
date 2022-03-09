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
import org.openmrs.PatientIdentifier;
import org.openmrs.Person;
import org.openmrs.PersonAddress;
import org.openmrs.PersonAttribute;
import org.openmrs.PersonName;
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
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * @uthor Willa Mhawila<a.mhawila@gmail.com> on 2/20/22.
 */
@Component
public class ImportHelperServiceTest extends BaseModuleWebContextSensitiveTest {
	
	private static final String OPENMRS_LOCATION_FILE = "/openmrs-rest/location.json";
	
	private static final String OPENMRS_CHILD_LOCATION_FILE = "/openmrs-rest/child_location.json";
	
	private static final String OPENMRS_PARENT_LOCATION_FILE = "/openmrs-rest/parent_location.json";
	
	private static final String OPENMRS_GRAND_LOCATION_FILE = "/openmrs-rest/grand_location.json";
	
	private static final String OPENMRS_USER_FILE = "/openmrs-rest/user_both_creator_and_changer_already_exist.json";
	
	private static final String OPENMRS_USER_CHANGER_NOT_EXISTS_FILE = "/openmrs-rest/user_changer_does_not_exist.json";
	
	private static final String OPENMRS_USER2_FILE = "/openmrs-rest/user2.json";
	
	private static final String USERS_TEST_FILE = "org/openmrs/api/include/UserServiceTest.xml";
	
	private static final String USERNAME = "user1";
	
	private static final String PASSWORD = "pa$$w0rd";
	
	private MockWebServer mockWebServer;
	
	final String PATIENT_IDENTIFIERS_JSON = IOUtils.toString(getClass().getResourceAsStream(
	    "/openmrs-rest/patient_identifiers.json"));
	
	final String PERSON_NAMES_JSON = IOUtils.toString(getClass().getResourceAsStream("/openmrs-rest/person_names.json"));
	
	final String PERSON_NAMES2_JSON = IOUtils.toString(getClass().getResourceAsStream("/openmrs-rest/person_names2.json"));
	
	final String PERSON_ADDRESSES_JSON = IOUtils.toString(getClass().getResourceAsStream(
	    "/openmrs-rest/person_addresses.json"));
	
	final String PERSON_ADDRESSES2_JSON = IOUtils.toString(getClass().getResourceAsStream(
	    "/openmrs-rest/person_addresses2.json"));
	
	final String PERSON_ATTRIBUTES_JSON = IOUtils.toString(getClass().getResourceAsStream(
	    "/openmrs-rest/person_attributes.json"));
	
	final String PERSON_FOR_USER = IOUtils.toString(getClass().getResourceAsStream("/openmrs-rest/person_for_user.json"));
	
	@Mock
	private AdministrationService adminService;
	
	@InjectMocks
	@Autowired
	private ImportHelperService helperService;
	
	@Autowired
	private LocationService locationService;
	
	@Autowired
	private UserService userService;
	
	public ImportHelperServiceTest() throws IOException {
	}
	
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
		final String PATIENT_JSON = IOUtils.toString(getClass().getResourceAsStream("/openmrs-rest/single_patient.json"));
		
		setUpMockWebServerToReturnPersonPropertiesInRequiredOrder();
		mockWebServer.enqueue(new MockResponse().setResponseCode(HttpServletResponse.SC_OK)
		        .addHeader("Content-Type", "application/json").setBody(PATIENT_IDENTIFIERS_JSON));
		
		SimpleObject patientObject = SimpleObject.parseJson(PATIENT_JSON);
		Map personObject = patientObject.get("person");
		Map<String, Object> auditInfo = patientObject.get("auditInfo");
		Boolean voided = patientObject.get("voided");
		
		Patient patient = helperService.getPatientFromOpenmrsRestPayload(patientObject);
		
		List<Map> identifiersMaps = SimpleObject.parseJson(PATIENT_IDENTIFIERS_JSON).get("results");
		Set<PatientIdentifier> patientIdentifiers = patient.getIdentifiers();
		for (Map identifierMap : identifiersMaps) {
			String identifierUuid = (String) identifierMap.get("uuid");
			PatientIdentifier patientIdentifier = null;
			for (PatientIdentifier identifier : patientIdentifiers) {
				if (identifierUuid.equals(identifier.getUuid())) {
					patientIdentifier = identifier;
					break;
				}
			}
			Map<String, Object> identifierAuditInfo = (Map<String, Object>) identifierMap.get("auditInfo");
			assertNotNull(patientIdentifier);
			assertNotNull(identifierAuditInfo);
			assertEquals(identifierMap.get("identifier"), patientIdentifier.getIdentifier());
			assertEquals(((Map) identifierMap.get("identifierType")).get("uuid"), patientIdentifier.getIdentifierType()
			        .getUuid());
			assertEquals(((Map) identifierAuditInfo.get("creator")).get("uuid"), patientIdentifier.getCreator().getUuid());
			assertEquals(Utils.parseDateString((String) identifierAuditInfo.get("dateCreated")),
			    patientIdentifier.getDateCreated());
		}
		
		assertEquals(personObject.get("uuid"), patient.getUuid());
		assertEquals(voided, patient.getVoided());
		assertEquals(personObject.get("gender"), patient.getGender());
		assertEquals(Utils.parseDateString((String) personObject.get("birthdate")), patient.getBirthdate());
		assertNull(patient.getDeathDate());
		assertNull(patient.getCauseOfDeath());
		assertEquals(((Map) auditInfo.get("creator")).get("uuid"), patient.getCreator().getUuid());
		assertNull(patient.getChangedBy());
		assertEquals(Utils.parseDateString((String) auditInfo.get("dateCreated")), patient.getDateCreated());
		assertNull(patient.getDateChanged());
		assertFalse(patient.getVoided());
		
		auditInfo = (Map) personObject.get("auditInfo");
		Person person = patient.getPerson();
		assertEquals(((Map) auditInfo.get("creator")).get("uuid"), person.getCreator().getUuid());
		assertNull(patient.getChangedBy());
		assertEquals(Utils.parseDateString((String) auditInfo.get("dateCreated")), person.getDateCreated());
		assertNull(person.getDateChanged());
		assertFalse(person.getVoided());
		
		PersonName name = person.getPersonName();
		List<Map> namesMaps = SimpleObject.parseJson(PERSON_NAMES_JSON).get("results");
		Map preferredName = namesMaps.get(0);
		
		Map nameAuditInfo = (Map) preferredName.get("auditInfo");
		assertTrue(name.isPreferred());
		assertEquals(preferredName.get("uuid"), name.getUuid());
		assertEquals(preferredName.get("givenName"), name.getGivenName());
		assertEquals(preferredName.get("middleName"), name.getMiddleName());
		assertEquals(preferredName.get("familyName"), name.getFamilyName());
		assertNull(name.getFamilyName2());
		assertNull(name.getPrefix());
		assertNull(name.getFamilyNameSuffix());
		assertFalse(name.getVoided());
		assertEquals(((Map) nameAuditInfo.get("creator")).get("uuid"), name.getCreator().getUuid());
		assertEquals(Utils.parseDateString((String) nameAuditInfo.get("dateCreated")), name.getDateCreated());
		assertNull(name.getChangedBy());
		assertNull(name.getDateChanged());
		
		PersonAddress address = person.getPersonAddress();
		List<Map> addressesMaps = SimpleObject.parseJson(PERSON_ADDRESSES_JSON).get("results");
		Map preferredAddress = addressesMaps.get(0);
		Map addressAuditInfo = (Map) preferredAddress.get("auditInfo");
		
		assertTrue(address.isPreferred());
		assertEquals(preferredAddress.get("uuid"), address.getUuid());
		assertEquals(preferredAddress.get("address1"), address.getAddress1());
		assertEquals(preferredAddress.get("address2"), address.getAddress2());
		assertEquals(preferredAddress.get("address5"), address.getAddress5());
		assertEquals(preferredAddress.get("countyDistrict"), address.getCountyDistrict());
		assertEquals(preferredAddress.get("stateProvince"), address.getStateProvince());
		assertEquals(preferredAddress.get("country"), address.getCountry());
		assertNull(address.getCityVillage());
		assertNull(address.getAddress3());
		assertNull(address.getAddress4());
		assertNull(address.getAddress6());
		assertNull(address.getLongitude());
		assertNull(address.getLatitude());
		assertNull(address.getPostalCode());
		assertFalse(address.getVoided());
		assertEquals(((Map) addressAuditInfo.get("creator")).get("uuid"), address.getCreator().getUuid());
		assertEquals(Utils.parseDateString((String) addressAuditInfo.get("dateCreated")), address.getDateCreated());
		assertNull(address.getChangedBy());
		assertNull(address.getDateChanged());
		
		List<Map> attributesMaps = SimpleObject.parseJson(PERSON_ATTRIBUTES_JSON).get("results");
		Map attributeMap = attributesMaps.get(0);
		Map attributeAuditInfo = (Map) attributeMap.get("auditInfo");
		PersonAttribute personAttribute = person.getAttributes().iterator().next();
		assertNotNull(personAttribute);
		assertEquals(attributeMap.get("value"), personAttribute.getValue());
		assertEquals(((Map) attributeMap.get("attributeType")).get("uuid"), personAttribute.getAttributeType().getUuid());
		assertEquals(((Map) attributeAuditInfo.get("creator")).get("uuid"), personAttribute.getCreator().getUuid());
		assertEquals(Utils.parseDateString((String) attributeAuditInfo.get("dateCreated")), personAttribute.getDateCreated());
		assertNull(personAttribute.getChangedBy());
		assertNull(personAttribute.getDateChanged());
	}
	
	@Test
	public void importUserFromRemoteOpenmrsServerShouldImportCorrectly() throws IOException {
		final String USER = IOUtils.toString(getClass().getResourceAsStream(OPENMRS_USER_FILE));
		SimpleObject userObject = SimpleObject.parseJson(USER);
		mockWebServer.enqueue(new MockResponse().setResponseCode(HttpServletResponse.SC_OK)
		        .addHeader("Content-Type", "application/json").setBody(USER));
		
		mockWebServer.enqueue(new MockResponse().setResponseCode(HttpServletResponse.SC_OK)
		        .addHeader("Content-Type", "application/json").setBody(PERSON_FOR_USER));
		
		setUpMockWebServerToReturnPersonPropertiesInRequiredOrder();
		
		String userUuid = userObject.get("uuid");
		Map auditInfo = userObject.get("auditInfo");
		helperService.importUserFromRemoteOpenmrsServer(userUuid);
		
		User importedUser = userService.getUserByUuid(userUuid);
		assertNotNull(importedUser);
		assertEquals(userUuid, importedUser.getUuid());
		assertEquals(userObject.get("username"), importedUser.getUsername());
		assertEquals(userObject.get("systemId"), importedUser.getSystemId());
		assertEquals(((Map) auditInfo.get("creator")).get("uuid"), importedUser.getCreator().getUuid());
		assertEquals(((Map) auditInfo.get("changedBy")).get("uuid"), importedUser.getChangedBy().getUuid());
		assertEquals(Utils.parseDateString((String) auditInfo.get("dateCreated")), importedUser.getDateCreated());
		assertEquals(Utils.parseDateString((String) auditInfo.get("dateChanged")), importedUser.getDateChanged());
	}
	
	@Test
	public void importUserFromRemoteOpenmrsServerShouldImportCorrectlyWithChangerNotExisting() throws IOException {
		final String USER = IOUtils.toString(getClass().getResourceAsStream(OPENMRS_USER_CHANGER_NOT_EXISTS_FILE));
		final String CHANGER = IOUtils.toString(getClass().getResourceAsStream(OPENMRS_USER2_FILE));
		SimpleObject userObject = SimpleObject.parseJson(USER);
		
		mockWebServer.enqueue(new MockResponse().setResponseCode(HttpServletResponse.SC_OK)
		        .addHeader("Content-Type", "application/json").setBody(USER));
		
		mockWebServer.enqueue(new MockResponse().setResponseCode(HttpServletResponse.SC_OK)
		        .addHeader("Content-Type", "application/json").setBody(PERSON_FOR_USER));
		
		setUpMockWebServerToReturnPersonPropertiesInRequiredOrder();
		
		mockWebServer.enqueue(new MockResponse().setResponseCode(HttpServletResponse.SC_OK)
		        .addHeader("Content-Type", "application/json").setBody(CHANGER));
		
		mockWebServer.enqueue(new MockResponse().setResponseCode(HttpServletResponse.SC_OK)
		        .addHeader("Content-Type", "application/json").setBody(PERSON_NAMES2_JSON));
		
		mockWebServer.enqueue(new MockResponse().setResponseCode(HttpServletResponse.SC_OK)
		        .addHeader("Content-Type", "application/json").setBody(PERSON_ADDRESSES2_JSON));
		
		String userUuid = userObject.get("uuid");
		Map auditInfo = userObject.get("auditInfo");
		helperService.importUserFromRemoteOpenmrsServer(userUuid);
		
		User importedUser = userService.getUserByUuid(userUuid);
		assertNotNull(importedUser);
		assertEquals(userUuid, importedUser.getUuid());
		assertEquals(userObject.get("username"), importedUser.getUsername());
		assertEquals(userObject.get("systemId"), importedUser.getSystemId());
		assertEquals(((Map) auditInfo.get("creator")).get("uuid"), importedUser.getCreator().getUuid());
		assertEquals(((Map) auditInfo.get("changedBy")).get("uuid"), importedUser.getChangedBy().getUuid());
		assertEquals(Utils.parseDateString((String) auditInfo.get("dateCreated")), importedUser.getDateCreated());
		assertEquals(Utils.parseDateString((String) auditInfo.get("dateChanged")), importedUser.getDateChanged());
		
		importedUser = importedUser.getChangedBy();
		userObject = SimpleObject.parseJson(CHANGER);
		auditInfo = userObject.get("auditInfo");
		assertEquals(userObject.get("username"), importedUser.getUsername());
		assertEquals(userObject.get("systemId"), importedUser.getSystemId());
		assertEquals(((Map) auditInfo.get("creator")).get("uuid"), importedUser.getCreator().getUuid());
		assertNull(importedUser.getChangedBy());
		assertEquals(Utils.parseDateString((String) auditInfo.get("dateCreated")), importedUser.getDateCreated());
		assertNull(importedUser.getDateChanged());
	}
	
	@Test
	public void importUserFromRemoteOpenmrsServerShouldImportSelfReferencingUser() throws Exception {
		final String SELF_REFERENCING_USER_JSON = IOUtils.toString(getClass().getResourceAsStream(
		    "/openmrs-rest/self_referencing_user.json"));
		final String SINGLE_PERSON = IOUtils.toString(getClass().getResourceAsStream("/openmrs-rest/single_person.json"));
		
		SimpleObject userObject = SimpleObject.parseJson(SELF_REFERENCING_USER_JSON);
		SimpleObject personObject = SimpleObject.parseJson(SINGLE_PERSON);
		
		mockWebServer.enqueue(new MockResponse().setResponseCode(HttpServletResponse.SC_OK)
		        .addHeader("Content-Type", "application/json").setBody(SELF_REFERENCING_USER_JSON));
		
		mockWebServer.enqueue(new MockResponse().setResponseCode(HttpServletResponse.SC_OK)
		        .addHeader("Content-Type", "application/json").setBody(SINGLE_PERSON));
		
		mockWebServer.enqueue(new MockResponse().setResponseCode(HttpServletResponse.SC_OK)
		        .addHeader("Content-Type", "application/json").setBody(PERSON_NAMES_JSON));
		
		String userUuid = userObject.get("uuid");
		
		helperService.importUserFromRemoteOpenmrsServer(userUuid);
		
		User importedUser = userService.getUserByUuid(userUuid);
		assertNotNull(importedUser.getUserId());
		assertEquals(importedUser, importedUser.getCreator());
		assertEquals(importedUser, importedUser.getPerson().getPersonCreator());
		assertEquals(importedUser, importedUser.getPerson().getPersonChangedBy());
	}
	
	@Test
	public void importLocationFromRemoteOpenmrsServerShouldImportLocationWithoutParent() throws IOException,
	        InterruptedException {
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
		assertEquals(((Map) auditInfo.get("creator")).get("uuid"), location.getCreator().getUuid());
		assertEquals(Utils.parseDateString((String) auditInfo.get("dateCreated")), location.getDateCreated());
	}
	
	@Test
	public void importLocationFromRemoteOpenmrsServerShouldImportLocationWithAncestors() throws IOException,
	        InterruptedException {
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
	
	private void setUpMockWebServerToReturnPersonPropertiesInRequiredOrder() {
		mockWebServer.enqueue(new MockResponse().setResponseCode(HttpServletResponse.SC_OK)
		        .addHeader("Content-Type", "application/json").setBody(PERSON_NAMES_JSON));
		
		mockWebServer.enqueue(new MockResponse().setResponseCode(HttpServletResponse.SC_OK)
		        .addHeader("Content-Type", "application/json").setBody(PERSON_ADDRESSES_JSON));
		
		mockWebServer.enqueue(new MockResponse().setResponseCode(HttpServletResponse.SC_OK)
		        .addHeader("Content-Type", "application/json").setBody(PERSON_ATTRIBUTES_JSON));
	}
}
