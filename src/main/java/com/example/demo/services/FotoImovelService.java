package com.example.demo.services;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import com.example.demo.models.FotoImovelModel;
import com.example.demo.models.ImovelModel;
import com.example.demo.repositories.FotoImovelRepository;
import com.example.demo.repositories.ImovelRepository;

@Service
public class FotoImovelService {
    private final FotoImovelRepository fotoImovelRepository;
    private final ImovelRepository imovelRepository;
    private final Path uploadDir;

    public FotoImovelService(FotoImovelRepository fotoImovelRepository,
            ImovelRepository imovelRepository, @Value("${app.upload-dir}") String uploadDir) {
        this.fotoImovelRepository = fotoImovelRepository;
        this.imovelRepository = imovelRepository;
        this.uploadDir = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    public List<FotoImovelModel> listarPorImovel(Integer id) {
        return fotoImovelRepository.findByImovel_IdOrderByIdAsc(id).stream()
            .sorted(Comparator.comparing(FotoImovelModel::getOrdem,
                Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(FotoImovelModel::getId))
            .toList();
    }

    private FotoImovelModel buscar(Integer id) {
        return fotoImovelRepository.findById(id).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "Foto não encontrada: " + id));
    }

    private Path caminho(FotoImovelModel foto) {
        Path caminho = Paths.get(foto.getCaminho()).toAbsolutePath().normalize();
        if (!caminho.startsWith(uploadDir))
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Caminho da foto inválido.");
        return caminho;
    }

    private Path salvarArquivo(MultipartFile arquivo, Integer imovelId) {
        if (arquivo.isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecione uma foto.");
        String original = arquivo.getOriginalFilename();
        if (original == null || original.isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Arquivo sem nome.");
        String lowerName = original.toLowerCase(java.util.Locale.ROOT);
        if (!(lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg")
                || lowerName.endsWith(".png") || lowerName.endsWith(".gif")))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Envie uma imagem JPG, PNG ou GIF.");
        try (var input = arquivo.getInputStream()) {
            if (javax.imageio.ImageIO.read(input) == null)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Imagem inválida.");
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Não foi possível ler a imagem.", e);
        }
        original = original.replace('\\', '/');
        original = original.substring(original.lastIndexOf('/') + 1);
        String nomeFinal = UUID.randomUUID() + "_" + original;
        if (nomeFinal.length() > 255)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nome do arquivo muito longo.");
        Path pasta = uploadDir.resolve(String.valueOf(imovelId));
        Path destino = pasta.resolve(nomeFinal).normalize();
        if (!destino.startsWith(pasta))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nome de arquivo inválido.");
        try {
            Files.createDirectories(pasta);
            arquivo.transferTo(destino);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Erro ao salvar arquivo.", e);
        }
        return destino;
    }

    private void limparNovoArquivo(Path arquivo, RuntimeException causa) {
        try { Files.deleteIfExists(arquivo); }
        catch (IOException e) { causa.addSuppressed(e); }
    }

    public FotoImovelModel salvar(MultipartFile arquivo, Integer imovelId) {
        ImovelModel imovel = imovelRepository.findById(imovelId).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "Imóvel não encontrado: " + imovelId));
        Path destino = salvarArquivo(arquivo, imovelId);
        FotoImovelModel model = new FotoImovelModel();
        model.setNomeArquivo(destino.getFileName().toString());
        model.setCaminho(destino.toString());
        model.setImovel(imovel);
        model.setCapa(false);
        try { return fotoImovelRepository.save(model); }
        catch (RuntimeException e) { limparNovoArquivo(destino, e); throw e; }
    }

    public FotoImovelModel editar(Integer id, Integer ordem, MultipartFile arquivo) {
        if (ordem == null || ordem < 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe uma ordem maior ou igual a zero.");
        FotoImovelModel foto = buscar(id);
        Path anterior = caminho(foto);
        Path novo = arquivo == null ? null : salvarArquivo(arquivo, foto.getImovel().getId());
        foto.setOrdem(ordem);
        if (novo != null) {
            foto.setNomeArquivo(novo.getFileName().toString());
            foto.setCaminho(novo.toString());
        }
        FotoImovelModel salva;
        try { salva = fotoImovelRepository.saveAndFlush(foto); }
        catch (RuntimeException e) {
            if (novo != null) limparNovoArquivo(novo, e);
            throw e;
        }
        if (novo != null) {
            try { Files.deleteIfExists(anterior); }
            catch (IOException e) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Foto editada, mas não foi possível remover o arquivo anterior.", e);
            }
        }
        return salva;
    }

    @Transactional
    public FotoImovelModel tornarCapa(Integer id) {
        FotoImovelModel escolhida = buscar(id);
        Integer imovelId = escolhida.getImovel().getId();
        // Serializa a escolha da capa para o mesmo imovel.
        imovelRepository.bloquearPorId(imovelId).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "Imóvel não encontrado."));
        List<FotoImovelModel> fotos = fotoImovelRepository.findByImovel_IdOrderByIdAsc(imovelId);
        for (FotoImovelModel foto : fotos) foto.setCapa(foto.getId().equals(id));
        fotoImovelRepository.saveAll(fotos);
        return escolhida;
    }

    public Path arquivo(Integer id) {
        Path caminho = caminho(buscar(id));
        if (!Files.isRegularFile(caminho))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Arquivo não encontrado.");
        return caminho;
    }

    public void deletar(Integer id) {
        FotoImovelModel foto = buscar(id);
        try { Files.deleteIfExists(caminho(foto)); }
        catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Erro ao apagar arquivo.", e);
        }
        fotoImovelRepository.delete(foto);
    }
}