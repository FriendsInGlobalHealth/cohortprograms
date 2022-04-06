<h2><openmrs:message code="esaudefeatures.remote.import.log"/></h2>

<c:if test="${authenticatedUser != null}">
    <openmrs:htmlInclude file="/moduleResources/esaudefeatures/bootstrap/bootstrap-3.7.7.min.css"/>
    <openmrs:htmlInclude file="/moduleResources/esaudefeatures/DataTables/datatables.min.css"/>
    <openmrs:htmlInclude file="/moduleResources/esaudefeatures/DataTables/datatables.min.js"/>
    <openmrs:htmlInclude file="/moduleResources/esaudefeatures/bootstrap/bootstrap-3.7.7.min.js"/>

    <script type="text/javascript">
        $j(document).ready(function() {
            $j('#imported-objects-table').DataTable();
        });
    </script>

    <div>
        <form method="GET" action="/module/esaudefeatures/remoteImportLog.htm">

        </form>
        <table id="imported-objects-table" class="table table-striped table-bordered" style="width:100%">
            <thead>
                <tr>
                    <th></th>
                    <th>Type</th>
                    <th>Importer</th>
                    <th>Date Imported</th>
                    <th>Object UUID</th>
                </tr>
            </thead>
            <tbody>
                <c:if test="${not empty importedObjects}">
                    <c:forEach var="importedObject" items="importedObjects" varStatus="loop">
                        <tr>
                            <td>${loop.count}</td>
                            <td>${importedObject.type}</td>
                            <td>${importedObject.importer.username}</td>
                            <td>${importedObject.dateImported}</td>
                            <td>${importedObject.objectUuid}</td>
                        </tr>
                    </c:forEach>
                </c:if>
            </tbody>
        </table>
    </div>
</c:if>