package br.com.adapter.in.web;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import br.com.adapter.in.web.auth.RefreshTokenStore;
import br.com.domain.model.Familiar;
import br.com.domain.model.HistoricoMedicamento;
import br.com.domain.model.Idoso;
import br.com.domain.model.Medicamento;
import br.com.domain.model.PedidoVinculo;
import br.com.domain.model.StatusVinculo;
import br.com.domain.model.Usuario;
import br.com.domain.port.out.GerarIdPort;
import br.com.domain.port.out.SalvarHistoricoPort;
import br.com.domain.port.out.SalvarMedicamentoPort;
import br.com.domain.port.out.SalvarUsuarioPort;

/** Versões em memória das portas, para testar a API sem banco de dados. */
final class PortasEmMemoria {

    private PortasEmMemoria() {
    }

    static class Ids implements GerarIdPort {
        private final AtomicInteger contador = new AtomicInteger();

        @Override
        public int proximoId() {
            return contador.incrementAndGet();
        }
    }

    static class Usuarios implements SalvarUsuarioPort {
        private record Vinculo(StatusVinculo status, LocalDateTime solicitadoEm) { }

        private final Map<Integer, Usuario> usuarios = new LinkedHashMap<>();
        private final Map<String, Vinculo> vinculos = new HashMap<>();

        private static String chave(int idosoId, int familiarId) {
            return idosoId + ":" + familiarId;
        }

        @Override
        public void salvar(Usuario usuario) {
            usuarios.put(usuario.getId(), usuario);
        }

        @Override
        public List<Usuario> listarTodos() {
            return usuarios.values().stream().map(this::comVinculos).toList();
        }

        @Override
        public void salvarVinculo(int idosoId, int familiarId) {
            vinculos.put(chave(idosoId, familiarId), new Vinculo(StatusVinculo.ACEITO, LocalDateTime.now()));
        }

        @Override
        public void atualizar(Usuario usuario) {
            usuarios.put(usuario.getId(), usuario);
        }

        @Override
        public void excluir(int id) {
            usuarios.remove(id);
            vinculos.keySet().removeIf(k -> k.startsWith(id + ":") || k.endsWith(":" + id));
        }

        @Override
        public List<Usuario> buscarPorNome(String nome) {
            return usuarios.values().stream()
                    .filter(u -> u.getNome().toLowerCase().contains(nome.toLowerCase()))
                    .map(this::comVinculos).toList();
        }

        @Override
        public Usuario buscarPorId(int id) {
            Usuario usuario = usuarios.get(id);
            if (usuario == null) {
                throw new NoSuchElementException("Usuário com id: " + id + " não encontrado.");
            }
            return comVinculos(usuario);
        }

        @Override
        public Usuario buscarPorEmail(String email) {
            return usuarios.values().stream()
                    .filter(u -> u.getEmail().equalsIgnoreCase(email))
                    .findFirst().map(this::comVinculos).orElse(null);
        }

        @Override
        public void solicitarVinculo(int idosoId, int familiarId) {
            vinculos.put(chave(idosoId, familiarId), new Vinculo(StatusVinculo.PENDENTE, LocalDateTime.now()));
        }

        @Override
        public StatusVinculo buscarStatusVinculo(int idosoId, int familiarId, Duration validade) {
            Vinculo v = vinculos.get(chave(idosoId, familiarId));
            if (v == null) {
                return null;
            }
            return v.status() == StatusVinculo.PENDENTE && expirou(v, validade) ? null : v.status();
        }

        @Override
        public List<PedidoVinculo> listarPedidosPendentes(int idosoId, Duration validade) {
            List<PedidoVinculo> pedidos = new ArrayList<>();
            vinculos.forEach((k, v) -> {
                String[] ids = k.split(":");
                if (Integer.parseInt(ids[0]) == idosoId && v.status() == StatusVinculo.PENDENTE && !expirou(v, validade)
                        && usuarios.get(Integer.parseInt(ids[1])) instanceof Familiar f) {
                    pedidos.add(new PedidoVinculo(idosoId, new Familiar(f.getId(), f.getNome(), f.getEmail(), f.getSenha()), v.solicitadoEm()));
                }
            });
            return pedidos;
        }

        @Override
        public boolean responderPedidoVinculo(int idosoId, int familiarId, boolean aceitar, Duration validade) {
            Vinculo v = vinculos.get(chave(idosoId, familiarId));
            if (v == null || v.status() != StatusVinculo.PENDENTE || expirou(v, validade)) {
                return false;
            }
            vinculos.put(chave(idosoId, familiarId),
                    new Vinculo(aceitar ? StatusVinculo.ACEITO : StatusVinculo.RECUSADO, v.solicitadoEm()));
            return true;
        }

        @Override
        public void removerVinculo(int idosoId, int familiarId) {
            vinculos.remove(chave(idosoId, familiarId));
        }

        private static boolean expirou(Vinculo v, Duration validade) {
            return v.solicitadoEm().isBefore(LocalDateTime.now().minus(validade));
        }

