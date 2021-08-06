<%@ page import="org.openmrs.util.OpenmrsConstants" %>

<%@ include file="/WEB-INF/view/module/legacyui/template/include.jsp" %>

<openmrs:require privilege="View Patients" otherwise="/login.htm" redirect="/findPatient.htm" />

<openmrs:message var="pageTitle" code="esaudefeatures.remote.patients" scope="page"/>
<%@ include file="/WEB-INF/view/module/legacyui/template/header.jsp" %>

<h2><openmrs:message code="esaudefeatures.remote.patients.search"/></h2>

<br />

<%@ include file="/WEB-INF/view/module/esaudefeatures/remotePatients/_findRemotePatient.jsp" %>
<%--<%@ include file="/WEB-INF/view/module/legacyui/template/footer.jsp" %>--%>

<% if(OpenmrsConstants.OPENMRS_VERSION_SHORT.startsWith("1")) { %>
    <%@ include file="/WEB-INF/template/footer.jsp" %>
<% } else { %>
    <%@ include file="/WEB-INF/view/module/legacyui/template/footer.jsp" %>
<% } %>