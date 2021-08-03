package org.openmrs.module.esaudefeatures.web;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Cohort;
import org.openmrs.api.CohortService;
import org.openmrs.api.context.Context;
import org.springframework.util.StringUtils;

import java.beans.PropertyEditorSupport;

/**
 * @uthor Willa Mhawila<mmhawila@juutech.co.tz> on 4/7/21.
 */
public class CohortEditor extends PropertyEditorSupport {
	
	private Log log = LogFactory.getLog(this.getClass());
	
	@Override
	public void setAsText(String text) throws IllegalArgumentException {
		CohortService cohortService = Context.getCohortService();
		if (StringUtils.hasText(text)) {
			try {
				this.setValue(cohortService.getCohort(Integer.valueOf(text)));
			}
			catch (Exception e) {
				Cohort cohort = cohortService.getCohortByUuid(text);
				
				if (cohort == null) {
					cohort = cohortService.getCohort(text);
				}
				if (cohort == null) {
					this.log.error("Error setting text" + text, e);
					throw new IllegalArgumentException("Cohort not found: " + e.getMessage());
				}
				this.setValue(cohort);
			}
		} else {
			this.setValue(null);
		}
	}
	
	@Override
	public String getAsText() {
		Cohort cohort = (Cohort) this.getValue();
		return cohort == null ? "" : cohort.getCohortId().toString();
	}
}
