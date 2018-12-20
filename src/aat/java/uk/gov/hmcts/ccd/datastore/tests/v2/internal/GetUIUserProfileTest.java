package uk.gov.hmcts.ccd.datastore.tests.v2.internal;

import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.ccd.datastore.tests.AATHelper;
import uk.gov.hmcts.ccd.datastore.tests.BaseTest;
import uk.gov.hmcts.ccd.v2.V2;

import static java.lang.Boolean.FALSE;
import static org.hamcrest.Matchers.*;

@DisplayName("Get UI user profile")
class GetUIUserProfileTest extends BaseTest {

    protected GetUIUserProfileTest(AATHelper aat) {
        super(aat);
    }

    @Test
    @DisplayName("should retrieve user profile")
    void shouldRetrieveWhenExists() {

        whenCallingGeUserProfile()
            .then()
            .log().ifError()
            .statusCode(200)
            .assertThat()

            .body("user.idam.defaultService", equalTo("CCD"))
            .body("user.idam.roles", hasItems(equalTo("caseworker"),
                                              equalTo("caseworker-autotest1"),
                                              equalTo("caseworker-loa1"),
                                              equalTo("caseworker-autotest1-loa1")))
            .body("channels.id", is(nullValue()))
            .body("jurisdictions.find { it.id == 'AUTOTEST1' }.name", equalTo("Auto Test 1"))
            .body("jurisdictions.find { it.id == 'AUTOTEST1' }.description", equalTo("Content for the Test Jurisdiction."))
            .body("jurisdictions.find { it.id == 'AUTOTEST1' }.caseTypes.find { it.id == 'MAPPER' }.name", equalTo("Case type for Mapper"))
            .body("jurisdictions.find { it.id == 'AUTOTEST1' }.caseTypes.find { it.id == 'AAT' }.name", equalTo("Case type for AAT"))
            .body("default.workbasket.jurisdiction_id", equalTo("AUTOTEST1"))
            .body("default.workbasket.case_type_id", equalTo("AAT"))
            .body("default.workbasket.state_id", equalTo("TODO"))

            .rootPath("_links")
            .body("self.href", equalTo(String.format("%s/internal/profile", aat.getTestUrl())));
    }

    private Response whenCallingGeUserProfile() {
        return asAutoTestCaseworker(FALSE)
            .get()
            .accept(V2.MediaType.UI_USER_PROFILE)
            .header("experimental", "true")

            .when()
            .get("/internal/profile");
    }
}
