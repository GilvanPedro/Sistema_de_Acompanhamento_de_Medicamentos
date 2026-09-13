package br.com.domain.model;

import java.util.List;

public class Idoso extends Usuario {
    private List<Familiar> familiares;

    public Idoso(int id, String nome, String email, String senha, List<Familiar> familiares) {
        super(id, nome, email, senha);
        this.familiares = familiares;
    }

    public List<Familiar> getFamiliares() {
        return familiares;
    }

    public void adicionarFamiliares(Familiar familiar){
        this.familiares.add(familiar);
    }
}
