package br.com.adapter.in.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest(classes = ApiApp.class, properties = "cuidamed.limite.cadastro-maximo=3")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ConfiguracaoDeTeste.class)
class LimiteDeCadastroApiTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;

    @Test
    void criarContasEmMassaDoMesmoIpEBloqueado() throws Exception {
        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/api/v1/auth/registro").contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of("tipo", "IDOSO", "nome", "Conta " + i,
                                    "email", "massa" + i + "@teste.com", "senha", "senha-forte-123"))))
                    .andExpect(status().isCreated());
        }
        mvc.perform(post("/api/v1/auth/registro").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("tipo", "IDOSO", "nome", "Conta 4",
                                "email", "massa4@teste.com", "senha", "senha-forte-123"))))
                .andExpect(status().isTooManyRequests());
        // um IP diferente não é afetado
        mvc.perform(post("/api/v1/auth/registro").with(r -> { r.setRemoteAddr("203.0.113.9"); return r; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("tipo", "IDOSO", "nome", "Outro IP",
                                "email", "outroip@teste.com", "senha", "senha-forte-123"))))
                .andExpect(status().isCreated());
    }
}
