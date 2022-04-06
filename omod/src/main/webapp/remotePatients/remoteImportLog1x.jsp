<%@ include file="/WEB-INF/template/include.jsp" %>

<openmrs:require privilege="View Patients" otherwise="/login.htm" redirect="/module/esaudefeatures/findRemotePatients.htm" />

<openmrs:message var="pageTitle" code="esaudefeatures.remote.import.log" scope="page"/>
<%@ include file="/WEB-INF/template/header.jsp" %>

<%@ include file="/WEB-INF/view/module/esaudefeatures/remotePatients/_remoteImportLog.jsp" %>


<%@ include file="/WEB-INF/template/footer.jsp" %>
