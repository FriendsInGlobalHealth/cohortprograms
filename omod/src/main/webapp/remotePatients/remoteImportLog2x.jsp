<%@ include file="/WEB-INF/view/module/legacyui/template/include.jsp" %>

<openmrs:require privilege="View Patients" otherwise="/login.htm" redirect="/findPatient.htm" />

<openmrs:message var="pageTitle" code="eesaudefeatures.remote.import.log" scope="page"/>
<%@ include file="/WEB-INF/view/module/legacyui/template/header.jsp" %>

<%@ include file="/WEB-INF/view/module/esaudefeatures/remotePatients/_remoteImportLog.jsp" %>

<%@ include file="/WEB-INF/view/module/legacyui/template/footer.jsp" %>