<%@ include file="/WEB-INF/view/module/legacyui/template/include.jsp" %>

<openmrs:require privilege="View Patients" otherwise="/login.htm" redirect="/findPatient.htm" />

<openmrs:message var="pageTitle" code="esaudefeatures.remote.patients" scope="page"/>
<%@ include file="/WEB-INF/view/module/legacyui/template/header.jsp" %>

<%@ include file="/WEB-INF/view/module/esaudefeatures/remotePatients/_findRemotePatient.jsp" %>

<%@ include file="/WEB-INF/view/module/legacyui/template/footer.jsp" %>