package org.openmrs.module.cohortprograms.web.interceptor;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Cohort;
import org.openmrs.Patient;
import org.openmrs.PatientProgram;
import org.openmrs.Program;
import org.openmrs.api.CohortService;
import org.openmrs.api.ProgramWorkflowService;
import org.openmrs.module.cohortprograms.api.CohortProgramsService;
import org.openmrs.util.OpenmrsClassLoader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;
import sun.reflect.Reflection;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import static org.openmrs.util.OpenmrsConstants.OPENMRS_VERSION_SHORT;

/**
 * @uthor Willa Mhawila<a.mhawila@gmail.com> on 3/29/21.
 */
@Component
public class CohortProgramsInterceptor extends HandlerInterceptorAdapter {
	
	private final static Log LOG = LogFactory.getLog(CohortProgramsInterceptor.class);
	
	private final static String ORIGINAL_PROGRAM_SERVLET_PATH = "/admin/programs/program.form";
	
	private final static String REDIRECT_PROGRAM_SERVLET_PATH = "/module/cohortprograms/admin/programs/program.form";
	
	private static float floatizedVersion;
	
	static {
		int firstDotIndex = OPENMRS_VERSION_SHORT.indexOf(".");
		int lastDotIndex = OPENMRS_VERSION_SHORT.indexOf(".", firstDotIndex + 1);
		String majorMinor = OPENMRS_VERSION_SHORT.substring(0, lastDotIndex);
		floatizedVersion = Float.parseFloat(majorMinor);
	}
	
	@Autowired
	@Qualifier("cohortprograms.CohortProgramsService")
	CohortProgramsService cpService;
	
	@Autowired
	CohortService cohortService;
	
	@Autowired
	ProgramWorkflowService programWorkflowService;
	
	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
		String servletPath = request.getServletPath();
		
		if (ORIGINAL_PROGRAM_SERVLET_PATH.equalsIgnoreCase(servletPath)) {
			LOG.info("Redirecting user to custom cohortPrograms program form: " + REDIRECT_PROGRAM_SERVLET_PATH);
			String redirectLocation = request.getContextPath().concat(REDIRECT_PROGRAM_SERVLET_PATH);
			final String QUERY_STRING = request.getQueryString();
			if (StringUtils.hasText(QUERY_STRING)) {
				redirectLocation = redirectLocation.concat("?").concat(QUERY_STRING);
			}
			response.sendRedirect(redirectLocation);
			return false;
		}
		return true;
	}
	
	@Override
	public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler,
	        ModelAndView modelAndView) throws Exception {
		String currentViewName = modelAndView != null ? modelAndView.getViewName() : null;
		
		if (currentViewName != null) {
			if (currentViewName.endsWith("programs/programForm")) {
				// Get all cohorts.
				List<Cohort> cohorts = cohortService.getAllCohorts(false);
				modelAndView.getModelMap().addAttribute("cohorts", cohorts);
				
				if (OPENMRS_VERSION_SHORT.startsWith("1")) {
					modelAndView.setViewName("module/cohortprograms/admin/programs/programForm1x");
				} else {
					modelAndView.setViewName("module/cohortprograms/admin/programs/programForm2x");
				}
			} else if (currentViewName.endsWith("portlets/patientPrograms")) {
				// Allow programs for which the patient is a member.
				
				// Get patient
				final Map<String, Object> MODEL = (Map<String, Object>) modelAndView.getModel().get("model");
				Patient patient = (Patient) MODEL.get("patient");
				List<Program> programsList = (List<Program>) MODEL.get("programs");
				
				// Get patient programs (already enrolled in)
				List<PatientProgram> patientPrograms = programWorkflowService.getPatientPrograms(patient, null, null, null,
				    null, null, true);
				if (programsList == null) {
					programsList = new ArrayList<Program>();
				}
				
				Map<Program, List<Cohort>> programListMap = cpService.getCohortsForPrograms();
				for (Map.Entry<Program, List<Cohort>> entry : programListMap.entrySet()) {
					Program program = entry.getKey();
					if (programsList.contains(program)) {
						programsList.remove(program);
					}
					
					for (Cohort cohort : entry.getValue()) {
						if (cohort.contains(patient.getPatientId()) || isPatientEnrolled(patientPrograms, patient, program)) {
							programsList.add(program);
							break;
						}
					}
				}
				
				// Get OpenMRS Version
				if (floatizedVersion >= 2.2f) {
					modelAndView.setViewName("module/cohortprograms/portlets/patientPrograms2x");
				} else {
					modelAndView.setViewName("module/cohortprograms/portlets/patientPrograms1x");
				}
			}
		}
	}
	
	private static boolean isPatientEnrolled(final List<PatientProgram> patientPrograms, Patient patient,
	        final Program program) {
		assert patientPrograms != null && patient != null && program != null;
		for (PatientProgram patientProgram : patientPrograms) {
			if (program.equals(patientProgram.getProgram()) && patient.equals(patientProgram.getPatient())
			        && !patientProgram.getVoided()) {
				return true;
			}
		}
		return false;
	}
}
