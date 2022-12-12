<h2><openmrs:message code="esaudefeatures.remote.patients.search"/></h2>
<div id="openmrs_msg" style="visibility:hidden;"><openmrs:message code="esaudefeatures.remote.patients.imported"/></div>
<div id="remote_patient_error_msg" class="error" style="visibility:hidden;">
    <openmrs:message code="esaudefeatures.remote.patients.import.error"/>
</div>

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
        var localOpenmrsContextPath = '${pageContext.request.contextPath}';
        var importedPatientLocationUuid = "${importedPatientLocationUuid}";
        var patientTable = null;
        var lastSearchedText = null;
        var foundPatientList = null;
        var opencrMergedPatients = {};
        const MIN_SEARCH_LENGTH = 3;
        const EMPTY_COLUMN_HEADER_ID = 'empty-header-column';
        const DATE_DISPLAY_OPTIONS = '%d-%b-%Y';
        const IMPORT_SUCCESS_MSG_PREFIX = $j('#openmrs_msg').html();
        const IMPORT_ERROR_MSG_PREFIX = $j('#remote_patient_error_msg').html();
        const ERROR_DURING_SEARCH_MSG_PREFIX = '<openmrs:message code="esaudefeatures.remote.patients.search.error"/>'
        const IMPORT_CONFIRM_MSG_PREFIX = '<openmrs:message code="esaudefeatures.remote.patients.import.confirmation"/>';
        const REMOTE_SERVER_TYPE = "${remoteServerType}";
        const OPENCR_NID_CODE = 'NID_TARV';
        const OPENCR_PERSON_UUID_CODE = 'OpenMRS_PATIENT_UUID';
        const ART_TREATMENT_PROGRAM_UUID = 'efe2481f-9e75-4515-8d5a-86bfde2b5ad3';
        const PREP_PROGRAM_UUID = 'ac7c5d2b-854a-48c4-a68f-0b8a92e11f4a';
        const ART_START_DATE_CONCEPT_UUID = 'e1d8f690-1d5f-11e0-b929-000c29ad1d07';
        const PHARMACY_ENCOUNTER_TYPE_UUID = 'e279133c-1d5f-11e0-b929-000c29ad1d07';

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
                if(error.includes('Failed to connect to')) {
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
                        }
                        infoUpdaterCallback(patientUuid, infoStore);
                    }).catch(error => {
                    searchErrorHandler(error);
                });
            };

            var programsUrl = localOpenmrsContextPath + "/module/esaudefeatures/openmrsRemoteGetRequest.json?patient=" + patientUuid;
            programsUrl += '&resource=programenrollment&v=custom:(uuid,dateEnrolled,dateCompleted,program:(uuid,name))';
            __fetchAndUpdateInfo(programsUrl, patient.programInfo, patientProgramsSearchController,insertProgramInfoInDetailsColumn);

            var artStartDateUrl = localOpenmrsContextPath + "/module/esaudefeatures/openmrsRemoteGetRequest.json?patient=" + patientUuid;
            artStartDateUrl += '&resource=obs&limit=1&v=custom:(uuid,value)&concepts=' + ART_START_DATE_CONCEPT_UUID;
            __fetchAndUpdateInfo(artStartDateUrl, patient.artDrugStartDateInfo, artStartDateFetchController,insertArtStartDateInDetailsColumn);

            var lastArtDrugPickupUrl = localOpenmrsContextPath + "/module/esaudefeatures/openmrsRemoteGetRequest.json?patient=" + patientUuid;
            lastArtDrugPickupUrl += '&resource=encounter&v=custom:(uuid,encounterDatetime,location:(uuid,name)&order=desc&limit=1';
            lastArtDrugPickupUrl += '&encounterType=' + PHARMACY_ENCOUNTER_TYPE_UUID;
            __fetchAndUpdateInfo(lastArtDrugPickupUrl, patient.lastArtDrugPickupInfo, lastArtPickupFetchController,insertLastArtPickupInDetailsColumn);
        }

        function searchPatientsFromRemoteServer(searchText) {
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

            if(REMOTE_SERVER_TYPE === 'OPENMRS') {
                var patientSearchUrl = localOpenmrsContextPath + "/module/esaudefeatures/openmrsRemotePatients.json?text=" + searchText;
            } else {
                var patientSearchUrl = localOpenmrsContextPath + '/module/esaudefeatures/opencrRemotePatients.json?text=' + searchText;
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
            if(REMOTE_SERVER_TYPE === 'OPENCR') {
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

                var NID_REGEX = /^\d+\/{1,3}\d+\/{1,3}\d+$/;

                return results.map(result => {
                    var NID = result.resource.identifier.find(ident => ident.type.coding.find(coding => OPENCR_NID_CODE == coding.code));
                    var NIDDisplay = '';
                    if(NID) {
                        NIDDisplay = NID.value;
                    }
                    var openmrsUuid = result.resource.identifier.find(ident => ident.type.coding.find(coding => OPENCR_PERSON_UUID_CODE == coding.code));
                    var openmrsUuidDisplay = openmrsUuid ? openmrsUuid.value : '';
                    var mapped = [openmrsUuidDisplay, NIDDisplay, _fullname(result.resource.name), result.resource.gender]
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
            var priorRow = $j('#program-info-' + patientUuid);
            if(Array.isArray(artDrugStartDateInfo) && artDrugStartDateInfo.length > 0) {
                var artDrugStartDate = '<openmrs:message code="esaudefeatures.remote.patients.no.value"/>';
                if (artDrugStartDateInfo[0].value !== null && artDrugStartDateInfo[0].value !== undefined) {
                    artDrugStartDate = new Date(artDrugStartDateInfo[0].value).toLocaleDateString('pt', DATE_DISPLAY_OPTIONS);
                }
                var artInfoRow = '<tr><td><openmrs:message code="esaudefeatures.remote.patients.art.drug.start.date"/></td><td>' + artDrugStartDate + '</td></tr>';
                $j(artInfoRow).insertAfter(priorRow);
            } else {
                var artInfoRow = '<tr><td><openmrs:message code="esaudefeatures.remote.patients.art.drug.start.date"/></td>'
                    + '<td><openmrs:message code="esaudefeatures.remote.patients.information.not.found"/></td></tr>';
                $j(artInfoRow).insertAfter(priorRow);
            }
        }

        function insertLastArtPickupInDetailsColumn(patientUuid, lastArtPickupInfo) {
            var priorRow = $j('#program-info-' + patientUuid);
            if(Array.isArray(lastArtPickupInfo) && lastArtPickupInfo.length > 0) {
                var lastArtPickupDate = '<openmrs:message code="esaudefeatures.remote.patients.no.value"/>';
                if (lastArtPickupInfo[0].encounterDatetime !== null && lastArtPickupInfo[0].encounterDatetime !== undefined) {
                    lastArtPickupDate = new Date(lastArtPickupInfo[0].encounterDatetime).toLocaleDateString('pt', DATE_DISPLAY_OPTIONS);
                }
                var lastPickupDateRow = '<tr><td><openmrs:message code="esaudefeatures.remote.patients.last.art.drug.pickup.date"/></td><td>' + lastArtPickupDate + '</td></tr>';
                $j(lastPickupDateRow).insertAfter(priorRow);
                if(typeof lastArtPickupInfo[0].location === 'object') {
                    var locationRow = '<tr><td><openmrs:message code="esaudefeatures.remote.patients.last.art.drug.pickup.location"/></td><td>' + lastArtPickupInfo[0].location.name + '</td></tr>';
                    $j(locationRow).insertAfter(priorRow);
                }
            } else {
                var lastPickupDateRow = '<tr><td><openmrs:message code="esaudefeatures.remote.patients.last.art.drug.pickup.date"/></td>'
                    + '<td><openmrs:message code="esaudefeatures.remote.patients.information.not.found"/></tr>';
                $j(lastPickupDateRow).insertAfter(priorRow);
            }
        }

        function insertProgramInfoInDetailsColumn(patientUuid, patientPrograms) {
            if(Array.isArray(patientPrograms) && patientPrograms.length > 0) {
                var currentRow =  $j('#program-info-' + patientUuid);
                currentRow.html('<td colspan="2" style="border-bottom: solid; border-top:solid;"><openmrs:message code="esaudefeatures.remote.patients.programInfo"/></td>');

                var artProgram = patientPrograms.find(program => program.program.uuid == ART_TREATMENT_PROGRAM_UUID);
                var programInfoAvailable = false;
                if(artProgram) {
                    var artDateEnrolled = new Date(artProgram.dateEnrolled).toLocaleDateString('pt', DATE_DISPLAY_OPTIONS);
                    var artInfoRow = '<tr><td><openmrs:message code="esaudefeatures.remote.patients.art.enrollment.date"/></td><td>' + artDateEnrolled + '</td></tr>';
                    $j(artInfoRow).insertAfter(currentRow);
                    currentRow = $j(artInfoRow);
                    programInfoAvailable = true;
                }
                var prepProgram =  patientPrograms.find(program => program.program.uuid == PREP_PROGRAM_UUID);
                if(prepProgram) {
                    var prepDateEnrolled = new Date(prepProgram.dateEnrolled).toLocaleDateString('pt', DATE_DISPLAY_OPTIONS);
                    var prepInfoRow = '<tr><td><openmrs:message code="esaudefeatures.remote.patients.prep.enrollment.date"/></td><td>' + prepDateEnrolled + '</td></tr>';
                    $j(prepInfoRow).insertAfter(currentRow);
                    currentRow = $j(prepInfoRow);
                    programInfoAvailable = true;
                }

                if(!programInfoAvailable) {
                    $j('<td><openmrs:message code="esaudefeatures.remote.patients.programInfo.not.found"/></td>').insertAfter(currentRow);
                }
            } else {
                $j('#program-info-' + patientUuid).html('<td colspan="2" style="border-bottom: solid; border-top:solid;"><openmrs:message code="esaudefeatures.remote.patients.programInfo.not.found"/></td>');
            }
        }

        function insertDetailsColumnInResultsTable(oTable) {
            var _searchPatientFromList = (uuid, patient) => {
                if(REMOTE_SERVER_TYPE === 'OPENMRS') {
                    return patient.uuid === uuid;
                }
                return patient.resource.identifier.find(ident => /openmrs/.test(ident.system)).value === uuid;
            };
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
            $j('#found-patients tbody td img').on('click', function () {
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
                        var UUID = patient.resource.identifier.find(ident => ident.type.coding.find(coding => OPENCR_PERSON_UUID_CODE == coding.code));
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
                                var detailsWithButtonEnabled = createPatientDetailsHtmlTableForOpenCR(patient, remotePatientDetailsTitle, true, false);
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
                                        var detailsWithButtonDisabled = createPatientDetailsHtmlTableForOpenCR(patient, remotePatientDetailsTitle, true, true);
                                    }
                                    detailsWithButtonDisabled += localPatientTable;
                                    oTable.fnOpen(nTr, detailsWithButtonDisabled, 'details' );
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
                                                            var detailsWithButtonDisabled = createPatientDetailsHtmlTableForOpenCR(patient, remotePatientDetailsTitle, true, true);
                                                        }
                                                        detailsWithButtonDisabled += localPatientTable;
                                                        oTable.fnOpen(nTr, detailsWithButtonDisabled, 'details' );
                                                    } else {
                                                        oTable.fnOpen(nTr, detailsWithButtonEnabled, 'details' );
                                                    }
                                                })
                                            } else {
                                                oTable.fnOpen(nTr, detailsWithButtonEnabled, 'details' );
                                            }
                                        })
                                        .catch(error => {
                                            console.log('error', error);
                                            oTable.fnOpen(nTr, detailsWithButtonEnabled, 'details' );
                                        });
                                }
                            } else {
                                oTable.fnOpen(nTr, detailsWithButtonEnabled, 'details' );
                            }
                        })
                        .catch(error => {
                            console.log('error', error);
                            if(REMOTE_SERVER_TYPE === 'OPENMRS') {
                                var detailsWithButtonEnabled = createPatientDetailsHtmlTable(patient, remotePatientDetailsTitle, true, false);
                            } else {
                                var detailsWithButtonEnabled = createPatientDetailsHtmlTableForOpenCR(patient, remotePatientDetailsTitle, true, false);
                            }
                            oTable.fnOpen(nTr, detailsWithButtonEnabled, 'details' );
                        });
                }
            });
        }

        function refreshTable(oTable, data) {
            oTable.fnClearTable();
            oTable.fnAddData(data);

            oTable.fnDraw();

            oTable.fnSetColumnVis(0, true);
            var emptyColumnHeader = document.getElementById(EMPTY_COLUMN_HEADER_ID);
            if(emptyColumnHeader !== null) {
                emptyColumnHeader.parentNode.removeChild(emptyColumnHeader);
            }

            if(Array.isArray(data) && data.length > 0) {
                insertDetailsColumnInResultsTable(oTable);
                oTable.fnSetColumnVis(0, false);
            }
        }

        function createPatientDetailsHtmlTable(patient, title, addImportButton, disableImportButton) {
            var __determineAttributeDisplayValue = function(attributeValue) {
                if(typeof attributeValue !== 'string' && typeof attributeValue === 'object' && attributeValue.display) {
                    return attributeValue.display;
                }
                return attributeValue;
            }

            var patientDetailsTable = '<table cellpadding="5" cellspacing="0" border="0" style="display: inline; padding-left:10px; border-spacing: 5px;">';
            var patientName = patient.person.names[0];
            if(patient.person.preferredName) {
                patientName = patient.person.preferredName;
            }

            if(title) {
                patientDetailsTable += '<tr><td colspan="2">' + title + '</td></tr>';
            }

            var givenName = patientName.givenName === null ? "" : patientName.givenName;
            var middleName = patientName.middleName === null ? "" : patientName.middleName;
            var familyName = patientName.familyName === null ? "" : patientName.familyName;
            patientDetailsTable += '<tr><td colspan="2" style="border-bottom: solid; border-top: solid;">';
            patientDetailsTable += '<openmrs:message code="esaudefeatures.remote.patients.names"/></td></tr>';
            patientDetailsTable += '<tr><td><openmrs:message code="esaudefeatures.remote.patients.givenName"/><td>' + givenName + '</td></tr>';
            patientDetailsTable += '<tr><td><openmrs:message code="esaudefeatures.remote.patients.middleName"/><td>' + middleName + '</td></tr>';
            patientDetailsTable += '<tr><td><openmrs:message code="esaudefeatures.remote.patients.familyName"/><td>' + familyName + '</td></tr>';
            patientDetailsTable += '<tr><td colspan="2" style="border-bottom: solid; border-top: solid; margin-top:15px;">';
            patientDetailsTable += '<openmrs:message code="esaudefeatures.remote.patients.identifiers"/></td></tr>';
            if(Array.isArray(patient.identifiers) && patient.identifiers.length > 0) {
                for(let identifier of patient.identifiers) {
                    patientDetailsTable += '<tr><td>' + identifier.identifierType.display + ':</td><td>' + identifier.identifier + '</td>';
                }
            } else {
                patientDetailsTable += '<tr><td colspan="2"><openmrs:message code="esaudefeatures.remote.patients.no.identifiers"/> </td></tr>';
            }

            patientDetailsTable += '<tr><td colspan="2" style="border-bottom: solid; border-top:solid;"><openmrs:message code="esaudefeatures.remote.patients.attributes"/></td></tr>';
            if(Array.isArray(patient.person.attributes) && patient.person.attributes.length > 0) {
                for(let personAttribute of patient.person.attributes) {
                    patientDetailsTable += '<tr><td>' + personAttribute.attributeType.display + ':</td><td>' + __determineAttributeDisplayValue(personAttribute.value) + '</td>';
                }
            } else {
                patientDetailsTable += '<tr><td colspan="2"><openmrs:message code="esaudefeatures.remote.patients.no.attributes"/></td></tr>';
            }
            patientDetailsTable += '<tr><td>uuid:<td>' + patient.uuid + '</td></tr>';

            if(addImportButton) {
                if(disableImportButton) {
                    patientDetailsTable += '<tr><td colspan="2"><input type="button" name="import-button-' + patient.uuid + '" disabled value="' +
                        '<openmrs:message code="esaudefeatures.remote.patients.remote.import.patient"/>' +
                        '" onclick="importPatient(\'' + patient.uuid + '\')"/></td></tr>';
                } else {
                    patientDetailsTable += '<tr><td colspan="2"><input type="button" id="import-button-' + patient.uuid + '" value="' +
                        '<openmrs:message code="esaudefeatures.remote.patients.remote.import.patient"/>' +
                        '" onclick="importPatient(\'' + patient.uuid + '\', this.id)"/>' +
                            '<span id="patience-message-' + patient.uuid + '" style="visibility:hidden;">(<openmrs:message code="esaudefeatures.remote.patients.remote.import.bePatient"/>)</span>' +
                        '<img class="import-busy-gif" src="${pageContext.request.contextPath}/moduleResources/esaudefeatures/images/loading.gif" style="visibility:hidden;"/></td></tr>';
                }
            }

            patientDetailsTable += '</table>';

            return patientDetailsTable;
        }

        function createPatientDetailsHtmlTableForOpenCR(patient, title, addImportButton, disableImportButton) {
            var _createMergedPatientsTables = (mergePatients) => {
                var tables = '';
                mergePatients.forEach(patient => {
                    var mergedPatientDetailsTable = '<div style="float:left; border:2.5px solid rgba(64,158,24,0.98); background-color: #c4ffca">';
                    mergedPatientDetailsTable += '<table cellpadding="5" cellspacing="0" border="0" style="display: inline; padding-left:10px; border-spacing: 5px;">';

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
                            mergedPatientDetailsTable += '<tr><td>' + identifier.type.text + ':</td><td>' + identifier.value + '</td>';
                        }
                    } else {
                        mergedPatientDetailsTable += '<tr><td colspan="2"><openmrs:message code="esaudefeatures.remote.patients.no.identifiers"/> </td></tr>';
                    }
                    mergedPatientDetailsTable += '<tr><td><openmrs:message code="esaudefeatures.remote.opencr.record.id"/></td><td>' + patient.resource.id + '</td></tr>';
                    mergedPatientDetailsTable += '</table></div>';

                    tables += mergedPatientDetailsTable;
                });
                return tables;
            };

            var patientDetailsTable = '<table cellpadding="5" cellspacing="0" border="0" style="display: inline; padding-left:10px; border-spacing: 5px;">';
            var patientName = patient.resource.name[0];

            if(title) {
                patientDetailsTable += '<tr><td colspan="2">' + title + '</td></tr>';
            }

            var givenNames = '';
            if(Array.isArray(patientName.given) && patientName.given.length > 0) {
                givenNames = patientName.given.join(' ');
            }
            var familyName = patientName.family === null ? '' : patientName.family;
            patientDetailsTable += '<tr><td colspan="2" style="border-bottom: solid; border-top: solid;">';
            patientDetailsTable += '<openmrs:message code="esaudefeatures.remote.patients.names"/></td></tr>';
            patientDetailsTable += '<tr><td><openmrs:message code="esaudefeatures.remote.patients.givenNames"/></td><td>' + givenNames + '</td></tr>';
            patientDetailsTable += '<tr><td><openmrs:message code="esaudefeatures.remote.patients.familyName"/></td><td>' + familyName + '</td></tr>';
            patientDetailsTable += '<tr><td colspan="2" style="border-bottom: solid; border-top: solid; margin-top:15px;">';
            patientDetailsTable += '<openmrs:message code="esaudefeatures.remote.patients.identifiers"/></td></tr>';
            if(Array.isArray(patient.resource.identifier) && patient.resource.identifier.length > 0) {
                for(let identifier of patient.resource.identifier) {
                    patientDetailsTable += '<tr><td>' + identifier.type.text + ':</td><td>' + identifier.value + '</td>';
                }
            } else {
                patientDetailsTable += '<tr><td colspan="2"><openmrs:message code="esaudefeatures.remote.patients.no.identifiers"/> </td></tr>';
            }
            patientDetailsTable += '<tr><td><openmrs:message code="esaudefeatures.remote.opencr.record.id"/></td><td>' + patient.resource.id + '</td></tr>';

            if(Array.isArray(patient.resource.telecom) && patient.resource.telecom.length > 0) {
                patientDetailsTable += '<tr><td colspan="2" style="border-bottom: solid; border-top:solid;"><openmrs:message code="esaudefeatures.remote.patients.contactInfo"/></td></tr>';
                for(let contact of patient.resource.telecom) {
                    var label = contact.system ? contact.system : '';
                    var useLabel = contact.use ? contact.use : '';
                    label += label.length > 0 && useLabel.length > 0 ? '/' + useLabel : useLabel;
                    if(label.length === 0) {
                        label = 'Contact';
                    }
                    patientDetailsTable += '<tr><td>' + label + ':</td><td>' + contact.value + '</td>';
                }
            }

            var UUID = patient.resource.identifier.find(ident => ident.type.coding.find(coding => OPENCR_PERSON_UUID_CODE == coding.code));
            patientDetailsTable += '<tr id="program-info-' + UUID.value + '"><td colspan="2" style="border-bottom: solid; border-top:solid;"><openmrs:message code="esaudefeatures.remote.patients.fetching.program.enrollment"/>...</td></tr>';
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
                var importPatientUrl = localOpenmrsContextPath + '/module/esaudefeatures/opencrPatient.json?patientId=' + patientUuid;
                _importWork(opencrPatient.resource.fullname, importPatientUrl);
            }
        }

        $j(document).ready(function() {
            $j('#dialog').dialog({
                autoOpen: false
            });
            patientTable = $j('#found-patients').dataTable({
                aaData: [],
                bFilter: false,
                bSort: false
            });

            $j("#find-remote-patients-button").on('click', function(e) {
                var searchText = $j('#find-remote-patients').val();
                if(searchText !== null) {
                    searchText.trim();
                    if(searchText.length > 0) {
                        $j('#remote_patient_error_msg').css('visibility', 'hidden');
                        $j('#openmrs_msg').css('visibility', 'hidden');
                        searchPatientsFromRemoteServer(searchText);
                    }
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
            <button id="find-remote-patients-button"><openmrs:message  code="esaudefeatures.remote.patients.search.button"/></button>
            <img id="search-busy-gif" src="${pageContext.request.contextPath}/moduleResources/esaudefeatures/images/loading.gif" style="visibility:hidden;"/>
            <table id="found-patients" class="display nowrap" style="width:100%">
                <thead>
                <tr>
                    <th></th>
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
    </div>
</c:if>

<div id="dialog" title="<openmrs:message code="esaudefeatures.remote.patients.connection.problem"/>">
    <p><openmrs:message code="esaudefeatures.remote.patients.connection.problem.message"/></p>
</div>
