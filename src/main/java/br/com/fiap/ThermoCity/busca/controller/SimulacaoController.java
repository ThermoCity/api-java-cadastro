package br.com.fiap.ThermoCity.busca.controller;

import br.com.fiap.ThermoCity.busca.dto.SimulacaoNivelRequestDTO;
import br.com.fiap.ThermoCity.busca.evento.EventoNivelCalor;
import br.com.fiap.ThermoCity.busca.mensageria.NivelCalorPublisher;
import br.com.fiap.ThermoCity.busca.service.GeracaoListaService;
import br.com.fiap.ThermoCity.busca.service.ListaConsultaService;
import br.com.fiap.ThermoCity.busca.service.ResultadoProcessamento;
import br.com.fiap.ThermoCity.exception.ErroResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/simulacoes")
@Tag(name = "Simulação de nível de calor", description = """
        Para demonstração: o gestor dispara o mesmo evento que a API de clima publica no RabbitMQ.""")
public class SimulacaoController {

    private final GeracaoListaService geracaoService;
    private final ListaConsultaService consultaService;
    private final NivelCalorPublisher publisher;
    private final Clock clock;

    public SimulacaoController(GeracaoListaService geracaoService, ListaConsultaService consultaService,
                               NivelCalorPublisher publisher, Clock clock) {
        this.geracaoService = geracaoService;
        this.consultaService = consultaService;
        this.publisher = publisher;
        this.clock = clock;
    }

    @PostMapping("/nivel-calor")
    @PreAuthorize("hasRole('GESTOR')")
    @Operation(summary = "Simula a mudança do nível de calor (GESTOR)",
            description = """
                    Gera um evento NIVEL_CALOR_ALTERADO com origem SIMULACAO. Por padrão o evento é processado na \
                    hora e a resposta (200) mostra quantas listas e pessoas foram afetadas. Com viaFila=true ele \
                    é publicado no RabbitMQ e consumido pelo listener, como o evento real (202).""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Evento processado"),
            @ApiResponse(responseCode = "202", description = "Evento publicado na fila"),
            @ApiResponse(responseCode = "400", description = "Nível ausente ou fora de 1 a 5",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "403", description = "Papel diferente de GESTOR",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    })
    public ResponseEntity<ResultadoProcessamento> simular(
            @RequestBody @Valid SimulacaoNivelRequestDTO dto,
            @Parameter(description = "true publica no RabbitMQ em vez de processar na hora")
            @RequestParam(defaultValue = "false") boolean viaFila) {
        LocalDate data = dto.dataLocal() != null ? dto.dataLocal() : consultaService.hoje();
        EventoNivelCalor evento = new EventoNivelCalor("SIM-" + UUID.randomUUID(), EventoNivelCalor.TIPO, data,
                null, dto.nivelNovo(), null, "SIMULACAO", OffsetDateTime.now(clock));

        if (viaFila) {
            publisher.publicar(evento);
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(new ResultadoProcessamento(evento.idEvento(), data,
                    dto.nivelNovo(), null, 0, 0, 0, 0));
        }
        return ResponseEntity.ok(geracaoService.processar(evento));
    }
}
