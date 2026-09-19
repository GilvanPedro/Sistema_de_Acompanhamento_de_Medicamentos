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

    /** Corpo de cadastro válido: os dados recebidos mais o aceite da política de privacidade (obrigatório). */
    private static Map<String, Object> reg(Object... paresChaveValor) {
        Map<String, Object> corpo = new java.util.HashMap<>();
        for (int i = 0; i < paresChaveValor.length; i += 2) {
            corpo.put((String) paresChaveValor[i], paresChaveValor[i + 1]);
        }
        corpo.put("aceitouPolitica", true);
        corpo.put("versaoPolitica", "1.0");
        return corpo;
    }

    @Test
    void criarContasEmMassaDoMesmoIpEBloqueado() throws Exception {
        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/api/v1/auth/registro").contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(reg("tipo", "IDOSO", "nome", "Conta " + i,
                                    "email", "massa" + i + "@teste.com", "senha", "senha-forte-123"))))
                    .andExpect(status().isCreated());
        }
        mvc.perform(post("/api/v1/auth/registro").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(reg("tipo", "IDOSO", "nome", "Conta 4",
                                "email", "massa4@teste.com", "senha", "senha-forte-123"))))
                .andExpect(status().isTooManyRequests());
        // um IP diferente não é afetado
        mvc.perform(post("/api/v1/auth/registro").with(r -> { r.setRemoteAddr("203.0.113.9"); return r; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(reg("tipo", "IDOSO", "nome", "Outro IP",
                                "email", "outroip@teste.com", "senha", "senha-forte-123"))))
                .andExpect(status().isCreated());
    }
}
