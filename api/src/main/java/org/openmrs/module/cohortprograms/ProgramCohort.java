package org.openmrs.module.cohortprograms;

import org.openmrs.Cohort;
import org.openmrs.Program;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.OneToOne;
import javax.persistence.Table;

/**
 * @uthor Willa Mhawila<a.mhawila@gmail.com> on 3/29/21.
 */
@Entity(name = "cohortprograms.ProgramCohort")
@Table(name = "cohortprograms_program_cohort")
public class ProgramCohort {
	
	@Id
	@GeneratedValue
	@Column(name = "program_cohort_id")
	private Integer id;
	
	@OneToOne
	@JoinColumn(name = "program_id", referencedColumnName = "program_id")
	private Program program;
	
	@OneToOne
	@JoinColumn(name = "cohort_id", referencedColumnName = "cohort_id")
	private Cohort cohort;
	
	public ProgramCohort() {
	}
	
	public ProgramCohort(Program program, Cohort cohort) {
		this.program = program;
		this.cohort = cohort;
	}
	
	public Integer getId() {
		return id;
	}
	
	public void setId(Integer id) {
		this.id = id;
	}
	
	public Program getProgram() {
		return program;
	}
	
	public void setProgram(Program program) {
		this.program = program;
	}
	
	public Cohort getCohort() {
		return cohort;
	}
	
	public void setCohort(Cohort cohort) {
		this.cohort = cohort;
	}
	
	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (!(o instanceof ProgramCohort))
			return false;
		
		ProgramCohort that = (ProgramCohort) o;
		
		return getId() != null ? getId().equals(that.getId()) : that.getId() == null;
	}
	
	@Override
	public int hashCode() {
		return getId() != null ? getId().hashCode() : 0;
	}
}
