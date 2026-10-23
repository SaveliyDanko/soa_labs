package ru.itmo.soa.isu;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import ru.itmo.soa.web.ApiWebConfiguration;
import ru.itmo.soa.model.ApiModels.ActionResult;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value = IsuController.class, properties = "spring.cloud.config.enabled=false")
@Import(ApiWebConfiguration.class)
class IsuApiTest {
    @Autowired MockMvc mvc;
    @MockBean IsuService service;

    @Test
    void keepsTheExpelContract() throws Exception {
        when(service.expelAll(7)).thenReturn(new ActionResult("ALL_STUDENTS_EXPELLED", "Все студенты отчислены; пустая группа удалена", 7));
        mvc.perform(post("/isu/group/7/expel-all")).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("ALL_STUDENTS_EXPELLED")).andExpect(jsonPath("$.resourceId").value(7));
    }

    @Test
    void rejectsInvalidParameters() throws Exception {
        mvc.perform(post("/isu/group/abc/expel-all")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER_TYPE"));
        mvc.perform(post("/isu/group/0/expel-all")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER_VALUE"));
        mvc.perform(post("/isu/group/7/change-edu-form/WRONG")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER_VALUE"));
        verifyNoInteractions(service);
    }
}
