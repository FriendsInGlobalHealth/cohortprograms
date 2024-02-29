package org.openmrs.module.esaudefeatures;

import org.openmrs.Patient;
import org.openmrs.PatientIdentifier;
import org.openmrs.User;
import org.openmrs.util.DatabaseUpdater;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.Date;

/**
 * @uthor Willa Mhawila<a.mhawila@gmail.com> on 2/27/24.
 */
public class ImportLogUtils {
	
	protected static String TABLE = "esaudefeatures_rps_import_log";
	
	public static void addImportLogRecord(final Patient patient, final String importLocation, final User importer)
	        throws Exception {
		String insertSql = new StringBuilder("INSERT INTO ").append(TABLE)
		        .append("(date_imported, health_facility, patient_uuid, importer_username, importer_uuid)")
		        .append(" VALUES(?,?,?,?,?)").toString();
		
		PreparedStatement insertStatement = null;
		try {
			insertStatement = DatabaseUpdater.getConnection().prepareStatement(insertSql);
			insertStatement.setTimestamp(1, new Timestamp(new Date().getTime()));
			insertStatement.setString(2, importLocation);
			insertStatement.setString(3, patient.getUuid());
			insertStatement.setString(4, importer.getUsername());
			insertStatement.setString(5, importer.getUuid());
			insertStatement.execute();
		}
		finally {
			if (insertStatement != null) {
				insertStatement.close();
			}
		}
	}
}
