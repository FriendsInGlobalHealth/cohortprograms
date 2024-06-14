<h2><openmrs:message code="esaudefeatures.remote.patients.search"/></h2>
<div id="openmrs_msg" style="visibility:hidden;"><openmrs:message code="esaudefeatures.remote.patients.imported"/></div>
<div id="remote_patient_error_msg" class="error" style="visibility:hidden;">
    <openmrs:message code="esaudefeatures.remote.patients.import.error"/>
</div>

<br />
<c:if test="${not empty remoteServerUrl}">
    <h3><openmrs:message code="esaudefeatures.remote.server.url" scope="page"/>: ${remoteServerUrl}</h3>
</c:if>
<openmrs:require privilege="View Patients" otherwise="/login.htm" redirect="/index.htm" />
<style>
    #found-patients_wrapper {
        /* Removes the empty space after datatable if the table is short */
        /* Over ride the value set by datatables */
        min-height: 150px; height: auto !important;
    }

    ul {
        list-style: none outside none;
        padding-left: inherit;
        padding-inline-start: 0px;
        margin-top: 0px;
    }

    table tbody td.details-cell {
        vertical-align: top;
    }
</style>
<openmrs:htmlInclude file="/scripts/jquery/dataTables/css/dataTables_jui.css"/>
<openmrs:htmlInclude file="/scripts/jquery/dataTables/js/jquery.dataTables.min.js"/>

<openmrs:globalProperty key="patient.listingAttributeTypes" var="attributesToList"/>

<style>
    div {
        margin-bottom: 1em;
    }

    table.vertical_table {
        margin-top: 10px;
    }

    table.vertical_table, table.vertical_table > * > tr > th, table.vertical_table > * > tr > td {
        border: 1px solid black;
        border-collapse: collapse;
        text-align: left;
        padding: 0.5em;
        width: 100%;
    }

    table.vertical_table > * > tr > th {
        width: fit-content;
        white-space:nowrap;
    }
</style>

