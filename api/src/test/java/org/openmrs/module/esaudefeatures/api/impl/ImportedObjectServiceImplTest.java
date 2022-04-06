package org.openmrs.module.esaudefeatures.api.impl;

import org.junit.Before;
import org.junit.Test;
import org.openmrs.User;
import org.openmrs.module.esaudefeatures.ImportedObject;
import org.openmrs.module.esaudefeatures.api.ImportedObjectService;
import org.openmrs.test.BaseModuleContextSensitiveTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * @uthor Willa Mhawila<a.mhawila@gmail.com> on 3/30/22.
 */
public class ImportedObjectServiceImplTest extends BaseModuleContextSensitiveTest {
	
	protected static final String TEST_DATA_FILE = "importedObjects_Test_data.xml";
	
	protected static final SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
	
	protected ImportedObjectService service;
	
	@Autowired
	public void setService(@Qualifier("esaudefeatures.ImportedObjectService") ImportedObjectService service) {
		this.service = service;
	}
	
	@Before
	public void setUp() throws Exception {
		executeDataSet(TEST_DATA_FILE);
	}
	
	@Test
	public void saveImportedObjectShouldSave() throws Exception {
		ImportedObject importedObject = new ImportedObject("org.openmrs.Person", new User(1), new Date(), UUID.randomUUID()
		        .toString());
		
		assertNull(importedObject.getId());
		service.saveImportedObject(importedObject);
		
		assertNotNull(importedObject.getId());
	}
	
	@Test
	public void saveImportedObjectsShouldSaveAll() throws Exception {
		User user = new User(1);
		List<ImportedObject> objects = Arrays.asList(new ImportedObject("org.openmrs.Patient", user, new Date(), UUID
		        .randomUUID().toString()), new ImportedObject("org.openmrs.Patient", user, new Date(), UUID.randomUUID()
		        .toString()));
		
		long initiaCount = service.getCountOfAllImportedObjects();
		
		service.saveImportedObjects(objects);
		assertEquals(Long.valueOf(initiaCount + objects.size()), service.getCountOfAllImportedObjects());
	}
	
	@Test
	public void getCountOfAllImportedObjectShouldReturnTheCorrectValue() throws Exception {
		assertEquals(Long.valueOf(7), service.getCountOfAllImportedObjects());
	}
	
	@Test
	public void getCountOfObjectsShouldReturnCorrectValueForGivenDates() throws Exception {
		Date startDate = formatter.parse("2022-03-01 00:00:00");
		Date endDate = formatter.parse("2022-03-31 00:00:00");
		assertEquals(Long.valueOf(3), service.getCountOfImportedObjects(null, startDate, endDate));
		
		startDate = formatter.parse("2022-04-30 00:00:00");
		assertEquals(Long.valueOf(4), service.getCountOfImportedObjects(null, startDate, null));
	}
	
	@Test
	public void getCountOfImportedObjectsByUserShouldReturnCorrectValue() throws Exception {
		User user = new User(1);
		assertEquals(Long.valueOf(4), service.getCountOfImportedObjectsByUser(user));
	}
	
	@Test
	public void getAllImportedObjectsShouldReturnAListOfAllImportedObjects() throws Exception {
		assertEquals(7, service.getAllImportedObjects().size());
	}
	
	@Test
	public void getImportedObjectsShouldReturnCorrectList() throws Exception {
		List<ImportedObject> objects = service.getImportedObjects(null, formatter.parse("2022-03-01 00:15:40"),
		    formatter.parse("2022-03-31 00:00:00"), 1, null);
		assertEquals(2, objects.size());
		assertEquals(Integer.valueOf(2), objects.get(0).getId());
		assertEquals(Integer.valueOf(3), objects.get(1).getId());
	}
}