        /** Cópia do usuário com os vínculos aceitos preenchidos, como o adaptador do banco faz. */
        private Usuario comVinculos(Usuario original) {
            if (original instanceof Idoso idoso) {
                Idoso copia = new Idoso(idoso.getId(), idoso.getNome(), idoso.getEmail(), idoso.getSenha());
                vinculos.forEach((k, v) -> {
                    String[] ids = k.split(":");
                    if (Integer.parseInt(ids[0]) == idoso.getId() && v.status() == StatusVinculo.ACEITO
                            && usuarios.get(Integer.parseInt(ids[1])) instanceof Familiar f) {
                        copia.adicionarFamiliares(new Familiar(f.getId(), f.getNome(), f.getEmail(), f.getSenha()));
                    }
                });
                return copia;
            }
            Familiar familiar = (Familiar) original;
            Familiar copia = new Familiar(familiar.getId(), familiar.getNome(), familiar.getEmail(), familiar.getSenha());
            vinculos.forEach((k, v) -> {
                String[] ids = k.split(":");
                if (Integer.parseInt(ids[1]) == familiar.getId() && v.status() == StatusVinculo.ACEITO
                        && usuarios.get(Integer.parseInt(ids[0])) instanceof Idoso i) {
                    copia.adicionarIdosos(new Idoso(i.getId(), i.getNome(), i.getEmail(), i.getSenha()));
                }
            });
            return copia;
        }
    }

    static class Medicamentos implements SalvarMedicamentoPort {
        private final Map<Integer, Medicamento> medicamentos = new LinkedHashMap<>();

        @Override
        public void salvar(Medicamento medicamento) {
            medicamentos.put(medicamento.getId(), medicamento);
        }

        @Override
        public List<Medicamento> listarTodos() {
            return new ArrayList<>(medicamentos.values());
        }

        @Override
        public void atualizar(Medicamento medicamento) {
            medicamentos.put(medicamento.getId(), medicamento);
        }

        @Override
        public Medicamento buscarPorId(int id) {
            Medicamento m = medicamentos.get(id);
            if (m == null) {
                throw new NoSuchElementException("Medicamento com id: " + id + " não encontrado.");
            }
            return m;
        }

        @Override
        public void excluir(int id) {
            medicamentos.remove(id);
        }
    }

    static class Historico implements SalvarHistoricoPort {
        private final List<HistoricoMedicamento> registros = new ArrayList<>();

        @Override
        public void salvar(HistoricoMedicamento historico) {
            registros.add(historico);
        }

        @Override
        public List<HistoricoMedicamento> listarTodos(Map<Integer, Idoso> idosos, Map<Integer, Medicamento> medicamentos) {
            return new ArrayList<>(registros);
        }

        @Override
        public void atualizar(HistoricoMedicamento historico, Map<Integer, Idoso> idosos, Map<Integer, Medicamento> medicamentos) {
        }

        @Override
        public void excluir(int id) {
            registros.removeIf(h -> h.getId() == id);
        }

        @Override
        public List<HistoricoMedicamento> listarHistoricoPorIdoso(int idIdoso, Map<Integer, Idoso> idosos,
                                                                  Map<Integer, Medicamento> medicamentos) {
            return registros.stream().filter(h -> h.getIdoso().getId() == idIdoso).toList();
        }
    }

    static class Aparelhos implements br.com.adapter.in.web.push.Dispositivos, br.com.adapter.in.web.push.NotificadorPush {
        final Map<String, Integer> tokens = new HashMap<>();
        final java.util.List<Integer> avisados = new java.util.ArrayList<>();

        @Override
        public void registrar(int usuarioId, String token) {
            tokens.put(token, usuarioId);
        }

        @Override
        public void remover(int usuarioId, String token) {
            tokens.remove(token, usuarioId);
        }

        @Override
        public void removerTodos(int usuarioId) {
            tokens.values().removeIf(id -> id == usuarioId);
        }

        @Override
        public void descartar(String token) {
            tokens.remove(token);
        }

        @Override
        public java.util.List<String> tokensDe(int usuarioId) {
            return tokens.entrySet().stream().filter(e -> e.getValue() == usuarioId).map(Map.Entry::getKey).toList();
        }

        @Override
        public void avisarNovidade(int usuarioId) {
            avisados.add(usuarioId);
        }

        @Override
        public String estado() {
            return "TESTE";
        }
    }

    static class Chaves implements br.com.adapter.in.web.idempotencia.ChavesDeIdempotencia {
        private final Map<String, Integer> chaves = new HashMap<>();

        @Override
        public Optional<Integer> buscar(int usuarioId, String chave) {
            return Optional.ofNullable(chaves.get(usuarioId + "|" + chave));
        }

        @Override
        public void salvar(int usuarioId, String chave, int recursoId) {
            chaves.putIfAbsent(usuarioId + "|" + chave, recursoId);
        }
    }

    static class Tokens implements RefreshTokenStore {
        private final Map<String, RefreshRegistro> registros = new HashMap<>();

        @Override
        public void salvar(String hash, int usuarioId, Instant expiraEm) {
            registros.put(hash, new RefreshRegistro(usuarioId, expiraEm, false));
        }

        @Override
        public Optional<RefreshRegistro> buscar(String hash) {
            return Optional.ofNullable(registros.get(hash));
        }

        @Override
        public boolean revogar(String hash) {
            RefreshRegistro r = registros.get(hash);
            if (r == null || r.revogado()) {
                return false;
            }
            registros.put(hash, new RefreshRegistro(r.usuarioId(), r.expiraEm(), true));
            return true;
        }

        @Override
        public void revogarTodos(int usuarioId) {
            registros.replaceAll((h, r) -> r.usuarioId() == usuarioId
                    ? new RefreshRegistro(r.usuarioId(), r.expiraEm(), true) : r);
        }
    }
}