<script type="text/javascript">
    var localOpenmrsContextPath = '${pageContext.request.contextPath}';
    var importedPatientLocationUuid = "${importedPatientLocationUuid}";
    var patientTable = null;
    var foundPatientList = null;
    var opencrMergedPatients = {};
    const DATE_DISPLAY_OPTIONS = '%d-%b-%Y';
    const IMPORT_SUCCESS_MSG_PREFIX = $j('#openmrs_msg').html();
    const IMPORT_ERROR_MSG_PREFIX = $j('#remote_patient_error_msg').html();
    const ERROR_DURING_SEARCH_MSG_PREFIX = '<openmrs:message code="esaudefeatures.remote.patients.search.error"/>'
    const IMPORT_CONFIRM_MSG_PREFIX = '<openmrs:message code="esaudefeatures.remote.patients.import.confirmation"/>';
    const REMOTE_SERVER_TYPE = "${remoteServerType}";
    const FHIR_IDENTIFIER_SYS_MAPPINGS_STRING = `${fhirIdentifierSystemMappings}`;
    const NID_TARV_IDENTIFIER_TYPE_UUID = 'e2b966d0-1d5f-11e0-b929-000c29ad1d07';
    const OPENMRS_PERSON_UUID_FHIR_SYSTEM_VALUE = "${openmrsPersonUuidFhirSystemValue}";
    const ART_TREATMENT_PROGRAM_UUID = 'efe2481f-9e75-4515-8d5a-86bfde2b5ad3';
    const PREP_PROGRAM_UUID = 'ac7c5d2b-854a-48c4-a68f-0b8a92e11f4a';
    const ART_START_DATE_CONCEPT_UUID = 'e1d8f690-1d5f-11e0-b929-000c29ad1d07';
    const PHARMACY_ENCOUNTER_TYPE_UUID = 'e279133c-1d5f-11e0-b929-000c29ad1d07';

    const FHIR_IDENTIFIER_SYS_MAPPINGS = {};
    FHIR_IDENTIFIER_SYS_MAPPINGS[OPENMRS_PERSON_UUID_FHIR_SYSTEM_VALUE] = "OpenMRS Internal UUID";
    let NID_SYSTEM_VALUE;
    if(FHIR_IDENTIFIER_SYS_MAPPINGS_STRING.length > 0) {
        FHIR_IDENTIFIER_SYS_MAPPINGS_STRING.split(',').forEach(component => {
            let parts = component.trim().split('^');
            FHIR_IDENTIFIER_SYS_MAPPINGS[parts[1]] = parts[0];
            if(parts[0] === NID_TARV_IDENTIFIER_TYPE_UUID) {
                NID_SYSTEM_VALUE = parts[1];
                FHIR_IDENTIFIER_SYS_MAPPINGS[parts[1]] = "NID (SERVICO TARV)";
            }
        });
    }

    class HttpError extends Error {
        constructor(response) {
            super(`${response.status} for ${response.url}`);
            this.name = 'HttpError';
            this.response = response;
        }
    }

    var searchController = null;
    var patientProgramsSearchController = null;
    var artStartDateFetchController = null;
    var lastArtPickupFetchController = null;

    function searchErrorHandler(error) {
        var __doStuffWithError = function(error) {
            $j('#search-busy-gif').css("visibility", "hidden");
            $j("#find-remote-patients-button").prop('disabled', false);
            if(error.includes('Failed to connect to') || error.includes('java.net.SocketTimeoutException') ||
                error.includes('java.net.SocketException')) {
                $j('#dialog').dialog('open');
            } else {
                $j('#remote_patient_error_msg').html(ERROR_DURING_SEARCH_MSG_PREFIX + ': ' + error);
                $j('#remote_patient_error_msg').css('visibility', 'visible');
            }
        };
        if(typeof error === 'object' && error.name !== 'AbortError') {
            if(error instanceof HttpError) {
                error.response.text().then(message => {
                    console.log('error', message);
                    __doStuffWithError(message);
                })
            } else {
                console.log('error', error);
                __doStuffWithError(error.message);
            }
        } else {
            console.log('error', error);
            if(!(typeof error === 'object' && error.name === 'AbortError')) {
                __doStuffWithError(error);
            }
        }
    }

    function searchExtraInfo(patient, patientUuid) {
        var __fetchAndUpdateInfo = function(url, infoStore, requestController, infoUpdaterCallback) {
            var requestOptions = {
                method: 'GET',
                headers:  new Headers({
                    'Content-Type': 'application/json',
                    'Accept': 'application/json'
                }),
            };

            if(requestController !== null) {
                requestController.abort();
            }
            requestController = new AbortController();
            requestOptions.signal = requestController.signal;

            fetch(url, requestOptions)
                .then(response => {
                    if(response.status !== 200) {
                        throw new HttpError(response);
                    } else {
                        return response.json()
                    }
                })
                .then(data => {
                    if(Array.isArray(data) && data.length > 0) {
                        infoStore = data;
                    } else if(data['results'] && Array.isArray((data['results'])) && data['results'].length > 0) {
                        infoStore = data['results'];
                    }
                    infoUpdaterCallback(patientUuid, infoStore);
                }).catch(error => {
                searchErrorHandler(error);
            });
        };

        var programsUrl = localOpenmrsContextPath + "/module/esaudefeatures/openmrsRemoteGetRequest.json?patient=" + patientUuid;
        programsUrl += '&resource=programenrollment&v=custom:(uuid,dateEnrolled,dateCompleted,program:(uuid,name),'
        programsUrl += 'location:(uuid,name,address6,parentLocation:(uuid,name))';
        __fetchAndUpdateInfo(programsUrl, patient.programInfo, patientProgramsSearchController,insertProgramInfoInDetailsColumn);

        var artStartDateUrl = localOpenmrsContextPath + "/module/esaudefeatures/openmrsRemoteGetRequest.json?patient=" + patientUuid;
        artStartDateUrl += '&resource=obs&limit=1&v=custom:(uuid,value)&concepts=' + ART_START_DATE_CONCEPT_UUID;
        __fetchAndUpdateInfo(artStartDateUrl, patient.artDrugStartDateInfo, artStartDateFetchController,insertArtStartDateInDetailsColumn);

        var lastArtDrugPickupUrl = localOpenmrsContextPath + "/module/esaudefeatures/openmrsRemoteGetRequest.json?patient=" + patientUuid;
        lastArtDrugPickupUrl += '&resource=encounter&v=custom:(uuid,encounterDatetime,location:(uuid,name)&order=desc&limit=1';
        lastArtDrugPickupUrl += '&encounterType=' + PHARMACY_ENCOUNTER_TYPE_UUID;
        __fetchAndUpdateInfo(lastArtDrugPickupUrl, patient.lastArtDrugPickupInfo, lastArtPickupFetchController,insertLastArtPickupInDetailsColumn);
    }

    function searchPatientsFromRemoteServer(searchText, matchMode) {
        $j('#search-busy-gif').css("visibility", "visible");
        $j("#find-remote-patients-button").prop('disabled', true);
        var requestHeaders = new Headers({
            'Content-Type': 'application/json',
            'Accept': 'application/json'
        });

        var requestOptions = {
            method: 'GET',
            headers: requestHeaders,
        };

        var patientSearchUrl = localOpenmrsContextPath + "/module/esaudefeatures/";
        if(REMOTE_SERVER_TYPE === 'OPENMRS') {
            patientSearchUrl += 'openmrsRemotePatients.json?text=' + searchText + '&matchMode=' + matchMode;
        } else {
            patientSearchUrl += 'fhirRemotePatients.json?text=' + searchText + '&matchMode=' + matchMode;
        }

        if(searchController !== null) {
            searchController.abort();
        }
        searchController = new AbortController();
        requestOptions.signal = searchController.signal;
        fetch(patientSearchUrl, requestOptions)
            .then(response => {
                if(response.status !== 200) {
                    throw new HttpError(response);
                } else {
                    return response.json()
                }
            })
            .then(data => {
                // Generate table.
                var results = [];
                if(REMOTE_SERVER_TYPE === 'OPENMRS') {
                    if (Array.isArray(data) && data.length > 0) {
                        foundPatientList = data;
                        results = mapResults(data);
                    }
                } else {
                    if(Array.isArray(data.entry) && data.entry.length > 0) {
                        opencrMergedPatients = findMergedPatientsAndRemoveThemForOpencr(data.entry);
                        foundPatientList = data.entry;
                        results = mapResults(data.entry);
                    }
                }
                refreshTable(patientTable, results);
                $j('#search-busy-gif').css("visibility", "hidden");
                $j("#find-remote-patients-button").prop('disabled', false);
            }).catch(error => {
                searchErrorHandler(error);
            });
    }

    function findMergedPatientsAndRemoveThemForOpencr(entries) {
        var _findReferLink = (links) => {
            return links.find(link => link.type === 'refer')
        };

        var mergedEntries = {};
        for(let i=0; i < entries.length; i++) {
            mergedEntries[entries[i].resource.id] = [];
            for(let j = i+1; j < entries.length; j++) {
                let leftRecordReferLink = _findReferLink(entries[i].resource.link);
                let rightRecordReferLink = _findReferLink(entries[j].resource.link);
                if(leftRecordReferLink && rightRecordReferLink &&
                    leftRecordReferLink['other']['reference'] === rightRecordReferLink['other']['reference']) {
                    mergedEntries[entries[i].resource.id].push(entries[j]);
                    entries.splice(j--, 1);
                }
            }
        }
        return mergedEntries;
    }

    function mapResults(results) {
        if(REMOTE_SERVER_TYPE === 'OPENCR' || REMOTE_SERVER_TYPE === 'SANTEMPI') {
            var _age = (birthdate) => {
                var now = new Date();
                var curYear = now.getFullYear();
                var curMonth = now.getMonth();
                var curDate = now.getDate();

                var birthYear = birthdate.getFullYear();
                var birthMonth = birthdate.getMonth();
                var birthday = birthdate.getDate();

                var age = curYear - birthYear;
                if(birthMonth > curMonth || (birthMonth == curMonth && birthday > curDate)) {
                    return age - 1;
                }

                return age;
            };
            var _fullname = (namesArray) => {
                // Pick the first one for now.
                if(!Array.isArray(namesArray) || namesArray.length === 0) return "No Name Found";
                var fullname = '';
                if(Array.isArray(namesArray[0].given) && namesArray[0].given.length > 0) {
                    fullname = namesArray[0].given.join(' ');
                }
                fullname += ' ' + namesArray[0].family;
                return fullname;
            };
            var _address = (addressArray) => {
                // Pick the first one for now.
                var address = '<openmrs:message code="esaudefeatures.remote.patients.no.address.found"/>';
                if(!Array.isArray(addressArray) || addressArray.length === 0) return address;
                if(Array.isArray(addressArray[0].line) && addressArray[0].line.length > 0) {
                    return addressArray[0].line.join(' - ');
                }

                if(addressArray[0].district && addressArray[0].state) {
                    return addressArray[0].district + ' - ' + addressArray[0].state;
                }
                return address;
            };

            var openButtonImageLink = '<img src="${pageContext.request.contextPath}/moduleResources/esaudefeatures/images/details_open.png"/>';
            return results.map(result => {
                var NID = result.resource.identifier.find(ident => ident.system === NID_SYSTEM_VALUE);
                var NIDDisplay = '';
                if(NID) {
                    NIDDisplay = NID.value;
                }
                var openmrsUuid = result.resource.identifier.find(ident => ident.system === OPENMRS_PERSON_UUID_FHIR_SYSTEM_VALUE);
                var openmrsUuidDisplay = openmrsUuid ? openmrsUuid.value : '';
                var mapped = [openmrsUuidDisplay, openButtonImageLink, '', NIDDisplay, _fullname(result.resource.name), result.resource.gender];
                var birthDate = new Date(result.resource.birthDate);
                mapped.push(birthDate.toLocaleDateString('pt', DATE_DISPLAY_OPTIONS));
                mapped.push(_age(birthDate));
                mapped.push(_address(result.resource.address));

                // put the full name and openmrsUuid in the opencr resource
                result.resource.openmrsUuid = openmrsUuid.value;
                result.resource.fullname = _fullname(result.resource.name);

                return mapped;
            });
        } else {
            //OPENMRS
            var _determineAddressToDisplay = function (address) {
                var displayedAddress = '<openmrs:message code="esaudefeatures.remote.patients.no.address.found"/>';
                if (address.address1 && address.address5) {
                    displayedAddress = address.address1 + ' - ' + address.address5;
                } else if (address.display) {
                    displayedAddress = address.display;
                }
                return displayedAddress;
            };

            return results.map(result => {
                var mapped = [result.uuid, result.identifiers[0].identifier, result.person.display, result.person.gender];
                var birthDate = new Date(result.person.birthdate);
                mapped.push(birthDate.toLocaleDateString('pt', DATE_DISPLAY_OPTIONS));
                mapped.push(result.person.age);
                if (result.person.preferredAddress) {
                    mapped.push(_determineAddressToDisplay(result.person.preferredAddress));
                } else if (Array.isArray(result.person.addresses && result.person.addresses.length > 0)) {
                    mapped.push(_determineAddressToDisplay(result.person.addresses[0]));
                } else {
                    mapped.push('<openmrs:message code="esaudefeatures.remote.patients.no.address.found"/>');
                }
                return mapped;
            });
        }
    }

    function insertArtStartDateInDetailsColumn(patientUuid, artDrugStartDateInfo) {
        var programInfoSection = $j('#program-info-' + patientUuid);
        if(Array.isArray(artDrugStartDateInfo) && artDrugStartDateInfo.length > 0) {
            var artDrugStartDate = '<openmrs:message code="esaudefeatures.remote.patients.no.value"/>';
            if (artDrugStartDateInfo[0].value !== null && artDrugStartDateInfo[0].value !== undefined) {
                artDrugStartDate = new Date(artDrugStartDateInfo[0].value).toLocaleDateString('pt', DATE_DISPLAY_OPTIONS);
            }
            programInfoSection.append('<ul><li><em><openmrs:message code="esaudefeatures.remote.patients.art.drug.start.date"/>:</em> ' + artDrugStartDate + '</li></ul>');
        } else {
            programInfoSection.append('<ul><li><em><openmrs:message code="esaudefeatures.remote.patients.art.drug.start.date"/>:</em> '
                + '<openmrs:message code="esaudefeatures.remote.patients.information.not.found"/></li></ul>');
        }
    }

    function insertLastArtPickupInDetailsColumn(patientUuid, lastArtPickupInfo) {
        var programInfoSection = $j('#program-info-' + patientUuid);
        if(Array.isArray(lastArtPickupInfo) && lastArtPickupInfo.length > 0) {
            var lastArtPickupDate = '<openmrs:message code="esaudefeatures.remote.patients.no.value"/>';
            if (lastArtPickupInfo[0].encounterDatetime !== null && lastArtPickupInfo[0].encounterDatetime !== undefined) {
                lastArtPickupDate = new Date(lastArtPickupInfo[0].encounterDatetime).toLocaleDateString('pt', DATE_DISPLAY_OPTIONS);
            }
            programInfoSection.append('<ul><li><em><openmrs:message code="esaudefeatures.remote.patients.last.art.drug.pickup.date"/>:</em> ' + lastArtPickupDate + '</li></ul>');
            if(typeof lastArtPickupInfo[0].location === 'object') {
                programInfoSection.append('<ul><li><em><openmrs:message code="esaudefeatures.remote.patients.last.art.drug.pickup.location"/>:</em> ' + lastArtPickupInfo[0].location.name + '</li></ul>');
            }
        } else {
            programInfoSection.append('<ul><li><em><openmrs:message code="esaudefeatures.remote.patients.last.art.drug.pickup.date"/>:</em> '
                + '<openmrs:message code="esaudefeatures.remote.patients.information.not.found"/></li></ul>');
        }
    }

    function insertProgramInfoInDetailsColumn(patientUuid, patientPrograms) {
        if(Array.isArray(patientPrograms) && patientPrograms.length > 0) {
            var programInfoSection =  $j('#program-info-' + patientUuid);
            programInfoSection.html('');

            var artProgram = patientPrograms.find(program => program.program.uuid == ART_TREATMENT_PROGRAM_UUID);
            var programInfoAvailable = false;
            if(artProgram) {
                var artDateEnrolled = new Date(artProgram.dateEnrolled).toLocaleDateString('pt', DATE_DISPLAY_OPTIONS);
                programInfoSection.append('<ul><li><em><openmrs:message code="esaudefeatures.remote.patients.art.enrollment.date"/>:</em> ' + artDateEnrolled + '</li>');
                if(artProgram['location']) {
                    programInfoSection.append('<ul><li><em><openmrs:message code="esaudefeatures.remote.patients.art.enrollment.location"/>:</em>&nbsp;'
                    + artProgram['location']['name'] + '</li></ul>');
                }
                programInfoAvailable = true;
            }
            var prepProgram =  patientPrograms.find(program => program.program.uuid == PREP_PROGRAM_UUID);
            if(prepProgram) {
                var prepDateEnrolled = new Date(prepProgram.dateEnrolled).toLocaleDateString('pt', DATE_DISPLAY_OPTIONS);
                programInfoSection.append('<ul><li><em><openmrs:message code="esaudefeatures.remote.patients.prep.enrollment.date"/>:</em> ' + prepDateEnrolled + '</li></ul>');
                if(prepProgram['location']) {
                    programInfoSection.append('<ul><li><em><openmrs:message code="esaudefeatures.remote.patients.prep.enrollment.location"/>:</em> '
                    + prepProgram['location']['name'] + '</li></ul>');
                }
                programInfoAvailable = true;
            }

            if(!programInfoAvailable) {
                programInfoSection.append('<em><openmrs:message code="esaudefeatures.remote.patients.programInfo.not.found"/></em>');
            }
        } else {
            $j('#program-info-' + patientUuid).html('<em><openmrs:message code="esaudefeatures.remote.patients.programInfo.not.found"/></em>');
        }
    }

    function refreshTable(oTable, data) {
        oTable.fnClearTable();
        oTable.fnAddData(data);
    }

    function createPatientDetailsHtmlTable(patient, title, addImportButton, disableImportButton) {
        var __determineAttributeDisplayValue = function(attributeValue) {
            if(typeof attributeValue !== 'string' && typeof attributeValue === 'object' && attributeValue.display) {
                return attributeValue.display;
            }
            return attributeValue;
        }

        var patientDetailsTable = '<table cellpadding="5" cellspacing="0" border="0" style="display: inline; border-spacing: 5px; border-collapse: collapse;">';
        var patientName = patient.person.names[0];
        if(patient.person.preferredName) {
            patientName = patient.person.preferredName;
        }

        if(title) {
            patientDetailsTable += '<tr><td colspan="3"><em><strong>' + title + '</strong></em></td></tr>';
        }
        patientDetailsTable += '<tr style="border-bottom: solid;"><td><openmrs:message code="esaudefeatures.remote.patients.names"/></td>';
        patientDetailsTable += '<td><openmrs:message code="esaudefeatures.remote.patients.identifiers"/></td>';
        patientDetailsTable += '<td><openmrs:message code="esaudefeatures.remote.patients.attributes"/></td></tr>';

        var givenName = patientName.givenName === null ? "" : patientName.givenName;
        var middleName = patientName.middleName === null ? "" : patientName.middleName;
        var familyName = patientName.familyName === null ? "" : patientName.familyName;

        // Names column
        patientDetailsTable += '<tr><td class="details-cell"><ul><li><em><openmrs:message code="esaudefeatures.remote.patients.givenName"/>:</em> ' + givenName + '</li></ul>';
        patientDetailsTable += '<ul><li><em><openmrs:message code="esaudefeatures.remote.patients.middleName"/>:</em> ' + middleName + '</li></ul>';
        patientDetailsTable += '<ul><li><em><openmrs:message code="esaudefeatures.remote.patients.familyName"/>:</em> ' + familyName + '</li></ul></td>';

        // Identifiers column
        patientDetailsTable += '<td class="details-cell">';
        if(Array.isArray(patient.identifiers) && patient.identifiers.length > 0) {
            for(let identifier of patient.identifiers) {
                patientDetailsTable += '<ul><li><em>' + identifier.identifierType.display + ':</em> ' + identifier.identifier + '</li></ul>';
            }
            patientDetailsTable += '</td>';
        } else {
            patientDetailsTable += '<openmrs:message code="esaudefeatures.remote.patients.no.identifiers"/></td>';
        }

        // Other info
        patientDetailsTable += '<td class="details-cell">';
        if(Array.isArray(patient.person.attributes) && patient.person.attributes.length > 0) {
            for(let personAttribute of patient.person.attributes) {
                patientDetailsTable += '<ul><li><em>' + personAttribute.attributeType.display + ':</em> ' + __determineAttributeDisplayValue(personAttribute.value) + '</li></ul>';
            }
            patientDetailsTable += '</td></tr>';
        } else {
            patientDetailsTable += '<openmrs:message code="esaudefeatures.remote.patients.no.attributes"/></td></tr>';
        }
//            patientDetailsTable += '<tr><td>uuid:</td><td colspan="2">' + patient.uuid + '</td></tr>';

        if(addImportButton) {
            if(disableImportButton) {
                patientDetailsTable += '<tr><td colspan="3" style="align:right;"><input type="button" name="import-button-' + patient.uuid + '" disabled value="' +
                    '<openmrs:message code="esaudefeatures.remote.patients.remote.import.patient"/>' +
                    '" onclick="importPatient(\'' + patient.uuid + '\')"/></td></tr>';
            } else {
                patientDetailsTable += '<tr><td colspan="3" style="align:right;"><input type="button" id="import-button-' + patient.uuid + '" value="' +
                    '<openmrs:message code="esaudefeatures.remote.patients.remote.import.patient"/>' +
                    '" onclick="importPatient(\'' + patient.uuid + '\', this.id)"/>' +
                        '<span id="patience-message-' + patient.uuid + '" style="visibility:hidden;">(<openmrs:message code="esaudefeatures.remote.patients.remote.import.bePatient"/>)</span>' +
                    '<img class="import-busy-gif" src="${pageContext.request.contextPath}/moduleResources/esaudefeatures/images/loading.gif" style="visibility:hidden;"/></td></tr>';
            }
        }

        patientDetailsTable += '</table>';

        return patientDetailsTable;
    }

    function createPatientDetailsHtmlTableForMPI(patient, title, addImportButton, disableImportButton) {
        var _createMergedPatientsTables = (mergePatients) => {
            var tables = '';
            mergePatients.forEach(patient => {
                var mergedPatientDetailsTable = '<div style="float:left; border:2.5px solid rgba(64,158,24,0.98); background-color: #c4ffca">';
                mergedPatientDetailsTable += '<table cellpadding="5" cellspacing="0" border="0" style="display: inline; padding-left:10px; border-spacing: 5px; border-collapse: collapse;">';

                mergedPatientDetailsTable += '<tr><td colspan="2"><strong><openmrs:message code="esaudefeatures.remote.patients.matched.record"/></strong></td></tr>';
                var givenNames = '';
                if(Array.isArray(patientName.given) && patientName.given.length > 0) {
                    givenNames = patientName.given.join(' ');
                }
                var familyName = patientName.family === null ? '' : patientName.family;
                mergedPatientDetailsTable += '<tr><td colspan="2" style="border-bottom: solid; border-top: solid;">';
                mergedPatientDetailsTable += '<openmrs:message code="esaudefeatures.remote.patients.names"/></td></tr>';
                mergedPatientDetailsTable += '<tr><td><openmrs:message code="esaudefeatures.remote.patients.givenNames"/></td><td>' + givenNames + '</td></tr>';
                mergedPatientDetailsTable += '<tr><td><openmrs:message code="esaudefeatures.remote.patients.familyName"/></td><td>' + familyName + '</td></tr>';
                mergedPatientDetailsTable += '<tr><td colspan="2" style="border-bottom: solid; border-top: solid; margin-top:15px;">';
                mergedPatientDetailsTable += '<openmrs:message code="esaudefeatures.remote.patients.identifiers"/></td></tr>';
                if(Array.isArray(patient.resource.identifier) && patient.resource.identifier.length > 0) {
                    for(let identifier of patient.resource.identifier) {
                        mergedPatientDetailsTable += '<tr><td>' + FHIR_IDENTIFIER_SYS_MAPPINGS[identifier.system] + ':</td><td>' + identifier.value + '</td>';
                    }
                } else {
                    mergedPatientDetailsTable += '<tr><td colspan="2"><openmrs:message code="esaudefeatures.remote.patients.no.identifiers"/> </td></tr>';
                }
                <%--mergedPatientDetailsTable += '<tr><td><openmrs:message code="esaudefeatures.remote.opencr.record.id"/></td><td>' + patient.resource.id + '</td></tr>';--%>
                mergedPatientDetailsTable += '</table></div>';

                tables += mergedPatientDetailsTable;
            });
            return tables;
        };

        var patientDetailsTable = '<table cellpadding="5" cellspacing="0" border="0" style="display: inline; border-spacing: 5px; border-collapse: collapse;">';
        var patientName = patient.resource.name[0];

        if(title) {
            patientDetailsTable += '<tr><td colspan="4"><em><strong>' + title + '</strong></em></td></tr>';
        }

        patientDetailsTable += '<tr style="border-bottom: solid;"><td><openmrs:message code="esaudefeatures.remote.patients.names"/></td>';
        patientDetailsTable += '<td><openmrs:message code="esaudefeatures.remote.patients.identifiers"/></td>';
        patientDetailsTable += '<td><openmrs:message code="esaudefeatures.remote.patients.programInfo"/></td>';
        patientDetailsTable += '<td><openmrs:message code="esaudefeatures.remote.patients.contactInfo"/></td></tr>';

        var givenNames = '';
        if(Array.isArray(patientName.given) && patientName.given.length > 0) {
            givenNames = patientName.given.join(' ');
        }
        var familyName = patientName.family === null ? '' : patientName.family;

        // Names column
        patientDetailsTable += '<tr><td class="details-cell"><ul><li><em><openmrs:message code="esaudefeatures.remote.patients.givenNames"/>:</em> ' + givenNames + '</li></ul>';
        patientDetailsTable += '<ul><li><em><openmrs:message code="esaudefeatures.remote.patients.familyName"/>:</em> ' + familyName + '</li></ul></td>';

        // Patient identifiers
        patientDetailsTable += '<td class="details-cell">';
        if(Array.isArray(patient.resource.identifier) && patient.resource.identifier.length > 0) {
            for(let identifier of patient.resource.identifier) {
                if(FHIR_IDENTIFIER_SYS_MAPPINGS[identifier.system] !== undefined && OPENMRS_PERSON_UUID_FHIR_SYSTEM_VALUE !== identifier.system) {
                    patientDetailsTable += '<ul><li><em>' + FHIR_IDENTIFIER_SYS_MAPPINGS[identifier.system] + ':</em>' + identifier.value + '</li></ul>';
                }
            }
        } else {
            patientDetailsTable += '<ul><li><openmrs:message code="esaudefeatures.remote.patients.no.identifiers"/></li></ul>';
        }
        patientDetailsTable += '</td>';

        // Program Info
        var UUID = patient.resource.identifier.find(ident => ident.system === OPENMRS_PERSON_UUID_FHIR_SYSTEM_VALUE);

        patientDetailsTable += '<td class="details-cell"><span id="program-info-' + UUID.value + '">';
        patientDetailsTable += '<em><openmrs:message code="esaudefeatures.remote.patients.fetching.program.enrollment"/>...</em></span></td>';
        if(patient.programInfo || patient.artDrugStartDateInfo || patient.lastArtDrugPickupInfo) {
            // A hack to wait for the DOM to be updated with details page.
            setTimeout(function () {
                insertProgramInfoInDetailsColumn(UUID.value, patient.programInfo);
                insertArtStartDateInDetailsColumn(UUID.value, patient.artDrugStartDateInfo);
                insertLastArtPickupInDetailsColumn(UUID.value, patient.lastArtDrugPickupInfo);
            }, 500);
        } else {
            searchExtraInfo(patient, UUID.value);
        }

        // Contact info
        patientDetailsTable += '<td class="details-cell">';
        if(Array.isArray(patient.resource.telecom) && patient.resource.telecom.length > 0) {
            patientDetailsTable += '<ul>';
            for(let contact of patient.resource.telecom) {
                var label = contact.system ? contact.system : '';
                var useLabel = contact.use ? contact.use : '';
                label += label.length > 0 && useLabel.length > 0 ? '/' + useLabel : useLabel;
                if(label.length === 0) {
                    label = 'Contact';
                }
                patientDetailsTable += '<li><em>' + label + ':</em> ' + contact.value + '</li>';
            }
            patientDetailsTable += '</ul>';
        }

        if(addImportButton) {
            if(disableImportButton) {
                patientDetailsTable += '<tr><td colspan="2"><input type="button" name="import-button-' + patient.uuid + '" disabled value="' +
                    '<openmrs:message code="esaudefeatures.remote.patients.remote.import.patient"/>' +
                    '" onclick="importPatient(\'' + patient.resource.id + '\')"/></td></tr>';
            } else {
                patientDetailsTable += '<tr><td colspan="2"><input type="button" id="import-button-' + patient.uuid + '" value="' +
                    '<openmrs:message code="esaudefeatures.remote.patients.remote.import.patient"/>' +
                    '" onclick="importPatient(\'' + patient.resource.id + '\', this.id)"/>' +
                    '<span id="patience-message-' + patient.resource.id + '" style="visibility:hidden;">(<openmrs:message code="esaudefeatures.remote.patients.remote.import.bePatient"/>)</span>' +
                    '<img class="import-busy-gif" src="${pageContext.request.contextPath}/moduleResources/esaudefeatures/images/loading.gif" style="visibility:hidden;"/></td></tr>';
            }
        }

        patientDetailsTable += '</table>';

        if(Array.isArray(opencrMergedPatients[patient.resource.id]) && opencrMergedPatients[patient.resource.id].length > 0) {
            //Display merged records information.
            patientDetailsTable += _createMergedPatientsTables(opencrMergedPatients[patient.resource.id]);
        }

        return patientDetailsTable;
    }

    function importPatient(patientUuid, pressedButtonId) {
        var _importWork = (patientName, importPatientUrl) => {
            var confirmationMessage = IMPORT_CONFIRM_MSG_PREFIX + "Patient: " + patientName;
            var confirmed = window.confirm(confirmationMessage);

            if (confirmed) {
                console.log("Importing patient with uuid: " + patientUuid);
                var pressedButton = document.getElementById(pressedButtonId);
                var busyGifImgs = document.getElementsByClassName('import-busy-gif');
                var patienceMessage = document.getElementById('patience-message-' + patientUuid);
                $j(patienceMessage).css('visibility', 'visible');
                $j(busyGifImgs).css('visibility', 'visible');
                $j(pressedButton).prop('disabled', true);

                $j('#openmrs_msg').css('visibility', 'hidden');
                $j('#openmrs_msg').html(IMPORT_SUCCESS_MSG_PREFIX + ' (' + patientName + ')');
                $j('#remote_patient_error_msg').css('visibility', 'hidden');
                $j('#remote_patient_error_msg').html(IMPORT_ERROR_MSG_PREFIX);

                var requestHeaders = new Headers();
                requestHeaders.append("Content-Type", "application/json");

                var requestOptions = {
                    method: 'POST',
                    headers: requestHeaders,
                };

                var importOk = false;
                fetch(importPatientUrl, requestOptions)
                    .then(response => {
                        importOk = response.ok;
                        if(response.ok) {
                            return response.json();
                        } else {
                            return response.text();
                        }
                    })
                    .then(result => {
                        if (importOk) {
                            $j('#openmrs_msg').css('visibility', 'visible');
                            $j(busyGifImgs).css('visibility', 'hidden');
                            refreshTable(patientTable, mapResults(foundPatientList));
                            // Redirect
                            setTimeout(function (patientId) {
                                window.location = localOpenmrsContextPath + '/patientDashboard.form?patientId=' + patientId;
                            }, 2000, result)

                        } else {
                            $j('#remote_patient_error_msg').append(result);
                            $j('#remote_patient_error_msg').css('visibility', 'visible');
                            $j(patienceMessage).css('visibility', 'hidden');
                            $j(busyGifImgs).css('visibility', 'hidden');
                            $j(pressedButton).prop('disabled', false);
                        }
                    })
                    .catch(trouble => {
                        $j('#remote_patient_error_msg').append(trouble.toString());
                        $j('#remote_patient_error_msg').css('visibility', 'visible');
                        $j(patienceMessage).css('visibility', 'hidden');
                        $j('.import-busy-gif').css('visibility', 'hidden');
                        $j(pressedButton).prop('disabled', false);
                        console.log('Error while importing patient record: ', trouble)
                    });
            }
        };

        if(REMOTE_SERVER_TYPE === 'OPENMRS') {
            var openmrsPatient = foundPatientList.find(patient => patient.uuid === patientUuid);
            var importPatientUrl = localOpenmrsContextPath + '/module/esaudefeatures/openmrsPatient.json?uuid=' + patientUuid;
            _importWork(openmrsPatient.display, importPatientUrl);
        } else {
            // OpenCR
            var opencrPatient = foundPatientList.find(patient => patient.resource.id === patientUuid);
            var importPatientUrl = localOpenmrsContextPath + '/module/esaudefeatures/fhirPatient.json?patientId=' + patientUuid;
            _importWork(opencrPatient.resource.fullname, importPatientUrl);
        }
    }

    function openCloseButtonHandler(oTable) {
        var nTr = this.parentNode.parentNode;
        if ( this.src.match('details_close') ) {
            /* This row is already open - close it */
            this.src = "${pageContext.request.contextPath}/moduleResources/esaudefeatures/images/details_open.png";
            oTable.fnClose(nTr);
        }
        else {
            /* Open this row */
            this.src = "${pageContext.request.contextPath}/moduleResources/esaudefeatures/images/details_close.png";

            // Fetch Similar records first.
            // 1. First try by uuid.
            var rowData = oTable.fnGetData(nTr);
            var patientUuid = rowData[0];
            var patient = foundPatientList.find((patient) => {
                if(REMOTE_SERVER_TYPE === 'OPENMRS') {
                    return patient.uuid === patientUuid;
                }
                var UUID = patient.resource.identifier.find(ident => ident.system === OPENMRS_PERSON_UUID_FHIR_SYSTEM_VALUE);
                return UUID != null && UUID.value === patientUuid;
            });
            var remotePatientDetailsTitle ='<openmrs:message code="esaudefeatures.remote.patients.remote.patient.details"/>';
            var localPatietSearchUrl = localOpenmrsContextPath + '/ws/rest/v1/patient/' + patientUuid + '?v=full';
            var requestHeaders = new Headers({
                'Content-Type': 'application/json',
            });

            var requestOptions = {
                method: 'GET',
                headers: requestHeaders,
                redirect: 'follow'
            };

            fetch(localPatietSearchUrl, requestOptions)
                .then(response => {
                    if(REMOTE_SERVER_TYPE === 'OPENMRS') {
                        var detailsWithButtonEnabled = createPatientDetailsHtmlTable(patient, remotePatientDetailsTitle, true, false);
                    } else {
                        var detailsWithButtonEnabled = createPatientDetailsHtmlTableForMPI(patient, remotePatientDetailsTitle, true, false);
                    }
                    if(response.status === 200) {
                        response.json().then(localPatient => {
                            var detailsTitle = '<openmrs:message code="esaudefeatures.remote.patients.same.uuid.local"/>';
                            var localPatientTable = '<div style="float:left; border:2.5px solid red; background-color: #FF9033">'
                                + createPatientDetailsHtmlTable(localPatient, detailsTitle, false)
                                + '</div>';
                            if(REMOTE_SERVER_TYPE === 'OPENMRS') {
                                var detailsWithButtonDisabled = createPatientDetailsHtmlTable(patient, remotePatientDetailsTitle, true, true);
                            } else {
                                var detailsWithButtonDisabled = createPatientDetailsHtmlTableForMPI(patient, remotePatientDetailsTitle, true, true);
                            }
                            detailsWithButtonDisabled += localPatientTable;
                            var localPatientRow =  oTable.fnOpen(nTr, detailsWithButtonDisabled);
                            $j(localPatientRow).attr('class', nTr.classList.value);
                        });

                    } else if(response.status === 404 && REMOTE_SERVER_TYPE === 'OPENMRS') {
                        // TODO: Go for identifiers & names (After discussion with the team)
                        var localPatientSearchUrlUsingIdentifier = localOpenmrsContextPath + '/ws/rest/v1/patient?v=full&identifier=';
                        // TODO: Fix this for OpenCR payload.
                        if(patient.identifiers.length > 0) {
                            localPatientSearchUrlUsingIdentifier += patient.identifiers[0].identifier;
                            fetch(localPatientSearchUrlUsingIdentifier, requestOptions)
                                .then(response => {
                                    if(response.status === 200) {
                                        response.json().then(data => {
                                            if(data.results.length > 0) {
                                                // Only get the first one
                                                // TODO: Accommodate all found patients later (this is a very rare case)
                                                var detailsTitle = '<openmrs:message code="esaudefeatures.remote.patients.same.uuid.local"/>';
                                                var localPatientTable = '<div style="float:left; border:2.5px solid red; background-color: #FF9033">'
                                                    + createPatientDetailsHtmlTable(data.results[0], detailsTitle, false)
                                                    + '</div>';
                                                if(REMOTE_SERVER_TYPE === 'OPENMRS') {
                                                    var detailsWithButtonDisabled = createPatientDetailsHtmlTable(patient, remotePatientDetailsTitle, true, true);
                                                } else {
                                                    var detailsWithButtonDisabled = createPatientDetailsHtmlTableForMPI(patient, remotePatientDetailsTitle, true, true);
                                                }
                                                detailsWithButtonDisabled += localPatientTable;
                                                var newAddedRow = oTable.fnOpen(nTr, detailsWithButtonDisabled);
                                                $j(newAddedRow).attr('class', nTr.classList.value);
                                            } else {
                                                var newAddedRow = oTable.fnOpen(nTr, detailsWithButtonEnabled);
                                                $j(newAddedRow).attr('class', nTr.classList.value);
                                            }
                                        })
                                    } else {
                                        var newAddedRow = oTable.fnOpen(nTr, detailsWithButtonEnabled);
                                        $j(newAddedRow).attr('class', nTr.classList.value);
                                    }
                                })
                                .catch(error => {
                                    console.log('error', error);
                                    var newAddedRow = oTable.fnOpen(nTr, detailsWithButtonEnabled);
                                    $j(newAddedRow).attr('class', nTr.classList.value);
                                });
                        }
                    } else {
                        var newAddedRow = oTable.fnOpen(nTr, detailsWithButtonEnabled);
                        $j(newAddedRow).attr('class', nTr.classList.value);
                    }
                })
                .catch(error => {
                    console.log('error', error);
                    if(REMOTE_SERVER_TYPE === 'OPENMRS') {
                        var detailsWithButtonEnabled = createPatientDetailsHtmlTable(patient, remotePatientDetailsTitle, true, false);
                    } else {
                        var detailsWithButtonEnabled = createPatientDetailsHtmlTableForMPI(patient, remotePatientDetailsTitle, true, false);
                    }
                    var newAddedRow = oTable.fnOpen(nTr, detailsWithButtonEnabled);
                    $j(newAddedRow).attr('class', nTr.classList.value);
                });
        }
    }

    $j(document).ready(function() {
        $j('#esaudefeatures-rps-tabs').tabs();
        $j('#esaudefeatures-import-log-table').dataTable();
        $j('#dialog').dialog({
            autoOpen: false
        });
        patientTable = $j('#found-patients').dataTable({
            aaData: [],
            bFilter: false,
            bSort: false,
            fnRowCallback: function( nRow, aData, iDisplayIndex, iDisplayIndexFull ) {
                $j('td:eq(1)', nRow).html(iDisplayIndexFull + 1);
                $j(nRow).css('cursor', 'pointer');
                return nRow;
            },
            sScrollX: "100%",
            bScrollCollapse: true,
            bAutoWidth: false
        });

        // A bit hacky.
        Array.prototype.push.apply(patientTable.fnSettings().aoDrawCallback, [{
            fn: function() {
                $j('#found-patients tbody tr').on('click', function() {
                    var image = $j('td:eq(0) > img', this)[0];
                    openCloseButtonHandler.call(image, patientTable);
                });
            }
        }, {
            fn: function() {
                patientTable.fnSetColumnVis(0, false);
            }
        }]);

        $j("#find-remote-patients-button").on('click', function(e) {
            var searchText = $j('#find-remote-patients').val();
            if(searchText !== null) {
                searchText.trim();
                if(searchText.length > 0) {
                    var matchMode = 'fuzzy';
                    if($j('#remote-patient-match-mode-exact').is(':checked')) {
                        matchMode = 'exact';
                    }
                    $j('#remote_patient_error_msg').css('visibility', 'hidden');
                    $j('#openmrs_msg').css('visibility', 'hidden');
                    searchPatientsFromRemoteServer(searchText, matchMode);
                }
            }
        });
    });

