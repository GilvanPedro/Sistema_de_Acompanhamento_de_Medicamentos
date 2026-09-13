package br.com.domain.model;

import java.util.ArrayList;
import java.util.List;

public class Idoso extends Usuario {
    private List<Familiar> familiares;

    public Idoso(int id, String nome, String email, String senha, List<Familiar> familiares) {
        super(id, nome, email, senha);
        this.familiares = familiares;
    }
    public Idoso(int id, String nome, String email, String senha) {
        super(id, nome, email, senha);
        this.familiares =  new ArrayList<>();
    }

    public List<Familiar> getFamiliares() {
        return familiares;
    }

    public void adicionarFamiliares(Familiar familiar){
        this.familiares.add(familiar);
    }

    @Override
    public String toString() {
        String cuidadores = familiares.isEmpty()
                ? "Nenhum cuidador cadastrado"
                : familiares.stream().map(f -> "id " + f.getId()).collect(java.util.stream.Collectors.joining(", "));

        return String.format("[%d] %s | Idoso | Email: %s | Cuidadores: %s",
                getId(), getNome(), getEmail(), cuidadores);
    }
}
