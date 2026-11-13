package br.com.fiap.ThermoCity.geolocalizacao;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.Optional;

/**
 * Converte um CEP em latitude e longitude
 * Fonte principal: AwesomeAPI. Reserva: BrasilAPI v2.
 * A coordenada e aproximada (centro do logradouro/faixa do CEP), nao o numero exato da casa.
 */
@Service
public class CepGeocodingService {

    private static final Logger log = LoggerFactory.getLogger(CepGeocodingService.class);

    private final RestClient restClient;
    private final String awesomeApiUrl;
    private final String brasilApiUrl;

    public CepGeocodingService(@Value("${geocoding.awesomeapi.url}") String awesomeApiUrl,
                               @Value("${geocoding.brasilapi.url}") String brasilApiUrl,
                               @Value("${geocoding.timeout-seconds:5}") long timeoutSeconds) {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(timeoutSeconds));
        requestFactory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));

        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .defaultHeader("User-Agent", "ThermoCity-API")
                .build();
        this.awesomeApiUrl = awesomeApiUrl;
        this.brasilApiUrl = brasilApiUrl;
    }

    /**
     * @param cep somente digitos (8)
     * @return coordenadas, ou vazio se o CEP existe mas nenhum servico conseguiu devolve-las
     *         (servicos fora do ar ou CEP sem coordenada cadastrada)
     * @throws CepNaoEncontradoException se os servicos que responderam informaram que o CEP nao existe
     */
    public Optional<Coordenadas> buscarCoordenadas(String cep) {
        boolean cepNaoEncontrado = false;
        boolean cepExiste = false;

        try {
            var resposta = restClient.get().uri(awesomeApiUrl, cep).retrieve().body(AwesomeApiCepResponse.class);
            if (resposta != null) {
                cepExiste = true;
                var coordenadas = converter(resposta.lat(), resposta.lng());
                if (coordenadas.isPresent()) return coordenadas;
            }
        } catch (HttpClientErrorException.NotFound e) {
            cepNaoEncontrado = true;
        } catch (RestClientException e) {
            log.warn("AwesomeAPI indisponível ao geocodificar CEP {}: {}", cep, e.getMessage());
        }

        try {
            var resposta = restClient.get().uri(brasilApiUrl, cep).retrieve().body(BrasilApiCepResponse.class);
            if (resposta != null) {
                cepExiste = true;
                if (resposta.location() != null && resposta.location().coordinates() != null) {
                    var coordenadas = converter(resposta.location().coordinates().latitude(),
                            resposta.location().coordinates().longitude());
                    if (coordenadas.isPresent()) return coordenadas;
                }
            }
        } catch (HttpClientErrorException.NotFound e) {
            cepNaoEncontrado = true;
        } catch (RestClientException e) {
            log.warn("BrasilAPI indisponível ao geocodificar CEP {}: {}", cep, e.getMessage());
        }

        if (cepNaoEncontrado && !cepExiste) {
            throw new CepNaoEncontradoException(cep);
        }
        log.warn("Não foi possível obter coordenadas para o CEP {}; cadastro seguirá sem localização", cep);
        return Optional.empty();
    }

    private Optional<Coordenadas> converter(String latitude, String longitude) {
        if (latitude == null || latitude.isBlank() || longitude == null || longitude.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new Coordenadas(Double.parseDouble(latitude), Double.parseDouble(longitude)));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AwesomeApiCepResponse(String cep, String lat, String lng) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record BrasilApiCepResponse(String cep, Location location) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        record Location(Coordinates coordinates) {
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Coordinates(String latitude, String longitude) {
        }
    }
}