</script>
<div id="esaudefeatures-rps-tabs">
    <ul>
        <li><a href="#esaudefeatures-search-tab"><openmrs:message code="esaudefeatures.search.tab.label"/></a></li>
        <li><a href="#esaudefeatures-import-log-tab"><openmrs:message code="esaudefeatures.importLog.label"/></a></li>
    </ul>
    <div id="esaudefeatures-search-tab">
        <div>
            <openmrs:message code="esaudefeatures.remote.patients.match.mode.message" javaScriptEscape="true"/>
            <input type="checkbox" name="remote-patient-match-mode" id="remote-patient-match-mode-exact" value="exact" ${matchModeExact}/>
        </div>
        <div>
            <openmrs:message code="esaudefeatures.remote.patients.search" javaScriptEscape="true"/>
            <input type="text" id="find-remote-patients"
               placeholder="<openmrs:message code="esaudefeatures.remote.patients.search.placeholder" javaScriptEscape="true"/>"/>
            <button id="find-remote-patients-button"><openmrs:message  code="esaudefeatures.remote.patients.search.button"/></button>
            <img id="search-busy-gif" src="${pageContext.request.contextPath}/moduleResources/esaudefeatures/images/loading.gif" style="visibility:hidden;"/>
        </div>
        <table id="found-patients" class="display nowrap" style="width:100%; border-spacing: 0px;">
            <thead>
            <tr>
                <th></th>
                <th></th>
                <th>S/N</th>
                <th><openmrs:message code="esaudefeatures.remote.patients.identifier"/></th>
                <th><openmrs:message code="esaudefeatures.remote.patients.fullname"/></th>
                <th><openmrs:message code="esaudefeatures.remote.patients.gender"/></th>
                <th><openmrs:message code="esaudefeatures.remote.patients.birthdate"/></th>
                <th><openmrs:message code="esaudefeatures.remote.patients.age"/></th>
                <th><openmrs:message code="esaudefeatures.remote.patients.address"/></th>
            </tr>
            </thead>
            <tbody></tbody>
        </table>
    </div>
    <div id="esaudefeatures-import-log-tab">
        <table id="esaudefeatures-import-log-table" class="display nowrap" style="width:100%; border-spacing: 0px;">
            <thead>
                <tr>
                    <th>S/N</th>
                    <th><openmrs:message code="esaudefeatures.importLog.dateImported.label"/></th>
                    <th><openmrs:message code="esaudefeatures.importLog.healthFacility.label"/></th>
                    <th><openmrs:message code="esaudefeatures.importLog.patientNID.label"/></th>
                    <th><openmrs:message code="esaudefeatures.importLog.patientUuid.label"/></th>
                    <th><openmrs:message code="esaudefeatures.importLog.initiator.label"/></th>
                </tr>
            </thead>
            <tbody>
                <c:forEach items="${importLogs}" var="importLog" varStatus="loop">
                    <tr>
                        <td>${loop.count}</td>
                        <td>
                            <c:if test="${not empty importLog.dateImported}">
                                ${formatter.format(importLog.dateImported)}
                            </c:if>
                        </td>
                        <td>${importLog.healthFacility}</td>
                        <td>${importLog.patientNID}</td>
                        <td>${importLog.patient.uuid}</td>
                        <td>
                            <c:choose>
                                <c:when test="${not empty importLog.initiator}">
                                    <c:if test="${not empty importLog.initiator.personName}">
                                        ${importLog.initiator.personName.fullName}
                                    </c:if>
                                    (${importLog.initiator.username})
                                </c:when>
                                <c:otherwise>
                                    <openmrs:message code="esaudefeatures.importLog.initiator.unknown.label"/>
                                </c:otherwise>
                            </c:choose>
                        </td>
                    </tr>
                </c:forEach>
            </tbody>
        </table>
    </div>
</div>
<div id="dialog" title="<openmrs:message code="esaudefeatures.remote.patients.connection.problem"/>">
    <p><openmrs:message code="esaudefeatures.remote.patients.connection.problem.message"/></p>
</div>
