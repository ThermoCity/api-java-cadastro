package br.com.fiap.ThermoCity.geolocalizacao;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Simula AwesomeAPI e BrasilAPI com as respostas reais observadas para o CEP 19010-000. */
class CepGeocodingServiceTest {

    private static final String AWESOME_OK = """
            {"cep":"19010000","address":"Rua Barão do Rio Branco","state":"SP",
             "lat":"-22.1228739","lng":"-51.3897085","city":"Presidente Prudente","city_ibge":"3541406"}""";
    private static final String BRASIL_OK = """
            {"cep":"19010000","state":"SP","city":"Presidente Prudente","service":"open-cep",
             "location":{"type":"Point","coordinates":{"longitude":"-51.38889","latitude":"-22.12556"}}}""";
    private static final String BRASIL_SEM_COORDENADAS = """
            {"cep":"19010000","state":"SP","city":"Presidente Prudente","location":{"type":"Point","coordinates":{}}}""";
    private static final String NAO_ENCONTRADO = """
            {"code":"not_found","message":"O CEP nao foi encontrado"}""";

    private HttpServer servidor;
    private int statusAwesome;
    private String corpoAwesome;
    private int statusBrasil;
    private String corpoBrasil;

    @BeforeEach
    void iniciar() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        servidor.createContext("/awesome/", troca -> responder(troca, statusAwesome, corpoAwesome));
        servidor.createContext("/brasil/", troca -> responder(troca, statusBrasil, corpoBrasil));
        servidor.start();
    }

    @AfterEach
    void parar() {
        servidor.stop(0);
    }

    private CepGeocodingService servico() {
        String base = "http://localhost:" + servidor.getAddress().getPort();
        return new CepGeocodingService(base + "/awesome/{cep}", base + "/brasil/{cep}", 2);
    }

    @Test
    void usaAwesomeApiQuandoDisponivel() {
        statusAwesome = 200; corpoAwesome = AWESOME_OK;
        statusBrasil = 500; corpoBrasil = "{}";

        assertThat(servico().buscarCoordenadas("19010000"))
                .contains(new Coordenadas(-22.1228739, -51.3897085));
    }

    @Test
    void usaBrasilApiQuandoAwesomeFalha() {
        statusAwesome = 503; corpoAwesome = "{}";
        statusBrasil = 200; corpoBrasil = BRASIL_OK;

        assertThat(servico().buscarCoordenadas("19010000"))
                .contains(new Coordenadas(-22.12556, -51.38889));
    }

    @Test
    void cepInexistenteNasDuasApisLancaExcecao() {
        statusAwesome = 404; corpoAwesome = NAO_ENCONTRADO;
        statusBrasil = 404; corpoBrasil = NAO_ENCONTRADO;

        assertThatThrownBy(() -> servico().buscarCoordenadas("99999999"))
                .isInstanceOf(CepNaoEncontradoException.class);
    }

    @Test
    void cepExisteMasSemCoordenadasDevolveVazio() {
        statusAwesome = 404; corpoAwesome = NAO_ENCONTRADO;
        statusBrasil = 200; corpoBrasil = BRASIL_SEM_COORDENADAS;

        assertThat(servico().buscarCoordenadas("19010000")).isEmpty();
    }

    @Test
    void ambasForaDoArDevolveVazio() {
        statusAwesome = 500; corpoAwesome = "{}";
        statusBrasil = 502; corpoBrasil = "{}";

        assertThat(servico().buscarCoordenadas("19010000")).isEmpty();
    }

    private static void responder(com.sun.net.httpserver.HttpExchange troca, int status, String corpo)
            throws IOException {
        byte[] bytes = corpo.getBytes(StandardCharsets.UTF_8);
        troca.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        troca.sendResponseHeaders(status, bytes.length);
        try (var saida = troca.getResponseBody()) {
            saida.write(bytes);
        }
    }
}
