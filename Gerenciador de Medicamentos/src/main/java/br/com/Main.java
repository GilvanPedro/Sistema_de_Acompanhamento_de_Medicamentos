package br.com;

import br.com.api.NotificacoesApi;
import br.com.model.*;
// Assumindo que TipoMedicamento é um Enum no seu pacote model
// import br.com.model.TipoMedicamento;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;

public class Main {
    public static void main(String[] args) {

        // 1. Instanciando Idosos e Familiares com listas vazias iniciais
        Idoso idoso1 = new Idoso(1, "Antônio Silva", "antonio@email.com", "senha123", new ArrayList<>());
        Idoso idoso2 = new Idoso(2, "Maria Oliveira", "maria@email.com", "senha456", new ArrayList<>());

        Familiar familiar1 = new Familiar(3, "Carlos Silva", "carlos@email.com", "senha789", new ArrayList<>());

        Familiar familiar2 = new Familiar(4, "Ana Maria", "carlos@email.com", "senha789", new ArrayList<>());

        Familiar familiar3 = new Familiar(5, "Carla Auberta", "carlos@email.com", "senha789", new ArrayList<>());

        // 2. Estabelecendo os vínculos (Relação entre Familiar e Idoso)
        // Adicionando idosos ao familiar
        familiar1.adicionarIdosos(idoso1);
        familiar1.adicionarIdosos(idoso2);

        // Adicionando familiares aos idosos
        idoso1.adicionarFamiliares(familiar1);
        idoso1.adicionarFamiliares(familiar3);
        idoso1.adicionarFamiliares(familiar2);
        idoso2.adicionarFamiliares(familiar1);

        // 3. Criando dados de Medicamentos
        // Utilizando LocalTime e DayOfWeek conforme definido na classe
        Medicamento med1 = new Medicamento(
                "Losartana",
                LocalTime.of(8, 0), // 08:00 da manhã
                DayOfWeek.MONDAY,
                TipoMedicamento.COMPRIMIDO
        );

        Medicamento med2 = new Medicamento(
                "Insulina",
                LocalTime.of(12, 30), // 12:30
                DayOfWeek.MONDAY,
                TipoMedicamento.COMPRIMIDO
        );

        // 4. Testando as saídas no console
        System.out.println("--- TESTE DE DADOS ---");
        System.out.println("Idoso: " + idoso1.getNome());
        System.out.println("Familiares responsaveis de " + idoso1.getNome() + ":");

        // Iterando sobre a lista de idosos do familiar
        for (Usuario u : idoso1.getFamiliares()) {
            System.out.println("- " + u.getNome());
        }

        System.out.println();
        NotificacoesApi notificacoesApi = new NotificacoesApi();

        notificacoesApi.notificarEsqueceuRemedio(idoso1, med1);
    }
}