package br.com.adapter.in.gui;

import java.util.List;

import javax.swing.JTextField;

import br.com.adapter.in.gui.Cartao.Tom;
import br.com.adapter.in.gui.Tema.Papel;
import br.com.application.service.CriarVinculoService;
import br.com.config.AppConfig;
import br.com.domain.exception.DadosInvalidosException;
import br.com.domain.model.Familiar;
import br.com.domain.model.Idoso;
import br.com.domain.model.Usuario;

/** Liga um idoso a um familiar. Mostra quem já está ligado e permite adicionar mais pessoas. */
class TelaVinculo extends Pagina {

    TelaVinculo(Navegador nav, Usuario usuario, Runnable voltar, String mensagem) {
        super(usuario instanceof Idoso ? "Meus familiares" : "Pessoas que eu acompanho", null, voltar);
        boolean souIdoso = usuario instanceof Idoso;
        CriarVinculoService criarVinculo = AppConfig.criarCriarVinculoService();

        List<? extends Usuario> vinculados = souIdoso ? ((Idoso) usuario).getFamiliares() : ((Familiar) usuario).getIdosos();
        if (vinculados.isEmpty()) {
            Cartao vazio = new Cartao();
            vazio.add(Texto.corpo(souIdoso ? "Nenhum familiar vinculado ainda." : "Você ainda não acompanha ninguém."));
            adicionar(vazio);
        }
        for (Usuario v : vinculados) {
            Cartao c = new Cartao();
            c.add(new Texto(v.getNome(), 26, true, Papel.TEXTO));
            c.add(Texto.suave(v.getEmail()));
            adicionar(c);
        }

        JTextField busca = Campo.texto();
        Botao vincular = Botao.primario(souIdoso ? "Vincular familiar" : "Vincular idoso");
        vincular.addActionListener(e -> {
            String entrada = busca.getText().trim();
            if (entrada.isEmpty()) {
                aviso("Digite o e-mail " + (souIdoso ? "do familiar." : "do idoso."), Tom.AVISO);
                return;
            }
            try {
                Usuario outro = localizar(entrada);
                if (souIdoso && !(outro instanceof Familiar)) {
                    throw new DadosInvalidosException("Essa conta é de um idoso. Digite o e-mail de um familiar.");
                }
                if (!souIdoso && !(outro instanceof Idoso)) {
                    throw new DadosInvalidosException("Essa conta é de um familiar. Digite o e-mail de um idoso.");
                }
                if (souIdoso) {
                    criarVinculo.criarVinculo(usuario.getId(), outro.getId());
                } else {
                    criarVinculo.criarVinculo(outro.getId(), usuario.getId());
                }
                nav.atualizarUsuario();
                nav.mostrar(new TelaVinculo(nav, nav.usuario(), voltar, "Pronto! " + outro.getNome() + " foi vinculado."));
            } catch (RuntimeException ex) {
                aviso(Rotulos.erro(ex), Tom.ERRO);
            }
        });

        Cartao novo = new Cartao();
        novo.add(Texto.secao(souIdoso ? "Adicionar um familiar" : "Adicionar um idoso"));
        novo.add(Texto.corpo("A pessoa precisa ter uma conta no CuidaMed. Digite o e-mail dela:"));
        novo.add(busca);
        novo.add(vincular);
        adicionar(novo);

        if (mensagem != null) {
            aviso(mensagem, Tom.OK);
        }
        focoInicial(busca);
        botaoPadrao(vincular);
    }

    private static Usuario localizar(String entrada) {
        Usuario achado;
        try {
            achado = entrada.matches("\\d+") ? AppConfig.getUsuarioPort().buscarPorId(Integer.parseInt(entrada))
                    : AppConfig.getUsuarioPort().buscarPorEmail(entrada);
        } catch (java.util.NoSuchElementException e) {
            achado = null;
        }
        if (achado == null) {
            throw new DadosInvalidosException("Não encontramos nenhuma conta com \"" + entrada + "\". Confira se está escrito certo.");
        }
        return achado;
    }
}
