package br.com.adapter.in.console;

import br.com.application.service.RegistrarMedicamentoService;
import br.com.application.service.RegistrarUsuarioService;
import br.com.config.AppConfig;
import br.com.domain.model.*;
import br.com.domain.port.out.GerarIdPort;
import br.com.domain.port.out.SalvarHistoricoPort;
import br.com.domain.port.out.SalvarMedicamentoPort;
import br.com.domain.port.out.SalvarUsuarioPort;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

public class Teste2 {
    public static void main(String[] args) {
        RegistrarUsuarioService registrarUsuarioService = AppConfig.criarRegistrarUsuarioService();
        RegistrarMedicamentoService registrarMedicamentoService = AppConfig.criarRegistrarMedicamentoService();
        SalvarUsuarioPort usuarioPort = AppConfig.getUsuarioPort();
        SalvarMedicamentoPort medicamentoPort = AppConfig.getMedicamentoPort();
        SalvarHistoricoPort historicoPort = AppConfig.getHistoricoPort();
        GerarIdPort gerarIdHistorico = AppConfig.getGerarIdHistorico();

        System.out.println("=== 1. Cadastrando 10 usuários (5 idosos + 5 familiares) ===");

        List<Idoso> idosos = new ArrayList<>();
        idosos.add((Idoso) registrarUsuarioService.registrarIdoso("Pedro Carlos", "pedrocarlos@email.com", "432156"));
        idosos.add((Idoso) registrarUsuarioService.registrarIdoso("Maria das Graças", "mariadasgracas@email.com", "654321"));
        idosos.add((Idoso) registrarUsuarioService.registrarIdoso("José Antônio", "joseantonio@email.com", "abc123"));
        idosos.add((Idoso) registrarUsuarioService.registrarIdoso("Antônia Ferreira", "antoniaferreira@email.com", "senha01"));
        idosos.add((Idoso) registrarUsuarioService.registrarIdoso("Francisco Alves", "franciscoalves@email.com", "senha02"));

        List<Familiar> familiares = new ArrayList<>();
        familiares.add((Familiar) registrarUsuarioService.registrarFamiliar("Marcos Henrique", "marcos@email.com", "34b56gf"));
        familiares.add((Familiar) registrarUsuarioService.registrarFamiliar("Carlos Souza", "carlos@email.com", "senha123"));
        familiares.add((Familiar) registrarUsuarioService.registrarFamiliar("Juliana Lima", "julianalima@email.com", "senha124"));
        familiares.add((Familiar) registrarUsuarioService.registrarFamiliar("Renata Costa", "renatacosta@email.com", "senha125"));
        familiares.add((Familiar) registrarUsuarioService.registrarFamiliar("Bruno Martins", "brunomartins@email.com", "senha126"));

        for (Idoso idoso : idosos) {
            System.out.println("Idoso cadastrado: " + idoso);
        }
        for (Familiar familiar : familiares) {
            System.out.println("Familiar cadastrado: " + familiar);
        }

        System.out.println("\n=== 2. Criando vínculos entre idosos e familiares ===");

        // cada idoso fica com o familiar de mesmo índice
        for (int i = 0; i < idosos.size(); i++) {
            Idoso idoso = idosos.get(i);
            Familiar familiar = familiares.get(i);
            idoso.adicionarFamiliares(familiar);
            familiar.adicionarIdosos(idoso);
            usuarioPort.salvarVinculo(idoso.getId(), familiar.getId());
        }

        // alguns vínculos extras: um segundo familiar cuidando do primeiro e do segundo idoso
        idosos.get(0).adicionarFamiliares(familiares.get(1));
        familiares.get(1).adicionarIdosos(idosos.get(0));
        usuarioPort.salvarVinculo(idosos.get(0).getId(), familiares.get(1).getId());

        idosos.get(1).adicionarFamiliares(familiares.get(2));
        familiares.get(2).adicionarIdosos(idosos.get(1));
        usuarioPort.salvarVinculo(idosos.get(1).getId(), familiares.get(2).getId());

        System.out.println("Vínculos criados.");

        System.out.println("\n=== 3. Cadastrando 10 medicamentos (2 por idoso) ===");

        String[] nomesMedicamentos = {
                "Losartana", "Metformina", "Sinvastatina", "Atenolol", "Omeprazol",
                "Paracetamol", "Insulina NPH", "Vitamina D", "Ácido Acetilsalicílico", "Captopril"
        };
        DayOfWeek[] dias = {
                DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
                DayOfWeek.SATURDAY, DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY
        };
        LocalTime[] horarios = {
                LocalTime.of(8, 0), LocalTime.of(12, 30), LocalTime.of(20, 0), LocalTime.of(9, 15), LocalTime.of(7, 45),
                LocalTime.of(14, 0), LocalTime.of(22, 0), LocalTime.of(10, 0), LocalTime.of(18, 30), LocalTime.of(6, 30)
        };
        TipoMedicamento[] tipos = {
                TipoMedicamento.COMPRIMIDO, TipoMedicamento.COMPRIMIDO, TipoMedicamento.COMPRIMIDO, TipoMedicamento.COMPRIMIDO, TipoMedicamento.COMPRIMIDO,
                TipoMedicamento.COMPRIMIDO, TipoMedicamento.INJECAO, TipoMedicamento.GOTAS, TipoMedicamento.COMPRIMIDO, TipoMedicamento.COMPRIMIDO
        };

        List<Medicamento> medicamentos = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Idoso idosoDoMedicamento = idosos.get(i % idosos.size());
            Medicamento medicamento = registrarMedicamentoService.registrarMedicamento(
                    nomesMedicamentos[i], dias[i], horarios[i], tipos[i], idosoDoMedicamento.getId()
            );
            medicamentos.add(medicamento);
            System.out.println("Medicamento cadastrado: " + medicamento + " (idoso " + idosoDoMedicamento.getId() + ")");
        }

        System.out.println("\n=== 4. Cadastrando 10 históricos de tomada ===");

        for (int i = 0; i < 10; i++) {
            Medicamento medicamento = medicamentos.get(i);
            Idoso idoso = idosos.get(i % idosos.size());
            boolean tomou = i % 3 != 0; // a maioria tomou, alguns não

            int novoId = gerarIdHistorico.proximoId();
            HistoricoMedicamento historico = new HistoricoMedicamento(
                    novoId, medicamento, idoso, LocalDateTime.now().minusHours(i), tomou
            );
            historicoPort.salvar(historico);
            System.out.println("Histórico cadastrado: " + historico);
        }

        System.out.println("\n=== Seed finalizado: 10 usuários, 10 medicamentos, 10 históricos e vínculos criados. ===");
    }
}