package org.openmrs.module.cohortprograms.web;

import org.openmrs.Cohort;
import org.openmrs.Program;

import java.util.ArrayList;
import java.util.List;

/**
 * @uthor Willa Mhawila<a.mhawila@gmail.com> on 4/1/21.
 */
public class ProgramModel {
	
	private Program program;
	
	private List<Cohort> cohorts = new ArrayList<Cohort>();
	
	public ProgramModel() {
	}
	
	public ProgramModel(Program program, List<Cohort> cohorts) {
		this.program = program;
		this.cohorts = cohorts;
	}
	
	public Program getProgram() {
		return program;
	}
	
	public void setProgram(Program program) {
		this.program = program;
	}
	
	public List<Cohort> getCohorts() {
		return cohorts;
	}
	
	public void setCohorts(List<Cohort> cohorts) {
		if (cohorts != null && !cohorts.isEmpty()) {
			this.cohorts = cohorts;
		}
	}
}
