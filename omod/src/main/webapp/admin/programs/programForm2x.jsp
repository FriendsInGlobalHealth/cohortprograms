<%@ include file="/WEB-INF/view/module/legacyui/template/include.jsp" %>

<openmrs:require privilege="Manage Programs" otherwise="/login.htm" redirect="/admin/programs/program.form" />

<%@ include file="/WEB-INF/view/module/legacyui/template/header.jsp" %>
<%@ include file="/WEB-INF/view/module/legacyui/admin/programs/localHeader.jsp" %>
<%@ include file="/WEB-INF/view/module/esaudefeatures/admin/programs/_programForm.jsp" %>
<%@ include file="/WEB-INF/view/module/legacyui/template/footer.jsp" %>