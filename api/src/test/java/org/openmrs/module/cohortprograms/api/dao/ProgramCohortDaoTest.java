package org.openmrs.module.cohortprograms.api.dao;

import org.junit.Before;
import org.junit.Test;
import org.openmrs.Cohort;
import org.openmrs.Program;
import org.openmrs.module.cohortprograms.ProgramCohort;
import org.openmrs.test.BaseModuleContextSensitiveTest;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * @uthor Willa Mhawila<a.mhawila@gmail.com> on 4/8/21.
 */
public class ProgramCohortDaoTest extends BaseModuleContextSensitiveTest {
	
	protected static final String TEST_DATA_FILE = "cohortPrograms_Test_data.xml";
	
	protected ProgramCohortDao programCohortDao;
	
	@Autowired
	public void setProgramCohortDao(ProgramCohortDao programCohortDao) {
		this.programCohortDao = programCohortDao;
	}
	
	@Before
	public void setup() throws Exception {
		executeDataSet(TEST_DATA_FILE);
	}
	
	@Test
	public void getProgramCohortByIdShouldReturnTheCorrectRecord() {
		final Integer PROGRAM_COHORT_ID = 13;
		ProgramCohort programCohort = programCohortDao.getProgramCohortById(PROGRAM_COHORT_ID);
		
		assertNotNull(programCohort);
		assertEquals(PROGRAM_COHORT_ID, programCohort.getId());
	}
	
	@Test
	public void getProgramCohortByIdShouldReturnNullIfIDDoesNotExist() {
		Integer idDoesNotExists = -999;
		assertNull(programCohortDao.getProgramCohortById(idDoesNotExists));
	}
	
	@Test
	public void getProgramCohortByProgramAndCohortShouldReturnCorrectRecord() throws Exception {
		Program program = new Program(1);
		Cohort cohort = new Cohort(1);
		
		ProgramCohort programCohort = programCohortDao.getProgramCohortByProgramAndCohort(program, cohort);
		
		assertNotNull(programCohort);
		assertEquals(program.getId(), programCohort.getProgram().getId());
		assertEquals(cohort.getId(), programCohort.getCohort().getId());
	}
	
	@Test
	public void getProgramCohortByProgramShouldReturnCorrectList() throws Exception {
		Program program = new Program(1);
		List<ProgramCohort> programCohorts = programCohortDao.getProgramCohortsByProgram(program);
		
		assertEquals(2, programCohorts.size());
		
		for (ProgramCohort programCohort : programCohorts) {
			assertEquals(program.getId(), programCohort.getProgram().getId());
		}
	}
	
	@Test
	public void isCohortAssociatedWithProgramShouldReturnCorrectOutcome() throws Exception {
		Program program1 = new Program(1);
		Program program2 = new Program(2);
		Cohort cohort = new Cohort(1);
		
		assertTrue(programCohortDao.isCohortAssociatedWithProgram(program1, cohort));
		assertFalse(programCohortDao.isCohortAssociatedWithProgram(program2, cohort));
	}
	
	@Test
	public void getAllProgramCohortsShouldReturnAllRecords() throws Exception {
		assertEquals(3, programCohortDao.getAllProgramCohorts().size());
	}
	
	@Test
	public void saveAndDeleteProgramCohortShouldWorkAsExpected() throws Exception {
		final Integer PROGRAM_COHORT_ID = 11;
		ProgramCohort programCohort = programCohortDao.getProgramCohortById(PROGRAM_COHORT_ID);
		
		assertNotNull(programCohort);
		
		Cohort cohort = programCohort.getCohort();
		Program program = programCohort.getProgram();
		
		programCohortDao.deleteProgramCohort(programCohort);
		
		assertNull(programCohortDao.getProgramCohortById(PROGRAM_COHORT_ID));
		
		// Create new and save.
		programCohort = new ProgramCohort(program, cohort);
		programCohortDao.saveProgramCohort(programCohort);
		
		assertNotNull(programCohortDao.getProgramCohortByProgramAndCohort(program, cohort));
	}
}
