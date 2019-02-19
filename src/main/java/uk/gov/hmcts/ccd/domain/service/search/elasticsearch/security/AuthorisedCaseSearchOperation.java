package uk.gov.hmcts.ccd.domain.service.search.elasticsearch.security;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static uk.gov.hmcts.ccd.domain.service.common.AccessControlService.CAN_READ;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.ccd.data.user.CachedUserRepository;
import uk.gov.hmcts.ccd.data.user.UserRepository;
import uk.gov.hmcts.ccd.domain.model.definition.CaseDetails;
import uk.gov.hmcts.ccd.domain.model.definition.CaseType;
import uk.gov.hmcts.ccd.domain.model.search.CaseSearchResult;
import uk.gov.hmcts.ccd.domain.service.common.AccessControlService;
import uk.gov.hmcts.ccd.domain.service.common.ObjectMapperService;
import uk.gov.hmcts.ccd.domain.service.common.SecurityClassificationService;
import uk.gov.hmcts.ccd.domain.service.search.elasticsearch.CaseSearchOperation;
import uk.gov.hmcts.ccd.domain.service.search.elasticsearch.CrossCaseTypeSearchRequest;
import uk.gov.hmcts.ccd.domain.service.search.elasticsearch.ElasticsearchCaseSearchOperation;
import uk.gov.hmcts.ccd.domain.service.security.AuthorisedCaseDefinitionDataService;

@Service
@Qualifier(AuthorisedCaseSearchOperation.QUALIFIER)
public class AuthorisedCaseSearchOperation implements CaseSearchOperation {

    public static final String QUALIFIER = "AuthorisedCaseSearchOperation";

    private final CaseSearchOperation caseSearchOperation;
    private final AuthorisedCaseDefinitionDataService authorisedCaseDefinitionDataService;
    private final AccessControlService accessControlService;
    private final SecurityClassificationService classificationService;
    private final ObjectMapperService objectMapperService;
    private final UserRepository userRepository;

    @Autowired
    public AuthorisedCaseSearchOperation(
        @Qualifier(ElasticsearchCaseSearchOperation.QUALIFIER) CaseSearchOperation caseSearchOperation,
        AuthorisedCaseDefinitionDataService authorisedCaseDefinitionDataService,
        AccessControlService accessControlService,
        SecurityClassificationService classificationService,
        ObjectMapperService objectMapperService,
        @Qualifier(CachedUserRepository.QUALIFIER) UserRepository userRepository) {

        this.caseSearchOperation = caseSearchOperation;
        this.authorisedCaseDefinitionDataService = authorisedCaseDefinitionDataService;
        this.accessControlService = accessControlService;
        this.classificationService = classificationService;
        this.objectMapperService = objectMapperService;
        this.userRepository = userRepository;
    }

    @Override
    public CaseSearchResult execute(CrossCaseTypeSearchRequest searchRequest) {
        List<CaseType> authorisedCaseTypes = getAuthorisedCaseTypes(searchRequest);
        CrossCaseTypeSearchRequest authorisedSearchRequest = createAuthorisedSearchRequest(authorisedCaseTypes, searchRequest);

        return searchCasesAndFilterFieldsByAccess(authorisedCaseTypes, authorisedSearchRequest);
    }

    private List<CaseType> getAuthorisedCaseTypes(CrossCaseTypeSearchRequest searchRequest) {
        return searchRequest.getCaseTypeIds()
            .stream()
            .map(caseTypeId -> authorisedCaseDefinitionDataService.getAuthorisedCaseType(caseTypeId, CAN_READ).orElse(null))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    private CrossCaseTypeSearchRequest createAuthorisedSearchRequest(List<CaseType> authorisedCaseTypes, CrossCaseTypeSearchRequest originalSearchRequest) {
        List<String> authorisedCaseTypeIds = authorisedCaseTypes.stream().map(CaseType::getId).collect(Collectors.toList());
        return new CrossCaseTypeSearchRequest(authorisedCaseTypeIds, originalSearchRequest.getSearchRequestJsonNode());
    }

    private CaseSearchResult searchCasesAndFilterFieldsByAccess(List<CaseType> authorisedCaseTypes, CrossCaseTypeSearchRequest authorisedSearchRequest) {
        if (authorisedCaseTypes.isEmpty()) {
            return CaseSearchResult.EMPTY;
        }

        CaseSearchResult result = caseSearchOperation.execute(authorisedSearchRequest);
        filterFieldsByAccess(authorisedCaseTypes, result.getCases());

        return result;
    }

    private void filterFieldsByAccess(List<CaseType> authorisedCaseTypes, List<CaseDetails> cases) {
        Map<String, CaseType> caseTypeIdByCaseType = authorisedCaseTypes
            .stream()
            .collect(Collectors.toMap(CaseType::getId, Function.identity()));

        cases.stream()
            .filter(caseDetails -> caseTypeIdByCaseType.containsKey(caseDetails.getCaseTypeId()))
            .forEach(caseDetails -> {
                filterCaseFieldsByAclAccess(caseTypeIdByCaseType.get(caseDetails.getCaseTypeId()), caseDetails);
                filterCaseFieldsBySecurityClassification(caseDetails);
            });
    }

    private void filterCaseFieldsBySecurityClassification(CaseDetails caseDetails) {
        classificationService.applyClassification(caseDetails);
    }

    private void filterCaseFieldsByAclAccess(CaseType authorisedCaseType, CaseDetails caseDetails) {
        JsonNode data = objectMapperService.convertObjectToJsonNode(caseDetails.getData());
        JsonNode filteredData = accessControlService.filterCaseFieldsByAccess(data, authorisedCaseType.getCaseFields(), getUserRoles(), CAN_READ);
        caseDetails.setData(objectMapperService.convertJsonNodeToMap(filteredData));
    }

    private Set<String> getUserRoles() {
        return userRepository.getUserRoles();
    }
}
