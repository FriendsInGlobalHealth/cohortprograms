<%@ include file="/WEB-INF/template/include.jsp" %>

<openmrs:require privilege="Manage Programs" otherwise="/login.htm" redirect="/admin/programs/program.form" />

<%@ include file="/WEB-INF/template/header.jsp" %>
<%@ include file="/WEB-INF/view/admin/programs/localHeader.jsp" %>
<%@ include file="/WEB-INF/view/module/cohortprograms/admin/programs/_programForm.jsp" %>
<%@ include file="/WEB-INF/template/footer.jsp" %>