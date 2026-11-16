package br.com.fiap.ThermoCity.pessoa.controller;

import br.com.fiap.ThermoCity.common.PaginaResponse;
import br.com.fiap.ThermoCity.exception.ErroResponse;
import br.com.fiap.ThermoCity.pessoa.dto.PessoaRequestDTO;
import br.com.fiap.ThermoCity.pessoa.dto.PessoaResponseDTO;
import br.com.fiap.ThermoCity.pessoa.model.Pessoa;
import br.com.fiap.ThermoCity.pessoa.service.PessoaService;
import br.com.fiap.ThermoCity.user.model.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/pessoas")
@Tag(name = "Pessoas acompanhadas", description = """
        Cadastro de idosos (60+) que querem ser contatados pela UBS em dias de calor extremo. \
        Setor censitário e UBS de referência são definidos pela coordenada.""")
public class PessoaController {

    private static final String ERRO_400 = """
            {"status":400,"erro":"Bad Request","mensagem":"É necessário o consentimento para o cadastro",
             "detalhes":[],"timestamp":"2026-11-14T21:10:00-03:00"}""";
    private static final String ERRO_403 = """
            {"status":403,"erro":"Forbidden","mensagem":"Somente o titular do cadastro pode alterá-lo ou excluí-lo",
             "detalhes":[],"timestamp":"2026-11-14T21:10:00-03:00"}""";
    private static final String ERRO_404 = """
            {"status":404,"erro":"Not Found","mensagem":"Pessoa não encontrada",
             "detalhes":[],"timestamp":"2026-11-14T21:10:00-03:00"}""";

    private final PessoaService pessoaService;

    public PessoaController(PessoaService pessoaService) {
        this.pessoaService = pessoaService;
    }

    @PostMapping
    @PreAuthorize("hasRole('CIDADAO')")
    @Operation(summary = "Cadastra uma pessoa acompanhada (CIDADAO)",
            description = """
                    O cidadão autenticado vira o titular do cadastro. Exige consentimento LGPD e 60 anos ou mais. \
                    Setor e UBS de referência são calculados pela coordenada; coordenada fora de Presidente \
                    Prudente é recusada. A data e a hora do consentimento são registradas.""")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Pessoa criada; o cabeçalho Location aponta para ela"),
            @ApiResponse(responseCode = "400", description = """
                    Campos inválidos, sem consentimento, menor de 60 anos ou endereço fora da área atendida""",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class),
                            examples = @ExampleObject(value = ERRO_400))),
            @ApiResponse(responseCode = "401", description = "Token ausente, inválido ou expirado",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "403", description = "Papel diferente de CIDADAO",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    })
    public ResponseEntity<PessoaResponseDTO> cadastrar(@AuthenticationPrincipal User usuario,
                                                       @RequestBody @Valid PessoaRequestDTO dto) {
        Pessoa pessoa = pessoaService.cadastrar(usuario, dto);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(pessoa.getId()).toUri();
        return ResponseEntity.created(location).body(PessoaResponseDTO.from(pessoa, pessoaService.hoje()));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('CIDADAO', 'AGENTE', 'GESTOR')")
    @Operation(summary = "Lista pessoas acompanhadas, paginado e ordenado por nome",
            description = """
                    **CIDADAO**: só as pessoas que cadastrou. **AGENTE**: as da sua UBS (pedir outra UBS dá 403). \
                    **GESTOR**: todas, com filtro opcional por UBS ou setor.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de pessoas"),
            @ApiResponse(responseCode = "400", description = "page negativo ou size fora de 1 a 100",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token ausente, inválido ou expirado",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "403", description = "Agente pedindo outra UBS ou papel sem acesso",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    })
    public PaginaResponse<PessoaResponseDTO> listar(
            @AuthenticationPrincipal User usuario,
            @Parameter(description = "Id da UBS (ex.: CNES-2755114)") @RequestParam(required = false) String ubs,
            @Parameter(description = "Código do setor censitário") @RequestParam(required = false) String setor,
            @Parameter(description = "Parte do nome, sem diferenciar maiúsculas") @RequestParam(required = false)
            String nome,
            @Parameter(description = "Página, começando em 0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Itens por página (1 a 100)") @RequestParam(defaultValue = "10") int size) {
        var hoje = pessoaService.hoje();
        return PaginaResponse.de(pessoaService.listar(usuario, ubs, setor, nome, page, size),
                p -> PessoaResponseDTO.from(p, hoje));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CIDADAO', 'AGENTE', 'GESTOR')")
    @Operation(summary = "Consulta uma pessoa",
            description = "O cidadão só vê os próprios cadastros; o agente, os da sua UBS; o gestor, todos.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pessoa encontrada"),
            @ApiResponse(responseCode = "401", description = "Token ausente, inválido ou expirado",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "403", description = "Cadastro de outro cidadão ou de outra UBS",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "404", description = "Pessoa não encontrada",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class),
                            examples = @ExampleObject(value = ERRO_404)))
    })
    public PessoaResponseDTO buscar(@AuthenticationPrincipal User usuario, @PathVariable Long id) {
        return PessoaResponseDTO.from(pessoaService.buscar(usuario, id), pessoaService.hoje());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('CIDADAO')")
    @Operation(summary = "Altera uma pessoa (só o titular)",
            description = "Substitui todos os campos. Se a coordenada mudar, setor e UBS são recalculados.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pessoa atualizada"),
            @ApiResponse(responseCode = "400", description = "Campos inválidos ou regra de cadastro violada",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token ausente, inválido ou expirado",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "403", description = "O cadastro pertence a outro cidadão",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class),
                            examples = @ExampleObject(value = ERRO_403))),
            @ApiResponse(responseCode = "404", description = "Pessoa não encontrada",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class),
                            examples = @ExampleObject(value = ERRO_404)))
    })
    public PessoaResponseDTO atualizar(@AuthenticationPrincipal User usuario, @PathVariable Long id,
                                       @RequestBody @Valid PessoaRequestDTO dto) {
        return PessoaResponseDTO.from(pessoaService.atualizar(usuario, id, dto), pessoaService.hoje());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('CIDADAO')")
    @Operation(summary = "Exclui uma pessoa (só o titular)",
            description = """
                    Direito de eliminação da LGPD: os dados pessoais são apagados e a pessoa deixa de aparecer \
                    nas listas. O histórico de contatos é mantido, anonimizado (sem pessoa e sem observação).""")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Pessoa excluída"),
            @ApiResponse(responseCode = "401", description = "Token ausente, inválido ou expirado",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "403", description = "O cadastro pertence a outro cidadão",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class),
                            examples = @ExampleObject(value = ERRO_403))),
            @ApiResponse(responseCode = "404", description = "Pessoa não encontrada",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class),
                            examples = @ExampleObject(value = ERRO_404)))
    })
    public ResponseEntity<Void> excluir(@AuthenticationPrincipal User usuario, @PathVariable Long id) {
        pessoaService.excluir(usuario, id);
        return ResponseEntity.noContent().build();
    }
}
