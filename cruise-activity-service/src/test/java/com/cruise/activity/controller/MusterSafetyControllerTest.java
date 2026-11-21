package com.cruise.activity.controller;
import com.cruise.activity.dto.MusterSafetyDtos.*;
import com.cruise.activity.exception.GlobalExceptionHandler;
import com.cruise.activity.service.*;
import com.cruise.common.enums.Role;
import com.cruise.common.security.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.MediaType;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MusterSafetyControllerTest {
    private MusterDrillService muster;
    private SafetyConfigurationService configuration;
    private MockMvc mvc;
    @BeforeEach void setup() {
        muster=mock(MusterDrillService.class); configuration=mock(SafetyConfigurationService.class);
        AccessTokenVerifier verifier=token->{try{return Optional.of(new VerifiedCaller(1L,"officer",Role.valueOf(token)));}catch(IllegalArgumentException ex){return Optional.empty();}};
        mvc=MockMvcBuilders.standaloneSetup(new MusterSafetyController(muster),new SafetyTelemetryController(mock(PassengerTelemetryService.class),configuration))
            .setControllerAdvice(new GlobalExceptionHandler()).addFilters(new AuthorizationFilter(verifier,new ServiceAccessPolicy())).build();
    }
    @ParameterizedTest @EnumSource(Role.class)
    void onlySafetyOperationsAndSystemAdminCanReadMuster(Role role) throws Exception {
        boolean allowed=Set.of(Role.SAFETY_OFFICER,Role.OPERATIONS_MANAGER,Role.SYSTEM_ADMIN).contains(role);
        mvc.perform(get("/api/v1/safety/muster/live-dashboard").param("drillId","1").header("Authorization","Bearer "+role))
            .andExpect(status().is(allowed?200:403));
        if(allowed) verify(muster).getLiveDashboard(1L,null);else verifyNoInteractions(muster);
    }
    @Test void anonymousAndSpoofedRoleHeadersCannotInvokeMuster() throws Exception {
        mvc.perform(get("/api/v1/safety/muster/live-dashboard").param("drillId","1").header("X-Caller-Role","SAFETY_OFFICER")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/safety/muster/drill/start").header("Authorization","Bearer POS_STAFF").header("X-Caller-Role","SAFETY_OFFICER")
            .contentType(MediaType.APPLICATION_JSON).content("{\"tourId\":7,\"drillType\":\"DRILL_PRACTICE\"}")).andExpect(status().isForbidden());
        verifyNoInteractions(muster);
    }
    @Test void startUsesVerifiedActorAndValidatesBody() throws Exception {
        mvc.perform(post("/api/v1/safety/muster/drill/start").header("Authorization","Bearer SAFETY_OFFICER")
            .contentType(MediaType.APPLICATION_JSON).content("{\"tourId\":7,\"drillType\":\"DRILL_PRACTICE\"}"))
            .andExpect(status().isCreated());
        verify(muster).triggerEmergencyAlarm(com.cruise.activity.enums.SolasEmergencyType.DRILL_PRACTICE,7L,"officer");
        mvc.perform(post("/api/v1/safety/muster/drill/start").header("Authorization","Bearer SAFETY_OFFICER")
            .contentType(MediaType.APPLICATION_JSON).content("{\"tourId\":0,\"drillType\":\"DRILL_PRACTICE\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/safety/muster/drill/start").header("Authorization","Bearer SAFETY_OFFICER")
            .contentType(MediaType.APPLICATION_JSON).content("{\"tourId\":7,\"drillType\":\"NOT_A_TYPE\"}")).andExpect(status().isBadRequest());
    }
    @Test void malformedDeckZoneNeverReachesConfigurationService() throws Exception {
        mvc.perform(post("/api/v1/safety/config/zones").header("Authorization","Bearer SAFETY_OFFICER")
            .contentType(MediaType.APPLICATION_JSON).content("{\"zoneCode\":\"POOL\",\"zoneName\":\"Pool\",\"deckNumber\":15,\"capacity\":10}"))
            .andExpect(status().isBadRequest());verifyNoInteractions(configuration);
    }
    @Test void wrongStationIsAnExplicitOutcomeAndMissingListAcceptsFilters() throws Exception {
        when(muster.recordMusterArrival(anyLong(),anyString(),anyLong(),any())).thenReturn(new ArrivalResult(1L,7L,"STATION-A","STATION-B",PassengerStatus.WRONG_MUSTER_STATION,false));
        mvc.perform(post("/api/v1/safety/muster/checkin").header("Authorization","Bearer SAFETY_OFFICER").contentType(MediaType.APPLICATION_JSON)
            .content("{\"drillId\":1,\"passengerId\":7,\"stationCode\":\"STATION-B\",\"method\":\"NFC\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("WRONG_MUSTER_STATION"));
        mvc.perform(get("/api/v1/safety/muster/missing-passengers").param("drillId","1").param("stationCode","STATION-A").param("status","MISSING")
            .header("Authorization","Bearer OPERATIONS_MANAGER")).andExpect(status().isOk());
        verify(muster).getMissingPassengersSummary(1L,"STATION-A",PassengerStatus.MISSING);
    }
}
