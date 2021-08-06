<%@ page import="org.openmrs.util.OpenmrsConstants" %>
<%@ include file="/WEB-INF/template/include.jsp" %>

<openmrs:require privilege="View Patients" otherwise="/login.htm" redirect="/module/esaudefeatures/findRemotePatients.htm" />

<openmrs:message var="pageTitle" code="esaudefeatures.remote.patients" scope="page"/>
<%@ include file="/WEB-INF/template/header.jsp" %>

<h2><openmrs:message code="esaudefeatures.remote.patients.search"/></h2>

<br />
<openmrs:portlet moduleId="esaudefeatures" url="findRemotePatient1x" parameters="size=full|postURL=patientDashboard.form|showIncludeVoided=false|viewType=shortEdit" />
<%--<openmrs:portlet  url="findPatient" parameters="size=full|postURL=patientDashboard.form|showIncludeVoided=false|viewType=shortEdit" />--%>

<openmrs:extensionPoint pointId="org.openmrs.findPatient" type="html" />

<%@ include file="/WEB-INF/template/footer.jsp" %>
