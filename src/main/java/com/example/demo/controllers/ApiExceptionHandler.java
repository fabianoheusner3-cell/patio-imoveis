package com.example.demo.controllers;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import java.util.Map;
@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(ResponseStatusException.class) public ResponseEntity<?> resposta(ResponseStatusException ex){return ResponseEntity.status(ex.getStatusCode()).body(Map.of("mensagem",ex.getReason()==null?"Não foi possível concluir a operação.":ex.getReason(),"status",ex.getStatusCode().value()));}
    @ExceptionHandler(DataIntegrityViolationException.class) public ResponseEntity<?> vinculo(){return ResponseEntity.status(409).body(Map.of("mensagem","Este registro tem vínculos com outros dados e não pode ser excluído ou duplicado."));}
    @ExceptionHandler(HttpMessageNotReadableException.class) public ResponseEntity<?> invalido(){return ResponseEntity.badRequest().body(Map.of("mensagem","Confira os campos e os formatos dos dados enviados."));}
}
