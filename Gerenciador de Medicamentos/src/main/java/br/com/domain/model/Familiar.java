package br.com.domain.model;

import java.util.ArrayList;
import java.util.List;

public class Familiar extends Usuario {
    private List<Idoso> idosos;

    public Familiar(int id, String nome, String email, String senha, List<Idoso> idosos) {
        super(id, nome, email, senha);
        this.idosos = idosos;
    }
    public Familiar(int id, String nome, String email, String senha) {
        super(id, nome, email, senha);
        this.idosos = new ArrayList<>();
    }

    public List<Idoso> getIdosos() {
        return idosos;
    }

    public void adicionarIdosos(Idoso idoso){
        this.idosos.add(idoso);
    }
}
