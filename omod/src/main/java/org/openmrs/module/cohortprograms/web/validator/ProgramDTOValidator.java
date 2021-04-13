package org.openmrs.module.cohortprograms.web.validator;

import org.openmrs.annotation.Handler;
import org.openmrs.module.cohortprograms.web.ProgramDTO;
import org.openmrs.validator.ProgramValidator;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

/**
 * @uthor Willa Mhawila<a.mhawila@gmail.com> on 4/1/21.
 */
@Handler(supports = ProgramDTO.class, order = 60)
public class ProgramDTOValidator implements Validator {
	
	private ProgramValidator programValidator = new ProgramValidator();
	
	@Override
	public boolean supports(Class<?> clazz) {
		return clazz.equals(ProgramDTO.class);
	}
	
	@Override
	public void validate(Object target, Errors errors) {
		ProgramDTO programDTO = (ProgramDTO) target;
		programValidator.validate(programDTO.getProgram(), errors);
	}
}
