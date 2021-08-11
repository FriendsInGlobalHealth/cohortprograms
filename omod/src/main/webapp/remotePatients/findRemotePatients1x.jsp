<%@ page import="org.openmrs.util.OpenmrsConstants" %>
<%@ include file="/WEB-INF/template/include.jsp" %>

<openmrs:require privilege="View Patients" otherwise="/login.htm" redirect="/module/esaudefeatures/findRemotePatients.htm" />

<openmrs:message var="pageTitle" code="esaudefeatures.remote.patients" scope="page"/>
<%@ include file="/WEB-INF/template/header.jsp" %>

<h2><openmrs:message code="esaudefeatures.remote.patients.search"/></h2>

<br />
<c:if test="${not empty remoteServerUrl}">
    <h3><openmrs:message code="esaudefeatures.remote.server.url" scope="page"/>: ${remoteServerUrl}</h3>
</c:if>
<c:if test="${authenticatedUser == null}">
    <h2>Auth is null, but why?</h2>
</c:if>
<c:if test="${authenticatedUser != null}">
    <openmrs:require privilege="View Patients" otherwise="/login.htm" redirect="/index.htm" />
    <style>
        #found-patients_wrapper{
            /* Removes the empty space after datatable if the table is short */
            /* Over ride the value set by datatables */
            min-height: 150px; height: auto !important;
        }
    </style>
    <openmrs:htmlInclude file="/scripts/jquery/dataTables/css/dataTables_jui.css"/>
    <openmrs:htmlInclude file="/scripts/jquery/dataTables/js/jquery.dataTables.min.js"/>

    <openmrs:globalProperty key="patient.listingAttributeTypes" var="attributesToList"/>

    <script type="text/javascript">
        var remoteServerUrl = "${remoteServerUrl}";
        var base64encodedCredos = "${remoteServerAuth}";
        var patientTable = null;
        var lastSearchedText = null;
        var foundPatientList = null;
        const MIN_SEARCH_LENGTH = 3;
        const EMPTY_COLUMN_HEADER_ID = 'empty-header-column';
        const DATE_DISPLAY_OPTIONS = { year: 'numeric', month: 'short', day: 'numeric' };

        var searchController = null;
        function searchPatientFromRemoteServer(searchText) {
            var requestHeaders = new Headers({
                'Content-Type': 'application/json',
                'Authorization': 'Basic ' + base64encodedCredos
            });

            var requestOptions = {
                method: 'GET',
                headers: requestHeaders,
                redirect: 'follow'
            };

            var searchUrl = patientSearchUrl() + "&q=" + searchText;
            if(searchController !== null) {
                searchController.abort();
            }
            searchController = new AbortController();
            requestOptions.signal = searchController.signal;
            fetch(searchUrl, requestOptions)
                .then(response => response.json())
                .then(data => {
                    // Generate table.
                    console.log("Here and data ni: ", data);
                    var results = [];
                    if(Array.isArray(data.results) && data.results.length > 0) {
                        foundPatientList = data.results;
                        results = mapResults(data.results);
                    }
                    refreshTable(patientTable, results);
                }).catch(error => console.log('error', error));
        }

        function patientSearchUrl() {
            var patientSearchUrl = remoteServerUrl;
            if(remoteServerUrl !== null && !remoteServerUrl.endsWith("/")) {
                patientSearchUrl += "/"
            }
            if(patientSearchUrl == null || patientSearchUrl == undefined) {
                console.log("Remote Server URL is not set");
                return;
            }

            return patientSearchUrl += "ws/rest/v1/patient?v=full"
        }

        function mapResults(results) {
            return results.map(result => {
                var mapped = [ result.uuid, result.identifiers[0].identifier, result.person.display, result.person.gender ];
                var birthDate = new Date(result.person.birthdate);
                mapped.push(birthDate.toLocaleDateString(undefined, DATE_DISPLAY_OPTIONS));
                mapped.push(result.person.age);
                if(result.person.preferredAddress) {
                    mapped.push(result.person.preferredAddress.cityVillage);
                } else if(Array.isArray(result.person.addresses && result.person.addresses.length > 0)) {
                    mapped.push(result.person.addresses[0].cityVillage);
                } else {
                    mapped.push('<openmrs:message code="esaudefeatures.remote.patients.no.address.found"/>');
                }
                return mapped;
            });
        }

        function insertDetailsColumnInResultsTable(oTable) {
            /*
             * Insert a 'details' column to the table
             */
            var nCloneTh = document.createElement('th');
            nCloneTh.setAttribute("id", EMPTY_COLUMN_HEADER_ID);

            var nCloneTd = document.createElement('td');
            nCloneTd.innerHTML = '<img src="${pageContext.request.contextPath}/moduleResources/esaudefeatures/images/details_open.png" />';
            nCloneTd.className = "center";

            $j('#found-patients thead tr').each(function () {
                this.insertBefore(nCloneTh, this.childNodes[1]);
            });

            $j('#found-patients tbody tr').each( function () {
                this.insertBefore(nCloneTd.cloneNode(true), this.childNodes[1] );
            });

            /* Add event listener for opening and closing details
             * Note that the indicator for showing which row is open is not controlled by DataTables,
             * rather it is done here
             */
            $j('#found-patients tbody td img').live('click', function () {
                var nTr = this.parentNode.parentNode;
                if ( this.src.match('details_close') ) {
                    /* This row is already open - close it */
                    this.src = "${pageContext.request.contextPath}/moduleResources/esaudefeatures/images/details_open.png";
                    oTable.fnClose(nTr);
                }
                else {
                    /* Open this row */
                    this.src = "${pageContext.request.contextPath}/moduleResources/esaudefeatures/images/details_close.png";
                    oTable.fnOpen(nTr, fnFormatDetails(patientTable, nTr), 'details' );
                }
            });
        }

        function refreshTable(oTable, data) {
            oTable.fnClearTable();
            oTable.fnAddData(data);

            oTable.fnDraw();

            if(Array.isArray(data) && data.length > 0) {
                insertDetailsColumnInResultsTable(oTable);
                oTable.fnSetColumnVis(0, false);
            } else {
                // Remove the added column in the header row if it already added.
                var emptyColumnHeader = document.getElementById(EMPTY_COLUMN_HEADER_ID);
                if(emptyColumnHeader !== null) {
                    emptyColumnHeader.parentNode.removeChild(emptyColumnHeader);
                }
            }
        }

        function fnFormatDetails ( oTable, nTr ) {
            var aData = oTable.fnGetData(nTr);
            var patientUuid = aData[0];
            var patient = foundPatientList.find(patient => patient.uuid === patientUuid);
            var sOut = '<table cellpadding="5" cellspacing="0" border="0" style="padding-left:10px; border-spacing: 5px;">';
            var patientName = patient.person.names[0];
            if(patient.person.preferredName) {
                patientName = patient.person.preferredName;
            }
            sOut += '<tr><td colspan="2" style="border-bottom: solid; border-top: solid;"><openmrs:message code="esaudefeatures.remote.patients.names"/></td></tr>';
            sOut += '<tr><td><openmrs:message code="esaudefeatures.remote.patients.givenName"/><td>' + patientName.givenName + '</td></tr>';
            sOut += '<tr><td><openmrs:message code="esaudefeatures.remote.patients.middleName"/><td>' + patientName.middleName + '</td></tr>';
            sOut += '<tr><td><openmrs:message code="esaudefeatures.remote.patients.familyName"/><td>' + patientName.familyName + '</td></tr>';

            sOut += '<tr><td colspan="2" style="border-bottom: solid; border-top: solid; margin-top:15px;"><openmrs:message code="esaudefeatures.remote.patients.identifiers"/></td></tr>';
            if(Array.isArray(patient.identifiers) && patient.identifiers.length > 0) {
                for(let identifier of patient.identifiers) {
                    sOut += '<tr><td>' + identifier.identifierType.display + ':</td><td>' + identifier.identifier + '</td>';
                }
            } else {
                sOut += '<tr><td colspan="2"><openmrs:message code="esaudefeatures.remote.patients.no.identifiers"/> </td></tr>';
            }

            sOut += '<tr><td colspan="2" style="border-bottom: solid; border-top:solid;"><openmrs:message code="esaudefeatures.remote.patients.attributes"/></td></tr>';
            if(Array.isArray(patient.person.attributes) && patient.person.attributes.length > 0) {
                for(let personAttribute of patient.person.attributes) {
                    sOut += '<tr><td>' + personAttribute.attributeType.display + ':</td><td>' + personAttribute.value + '</td>';
                }
            } else {
                sOut += '<tr><td colspan="2"><openmrs:message code="esaudefeatures.remote.patients.no.attributes"/></td></tr>';
            }
            sOut += '<tr><td>uuid:<td>' + patientUuid + '</td></tr>';
            sOut +='<tr><td colspan="2"><input type="button" value="Import patient" onclick="importPatient(\'' + patientUuid + '\')"/></td></tr>';
            sOut += '</table>';

            return sOut;
        }ß

        function importPatient(patientUuid) {
            console.log("Importing patient with uuid: " + patientUuid);
        }

        $j(document).ready(function() {
            patientTable = $j('#found-patients').dataTable({
                aaData: [],
                bFilter: false,
                aaSorting: [[2, 'asc']]
            });

            $j("#find-remote-patients").on('change keyup cut paste', function(e) {
                var searchText = $j(this).val();
                if(searchText !== null) searchText.trim();
                if(lastSearchedText === null || searchText !== lastSearchedText) {
                    if(searchText.length >= MIN_SEARCH_LENGTH) {
                        searchPatientFromRemoteServer(searchText);
                    } else if(lastSearchedText !== null) {
                        // Subsequent searches with text less than minimum length.
                        foundPatientList = null;
                        refreshTable(patientTable, []);
                    }
                    lastSearchedText = searchText;
                }
            });
        });

    </script>

    <div>
        <b class="boxHeader"><openmrs:message code="esaudefeatures.remote.patients.search"/></b>
        <div class="box">
            <openmrs:message code="esaudefeatures.remote.patients.search" javaScriptEscape="true"/>
            <input type="text" id="find-remote-patients"
                   placeholder="<openmrs:message code="esaudefeatures.remote.patients.search.placeholder" javaScriptEscape="true"/>"/>
            <table id="found-patients" class="display nowrap" style="width:100%">
                <thead>
                <tr>
                    <th></th>
                    <th>Identifier</th>
                    <th>Full name</th>
                    <th>Gender</th>
                    <th>Birth date</th>
                    <th>Age</th>
                    <th>Address</th>
                </tr>
                </thead>
                <tbody></tbody>
            </table>
        </div>
    </div>
</c:if>

<%@ include file="/WEB-INF/template/footer.jsp" %>
