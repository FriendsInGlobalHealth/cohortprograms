package org.openmrs.module.esaudefeatures;

import org.apache.commons.lang.StringUtils;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifier;
import org.openmrs.User;
import org.openmrs.api.PatientService;
import org.openmrs.api.UserService;
import org.openmrs.util.DatabaseUpdater;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.Date;
import java.util.UUID;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @uthor Willa Mhawila<a.mhawila@gmail.com> on 2/27/24.
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ImportLogUtils {
	
	protected static String TABLE = "esaudefeatures_rps_import_log";
	
	protected static Logger LOGGER = LoggerFactory.getLogger(ImportLogUtils.class);
	
	public static Integer NID_TYPE_ID = 2;
	
	private PatientService patientService;
	
	private UserService userService;
	
	@Autowired
	public void setPatientService(PatientService patientService) {
		this.patientService = patientService;
	}
	
	@Autowired
	public void setUserService(UserService userService) {
		this.userService = userService;
	}
	
	@Transactional
	public void addImportLogRecord(final Patient patient, final String importLocation, final User importer) throws Exception {
		String insertSql = new StringBuilder("INSERT INTO ").append(TABLE)
		        .append("(date_imported, health_facility, patient_uuid, importer_username, importer_uuid, uuid)")
		        .append(" VALUES(?,?,?,?,?,?)").toString();
		
		PreparedStatement insertStatement = null;
		try {
			insertStatement = DatabaseUpdater.getConnection().prepareStatement(insertSql);
			insertStatement.setTimestamp(1, new Timestamp(new Date().getTime()));
			insertStatement.setString(2, importLocation);
			insertStatement.setString(3, patient.getUuid());
			insertStatement.setString(4, importer.getUsername());
			insertStatement.setString(5, importer.getUuid());
			insertStatement.setString(6, UUID.randomUUID().toString());
			insertStatement.execute();
			LOGGER.info("Saved import log record");
		}
		finally {
			if (insertStatement != null) {
				insertStatement.close();
			}
		}
	}
	
	@Transactional
	public List<Map<String, Object>> getImportLogs(final Integer startIndex, final Integer pageSize,
	        final String startDateImported, final String endDateImported, final String importerUuid) {
		StringBuilder selectSql = new StringBuilder("SELECT * FROM ").append(TABLE);
		StringBuilder conditionBuilder = new StringBuilder("WHERE ");
		if (StringUtils.isNotBlank(startDateImported)) {
			conditionBuilder.append("date_imported >= '").append(startDateImported).append("' AND ");
		}
		if(StringUtils.isNotBlank(endDateImported)) {
			conditionBuilder.append("date_imported < '").append(endDateImported).append("' AND ");
		}
		if(StringUtils.isNotBlank(importerUuid)) {
			conditionBuilder.append(" importer_uuid = '").append(importerUuid).append("'");
		}

		String condition = StringUtils.stripEnd(conditionBuilder.toString().trim(), "AND");

		//If condition is more than the original where word then include it.
		if(condition.length() > "WHERE".length()) {
			selectSql.append(" ").append(condition);
		}

		selectSql.append(" ORDER BY date_imported DESC");

		if(startIndex != null) {
			selectSql.append(" limit ").append(startIndex);
		}

		if(pageSize != null) {
			if(startIndex != null) {
				selectSql.append(", ").append(pageSize);
			} else {
				selectSql.append(" limit ").append(pageSize);
			}
		}

		Statement statement = null;
		List<Map<String, Object>> importLogs = new ArrayList<>();
		try {
			statement = DatabaseUpdater.getConnection().createStatement();
			ResultSet results = statement.executeQuery(selectSql.toString());
			while(results.next()) {
				Map<String, Object> importLog = new HashMap<>();
				Timestamp dateImported = results.getTimestamp("date_imported");
				if(dateImported != null) {
					importLog.put("dateImported",dateImported.toLocalDateTime());
				}
				importLog.put("healthFacility", results.getString("health_facility"));
				importLog.put("initiatorUsername", results.getString("importer_username"));

				String patientUuid = results.getString("patient_uuid");
				Patient importedPatient = patientService.getPatientByUuid(patientUuid);
				importLog.put("patient", importedPatient);
				PatientIdentifier patientNID = importedPatient.getPatientIdentifier(NID_TYPE_ID);
				if(patientNID != null) {
					importLog.put("patientNID", patientNID.getIdentifier());
				}
				importLog.put("initiator", userService.getUserByUuid(results.getString("importer_uuid")));
				importLogs.add(importLog);
			}
		} catch (SQLException e) {
            throw new RuntimeException(e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
			if (statement != null) {
                try {
                    statement.close();
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            }
		}
		return importLogs;
	}
	
	public List<Map<String, Object>> getAllImportLogs() {
		return getImportLogs(null, null, null, null, null);
	}
}
