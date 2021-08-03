/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.esaudefeatures.api.impl;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.openmrs.Cohort;
import org.openmrs.Program;
import org.openmrs.module.esaudefeatures.ProgramCohort;
import org.openmrs.module.esaudefeatures.api.dao.ProgramCohortDao;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;

/**
 * This is a unit test, which verifies logic in CohortProgramsService. It doesn't extend
 * BaseModuleContextSensitiveTest, thus it is run without the in-memory DB and Spring context.
 */
public class CohortProgramsServiceImplTest {
	
	@InjectMocks
	CohortProgramsServiceImpl cohortProgramsService;
	
	@Mock
	ProgramCohortDao programCohortDao;
	
	@Before
	public void setupMocks() {
		MockitoAnnotations.initMocks(this);
	}
	
	@Test
	public void getCohortsForProgramShouldReturnCorrectResults() throws Exception {
		Program program = new Program(213);
		Program program1 = new Program(215);
		Cohort cohort1 = new Cohort(299);
		cohort1.setUuid("cohort-1-test-uuid");
		Cohort cohort2 = new Cohort(399);
		cohort1.setUuid("cohort-2-test-uuid");
		List<ProgramCohort> programCohorts = Arrays.asList(new ProgramCohort(program, cohort1), new ProgramCohort(program,
		        cohort2));
		Mockito.when(programCohortDao.getProgramCohortsByProgram(program)).thenReturn(programCohorts);
		Mockito.when(programCohortDao.getProgramCohortsByProgram(program1)).thenReturn(null);
		
		List<Cohort> cohortsAssociatedWithProgram = cohortProgramsService.getCohortsForProgram(program);
		
		assertEquals(2, cohortsAssociatedWithProgram.size());
		assertTrue(cohortsAssociatedWithProgram.containsAll(Arrays.asList(cohort1, cohort2)));
		
		assertTrue(cohortProgramsService.getCohortsForProgram(program1).isEmpty());
	}
	
	@Test
	public void addCohortToProgramShouldProduceCorrectResult() throws Exception {
		Program program = new Program(210);
		Cohort cohort1 = new Cohort(277);
		Cohort cohort2 = new Cohort(377);
		
		List<Cohort> cohorts = Arrays.asList(cohort1, cohort2);
		ProgramCohort programCohort1 = new ProgramCohort(program, cohort1);
		programCohort1.setId(100);
		ProgramCohort programCohort2 = new ProgramCohort(program, cohort2);
		programCohort2.setId(200);
		Mockito.when(programCohortDao.saveProgramCohort(any(ProgramCohort.class))).thenReturn(programCohort1,
		    programCohort2, programCohort1);
		Mockito.when(programCohortDao.isCohortAssociatedWithProgram(program, cohort1)).thenReturn(false, false, true);
		Mockito.when(programCohortDao.isCohortAssociatedWithProgram(program, cohort2)).thenReturn(false, true, true);
		
		// First call, both cohorts are not yet associated
		List<ProgramCohort> addedProgramCohorts = cohortProgramsService.addCohortsToProgram(program, cohorts);
		assertEquals(2, addedProgramCohorts.size());
		assertTrue(addedProgramCohorts.containsAll(Arrays.asList(programCohort1, programCohort2)));
		
		// Second call cohort2 is already associated with program
		addedProgramCohorts = cohortProgramsService.addCohortsToProgram(program, cohorts);
		assertEquals(1, addedProgramCohorts.size());
		assertEquals(programCohort1, addedProgramCohorts.get(0));
		
		// Third call both cohort1 & cohort2 are already associated with program
		addedProgramCohorts = cohortProgramsService.addCohortsToProgram(program, cohorts);
		assertTrue(addedProgramCohorts.isEmpty());
		
		Mockito.times(9);
	}
}
